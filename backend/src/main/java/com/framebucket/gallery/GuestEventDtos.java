package com.framebucket.gallery;

import java.time.LocalDate;

public final class GuestEventDtos {
    private GuestEventDtos() {}

    public record EventContext(String eventName, LocalDate eventDate, String photographerName,
                               boolean faceRecognitionAvailable) {}

    public record RegistrationResponse(String message, int photosMatched, int photosQueued,
                                       String guestId, String deletionToken) {}

    public record GuestDeleteRequest(String guestId, String deletionToken) {}
}
