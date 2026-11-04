package com.framebucket.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @Size(max = 160) String studioName,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 12, max = 64) String password
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank String password
    ) {
    }

    public record UserResponse(
            UUID id,
            String fullName,
            String studioName,
            String email,
            Instant createdAt
    ) {
    }
}
