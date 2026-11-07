package com.framebucket.event.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class EventDtos {
    private EventDtos() {
    }

    public record EventTypeCreateRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record EventTypeResponse(UUID id, String name) {
    }

    public record EventIdPreview(String eventId) {
    }

    public record EventCreateRequest(
            @NotNull LocalDate eventDate,
            @NotNull UUID eventTypeId,
            @NotNull UUID customerId,
            @Size(max = 1000) String price
    ) {
    }

    public record EventResponse(
            UUID id,
            String eventCode,
            LocalDate eventDate,
            String eventTypeName,
            String price,
            UUID customerUuid,
            String customerCode,
            String customerName,
            UUID enquiryId,
            Instant createdAt
    ) {
    }
}
