package com.framebucket.customer.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class CustomerPrincipal implements UserDetails, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String name;
    private final String mobileNumber;
    private final List<UUID> customerIds;

    public CustomerPrincipal(String name, String mobileNumber, List<UUID> customerIds) {
        this.name = name;
        this.mobileNumber = mobileNumber;
        this.customerIds = List.copyOf(customerIds);
    }

    public String getName() {
        return name;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public List<UUID> getCustomerIds() {
        return customerIds;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return mobileNumber;
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
        return true;
    }
}
