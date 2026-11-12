package com.framebucket.delivery;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DeliveryDtos {
    private DeliveryDtos() {
    }

    // ---- brand ------------------------------------------------------------------------------------

    /** Public branding for client-facing pages. Colour and logo are only set on plans with custom branding. */
    public record BrandView(String studioName, String color, String logoUrl, boolean poweredByFrameBucket) {
    }

    public record BrandResponse(
            String studioName,
            String brandColor,
            String logoUrl,
            String watermarkMode,
            String watermarkText,
            boolean watermarkUseLogo,
            String watermarkPosition,
            int watermarkOpacity,
            String customDomain,
            Instant customDomainVerifiedAt,
            String customDomainTarget,
            boolean canBrand,
            boolean canWatermark,
            boolean canUseCustomDomain
    ) {
    }

    public record BrandRequest(
            @Pattern(regexp = "^$|^#[0-9A-Fa-f]{6}$") String brandColor,
            @NotBlank @Pattern(regexp = "OFF|ALWAYS|UNTIL_PAID") String watermarkMode,
            @Size(max = 60) String watermarkText,
            boolean watermarkUseLogo,
            @NotBlank @Pattern(regexp = "CENTER|BOTTOM_RIGHT|TILED") String watermarkPosition,
            @Min(5) @Max(80) int watermarkOpacity
    ) {
    }

    public record DomainRequest(@NotBlank @Size(max = 253) String domain) {
    }

    public record DomainResolution(String slug, String studioName) {
    }

    // ---- delivery checklist -----------------------------------------------------------------------

    public record StepView(UUID id, String title, String kind, String status, LocalDate dueDate, Instant completedAt,
                           String note, int sortOrder) {
    }

    public record StepRequest(
            @NotBlank @Size(max = 120) String title,
            @Pattern(regexp = "TEASER|HIGHLIGHTS|GALLERY|ALBUM_DESIGN|PRINTED_ALBUM|FULL_FILM|OTHER") String kind,
            @Pattern(regexp = "NOT_STARTED|IN_PROGRESS|DONE") String status,
            LocalDate dueDate,
            @Size(max = 500) String note,
            @Min(0) @Max(10000) Integer sortOrder,
            boolean notifyClient
    ) {
    }

    // ---- films --------------------------------------------------------------------------------------

    public record FilmView(UUID id, String title, String kind, String source, String mediaId, String externalUrl,
                           String embedUrl, int sortOrder, Instant createdAt) {
    }

    public record FilmRequest(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Pattern(regexp = "TEASER|HIGHLIGHTS|FULL_FILM|REEL|OTHER") String kind,
            @Size(max = 128) String mediaId,
            @Size(max = 2048) String externalUrl,
            @Min(0) @Max(10000) Integer sortOrder,
            boolean notifyClient
    ) {
    }

    public record VideoOption(String mediaId, String filename, String folderId, Long sizeBytes) {
    }

    // ---- album proofing ----------------------------------------------------------------------------

    public record ProofView(UUID id, int version, String status, int spreadCount, List<String> mediaIds, String message,
                            String customerNote, Instant createdAt, Instant respondedAt, List<CommentView> comments) {
    }

    public record CommentView(UUID id, int spreadIndex, String author, String body, boolean resolved, Instant createdAt) {
    }

    public record ProofCreateRequest(
            @Size(max = 128) String folderId,
            @Size(max = 400) List<@NotBlank @Size(max = 128) String> mediaIds,
            @Size(max = 1000) String message
    ) {
    }

    public record CommentRequest(@Min(0) @Max(1000) int spreadIndex, @NotBlank @Size(max = 2000) String body) {
    }

    public record ProofDecisionRequest(@Size(max = 2000) String note) {
    }

    // ---- customer view ---------------------------------------------------------------------------

    public record CustomerDelivery(BrandView brand, List<StepView> steps, List<FilmView> films, ProofView proof) {
    }
}
