package com.framebucket.studio;

import com.framebucket.common.money.PriceAmountParser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Read-only event lookups for the studio features, with photographer / customer ownership checks.
 */
@Component
public class StudioEvents {
    static final String SELECT = """
            SELECT e.id, e.event_code, e.event_date, e.event_time, e.price, e.customer_id, e.photographer_id,
                   e.location_name, t.name AS event_type_name,
                   c.name AS customer_name, c.mobile_number AS customer_mobile, c.email AS customer_email,
                   u.full_name, u.studio_name, u.email AS photographer_email, u.mobile_number AS photographer_mobile,
                   COALESCE(tm.name, f.name) AS assignee_name,
                   COALESCE(tm.mobile_number, f.mobile_number) AS assignee_mobile
            FROM fb_event_booking e
            JOIN fb_event_list t ON t.id = e.event_type_id
            JOIN fb_customer c ON c.id = e.customer_id
            JOIN fb_user u ON u.id = e.photographer_id
            LEFT JOIN fb_team_member tm ON tm.id = e.team_member_assignee_id
            LEFT JOIN fb_freelancer f ON f.id = e.freelancer_assignee_id
            """;

    public record EventInfo(
            UUID id,
            String eventCode,
            LocalDate eventDate,
            LocalTime eventTime,
            String price,
            UUID customerId,
            UUID photographerId,
            String locationName,
            String eventTypeName,
            String customerName,
            String customerMobile,
            String customerEmail,
            String photographerFullName,
            String studioName,
            String photographerEmail,
            String photographerMobile,
            String assigneeName,
            String assigneeMobile
    ) {
        public String studioDisplayName() {
            return studioName == null || studioName.isBlank() ? photographerFullName : studioName;
        }

        /** Agreed event price parsed from the free-text price field, or zero when it is not a plain amount. */
        public BigDecimal totalAmount() {
            BigDecimal parsed = PriceAmountParser.parse(price);
            return parsed == null ? BigDecimal.ZERO.setScale(2) : parsed;
        }
    }

    static final RowMapper<EventInfo> MAPPER = (rs, rowNum) -> {
        Date date = rs.getDate("event_date");
        Time time = rs.getTime("event_time");
        return new EventInfo(
                rs.getObject("id", UUID.class),
                rs.getString("event_code"),
                date == null ? null : date.toLocalDate(),
                time == null ? null : time.toLocalTime(),
                rs.getString("price"),
                rs.getObject("customer_id", UUID.class),
                rs.getObject("photographer_id", UUID.class),
                rs.getString("location_name"),
                rs.getString("event_type_name"),
                rs.getString("customer_name"),
                rs.getString("customer_mobile"),
                rs.getString("customer_email"),
                rs.getString("full_name"),
                rs.getString("studio_name"),
                rs.getString("photographer_email"),
                rs.getString("photographer_mobile"),
                rs.getString("assignee_name"),
                rs.getString("assignee_mobile")
        );
    };

    private final JdbcTemplate jdbc;

    public StudioEvents(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public EventInfo forPhotographer(UUID eventId, UUID photographerId) {
        return jdbc.query(SELECT + " WHERE e.id = ? AND e.photographer_id = ?", MAPPER, eventId, photographerId)
                .stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."));
    }

    public EventInfo forCustomer(UUID eventId, Collection<UUID> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.");
        }
        return jdbc.query(SELECT + " WHERE e.id = ? AND e.customer_id = ANY (?)",
                        ps -> {
                            ps.setObject(1, eventId);
                            ps.setArray(2, ps.getConnection().createArrayOf("uuid", customerIds.toArray()));
                        }, MAPPER)
                .stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."));
    }

    public EventInfo byId(UUID eventId) {
        return jdbc.query(SELECT + " WHERE e.id = ?", MAPPER, eventId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found."));
    }

    public List<EventInfo> allForPhotographer(UUID photographerId) {
        return jdbc.query(SELECT + " WHERE e.photographer_id = ? ORDER BY e.event_date DESC, e.created_at DESC",
                MAPPER, photographerId);
    }

    public List<EventInfo> onDate(LocalDate date) {
        return jdbc.query(SELECT + " WHERE e.event_date = ?", MAPPER, Date.valueOf(date));
    }
}
