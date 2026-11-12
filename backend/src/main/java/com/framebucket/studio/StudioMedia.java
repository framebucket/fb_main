package com.framebucket.studio;

import com.framebucket.gallery.DataServiceClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

/** Serves a processed (web-size) preview for portfolio and flipbook pages without exposing originals. */
@Component
public class StudioMedia {
    private final DataServiceClient dataService;

    public StudioMedia(DataServiceClient dataService) {
        this.dataService = dataService;
    }

    public ResponseEntity<StreamingResponseBody> preview(UUID eventId, UUID customerId, String mediaId) {
        String url = dataService.previewUrl(eventId.toString(), mediaId, customerId.toString());
        if (url != null && !url.isBlank()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(url))
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                    .build();
        }
        DataServiceClient.MediaStream media = dataService.openPreview(eventId.toString(), mediaId, customerId.toString(), null);
        StreamingResponseBody body = output -> {
            try (InputStream input = media.body()) {
                input.transferTo(output);
            }
        };
        ResponseEntity.BodyBuilder response = ResponseEntity.status(media.status())
                .contentType(MediaType.parseMediaType(media.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300");
        if (media.contentLength() >= 0) response.contentLength(media.contentLength());
        return response.body(body);
    }
}
