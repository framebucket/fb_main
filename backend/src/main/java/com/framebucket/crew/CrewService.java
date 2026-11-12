package com.framebucket.crew;

import com.framebucket.crew.CrewDtos.AvailabilityCheck;
import com.framebucket.crew.CrewDtos.Balance;
import com.framebucket.crew.CrewDtos.CrewRequest;
import com.framebucket.crew.CrewDtos.CrewView;
import com.framebucket.crew.CrewDtos.Member;
import com.framebucket.crew.CrewDtos.PayoutRequest;
import com.framebucket.crew.CrewDtos.PayoutView;
import com.framebucket.crew.CrewDtos.UnavailableRequest;
import com.framebucket.crew.CrewDtos.UnavailableView;
import com.framebucket.studio.StudioEvents;
import com.framebucket.subscription.EntitlementService;
import com.framebucket.subscription.Feature;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Crew per event (team members and freelancers with role, call time and fee), clash alerts, unavailable dates,
 * and a payouts ledger with what each person is owed.
 */
@Service
public class CrewService {
    private static final String MEMBERS = """
            SELECT 'TEAM' AS member_type, id, name, mobile_number, status FROM fb_team_member WHERE photographer_id = ?
            UNION ALL
            SELECT 'FREELANCER', id, name, mobile_number, status FROM fb_freelancer WHERE photographer_id = ?
            """;

    private final JdbcTemplate jdbc;
    private final StudioEvents events;
    private final EntitlementService entitlements;

    public CrewService(JdbcTemplate jdbc, StudioEvents events, EntitlementService entitlements) {
        this.jdbc = jdbc;
        this.events = events;
        this.entitlements = entitlements;
    }

    public List<Member> members(UUID photographerId) {
        return jdbc.query(MEMBERS + " ORDER BY 3", (rs, n) -> new Member(rs.getString(1), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getString(4), rs.getString(5)), photographerId, photographerId);
    }

    public List<CrewView> eventCrew(UUID photographerId, UUID eventId) {
        StudioEvents.EventInfo event = events.forPhotographer(eventId, photographerId);
        return jdbc.query("""
                SELECT c.*, COALESCE(tm.name, f.name) AS name, COALESCE(tm.mobile_number, f.mobile_number) AS mobile
                FROM fb_event_crew c
                LEFT JOIN fb_team_member tm ON tm.id = c.team_member_id
                LEFT JOIN fb_freelancer f ON f.id = c.freelancer_id
                WHERE c.event_id = ? ORDER BY c.created_at
                """, (rs, n) -> {
            boolean team = rs.getObject("team_member_id") != null;
            UUID memberId = team ? rs.getObject("team_member_id", UUID.class) : rs.getObject("freelancer_id", UUID.class);
            Time call = rs.getTime("call_time");
            return new CrewView(rs.getObject("id", UUID.class), team ? "TEAM" : "FREELANCER", memberId, rs.getString("name"),
                    rs.getString("mobile"), rs.getString("role"), call == null ? null : call.toLocalTime(), rs.getBigDecimal("fee"),
                    rs.getString("notes"), warnings(photographerId, team ? "TEAM" : "FREELANCER", memberId, rs.getString("mobile"),
                            event.eventDate(), eventId));
        }, eventId);
    }

    public AvailabilityCheck check(UUID photographerId, UUID eventId, String memberType, UUID memberId) {
        StudioEvents.EventInfo event = events.forPhotographer(eventId, photographerId);
        Member member = member(photographerId, memberType, memberId);
        return new AvailabilityCheck(memberType, memberId, warnings(photographerId, memberType, memberId, member.mobileNumber(),
                event.eventDate(), eventId));
    }

    @Transactional
    public List<CrewView> add(UUID photographerId, UUID eventId, CrewRequest request) {
        entitlements.require(photographerId, Feature.CREW_PAYOUTS);
        events.forPhotographer(eventId, photographerId);
        member(photographerId, request.memberType(), request.memberId());
        boolean team = "TEAM".equals(request.memberType());
        try {
            jdbc.update("""
                            INSERT INTO fb_event_crew (id, photographer_id, event_id, team_member_id, freelancer_id, role, call_time, fee, notes)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """, UUID.randomUUID(), photographerId, eventId, team ? request.memberId() : null,
                    team ? null : request.memberId(), request.role().strip(),
                    request.callTime() == null ? null : Time.valueOf(request.callTime()),
                    request.fee() == null ? BigDecimal.ZERO : request.fee(), blankToNull(request.notes()));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That person is already on this event's crew.");
        }
        return eventCrew(photographerId, eventId);
    }

