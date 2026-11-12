package com.framebucket.delivery;

import com.framebucket.delivery.DeliveryDtos.ProofCreateRequest;
import com.framebucket.delivery.DeliveryDtos.ProofView;
import com.framebucket.gallery.DataServiceClient;
import com.framebucket.studio.StudioEvents;
import com.framebucket.subscription.EntitlementService;
import com.framebucket.subscription.Feature;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Album auto-design: after the client submits their album selection, the studio picks a size and sheet count and
 * the data service lays the photos out into print-ready spreads (preview JPEGs, print JPEGs and layered PSDs).
 * Finished designs can be sent to the client through the normal album proofing flow.
 */
@RestController
@RequestMapping("/api/events/{eventId}/album-designs")
public class AlbumDesignController {
    /** Common Indian album sizes: height x open-spread width, in inches. */
    static final List<String> SIZES = List.of("12x24", "12x30", "12x36", "10x24", "15x24", "15x30", "16x24", "17x24", "18x24");
    private static final Pattern SIZE = Pattern.compile("(\\d{1,2}(?:\\.\\d)?)\\s*[xX×*]\\s*(\\d{2}(?:\\.\\d)?)");

    private final DataServiceClient dataService;
    private final StudioEvents events;
    private final EntitlementService entitlements;
    private final ProofService proofs;

    public AlbumDesignController(DataServiceClient dataService, StudioEvents events, EntitlementService entitlements,
                                 ProofService proofs) {
        this.dataService = dataService;
        this.events = events;
        this.entitlements = entitlements;
        this.proofs = proofs;
    }

    public record DesignRequest(@NotBlank @Size(max = 128) String submissionId,
                                @NotBlank @Size(max = 20) String size,
                                @Min(1) @Max(120) int sheets,
                                Integer dpi) {
    }

    public record SendRequest(@Size(max = 1000) String message) {
    }

    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId) {
        StudioEvents.EventInfo event = events.forPhotographer(eventId, p.getId());
        Map<String, Object> response = new LinkedHashMap<>(dataService.albumDesigns(
                event.id().toString(), p.getId().toString(), event.customerId().toString()));
        response.put("sizes", SIZES);
        response.put("planAllows", entitlements.forPhotographer(p.getId()).has(Feature.AI_ALBUM_DESIGN));
        return response;
    }

    @PostMapping
    public Map<String, Object> create(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId,
                                      @Valid @RequestBody DesignRequest request) {
        entitlements.require(p.getId(), Feature.AI_ALBUM_DESIGN);
        StudioEvents.EventInfo event = events.forPhotographer(eventId, p.getId());
        Matcher size = SIZE.matcher(request.size().strip());
        if (!size.matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the size as height x width, for example 12x36.");
        }
        double height = Double.parseDouble(size.group(1));
        double width = Double.parseDouble(size.group(2));
        int dpi = request.dpi() == null ? 300 : request.dpi();
        String label = trim(size.group(1)) + "x" + trim(size.group(2));
        return dataService.createAlbumDesign(event.id().toString(), p.getId().toString(), event.customerId().toString(),
                request.submissionId(), height, width, request.sheets(), dpi, label);
    }

    @GetMapping("/{designId}")
    public Map<String, Object> status(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId,
                                      @PathVariable String designId) {
        StudioEvents.EventInfo event = events.forPhotographer(eventId, p.getId());
        return dataService.albumDesign(event.id().toString(), id(designId), p.getId().toString(), event.customerId().toString());
    }

    @GetMapping("/{designId}/spreads/{index}")
    public ResponseEntity<StreamingResponseBody> spread(@AuthenticationPrincipal PhotographerPrincipal p,
                                                        @PathVariable UUID eventId, @PathVariable String designId,
                                                        @PathVariable int index) {
        StudioEvents.EventInfo event = events.forPhotographer(eventId, p.getId());
        DataServiceClient.MediaStream media = dataService.albumDesignSpread(event.id().toString(), id(designId), index,
                p.getId().toString(), event.customerId().toString());
        return stream(media, CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePrivate());
    }

    @GetMapping("/{designId}/download/{kind}")
    public ResponseEntity<StreamingResponseBody> download(@AuthenticationPrincipal PhotographerPrincipal p,
                                                          @PathVariable UUID eventId, @PathVariable String designId,
                                                          @PathVariable String kind) {
        if (!"psd".equals(kind) && !"jpeg".equals(kind)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown download.");
        }
        entitlements.require(p.getId(), Feature.AI_ALBUM_DESIGN);
        StudioEvents.EventInfo event = events.forPhotographer(eventId, p.getId());
        DataServiceClient.MediaStream media = dataService.albumDesignArchive(event.id().toString(), id(designId), kind,
                p.getId().toString(), event.customerId().toString());
        if (media.status() == HttpStatus.FOUND.value()) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(media.contentRange()))
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store").build();
        }
        return stream(media, CacheControl.noStore());
    }

    /** Copies the spreads into a gallery folder and sends them to the client as a new album proof. */
    @PostMapping("/{designId}/send")
    public List<ProofView> sendToClient(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId,
                                        @PathVariable String designId, @Valid @RequestBody(required = false) SendRequest request) {
        entitlements.require(p.getId(), Feature.AI_ALBUM_DESIGN);
        StudioEvents.EventInfo event = events.forPhotographer(eventId, p.getId());
        Map<String, Object> published = dataService.publishAlbumDesign(event.id().toString(), id(designId),
                p.getId().toString(), event.customerId().toString());
        String folderId = String.valueOf(published.get("folder_id"));
        return proofs.create(p.getId(), eventId, new ProofCreateRequest(folderId, null, request == null ? null : request.message()));
    }

    private static ResponseEntity<StreamingResponseBody> stream(DataServiceClient.MediaStream media, CacheControl cache) {
        StreamingResponseBody body = output -> {
            try (InputStream input = media.body()) {
                input.transferTo(output);
            }
        };
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, media.contentDisposition())
                .cacheControl(cache);
        if (media.contentLength() >= 0) response.contentLength(media.contentLength());
        return response.body(body);
    }

    private static String id(String designId) {
        if (designId == null || !designId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Album design not found.");
        }
        return designId;
    }

    private static String trim(String number) {
        return number.endsWith(".0") ? number.substring(0, number.length() - 2) : number;
    }
}
