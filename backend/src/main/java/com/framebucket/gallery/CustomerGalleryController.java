package com.framebucket.gallery;

import com.framebucket.customer.model.CustomerPrincipal;
import com.framebucket.gallery.GalleryDtos.AlbumSubmissionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/customer-portal/events/{eventId}/gallery")
public class CustomerGalleryController {
    private final DataGalleryService gallery;

    public CustomerGalleryController(DataGalleryService gallery) {
        this.gallery = gallery;
    }

    @GetMapping("/media")
    public Map<String, Object> media(@PathVariable UUID eventId,
                                     @AuthenticationPrincipal CustomerPrincipal customer) {
        return gallery.mediaForCustomer(eventId, customer);
    }

    @GetMapping("/media/{mediaId}/preview")
    public ResponseEntity<StreamingResponseBody> preview(@PathVariable UUID eventId,
                                                         @PathVariable String mediaId,
                                                         @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                                                         @AuthenticationPrincipal CustomerPrincipal customer) {
        DataServiceClient.MediaStream media = gallery.previewForCustomer(eventId, mediaId, range, customer);
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

    @PostMapping("/album-submissions")
    public Map<String, Object> submitAlbum(@PathVariable UUID eventId,
                                           @AuthenticationPrincipal CustomerPrincipal customer,
                                           @Valid @RequestBody AlbumSubmissionRequest request) {
        return gallery.submitAlbum(eventId, customer, request.photoIds());
    }
}
