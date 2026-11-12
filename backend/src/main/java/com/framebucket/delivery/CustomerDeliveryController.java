package com.framebucket.delivery;

import com.framebucket.customer.model.CustomerPrincipal;
import com.framebucket.delivery.DeliveryDtos.CommentRequest;
import com.framebucket.delivery.DeliveryDtos.CustomerDelivery;
import com.framebucket.delivery.DeliveryDtos.ProofDecisionRequest;
import com.framebucket.delivery.DeliveryDtos.ProofView;
import com.framebucket.gallery.DataGalleryService;
import com.framebucket.gallery.DataServiceClient;
import com.framebucket.studio.StudioEvents;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/customer-portal/events/{eventId}")
public class CustomerDeliveryController {
    private final StudioEvents events;
    private final DeliveryService delivery;
    private final FilmService films;
    private final ProofService proofs;
    private final BrandService brand;
    private final DataGalleryService gallery;
    private final WatermarkService watermarks;

    public CustomerDeliveryController(StudioEvents events, DeliveryService delivery, FilmService films, ProofService proofs,
                                      BrandService brand, DataGalleryService gallery, WatermarkService watermarks) {
        this.events = events;
        this.delivery = delivery;
        this.films = films;
        this.proofs = proofs;
        this.brand = brand;
        this.gallery = gallery;
        this.watermarks = watermarks;
    }

    @GetMapping("/delivery")
    public CustomerDelivery delivery(@PathVariable UUID eventId, @AuthenticationPrincipal CustomerPrincipal customer) {
        StudioEvents.EventInfo event = events.forCustomer(eventId, customer.getCustomerIds());
        return new CustomerDelivery(brand.brandView(event.photographerId()), delivery.list(eventId), films.list(eventId, true),
                proofs.latestForCustomer(eventId));
    }

    @GetMapping("/films/{filmId}/stream")
    public ResponseEntity<StreamingResponseBody> film(@PathVariable UUID eventId, @PathVariable UUID filmId,
                                                      @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                                                      @AuthenticationPrincipal CustomerPrincipal customer) {
        events.forCustomer(eventId, customer.getCustomerIds());
        FilmService.FilmRow film = films.film(eventId, filmId);
        if (film.mediaId() == null) return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(film.externalUrl())).build();
        String url = gallery.previewUrlForCustomer(eventId, film.mediaId(), customer);
        if (url != null) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).header(HttpHeaders.CACHE_CONTROL, "private, max-age=60").build();
        }
        return stream(gallery.previewForCustomer(eventId, film.mediaId(), range, customer));
    }

    @GetMapping("/proofs/{proofId}/spreads/{index}")
    public ResponseEntity<StreamingResponseBody> spread(@PathVariable UUID eventId, @PathVariable UUID proofId, @PathVariable int index,
                                                        @AuthenticationPrincipal CustomerPrincipal customer) {
        StudioEvents.EventInfo event = events.forCustomer(eventId, customer.getCustomerIds());
        String mediaId = proofs.spreadMediaId(eventId, proofId, index);
        return image(watermarks.proofSpread(event, mediaId));
    }

    @PostMapping("/proofs/{proofId}/comments")
    public ProofView comment(@PathVariable UUID eventId, @PathVariable UUID proofId, @Valid @RequestBody CommentRequest request,
                             @AuthenticationPrincipal CustomerPrincipal customer) {
        return proofs.customerComment(events.forCustomer(eventId, customer.getCustomerIds()), proofId, request);
    }

    @PostMapping("/proofs/{proofId}/approve")
    public ProofView approve(@PathVariable UUID eventId, @PathVariable UUID proofId,
                             @Valid @RequestBody(required = false) ProofDecisionRequest request,
                             @AuthenticationPrincipal CustomerPrincipal customer) {
        return proofs.decide(events.forCustomer(eventId, customer.getCustomerIds()), proofId, true, request == null ? null : request.note());
    }

    @PostMapping("/proofs/{proofId}/request-changes")
    public ProofView requestChanges(@PathVariable UUID eventId, @PathVariable UUID proofId,
                                    @Valid @RequestBody(required = false) ProofDecisionRequest request,
                                    @AuthenticationPrincipal CustomerPrincipal customer) {
        return proofs.decide(events.forCustomer(eventId, customer.getCustomerIds()), proofId, false, request == null ? null : request.note());
    }

    public static ResponseEntity<StreamingResponseBody> image(WatermarkService.Result result) {
        if (result.image() != null) {
            byte[] bytes = result.image();
            return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).contentLength(bytes.length)
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300").body(output -> output.write(bytes));
        }
        return stream(result.raw());
    }

    public static ResponseEntity<StreamingResponseBody> stream(DataServiceClient.MediaStream media) {
        StreamingResponseBody body = output -> {
            try (InputStream input = media.body()) {
                input.transferTo(output);
            }
        };
        ResponseEntity.BodyBuilder response = ResponseEntity.status(media.status())
                .contentType(MediaType.parseMediaType(media.contentType() == null ? "application/octet-stream" : media.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store");
        if (media.contentRange() != null) response.header(HttpHeaders.CONTENT_RANGE, media.contentRange());
        if (media.acceptRanges() != null) response.header(HttpHeaders.ACCEPT_RANGES, media.acceptRanges());
        if (media.contentLength() >= 0) response.contentLength(media.contentLength());
        return response.body(body);
    }
}
