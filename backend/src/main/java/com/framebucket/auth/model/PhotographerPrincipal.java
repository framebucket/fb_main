package com.framebucket.auth.model;

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
    private final String passwordHash;
    private final boolean active;
    private final java.time.Instant createdAt;

    private PhotographerPrincipal(PhotographerUser user) {
        this.id = user.getId();
        this.fullName = user.getFullName();
        this.studioName = user.getStudioName();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.active = user.isActive();
        this.createdAt = user.getCreatedAt();
    }

    public static PhotographerPrincipal from(PhotographerUser user) {
        return new PhotographerPrincipal(user);
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

    public java.time.Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_PHOTOGRAPHER"));
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
