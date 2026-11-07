package com.framebucket.customer.repository;

import com.framebucket.customer.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    List<Customer> findAllByPhotographer_IdOrderByCreatedAtDesc(UUID photographerId);

    long countByPhotographer_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID photographerId,
            Instant start,
            Instant end
    );

    java.util.Optional<Customer> findByIdAndPhotographer_Id(UUID id, UUID photographerId);

    java.util.Optional<Customer> findByPhotographer_IdAndNameIgnoreCaseAndEmailIgnoreCaseAndMobileNumber(
            UUID photographerId,
            String name,
            String email,
            String mobileNumber
    );

    List<Customer> findAllByMobileNumberOrderByCreatedAtDesc(String mobileNumber);

    boolean existsByPhotographer_IdAndNameIgnoreCaseAndEmailIgnoreCaseAndMobileNumber(
            UUID photographerId,
            String name,
            String email,
            String mobileNumber
    );
}
