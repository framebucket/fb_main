package com.framebucket.common.notify;

import com.framebucket.enquiry.notification.EnquiryEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Best-effort email notifications plus a small lookup of a photographer's contact details. */
@Component
public class Notifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(Notifier.class);

    public record Studio(UUID id, String displayName, String fullName, String email, String mobileNumber) {
    }

    private final EnquiryEmailSender emailSender;
    private final JdbcTemplate jdbc;

    public Notifier(EnquiryEmailSender emailSender, JdbcTemplate jdbc) {
        this.emailSender = emailSender;
        this.jdbc = jdbc;
    }

    public void email(String to, String subject, String body) {
        if (to == null || to.isBlank()) return;
        try {
            emailSender.send(to.trim(), subject, body);
        } catch (RuntimeException exception) {
            LOGGER.warn("Email \"{}\" could not be sent.", subject, exception);
        }
    }

    public Studio studio(UUID photographerId) {
        return jdbc.query("""
                        SELECT id, COALESCE(NULLIF(studio_name, ''), full_name) AS display_name, full_name, email, mobile_number
                        FROM fb_user WHERE id = ?
                        """, (rs, n) -> new Studio(rs.getObject("id", UUID.class), rs.getString("display_name"),
                        rs.getString("full_name"), rs.getString("email"), rs.getString("mobile_number")), photographerId)
                .stream().findFirst()
                .orElse(new Studio(photographerId, "Your photographer", "", null, null));
    }
}
