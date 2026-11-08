package com.framebucket.event.api;

import com.framebucket.team.model.MemberType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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

    public record AssigneeUpdateRequest(MemberType assigneeType, UUID assigneeId) {
        @AssertTrue(message = "Choose both an assignee type and an assignee, or leave both empty to clear the assignment.")
        public boolean isAssigneeSelectionValid() {
            return (assigneeType == null) == (assigneeId == null);
        }
    }

    public record CustomerLocationUpdateRequest(
            @NotBlank @Size(max = 240) String locationName,
            @Size(max = 2048) String locationMapUrl,
            @Size(max = 240) String landmark,
            @Size(max = 2000) String notes,
            @NotNull LocalTime eventTime
    ) {
    }

    public record EventResponse(
            UUID id,
            String eventCode,
            LocalDate eventDate,
            LocalTime eventTime,
            String eventTypeName,
            String price,
            UUID customerUuid,
            String customerCode,
            String customerName,
            UUID enquiryId,
            Instant createdAt,
            UUID assigneeId,
            MemberType assigneeType,
            String assigneeName,
            String locationName,
            String locationMapUrl,
            String locationLandmark,
            String locationNotes,
            Instant locationUpdatedAt
    ) {
    }
}