    @Transactional
    public List<CrewView> update(UUID photographerId, UUID eventId, UUID crewId, CrewRequest request) {
        entitlements.require(photographerId, Feature.CREW_PAYOUTS);
        events.forPhotographer(eventId, photographerId);
        if (jdbc.update("UPDATE fb_event_crew SET role = ?, call_time = ?, fee = ?, notes = ? WHERE id = ? AND event_id = ? AND photographer_id = ?",
                request.role().strip(), request.callTime() == null ? null : Time.valueOf(request.callTime()),
                request.fee() == null ? BigDecimal.ZERO : request.fee(), blankToNull(request.notes()), crewId, eventId, photographerId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Crew assignment not found.");
        }
        return eventCrew(photographerId, eventId);
    }

    @Transactional
    public List<CrewView> remove(UUID photographerId, UUID eventId, UUID crewId) {
        events.forPhotographer(eventId, photographerId);
        if (jdbc.update("DELETE FROM fb_event_crew WHERE id = ? AND event_id = ? AND photographer_id = ?", crewId, eventId, photographerId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Crew assignment not found.");
        }
        return eventCrew(photographerId, eventId);
    }

    // ---- unavailable dates ----

    public List<UnavailableView> unavailable(UUID photographerId) {
        return jdbc.query("""
                SELECT u.*, COALESCE(tm.name, f.name) AS name FROM fb_crew_unavailable u
                LEFT JOIN fb_team_member tm ON tm.id = u.team_member_id LEFT JOIN fb_freelancer f ON f.id = u.freelancer_id
                WHERE u.photographer_id = ? AND u.unavailable_date >= CURRENT_DATE - 30 ORDER BY u.unavailable_date
                """, (rs, n) -> {
            boolean team = rs.getObject("team_member_id") != null;
            return new UnavailableView(rs.getObject("id", UUID.class), team ? "TEAM" : "FREELANCER",
                    team ? rs.getObject("team_member_id", UUID.class) : rs.getObject("freelancer_id", UUID.class), rs.getString("name"),
                    rs.getDate("unavailable_date").toLocalDate(), rs.getString("note"));
        }, photographerId);
    }

    @Transactional
    public List<UnavailableView> addUnavailable(UUID photographerId, UnavailableRequest request) {
        entitlements.require(photographerId, Feature.CREW_PAYOUTS);
        member(photographerId, request.memberType(), request.memberId());
        boolean team = "TEAM".equals(request.memberType());
        jdbc.update("INSERT INTO fb_crew_unavailable (id, photographer_id, team_member_id, freelancer_id, unavailable_date, note) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), photographerId, team ? request.memberId() : null, team ? null : request.memberId(),
                Date.valueOf(request.date()), blankToNull(request.note()));
        return unavailable(photographerId);
    }

    @Transactional
    public List<UnavailableView> deleteUnavailable(UUID photographerId, UUID id) {
        jdbc.update("DELETE FROM fb_crew_unavailable WHERE id = ? AND photographer_id = ?", id, photographerId);
        return unavailable(photographerId);
    }

    // ---- payouts ----

    public List<PayoutView> payouts(UUID photographerId) {
        return jdbc.query("""
                SELECT p.*, COALESCE(tm.name, f.name) AS name, e.event_code FROM fb_crew_payout p
                LEFT JOIN fb_team_member tm ON tm.id = p.team_member_id LEFT JOIN fb_freelancer f ON f.id = p.freelancer_id
                LEFT JOIN fb_event_booking e ON e.id = p.event_id
                WHERE p.photographer_id = ? ORDER BY p.paid_on DESC, p.created_at DESC LIMIT 500
                """, (rs, n) -> {
            boolean team = rs.getObject("team_member_id") != null;
            return new PayoutView(rs.getObject("id", UUID.class), team ? "TEAM" : "FREELANCER",
                    team ? rs.getObject("team_member_id", UUID.class) : rs.getObject("freelancer_id", UUID.class), rs.getString("name"),
                    rs.getObject("event_id", UUID.class), rs.getString("event_code"), rs.getBigDecimal("amount"),
                    rs.getDate("paid_on").toLocalDate(), rs.getString("method"), rs.getString("reference"), rs.getString("notes"));
        }, photographerId);
    }

    @Transactional
    public List<PayoutView> addPayout(UUID photographerId, PayoutRequest request) {
        entitlements.require(photographerId, Feature.CREW_PAYOUTS);
        member(photographerId, request.memberType(), request.memberId());
        if (request.eventId() != null) events.forPhotographer(request.eventId(), photographerId);
        boolean team = "TEAM".equals(request.memberType());
        jdbc.update("""
                        INSERT INTO fb_crew_payout (id, photographer_id, team_member_id, freelancer_id, event_id, amount, paid_on, method, reference, notes)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), photographerId, team ? request.memberId() : null, team ? null : request.memberId(),
                request.eventId(), request.amount(), Date.valueOf(request.paidOn()), request.method(), blankToNull(request.reference()),
                blankToNull(request.notes()));
        return payouts(photographerId);
    }

    @Transactional
    public List<PayoutView> deletePayout(UUID photographerId, UUID payoutId) {
        if (jdbc.update("DELETE FROM fb_crew_payout WHERE id = ? AND photographer_id = ?", payoutId, photographerId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payout not found.");
        }
        return payouts(photographerId);
    }

    /** Earned = fees for events up to today; due = earned - paid (negative means paid in advance). */
    public List<Balance> balances(UUID photographerId) {
        return jdbc.query("""
                WITH members AS (
                    SELECT 'TEAM' AS member_type, id, name, mobile_number FROM fb_team_member WHERE photographer_id = ?
                    UNION ALL
                    SELECT 'FREELANCER', id, name, mobile_number FROM fb_freelancer WHERE photographer_id = ?
                ), fees AS (
                    SELECT COALESCE(c.team_member_id, c.freelancer_id) AS member_id, count(*) AS assignments,
                           COALESCE(sum(c.fee) FILTER (WHERE e.event_date <= CURRENT_DATE), 0) AS earned,
                           COALESCE(sum(c.fee) FILTER (WHERE e.event_date > CURRENT_DATE), 0) AS upcoming
                    FROM fb_event_crew c JOIN fb_event_booking e ON e.id = c.event_id
                    WHERE c.photographer_id = ? GROUP BY 1
                ), paid AS (
                    SELECT COALESCE(team_member_id, freelancer_id) AS member_id, sum(amount) AS paid
                    FROM fb_crew_payout WHERE photographer_id = ? GROUP BY 1
                )
                SELECT m.member_type, m.id, m.name, m.mobile_number, COALESCE(f.assignments, 0) AS assignments,
                       COALESCE(f.earned, 0) AS earned, COALESCE(f.upcoming, 0) AS upcoming, COALESCE(p.paid, 0) AS paid
                FROM members m LEFT JOIN fees f ON f.member_id = m.id LEFT JOIN paid p ON p.member_id = m.id
                ORDER BY (COALESCE(f.earned, 0) - COALESCE(p.paid, 0)) DESC, m.name
                """, (rs, n) -> {
            BigDecimal earned = rs.getBigDecimal("earned");
            BigDecimal paid = rs.getBigDecimal("paid");
            return new Balance(rs.getString(1), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4),
                    rs.getLong("assignments"), earned, rs.getBigDecimal("upcoming"), paid, earned.subtract(paid));
        }, photographerId, photographerId, photographerId, photographerId);
    }

    // ---- helpers ----

    private Member member(UUID photographerId, String memberType, UUID memberId) {
        String table = "TEAM".equals(memberType) ? "fb_team_member" : "fb_freelancer";
        return jdbc.query("SELECT id, name, mobile_number, status FROM " + table + " WHERE id = ? AND photographer_id = ?",
                        (rs, n) -> new Member(memberType, rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4)),
                        memberId, photographerId)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team member not found."));
    }

    private List<String> warnings(UUID photographerId, String memberType, UUID memberId, String mobile, LocalDate date, UUID eventId) {
        List<String> warnings = new ArrayList<>();
        if (date == null) return warnings;
        String column = "TEAM".equals(memberType) ? "team_member_id" : "freelancer_id";
        jdbc.query("SELECT note FROM fb_crew_unavailable WHERE photographer_id = ? AND " + column + " = ? AND unavailable_date = ?",
                (rs, n) -> rs.getString(1), photographerId, memberId, Date.valueOf(date))
                .forEach(note -> warnings.add("Marked unavailable on this date" + (note == null ? "." : ": " + note)));
        if (mobile != null && !mobile.isBlank()) {
            Integer elsewhere = jdbc.queryForObject("""
                    SELECT count(*) FROM (
                        SELECT e.id, e.photographer_id FROM fb_event_crew c
                        JOIN fb_event_booking e ON e.id = c.event_id
                        LEFT JOIN fb_team_member tm ON tm.id = c.team_member_id
                        LEFT JOIN fb_freelancer f ON f.id = c.freelancer_id
                        WHERE e.event_date = ? AND e.id <> ? AND COALESCE(tm.mobile_number, f.mobile_number) = ?
                        UNION
                        SELECT e.id, e.photographer_id FROM fb_event_booking e
                        LEFT JOIN fb_team_member tm ON tm.id = e.team_member_assignee_id
                        LEFT JOIN fb_freelancer f ON f.id = e.freelancer_assignee_id
                        WHERE e.event_date = ? AND e.id <> ? AND COALESCE(tm.mobile_number, f.mobile_number) = ?
                    ) other
                    """, Integer.class, Date.valueOf(date), eventId, mobile, Date.valueOf(date), eventId, mobile);
            if (elsewhere != null && elsewhere > 0) {
                warnings.add("Already booked for another shoot on this date (on FrameBucket).");
            }
        }
        return warnings;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
