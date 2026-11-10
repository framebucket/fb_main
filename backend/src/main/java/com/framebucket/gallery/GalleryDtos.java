package com.framebucket.gallery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class GalleryDtos {
    private GalleryDtos() {}

    public record FolderCreateRequest(@NotBlank @Size(max = 80) String name) {}

    public record AlbumSubmissionRequest(@NotEmpty @Size(max = 5000) List<@NotBlank String> photoIds) {}
}
