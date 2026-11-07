package com.framebucket.enquiry.repository;

import com.framebucket.enquiry.model.Quotation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QuotationRepository extends JpaRepository<Quotation, UUID> {
    Optional<Quotation> findByEnquiry_Id(UUID enquiryId);

    Optional<Quotation> findByIdAndEnquiry_Photographer_Id(UUID id, UUID photographerId);
}
