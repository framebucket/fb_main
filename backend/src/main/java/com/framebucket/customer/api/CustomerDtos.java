package com.framebucket.customer.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class CustomerDtos {
    private CustomerDtos() {
    }

    public record CustomerRegistrationRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Pattern(regexp = "^(?:[0-9]{10}|\\+91[0-9]{10})$") @Size(max = 13) String mobileNumber,
            @NotNull @Past LocalDate dateOfBirth,
            @NotBlank @Size(max = 1000) String address
    ) {
    }

    public record CustomerResponse(
            UUID id,
            String customerCode,
            String name,
            String email,
            String mobileNumber,
            LocalDate dateOfBirth,
            String address,
            int eventsBooked,
            BigDecimal totalRevenueGenerated,
            Instant createdAt
    ) {
    }
}
