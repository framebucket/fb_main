package com.framebucket.studio;

import com.framebucket.common.phone.IndianMobileNumber;
import com.framebucket.enquiry.notification.EnquiryEmailSender;
import com.framebucket.enquiry.notification.WhatsAppTemplateSender;
import com.framebucket.studio.StudioEvents.EventInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Day-before reminders for booked events: email to the photographer and customer, plus WhatsApp to the
 * customer and the assigned team member when an approved reminder template is configured.
 * Each reminder is claimed in fb_event_reminder first, so running several app instances is safe.
 */
@Component
public class EventReminderScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventReminderScheduler.class);
    private static final ZoneId STUDIO_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final String KIND = "DAY_BEFORE";

    private final JdbcTemplate jdbc;
    private final StudioEvents events;
    private final StudioProfileService profiles;
    private final BillingService billing;
    private final EnquiryEmailSender emailSender;
    private final WhatsAppTemplateSender whatsAppSender;
    private final boolean enabled;
    private final String whatsAppTemplate;
    private final String whatsAppLanguage;

    public EventReminderScheduler(
            JdbcTemplate jdbc,
            StudioEvents events,
            StudioProfileService profiles,
            BillingService billing,
            EnquiryEmailSender emailSender,
            WhatsAppTemplateSender whatsAppSender,
            @Value("${app.reminders.enabled:true}") boolean enabled,
            @Value("${app.whatsapp.event-reminder-template:}") String whatsAppTemplate,
            @Value("${app.whatsapp.event-reminder-language:${app.whatsapp.language:en}}") String whatsAppLanguage
    ) {
        this.jdbc = jdbc;
        this.events = events;
        this.profiles = profiles;
        this.billing = billing;
        this.emailSender = emailSender;
        this.whatsAppSender = whatsAppSender;
        this.enabled = enabled;
        this.whatsAppTemplate = whatsAppTemplate;
        this.whatsAppLanguage = whatsAppLanguage;
    }

    @Scheduled(cron = "${app.reminders.cron:0 0 9 * * *}", zone = "Asia/Kolkata")
    public void sendDayBeforeReminders() {
        if (!enabled) return;
        LocalDate tomorrow = LocalDate.now(STUDIO_ZONE).plusDays(1);
        List<EventInfo> due = events.onDate(tomorrow);
        for (EventInfo event : due) {
            try {
                if (!profiles.profile(event.photographerId()).remindersEnabled()) continue;
                if (!claim(event)) continue;
                send(event);
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not send the reminder for event {}.", event.eventCode(), exception);
            }
        }
    }

    private boolean claim(EventInfo event) {
        return jdbc.update("INSERT INTO fb_event_reminder (event_id, kind) VALUES (?, ?) ON CONFLICT DO NOTHING",
                event.id(), KIND) == 1;
    }

    private void send(EventInfo event) {
        String when = DATE.format(event.eventDate()) + (event.eventTime() == null ? "" : " at " + TIME.format(event.eventTime()));
        String where = event.locationName() == null || event.locationName().isBlank() ? "Location to be confirmed" : event.locationName();
        BigDecimal balance = billing.balanceFor(event);

        if (notBlank(event.customerEmail())) {
            StringBuilder body = new StringBuilder("Hello ").append(event.customerName()).append(",\n\n")
                    .append("This is a reminder that your ").append(event.eventTypeName()).append(" shoot with ")
                    .append(event.studioDisplayName()).append(" is tomorrow, ").append(when).append(".\n")
                    .append("Location: ").append(where).append("\n");
            if (balance.signum() > 0) {
                body.append("Balance due: Rs. ").append(ReceiptPdfGenerator.money(balance))
                        .append(" (you can pay by UPI from your customer portal).\n");
            }
            body.append("\nRegards,\n").append(event.studioDisplayName());
            sendEmail(event, event.customerEmail(), "Reminder: your shoot is tomorrow", body.toString());
        }
        if (notBlank(event.photographerEmail())) {
            String body = "Hello,\n\nTomorrow: " + event.eventTypeName() + " for " + event.customerName()
                    + " (" + event.eventCode() + ")\nWhen: " + when + "\nWhere: " + where
                    + "\nAssigned to: " + (notBlank(event.assigneeName()) ? event.assigneeName() : "nobody yet")
                    + (balance.signum() > 0 ? "\nBalance to collect: Rs. " + ReceiptPdfGenerator.money(balance) : "")
                    + "\n\nRegards,\nframebucket.in";
            sendEmail(event, event.photographerEmail(), "Tomorrow: " + event.eventTypeName() + " · " + event.customerName(), body);
        }
        if (notBlank(whatsAppTemplate)) {
            List<String> params = List.of(event.eventTypeName(), event.studioDisplayName(), when, where);
            sendWhatsApp(event, event.customerMobile(), params);
            sendWhatsApp(event, event.assigneeMobile(), params);
        }
    }

    private void sendEmail(EventInfo event, String to, String subject, String body) {
        try {
            emailSender.send(to, subject, body);
        } catch (RuntimeException exception) {
            LOGGER.warn("Reminder email for event {} could not be sent.", event.eventCode(), exception);
        }
    }

    private void sendWhatsApp(EventInfo event, String mobile, List<String> params) {
        if (!notBlank(mobile)) return;
        try {
            whatsAppSender.sendTemplate("91" + IndianMobileNumber.normalize(mobile), whatsAppTemplate, whatsAppLanguage, params);
        } catch (RuntimeException exception) {
            LOGGER.warn("Reminder WhatsApp for event {} could not be sent.", event.eventCode(), exception);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
