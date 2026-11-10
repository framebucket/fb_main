package com.framebucket.customer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CustomerAuthDtos {
    private CustomerAuthDtos() {
    }

    public record CustomerOtpRequest(
            @NotBlank @Pattern(regexp = "^(?:[0-9]{10}|\\+91[0-9]{10})$") @Size(max = 13) String mobileNumber
    ) {
    }

    public record CustomerOtpVerifyRequest(
            @NotBlank @Pattern(regexp = "^(?:[0-9]{10}|\\+91[0-9]{10})$") @Size(max = 13) String mobileNumber,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code
    ) {
    }

    public record CustomerAuthMessage(String message) {
    }

    public record CustomerPortalProfileResponse(String name, String mobileNumber) {
    }

    public record CustomerEventResponse(
            java.util.UUID eventId,
            String eventName,
            String photographerName,
            java.time.LocalDate eventDate,
            java.time.LocalTime eventTime,
            String status,
            String assigneeName,
            String locationName,
            String locationMapUrl,
            String locationLandmark,
            String locationNotes,
            java.time.Instant locationUpdatedAt,
            boolean albumSubmitted
    ) {
    }
}
