package com.framebucket.user.auth.model;

import com.framebucket.user.subscription.model.SubscriptionPlan;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class PhotographerPrincipal implements UserDetails {
    private final UUID id;
    private final String fullName;
    private final String studioName;
    private final String email;
    private final String mobileNumber;
    private final String passwordHash;
    private final boolean active;
    private final boolean emailVerified;
    private final boolean mobileVerified;
    private final java.time.Instant createdAt;
    private final java.util.UUID subscriptionId;
    private final String subscriptionCode;
    private final String subscriptionName;
    private final java.time.Instant trialEndsAt;
    private final boolean dataModuleEnabled;
    private final boolean admin;

    private PhotographerPrincipal(PhotographerUser user, boolean admin) {
        this.admin = admin;
        this.id = user.getId();
        this.fullName = user.getFullName();
        this.studioName = user.getStudioName();
        this.email = user.getEmail();
        this.mobileNumber = user.getMobileNumber();
        this.passwordHash = user.getPasswordHash();
        this.active = user.isActive();
        this.emailVerified = user.isEmailVerified();
        this.mobileVerified = user.isMobileVerified();
        this.createdAt = user.getCreatedAt();
        this.trialEndsAt = user.getTrialEndsAt();
        SubscriptionPlan subscription = user.getSubscription();
        this.subscriptionId = subscription == null ? null : subscription.getId();
        this.subscriptionCode = subscription == null ? null : subscription.getPlanCode();
        this.subscriptionName = subscription == null ? null : subscription.getPlanName();
        this.dataModuleEnabled = subscription != null && subscription.isDataModuleEnabled();
    }

    public static PhotographerPrincipal from(PhotographerUser user) {
        return new PhotographerPrincipal(user, false);
    }

    /** @param admin true for FrameBucket owners/admins (APP_ADMIN_EMAILS); adds ROLE_ADMIN. */
    public static PhotographerPrincipal from(PhotographerUser user, boolean admin) {
        return new PhotographerPrincipal(user, admin);
    }

    public boolean isAdmin() {
        return admin;
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getStudioName() {
        return studioName;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public java.time.Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public boolean isMobileVerified() {
        return mobileVerified;
    }

    public java.util.UUID getSubscriptionId() {
        return subscriptionId;
    }

    public String getSubscriptionCode() {
        return subscriptionCode;
    }

    public String getSubscriptionName() {
        return subscriptionName;
    }

    public java.time.Instant getTrialEndsAt() {
        return trialEndsAt;
    }

    public boolean isDataModuleEnabled() {
        return dataModuleEnabled;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return admin
                ? List.of(new SimpleGrantedAuthority("ROLE_PHOTOGRAPHER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_PHOTOGRAPHER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
