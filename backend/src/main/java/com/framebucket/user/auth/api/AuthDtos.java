package com.framebucket.user.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @Size(max = 160) String studioName,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Pattern(regexp = "^(?:[0-9]{10}|\\+91[0-9]{10})$") @Size(max = 13) String mobileNumber,
            @NotBlank @Size(min = 12, max = 64) String password,
            @NotBlank @Size(min = 12, max = 64) String confirmPassword
    ) {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 320) @JsonAlias("email") String username,
            @NotBlank String password
    ) {
    }

    public record VerifyEmailRequest(
            @NotBlank @Size(max = 128) String token
    ) {
    }

    public record ResendVerificationRequest(
            @NotBlank @Email @Size(max = 320) String email
    ) {
    }

    public record VerifyMobileRequest(
            @NotBlank @Size(max = 320) String identifier,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code
    ) {
    }

    public record ResendMobileCodeRequest(
            @NotBlank @Size(max = 320) String identifier
    ) {
    }

    public record UserResponse(
            UUID id,
            String fullName,
            String studioName,
            String email,
            String mobileNumber,
            boolean emailVerified,
            boolean mobileVerified,
            Instant createdAt,
            UUID subscriptionId,
            String subscriptionCode,
            String subscriptionName,
            Instant trialEndsAt,
            boolean dataModuleEnabled
    ) {
    }
}
