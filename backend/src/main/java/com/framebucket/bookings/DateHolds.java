package com.framebucket.bookings;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Calendar holds created when a client signs a contract, released when the event is created or the contract is cancelled. */
@Component
public class DateHolds {
    private final JdbcTemplate jdbc;

    public DateHolds(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<LocalDate> heldDates(UUID photographerId) {
        return jdbc.query("SELECT hold_date FROM fb_date_hold WHERE photographer_id = ? ORDER BY hold_date",
                (rs, n) -> rs.getDate(1).toLocalDate(), photographerId);
    }

    public Optional<UUID> holder(UUID photographerId, LocalDate date) {
        return jdbc.query("SELECT enquiry_id FROM fb_date_hold WHERE photographer_id = ? AND hold_date = ?",
                (rs, n) -> rs.getObject(1, UUID.class), photographerId, Date.valueOf(date)).stream().findFirst();
    }

    public boolean eventBooked(UUID photographerId, LocalDate date) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM fb_event_booking WHERE photographer_id = ? AND event_date = ?",
                Integer.class, photographerId, Date.valueOf(date));
        return count != null && count > 0;
    }

    /** Throws 409 when the date is booked or held for a different enquiry. */
    public void requireAvailable(UUID photographerId, LocalDate date, UUID enquiryId) {
        if (eventBooked(photographerId, date)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That date is already booked for another event.");
        }
        Optional<UUID> holder = holder(photographerId, date);
        if (holder.isPresent() && !holder.get().equals(enquiryId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That date is already on hold for another signed contract.");
        }
    }

    public void hold(UUID photographerId, LocalDate date, UUID enquiryId) {
        requireAvailable(photographerId, date, enquiryId);
        release(enquiryId);
        try {
            jdbc.update("INSERT INTO fb_date_hold (id, photographer_id, hold_date, enquiry_id) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID(), photographerId, Date.valueOf(date), enquiryId);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That date was just reserved for another booking.");
        }
    }

    public void release(UUID enquiryId) {
        jdbc.update("DELETE FROM fb_date_hold WHERE enquiry_id = ?", enquiryId);
    }
}
