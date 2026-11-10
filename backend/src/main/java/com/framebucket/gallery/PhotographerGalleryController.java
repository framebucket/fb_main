package com.framebucket.gallery;

import com.framebucket.gallery.GalleryDtos.FolderCreateRequest;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/events/{eventId}/gallery")
public class PhotographerGalleryController {
    private final DataGalleryService gallery;

    public PhotographerGalleryController(DataGalleryService gallery) {
        this.gallery = gallery;
    }

    @GetMapping("/folders")
    public Map<String, Object> folders(@PathVariable UUID eventId,
                                      @AuthenticationPrincipal PhotographerPrincipal photographer) {
        return gallery.foldersForPhotographer(eventId, photographer);
    }

    @PostMapping("/folders")
    public Map<String, Object> createFolder(@PathVariable UUID eventId,
                                           @AuthenticationPrincipal PhotographerPrincipal photographer,
                                           @Valid @org.springframework.web.bind.annotation.RequestBody FolderCreateRequest request) {
        return gallery.createFolder(eventId, photographer, request.name());
    }

    @GetMapping("/media")
    public Map<String, Object> media(@PathVariable UUID eventId,
                                     @AuthenticationPrincipal PhotographerPrincipal photographer,
                                     @RequestParam(required = false) String folderId) {
        return gallery.mediaForPhotographer(eventId, photographer, folderId);
    }

    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@PathVariable UUID eventId,
                                      @AuthenticationPrincipal PhotographerPrincipal photographer,
                                      @RequestParam(required = false, defaultValue = "root") String folderId,
                                      @RequestParam("file") MultipartFile file) {
        return gallery.upload(eventId, photographer, folderId, file);
    }

    @GetMapping("/media/{mediaId}/preview")
    public ResponseEntity<StreamingResponseBody> preview(@PathVariable UUID eventId,
                                                         @PathVariable String mediaId,
                                                         @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                                                         @AuthenticationPrincipal PhotographerPrincipal photographer) {
        return stream(gallery.previewForPhotographer(eventId, mediaId, range, photographer));
    }

    @GetMapping("/media/{mediaId}/original")
    public ResponseEntity<StreamingResponseBody> original(@PathVariable UUID eventId,
                                                          @PathVariable String mediaId,
                                                          @AuthenticationPrincipal PhotographerPrincipal photographer) {
        return stream(gallery.originalForPhotographer(eventId, mediaId, photographer));
    }

    private static ResponseEntity<StreamingResponseBody> stream(DataServiceClient.MediaStream media) {
        StreamingResponseBody body = output -> {
            try (InputStream input = media.body()) {
                input.transferTo(output);
            }
        };
        ResponseEntity.BodyBuilder response = ResponseEntity.status(media.status())
                .contentType(MediaType.parseMediaType(media.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, media.contentDisposition())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store");
        if (media.contentRange() != null) response.header(HttpHeaders.CONTENT_RANGE, media.contentRange());
        if (media.acceptRanges() != null) response.header(HttpHeaders.ACCEPT_RANGES, media.acceptRanges());
        if (media.contentLength() >= 0) response.contentLength(media.contentLength());
        return response.body(body);
    }
}
