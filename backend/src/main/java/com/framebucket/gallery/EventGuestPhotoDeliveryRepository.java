package com.framebucket.gallery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventGuestPhotoDeliveryRepository extends JpaRepository<EventGuestPhotoDelivery, UUID> {
    Optional<EventGuestPhotoDelivery> findByGuest_IdAndPhotoId(UUID guestId, String photoId);

    @EntityGraph(attributePaths = {"guest", "guest.event", "guest.event.eventType", "guest.event.customer"})
    List<EventGuestPhotoDelivery> findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            List<String> statuses,
            Instant dueAt
    );
}
