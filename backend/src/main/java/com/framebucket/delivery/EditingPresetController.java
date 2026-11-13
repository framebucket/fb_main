package com.framebucket.delivery;

import com.framebucket.gallery.DataServiceClient;
import com.framebucket.gallery.DataServiceClient.NamedBytes;
import com.framebucket.subscription.EntitlementService;
import com.framebucket.subscription.PlanRestrictionException;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Editing presets: the studio uploads its Lightroom presets (.xmp, .lrtemplate) or LUTs (.cube) once; the data
 * service picks the right one for each photo and finishes the photo on top of it. Every plan gets presets; how many
 * depends on the plan (Free 3, Solo 5, Studio 10, Business 25, editable by admins).
 */
@RestController
public class EditingPresetController {
    private static final long MAX_PRESET_BYTES = 4L * 1024 * 1024;
    private static final long MAX_PHOTO_BYTES = 40L * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of(".xmp", ".lrtemplate", ".cube");
    private static final Set<String> SCENES = Set.of("any", "outdoor", "indoor", "night", "portrait");

    private final DataServiceClient dataService;
    private final EntitlementService entitlements;

    public EditingPresetController(DataServiceClient dataService, EntitlementService entitlements) {
        this.dataService = dataService;
        this.entitlements = entitlements;
    }

    @GetMapping("/api/studio/editing-presets")
    public Map<String, Object> list(@AuthenticationPrincipal PhotographerPrincipal p) {
        return withLimit(p, dataService.presets(p.getId()));
    }

    @PostMapping("/api/studio/editing-presets")
    public Map<String, Object> upload(@AuthenticationPrincipal PhotographerPrincipal p,
                                      @RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "name", required = false) String name,
                                      @RequestParam(value = "scene", required = false) String scene) {
        Integer limit = entitlements.editingPresetLimit(p.getId());
        if (limit != null && limit <= 0) {
            throw PlanRestrictionException.limitReached("editingPresets", "Editing presets aren't included in your plan.");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a preset file.");
        }
        if (file.getSize() > MAX_PRESET_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Preset files must be 4 MB or smaller.");
        }
        String filename = cleanName(file.getOriginalFilename(), "preset.xmp");
        String lower = filename.toLowerCase(Locale.ROOT);
        if (EXTENSIONS.stream().noneMatch(lower::endsWith)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Upload a Lightroom preset (.xmp or .lrtemplate) or a 3D LUT (.cube).");
        }
        String cleanScene = scene == null || scene.isBlank() ? "any" : scene.trim().toLowerCase(Locale.ROOT);
        if (!SCENES.contains(cleanScene)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown scene.");
        }
        if (name != null && name.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preset names can be up to 80 characters.");
        }
        Map<String, Object> current = dataService.presets(p.getId());
        if (limit != null && current.get("count") instanceof Number count && count.intValue() >= limit) {
            throw limitReached(limit);
        }
        try {
            NamedBytes bytes = new NamedBytes(filename, MediaType.APPLICATION_OCTET_STREAM, file.getBytes());
            return withLimit(p, dataService.uploadPreset(p.getId(), bytes, name, cleanScene, limit));
        } catch (ResponseStatusException exception) {
            // The service enforces the limit too (parallel uploads); show the same upgrade prompt.
            if (exception.getStatusCode().value() == 409 && limit != null) throw limitReached(limit);
            throw exception;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That file could not be read.", exception);
        }
    }

    @PatchMapping("/api/studio/editing-presets/{presetId}")
    public Map<String, Object> update(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable String presetId,
                                      @RequestBody PresetChange request) {
        Map<String, Object> changes = new LinkedHashMap<>();
        if (request.name() != null) {
            if (request.name().isBlank() || request.name().length() > 80) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give the preset a name of up to 80 characters.");
            }
            changes.put("name", request.name().trim());
        }
        if (request.scene() != null) {
            if (!SCENES.contains(request.scene())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown scene.");
            }
            changes.put("scene", request.scene());
        }
        return withLimit(p, dataService.updatePreset(p.getId(), presetId, changes));
    }

    @DeleteMapping("/api/studio/editing-presets/{presetId}")
    public Map<String, Object> delete(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable String presetId) {
        return withLimit(p, dataService.deletePreset(p.getId(), presetId));
    }

    @PutMapping("/api/studio/editing-presets/settings")
    public Map<String, Object> settings(@AuthenticationPrincipal PhotographerPrincipal p, @RequestBody PresetSettings request) {
        if (request.usePresets() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "usePresets is required.");
        }
        return withLimit(p, dataService.presetSettings(p.getId(), request.usePresets()));
    }

    /** Renders a sample photo; the AI picks the preset unless presetId is given. JSON so the pick can be shown. */
    @PostMapping("/api/studio/editing-presets/preview")
    public Map<String, Object> preview(@AuthenticationPrincipal PhotographerPrincipal p,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "presetId", required = false) String presetId) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a sample photo.");
        }
        if (file.getSize() > MAX_PHOTO_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The sample photo must be 40 MB or smaller.");
        }
        try {
            NamedBytes sample = new NamedBytes(cleanName(file.getOriginalFilename(), "sample.jpg"),
                    MediaType.APPLICATION_OCTET_STREAM, file.getBytes());
            DataServiceClient.PresetPreview preview = dataService.presetPreview(p.getId(), sample, presetId);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("image", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(preview.jpeg()));
            response.put("preset", preview.preset());
            response.put("scene", preview.scene());
            return response;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That photo could not be read.", exception);
        }
    }

    private Map<String, Object> withLimit(PhotographerPrincipal p, Map<String, Object> response) {
        Map<String, Object> copy = new LinkedHashMap<>(response);
        copy.put("limit", entitlements.editingPresetLimit(p.getId()));
        return copy;
    }

    private static PlanRestrictionException limitReached(int limit) {
        return PlanRestrictionException.limitReached("editingPresets",
                "Your plan allows " + limit + " editing presets. Delete one or upgrade your plan to add more.");
    }

    private static String cleanName(String original, String fallback) {
        return original == null || original.isBlank() ? fallback : original.replaceAll("[\\\\/\\r\\n\"]", "_");
    }

    public record PresetChange(String name, String scene) {
    }

    public record PresetSettings(Boolean usePresets) {
    }
}
