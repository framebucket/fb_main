package com.framebucket.event.repository;

import com.framebucket.event.model.EventBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EventBookingRepository extends JpaRepository<EventBooking, UUID> {
    List<EventBooking> findAllByPhotographer_IdOrderByEventDateAscCreatedAtAsc(UUID photographerId);

    List<EventBooking> findAllByCustomer_IdInOrderByEventDateDesc(Collection<UUID> customerIds);

    java.util.Optional<EventBooking> findByIdAndPhotographer_Id(UUID eventId, UUID photographerId);

    java.util.Optional<EventBooking> findByIdAndCustomer_IdIn(UUID eventId, Collection<UUID> customerIds);

    List<EventBooking> findAllByCustomer_IdAndPhotographer_Id(UUID customerId, UUID photographerId);

    List<EventBooking> findAllByPhotographer_IdOrderByEventDateAsc(UUID photographerId);

    boolean existsByPhotographer_IdAndEventDate(UUID photographerId, LocalDate eventDate);

    long countByPhotographer_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID photographerId,
            Instant start,
            Instant end
    );

    @Query("""
            select event.eventType.name as eventTypeName, event.price as price, event.createdAt as createdAt
            from EventBooking event
            where event.photographer.id = :photographerId
              and event.createdAt >= :start
              and event.createdAt < :end
            """)
    List<EventRevenueRow> findRevenueRows(
            @Param("photographerId") UUID photographerId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    interface EventRevenueRow {
        String getEventTypeName();

        String getPrice();

        Instant getCreatedAt();
    }

}
