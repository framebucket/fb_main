package com.framebucket.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who is a FrameBucket admin. Admins are photographer accounts whose email is listed in
 * APP_ADMIN_EMAILS (comma separated). They sign in on the normal login page and get ROLE_ADMIN,
 * which unlocks /admin and /api/admin/**.
 */
@Component
public class AdminAccess {
    private final Set<String> adminEmails;

    public AdminAccess(@Value("${app.admin.emails:}") String adminEmails) {
        this.adminEmails = Arrays.stream(adminEmails.split(","))
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(String email) {
        return email != null && adminEmails.contains(email.trim().toLowerCase(Locale.ROOT));
    }

    public Set<String> adminEmails() {
        return adminEmails;
    }
}
