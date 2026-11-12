package com.framebucket.bookings;

import com.framebucket.bookings.BookingDtos.ContractSummary;
import com.framebucket.bookings.BookingDtos.EnquiryTracking;
import com.framebucket.common.notify.Notifier;
import com.framebucket.subscription.EntitlementService;
import com.framebucket.subscription.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Quote follow-ups: if a client hasn't opened their quotation link 48 hours after the enquiry, FrameBucket
 * emails it again (once). Photographers can also send a reminder by hand, at most every 12 hours.
 */
@Service
public class FollowUpService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FollowUpService.class);

    record Candidate(UUID id, UUID photographerId, String name, String email, String eventType, boolean quoted,
                     String contractStatus) {
    }

    private final JdbcTemplate jdbc;
    private final QuoteTokens tokens;
    private final Notifier notifier;
    private final EntitlementService entitlements;

    public FollowUpService(JdbcTemplate jdbc, QuoteTokens tokens, Notifier notifier, EntitlementService entitlements) {
        this.jdbc = jdbc;
        this.tokens = tokens;
        this.notifier = notifier;
        this.entitlements = entitlements;
    }

    @Scheduled(cron = "${app.quote-follow-up.cron:0 20 * * * *}", zone = "Asia/Kolkata")
    public void sendDueReminders() {
        List<Candidate> due = jdbc.query("""
                SELECT e.id, e.photographer_id, e.name, e.email, t.name AS event_type, FALSE AS quoted, NULL AS contract_status
                FROM fb_enquiries e JOIN fb_event_list t ON t.id = e.event_type_id
                WHERE e.quote_opened_at IS NULL AND e.quote_reminder_sent_at IS NULL AND e.quote_token_enc IS NOT NULL
                  AND e.created_at < now() - interval '48 hours' AND e.created_at > now() - interval '30 days'
                  AND NOT EXISTS (SELECT 1 FROM fb_quotaion q WHERE q.enquiry_id = e.id)
                ORDER BY e.created_at LIMIT 200
                """, (rs, n) -> new Candidate(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getString(4), rs.getString(5), false, null));
        int sent = 0;
        for (Candidate candidate : due) {
            if (!entitlements.has(candidate.photographerId(), Feature.CONTRACTS)) continue;
            // Claim first so two instances never email the same client twice.
            if (jdbc.update("UPDATE fb_enquiries SET quote_reminder_sent_at = CURRENT_TIMESTAMP WHERE id = ? AND quote_reminder_sent_at IS NULL",
                    candidate.id()) == 1) {
                sendReminder(candidate);
                sent++;
            }
        }
        if (sent > 0) LOGGER.info("Sent {} quotation follow-up reminders.", sent);
    }

    @Transactional
    public EnquiryTracking remindNow(UUID photographerId, UUID enquiryId) {
        entitlements.require(photographerId, Feature.CONTRACTS);
        Candidate candidate = jdbc.query("""
                        SELECT e.id, e.photographer_id, e.name, e.email, t.name AS event_type,
                               EXISTS (SELECT 1 FROM fb_quotaion q WHERE q.enquiry_id = e.id) AS quoted,
                               (SELECT status FROM fb_contract c WHERE c.enquiry_id = e.id) AS contract_status,
                               e.quote_reminder_sent_at
                        FROM fb_enquiries e JOIN fb_event_list t ON t.id = e.event_type_id
                        WHERE e.id = ? AND e.photographer_id = ?
                        """, (rs, n) -> {
                    Instant last = rs.getTimestamp("quote_reminder_sent_at") == null ? null : rs.getTimestamp("quote_reminder_sent_at").toInstant();
                    if (last != null && last.isAfter(Instant.now().minus(Duration.ofHours(12)))) {
                        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "A reminder was sent in the last 12 hours.");
                    }
                    return new Candidate(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getBoolean(6), rs.getString(7));
                }, enquiryId, photographerId)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found."));
        if (tokens.link(enquiryId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This enquiry was created before reminders were available, so its link can't be re-sent. Share the original link instead.");
        }
        jdbc.update("UPDATE fb_enquiries SET quote_reminder_sent_at = CURRENT_TIMESTAMP WHERE id = ?", enquiryId);
        sendReminder(candidate);
        return tracking(photographerId).stream().filter(t -> t.enquiryId().equals(enquiryId)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<EnquiryTracking> tracking(UUID photographerId) {
        Map<UUID, ContractSummary> contracts = jdbc.query("SELECT * FROM fb_contract WHERE photographer_id = ?",
                        ContractService.CONTRACT, photographerId).stream()
                .collect(Collectors.toMap(ContractService.ContractRow::enquiryId, ContractService.ContractRow::summary, (a, b) -> a));
        boolean canRemind = entitlements.has(photographerId, Feature.CONTRACTS);
        return jdbc.query("""
                SELECT id, source, message, preferred_date, quote_opened_at, quote_reminder_sent_at,
                       quote_token_enc IS NOT NULL AS has_link
                FROM fb_enquiries WHERE photographer_id = ? ORDER BY created_at DESC
                """, (rs, n) -> {
            UUID id = rs.getObject("id", UUID.class);
            return new EnquiryTracking(id, rs.getString("source"), rs.getString("message"),
                    rs.getDate("preferred_date") == null ? null : rs.getDate("preferred_date").toLocalDate(),
                    ContractService.instant(rs.getTimestamp("quote_opened_at")),
                    ContractService.instant(rs.getTimestamp("quote_reminder_sent_at")),
                    canRemind && rs.getBoolean("has_link"), contracts.get(id));
        }, photographerId);
    }

    private void sendReminder(Candidate candidate) {
        String link = tokens.link(candidate.id()).orElse(null);
        if (link == null) return;
        String studio = notifier.studio(candidate.photographerId()).displayName();
        String body;
        String subject;
        if ("SENT".equals(candidate.contractStatus())) {
            subject = studio + ": your booking agreement is waiting for your signature";
            body = "Hi " + candidate.name() + ",\n\nJust a reminder that your booking agreement for " + candidate.eventType()
                    + " is ready. Your date is only reserved once you sign.\n\nReview and sign: " + link + "\n\n" + studio;
        } else if (candidate.quoted()) {
            subject = studio + ": your quotation";
            body = "Hi " + candidate.name() + ",\n\nThank you for building your quotation for " + candidate.eventType()
                    + ". You can view it again here: " + link + "\n\nReply to this email or call us with any questions.\n\n" + studio;
        } else {
            subject = studio + ": build your photography quote in 2 minutes";
            body = "Hi " + candidate.name() + ",\n\nThanks for your enquiry about " + candidate.eventType()
                    + ". Pick the coverage you'd like and see your estimate instantly: " + link
                    + "\n\nDates fill up fast in wedding season, so let us know soon.\n\n" + studio;
        }
        notifier.email(candidate.email(), subject, body);
    }

    static <T> Map<UUID, T> index(List<T> items, Function<T, UUID> key) {
        return items.stream().collect(Collectors.toMap(key, Function.identity(), (a, b) -> a));
    }
}
