package com.framebucket.delivery;

import com.framebucket.gallery.DataServiceClient;
import com.framebucket.gallery.DataServiceClient.NamedBytes;
import com.framebucket.subscription.EntitlementService;
import com.framebucket.subscription.Feature;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Studio editing look: the studio uploads finished photos it likes, the data service learns the look and
 * applies it to every photo it processes, then stamps the studio logo away from faces.
 */
@RestController
public class EditingStyleController {
    private static final int MAX_REFERENCES = 40;
    private static final long MAX_PHOTO_BYTES = 40L * 1024 * 1024;

    private final DataServiceClient dataService;
    private final EntitlementService entitlements;

    public EditingStyleController(DataServiceClient dataService, EntitlementService entitlements) {
        this.dataService = dataService;
        this.entitlements = entitlements;
    }

    @GetMapping("/api/studio/editing-style")
    public Map<String, Object> style(@AuthenticationPrincipal PhotographerPrincipal p) {
        Map<String, Object> response = new LinkedHashMap<>(dataService.studioStyle(p.getId()));
        response.put("planAllows", entitlements.forPhotographer(p.getId()).has(Feature.CUSTOM_BRANDING));
        return response;
    }

    @PostMapping("/api/studio/editing-style/references")
    public Map<String, Object> uploadReferences(@AuthenticationPrincipal PhotographerPrincipal p,
                                                @RequestParam("files") List<MultipartFile> files) {
        entitlements.require(p.getId(), Feature.CUSTOM_BRANDING);
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose the reference photos to learn from.");
        }
        if (files.size() > MAX_REFERENCES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload at most " + MAX_REFERENCES + " reference photos.");
        }
        List<NamedBytes> references = new ArrayList<>();
        for (MultipartFile file : files) {
            references.add(read(file));
        }
        return withPlan(p, dataService.saveStyleReferences(p.getId(), references));
    }

    @PutMapping("/api/studio/editing-style")
    public Map<String, Object> update(@AuthenticationPrincipal PhotographerPrincipal p, @RequestBody StyleSettings request) {
        entitlements.require(p.getId(), Feature.CUSTOM_BRANDING);
        Map<String, Object> settings = new LinkedHashMap<>();
        if (request.strength() != null) {
            if (request.strength() < 0 || request.strength() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Strength must be between 0 and 100.");
            }
            settings.put("strength", request.strength());
        }
        if (request.applyStyle() != null) {
            settings.put("apply_style", request.applyStyle());
        }
        if (request.stampLogo() != null) {
            settings.put("stamp_logo", request.stampLogo());
        }
        return withPlan(p, dataService.updateStyle(p.getId(), settings));
    }

    @DeleteMapping("/api/studio/editing-style")
    public Map<String, Object> clear(@AuthenticationPrincipal PhotographerPrincipal p) {
        entitlements.require(p.getId(), Feature.CUSTOM_BRANDING);
        return withPlan(p, dataService.clearStyle(p.getId()));
    }

    @PostMapping("/api/studio/editing-style/preview")
    public ResponseEntity<byte[]> preview(@AuthenticationPrincipal PhotographerPrincipal p,
                                         @RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "strength", required = false) Integer strength) {
        entitlements.require(p.getId(), Feature.CUSTOM_BRANDING);
        byte[] jpeg = dataService.stylePreview(p.getId(), read(file), strength);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore()).body(jpeg);
    }

    private Map<String, Object> withPlan(PhotographerPrincipal p, Map<String, Object> response) {
        Map<String, Object> copy = new LinkedHashMap<>(response);
        copy.put("planAllows", true);
        return copy;
    }

    private static NamedBytes read(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One of the photos is empty.");
        }
        if (file.getSize() > MAX_PHOTO_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Each photo must be 40 MB or smaller.");
        }
        String name = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "photo.jpg" : file.getOriginalFilename().replaceAll("[\\\\/\\r\\n\"]", "_");
        MediaType type;
        try {
            type = file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(file.getContentType());
        } catch (IllegalArgumentException exception) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return new NamedBytes(name, type, file.getBytes());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That photo could not be read.", exception);
        }
    }

    public record StyleSettings(Integer strength, Boolean applyStyle, Boolean stampLogo) {
    }
}
