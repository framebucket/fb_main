package com.framebucket.enquiry.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "fb_quotaion")
public class Quotation {
    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enquiry_id", nullable = false, unique = true)
    private Enquiry enquiry;

    @Column(name = "quote_number", nullable = false, unique = true, length = 80)
    private String quoteNumber;

    @Column(name = "customer_name", nullable = false, length = 120)
    private String customerName;

    @Column(name = "customer_mobile", nullable = false, length = 16)
    private String customerMobile;

    @Column(name = "customer_email", nullable = false, length = 320)
    private String customerEmail;

    @Column(nullable = false, length = 100)
    private String occasion;

    @Column(length = 240)
    private String venue;

    @Column(name = "preferred_event_date")
    private LocalDate preferredEventDate;

    @Column(name = "preferred_time", length = 40)
    private String preferredTime;

    @Column(name = "event_count", nullable = false)
    private int eventCount;

    @Column(name = "event_selections_json", nullable = false, columnDefinition = "TEXT")
    private String eventSelectionsJson;

    @Column(name = "album_sheets", nullable = false)
    private int albumSheets;


    @Column(name = "final_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalPrice;

    @Column(name = "quote_pdf", nullable = false, columnDefinition = "BYTEA")
    private byte[] quotePdf;

    @Column(name = "original_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "original_quote_pdf", nullable = false, columnDefinition = "BYTEA")
    private byte[] originalQuotePdf;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Quotation() {
    }

    public Quotation(
            Enquiry enquiry,
            String quoteNumber,
            String customerName,
            String customerMobile,
            String customerEmail,
            String occasion,
            String venue,
            LocalDate preferredEventDate,
            String preferredTime,
            int eventCount,
            String eventSelectionsJson,
            int albumSheets,
            BigDecimal finalPrice,
            byte[] quotePdf
    ) {
        this.id = UUID.randomUUID();
        this.enquiry = enquiry;
        this.quoteNumber = quoteNumber;
        this.customerName = customerName;
        this.customerMobile = customerMobile;
        this.customerEmail = customerEmail;
        this.occasion = occasion;
        this.venue = venue;
        this.preferredEventDate = preferredEventDate;
        this.preferredTime = preferredTime;
        this.eventCount = eventCount;
        this.eventSelectionsJson = eventSelectionsJson;
        this.albumSheets = albumSheets;
        this.finalPrice = finalPrice;
        this.quotePdf = quotePdf;
        this.originalPrice = finalPrice;
        this.originalQuotePdf = quotePdf;
    }

    @PrePersist
    void setCreatedAtIfMissing() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public Enquiry getEnquiry() {
        return enquiry;
    }

    public String getQuoteNumber() {
        return quoteNumber;
    }

    public BigDecimal getFinalPrice() {
        return finalPrice;
    }

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public String getOccasion() {
        return occasion;
    }

    public String getVenue() {
        return venue;
    }

    public LocalDate getPreferredEventDate() {
        return preferredEventDate;
    }

    public String getPreferredTime() {
        return preferredTime;
    }

    public String getEventSelectionsJson() {
        return eventSelectionsJson;
    }

    public int getAlbumSheets() {
        return albumSheets;
    }


    public void replaceFinalPriceAndPdf(BigDecimal finalPrice, byte[] quotePdf) {
        this.finalPrice = finalPrice;
        this.quotePdf = quotePdf;
    }

    public byte[] getQuotePdf() {
        return quotePdf;
    }

    public byte[] getOriginalQuotePdf() {
        return originalQuotePdf;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
