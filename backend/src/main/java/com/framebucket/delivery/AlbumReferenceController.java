package com.framebucket.delivery;

import com.framebucket.gallery.DataServiceClient;
import com.framebucket.gallery.DataServiceClient.NamedBytes;
import com.framebucket.subscription.EntitlementService;
import com.framebucket.subscription.PlanRestrictionException;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reference album designs (Studio: 50, Business: 100; not on Free or Solo): the studio uploads finished spreads
 * once and the AI album designer follows the best-fitting layout for every sheet.
 */
@RestController
public class AlbumReferenceController {
    private static final int MAX_PER_UPLOAD = 25;
    private static final long MAX_SPREAD_BYTES = 40L * 1024 * 1024;

    private final DataServiceClient dataService;
    private final EntitlementService entitlements;

    public AlbumReferenceController(DataServiceClient dataService, EntitlementService entitlements) {
        this.dataService = dataService;
        this.entitlements = entitlements;
    }

    @GetMapping("/api/studio/album-references")
    public Map<String, Object> list(@AuthenticationPrincipal PhotographerPrincipal p) {
        Integer limit = entitlements.albumReferenceLimit(p.getId());
        if (limit != null && limit <= 0) {
            Map<String, Object> locked = new LinkedHashMap<>();
            locked.put("references", List.of());
            locked.put("count", 0);
            locked.put("limit", 0);
            locked.put("planAllows", false);
            return locked;
        }
        return withLimit(limit, dataService.albumReferences(p.getId()));
    }

    @PostMapping("/api/studio/album-references")
    public Map<String, Object> upload(@AuthenticationPrincipal PhotographerPrincipal p,
                                      @RequestParam("files") List<MultipartFile> files) {
        Integer limit = requireIncluded(p);
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose the album spreads to upload.");
        }
        if (files.size() > MAX_PER_UPLOAD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload up to " + MAX_PER_UPLOAD + " spreads at a time.");
        }
        List<NamedBytes> spreads = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            if (file.getSize() > MAX_SPREAD_BYTES) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Each spread must be 40 MB or smaller.");
            }
            String name = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                    ? "spread.jpg" : file.getOriginalFilename().replaceAll("[\\\\/\\r\\n\"]", "_");
            try {
                spreads.add(new NamedBytes(name, MediaType.APPLICATION_OCTET_STREAM, file.getBytes()));
            } catch (IOException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That file could not be read.", exception);
            }
        }
        if (limit != null && dataService.albumReferences(p.getId()).get("count") instanceof Number count
                && count.intValue() + spreads.size() > limit) {
            throw limitReached(limit, count.intValue());
        }
        try {
            return withLimit(limit, dataService.uploadAlbumReferences(p.getId(), spreads, limit));
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 409 && limit != null) {
                throw PlanRestrictionException.limitReached("albumReferences", exception.getReason());
            }
            throw exception;
        }
    }

    @DeleteMapping("/api/studio/album-references/{referenceId}")
    public Map<String, Object> delete(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable String referenceId) {
        // Deleting is always allowed, even after a downgrade, so studios can clean up.
        return withLimit(entitlements.albumReferenceLimit(p.getId()), dataService.deleteAlbumReference(p.getId(), referenceId));
    }

    @GetMapping("/api/studio/album-references/{referenceId}/thumb")
    public ResponseEntity<byte[]> thumb(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable String referenceId) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(dataService.albumReferenceThumb(p.getId(), referenceId));
    }

    private Integer requireIncluded(PhotographerPrincipal p) {
        Integer limit = entitlements.albumReferenceLimit(p.getId());
        if (limit != null && limit <= 0) {
            throw PlanRestrictionException.limitReached("albumReferences",
                    "Reference album designs are included in the Studio and Business plans. Upgrade to upload your own designs.");
        }
        return limit;
    }

    private static PlanRestrictionException limitReached(int limit, int count) {
        int left = Math.max(0, limit - count);
        return PlanRestrictionException.limitReached("albumReferences",
                "Your plan allows " + limit + " reference designs and you have " + count + ". "
                        + (left > 0 ? "You can add " + left + " more; " : "") + "delete some or upgrade your plan.");
    }

    private static Map<String, Object> withLimit(Integer limit, Map<String, Object> response) {
        Map<String, Object> copy = new LinkedHashMap<>(response);
        copy.put("limit", limit);
        copy.put("planAllows", true);
        return copy;
    }
}
