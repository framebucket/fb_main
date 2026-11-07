package com.framebucket.enquiry.repository;

import com.framebucket.enquiry.model.Enquiry;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface EnquiryRepository extends JpaRepository<Enquiry, UUID> {
    List<Enquiry> findAllByPhotographer_IdOrderByCreatedAtDesc(UUID photographerId);

    long countByPhotographer_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID photographerId,
            Instant start,
            Instant end
    );

    Optional<Enquiry> findByIdAndPhotographer_Id(UUID id, UUID photographerId);

    Optional<Enquiry> findByCreatedEventIdAndPhotographer_Id(UUID eventId, UUID photographerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select enquiry from Enquiry enquiry where enquiry.id = :id and enquiry.photographer.id = :photographerId")
    Optional<Enquiry> findByIdAndPhotographer_IdForUpdate(@Param("id") UUID id, @Param("photographerId") UUID photographerId);

    Optional<Enquiry> findByQuoteTokenHash(String quoteTokenHash);

    boolean existsByPhotographer_IdAndEnquiryId(UUID photographerId, String enquiryId);
}
