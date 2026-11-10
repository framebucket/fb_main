package com.framebucket.gallery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventGuestRepository extends JpaRepository<EventGuest, UUID> {
    Optional<EventGuest> findByEvent_IdAndMobileNumber(UUID eventId, String mobileNumber);

    Optional<EventGuest> findByIdAndEvent_Id(UUID guestId, UUID eventId);
}
