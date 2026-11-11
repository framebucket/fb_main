package com.framebucket.gallery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class GalleryDtos {
    private GalleryDtos() {}

    public record FolderCreateRequest(@NotBlank @Size(max = 80) String name) {}

    public record UploadInitRequest(
            @Size(max = 128) String folderId,
            @NotBlank @Size(max = 255) String filename,
            @Positive long sizeBytes,
            @Size(max = 255) String contentType
    ) {}

    public record AlbumSubmissionRequest(@NotEmpty @Size(max = 5000) List<@NotBlank String> photoIds) {}

    public record AlbumSelectionUpdateRequest(
            @NotNull @Size(max = 5000) List<@NotBlank String> photoIds
    ) {}

    public record GalleryDownloadRequest(
            @Size(max = 5000) List<@NotBlank String> photoIds
    ) {}
}
