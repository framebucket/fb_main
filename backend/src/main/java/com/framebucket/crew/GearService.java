package com.framebucket.crew;

import com.framebucket.crew.CrewDtos.CostLine;
import com.framebucket.crew.CrewDtos.CostRequest;
import com.framebucket.crew.CrewDtos.EventCosts;
import com.framebucket.crew.CrewDtos.EventGear;
import com.framebucket.crew.CrewDtos.EventGearRequest;
import com.framebucket.crew.CrewDtos.EventGearUpdate;
import com.framebucket.crew.CrewDtos.GearItem;
import com.framebucket.crew.CrewDtos.GearRequest;
import com.framebucket.studio.StudioEvents;
import com.framebucket.subscription.EntitlementService;
import com.framebucket.subscription.Feature;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.util.List;
import java.util.UUID;

/** Gear inventory and per-event packing checklists, plus event costs (travel and other expenses, crew fees) and profit. */
@Service
public class GearService {
    private final JdbcTemplate jdbc;
    private final StudioEvents events;
    private final EntitlementService entitlements;

    public GearService(JdbcTemplate jdbc, StudioEvents events, EntitlementService entitlements) {
        this.jdbc = jdbc;
        this.events = events;
        this.entitlements = entitlements;
    }

    public List<GearItem> items(UUID photographerId) {
        return jdbc.query("SELECT * FROM fb_gear_item WHERE photographer_id = ? ORDER BY active DESC, category, name",
                (rs, n) -> new GearItem(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("category"),
                        rs.getString("serial_number"), rs.getBoolean("in_default_kit"), rs.getBoolean("active"), rs.getString("notes")),
                photographerId);
    }

    @Transactional
    public List<GearItem> saveItem(UUID photographerId, UUID gearId, GearRequest request) {
        entitlements.require(photographerId, Feature.GEAR_COSTS);
        String category = request.category() == null || request.category().isBlank() ? "Other" : request.category().strip();
        if (gearId == null) {
            Integer count = jdbc.queryForObject("SELECT count(*) FROM fb_gear_item WHERE photographer_id = ?", Integer.class, photographerId);
            if (count != null && count >= 500) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "You can track up to 500 gear items.");
            jdbc.update("INSERT INTO fb_gear_item (id, photographer_id, name, category, serial_number, in_default_kit, active, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), photographerId, request.name().strip(), category, blank(request.serialNumber()),
                    request.inDefaultKit(), request.active(), blank(request.notes()));
        } else if (jdbc.update("UPDATE fb_gear_item SET name = ?, category = ?, serial_number = ?, in_default_kit = ?, active = ?, notes = ? WHERE id = ? AND photographer_id = ?",
                request.name().strip(), category, blank(request.serialNumber()), request.inDefaultKit(), request.active(),
                blank(request.notes()), gearId, photographerId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Gear item not found.");
        }
        return items(photographerId);
    }

    @Transactional
    public List<GearItem> deleteItem(UUID photographerId, UUID gearId) {
        if (jdbc.update("DELETE FROM fb_gear_item WHERE id = ? AND photographer_id = ?", gearId, photographerId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Gear item not found.");
        }
        return items(photographerId);
    }

    public List<EventGear> eventGear(UUID photographerId, UUID eventId) {
        events.forPhotographer(eventId, photographerId);
        return jdbc.query("""
                SELECT g.id, g.name, g.category, g.serial_number, eg.packed, eg.returned
                FROM fb_event_gear eg JOIN fb_gear_item g ON g.id = eg.gear_id
                WHERE eg.event_id = ? ORDER BY g.category, g.name
                """, (rs, n) -> new EventGear(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getBoolean(5), rs.getBoolean(6)), eventId);
    }

    @Transactional
    public List<EventGear> addToEvent(UUID photographerId, UUID eventId, EventGearRequest request) {
        entitlements.require(photographerId, Feature.GEAR_COSTS);
        events.forPhotographer(eventId, photographerId);
        if (request.defaultKit()) {
            jdbc.update("""
                    INSERT INTO fb_event_gear (event_id, gear_id)
                    SELECT ?, id FROM fb_gear_item WHERE photographer_id = ? AND in_default_kit AND active
                    ON CONFLICT DO NOTHING
                    """, eventId, photographerId);
        }
        if (request.gearIds() != null) {
            for (UUID gearId : request.gearIds()) {
                jdbc.update("""
                        INSERT INTO fb_event_gear (event_id, gear_id)
                        SELECT ?, id FROM fb_gear_item WHERE id = ? AND photographer_id = ?
                        ON CONFLICT DO NOTHING
                        """, eventId, gearId, photographerId);
            }
        }
        return eventGear(photographerId, eventId);
    }

    @Transactional
    public List<EventGear> updateEventGear(UUID photographerId, UUID eventId, UUID gearId, EventGearUpdate update) {
        events.forPhotographer(eventId, photographerId);
        jdbc.update("UPDATE fb_event_gear SET packed = COALESCE(?, packed), returned = COALESCE(?, returned) WHERE event_id = ? AND gear_id = ?",
                update.packed(), update.returned(), eventId, gearId);
        return eventGear(photographerId, eventId);
    }

    @Transactional
    public List<EventGear> removeFromEvent(UUID photographerId, UUID eventId, UUID gearId) {
        events.forPhotographer(eventId, photographerId);
        jdbc.update("DELETE FROM fb_event_gear WHERE event_id = ? AND gear_id = ?", eventId, gearId);
        return eventGear(photographerId, eventId);
    }

    // ---- costs & profit ----

    public EventCosts costs(UUID photographerId, UUID eventId) {
        StudioEvents.EventInfo event = events.forPhotographer(eventId, photographerId);
        List<CostLine> expenses = jdbc.query("SELECT * FROM fb_expense WHERE event_id = ? AND photographer_id = ? ORDER BY spent_on, created_at",
                (rs, n) -> new CostLine(rs.getObject("id", UUID.class), rs.getString("category"), rs.getBigDecimal("amount"),
                        rs.getDate("spent_on").toLocalDate(), rs.getString("vendor"), rs.getString("notes")), eventId, photographerId);
        BigDecimal expenseTotal = expenses.stream().map(CostLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal crew = jdbc.queryForObject("SELECT COALESCE(sum(fee), 0) FROM fb_event_crew WHERE event_id = ?", BigDecimal.class, eventId);
        BigDecimal received = jdbc.queryForObject(
                "SELECT COALESCE(sum(amount), 0) FROM fb_event_payment WHERE event_id = ? AND purpose <> 'DOWNLOAD'", BigDecimal.class, eventId);
        BigDecimal downloads = jdbc.queryForObject(
                "SELECT COALESCE(sum(amount), 0) FROM fb_event_payment WHERE event_id = ? AND purpose = 'DOWNLOAD'", BigDecimal.class, eventId);
        BigDecimal agreed = event.totalAmount();
        BigDecimal revenue = agreed.add(downloads);
        BigDecimal totalCosts = expenseTotal.add(crew);
        BigDecimal profit = revenue.subtract(totalCosts);
        BigDecimal margin = revenue.signum() > 0 ? profit.multiply(BigDecimal.valueOf(100)).divide(revenue, 1, RoundingMode.HALF_UP) : null;
        return new EventCosts(agreed, received, agreed.subtract(received).max(BigDecimal.ZERO), expenses, expenseTotal, crew,
                totalCosts, profit, margin);
    }

    @Transactional
    public EventCosts addCost(UUID photographerId, UUID eventId, CostRequest request) {
        entitlements.require(photographerId, Feature.GEAR_COSTS);
        events.forPhotographer(eventId, photographerId);
        String category = request.category() == null || request.category().isBlank() ? "Travel" : request.category().strip();
        jdbc.update("INSERT INTO fb_expense (id, photographer_id, event_id, category, amount, spent_on, vendor, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), photographerId, eventId, category, request.amount(), Date.valueOf(request.spentOn()),
                blank(request.vendor()), blank(request.notes()));
        return costs(photographerId, eventId);
    }

    @Transactional
    public EventCosts deleteCost(UUID photographerId, UUID eventId, UUID expenseId) {
        events.forPhotographer(eventId, photographerId);
        if (jdbc.update("DELETE FROM fb_expense WHERE id = ? AND event_id = ? AND photographer_id = ?", expenseId, eventId, photographerId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cost not found.");
        }
        return costs(photographerId, eventId);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
