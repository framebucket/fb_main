package com.framebucket.delivery;

import com.framebucket.delivery.DeliveryDtos.BrandRequest;
import com.framebucket.delivery.DeliveryDtos.BrandResponse;
import com.framebucket.delivery.DeliveryDtos.CommentRequest;
import com.framebucket.delivery.DeliveryDtos.DomainRequest;
import com.framebucket.delivery.DeliveryDtos.FilmRequest;
import com.framebucket.delivery.DeliveryDtos.FilmView;
import com.framebucket.delivery.DeliveryDtos.ProofCreateRequest;
import com.framebucket.delivery.DeliveryDtos.ProofView;
import com.framebucket.delivery.DeliveryDtos.StepRequest;
import com.framebucket.delivery.DeliveryDtos.StepView;
import com.framebucket.delivery.DeliveryDtos.VideoOption;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class DeliveryController {
    private final DeliveryService delivery;
    private final FilmService films;
    private final ProofService proofs;
    private final BrandService brand;

    public DeliveryController(DeliveryService delivery, FilmService films, ProofService proofs, BrandService brand) {
        this.delivery = delivery;
        this.films = films;
        this.proofs = proofs;
        this.brand = brand;
    }

    // ---- delivery checklist ----
    @GetMapping("/api/events/{eventId}/delivery/steps")
    public List<StepView> steps(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId) {
        return delivery.steps(p.getId(), eventId);
    }

    @PostMapping("/api/events/{eventId}/delivery/steps")
    public List<StepView> addStep(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId,
                                  @Valid @RequestBody StepRequest request) {
        return delivery.save(p.getId(), eventId, null, request);
    }

    @PutMapping("/api/events/{eventId}/delivery/steps/{stepId}")
    public List<StepView> updateStep(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId,
                                     @PathVariable UUID stepId, @Valid @RequestBody StepRequest request) {
        return delivery.save(p.getId(), eventId, stepId, request);
    }

    @DeleteMapping("/api/events/{eventId}/delivery/steps/{stepId}")
    public List<StepView> deleteStep(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId,
                                     @PathVariable UUID stepId) {
        return delivery.delete(p.getId(), eventId, stepId);
    }

    // ---- films ----
    @GetMapping("/api/events/{eventId}/films")
    public List<FilmView> films(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId) {
        return films.forPhotographer(p.getId(), eventId);
    }

    @GetMapping("/api/events/{eventId}/films/videos")
    public List<VideoOption> galleryVideos(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId) {
        return films.galleryVideos(p.getId(), eventId);
    }

    @PostMapping("/api/events/{eventId}/films")
    public List<FilmView> addFilm(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId,
                                  @Valid @RequestBody FilmRequest request) {
        return films.add(p.getId(), eventId, request);
    }

    @DeleteMapping("/api/events/{eventId}/films/{filmId}")
    public List<FilmView> deleteFilm(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId,
                                     @PathVariable UUID filmId) {
        return films.delete(p.getId(), eventId, filmId);
    }

    // ---- album proofs ----
    @GetMapping("/api/events/{eventId}/proofs")
    public List<ProofView> proofs(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId) {
        return proofs.forPhotographer(p.getId(), eventId);
    }

    @PostMapping("/api/events/{eventId}/proofs")
    public List<ProofView> sendProof(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId,
                                     @Valid @RequestBody ProofCreateRequest request) {
        return proofs.create(p.getId(), eventId, request);
    }

    @PostMapping("/api/events/{eventId}/proofs/{proofId}/comments")
    public List<ProofView> reply(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId,
                                 @PathVariable UUID proofId, @Valid @RequestBody CommentRequest request) {
        return proofs.photographerComment(p.getId(), eventId, proofId, request);
    }

    @PutMapping("/api/events/{eventId}/proofs/{proofId}/comments/{commentId}")
    public List<ProofView> resolve(@AuthenticationPrincipal PhotographerPrincipal p, @PathVariable UUID eventId,
                                   @PathVariable UUID proofId, @PathVariable UUID commentId, @RequestBody Map<String, Boolean> body) {
        return proofs.resolveComment(p.getId(), eventId, proofId, commentId, Boolean.TRUE.equals(body.get("resolved")));
    }

    // ---- brand, watermark, domain ----
    @GetMapping("/api/studio/brand")
    public BrandResponse brand(@AuthenticationPrincipal PhotographerPrincipal p) {
        return brand.response(p.getId());
    }

    @PutMapping("/api/studio/brand")
    public BrandResponse updateBrand(@AuthenticationPrincipal PhotographerPrincipal p, @Valid @RequestBody BrandRequest request) {
        return brand.update(p.getId(), request);
    }

    @PostMapping("/api/studio/brand/logo")
    public BrandResponse uploadLogo(@AuthenticationPrincipal PhotographerPrincipal p, @RequestParam("file") MultipartFile file) {
        return brand.uploadLogo(p.getId(), file);
    }

    @DeleteMapping("/api/studio/brand/logo")
    public BrandResponse deleteLogo(@AuthenticationPrincipal PhotographerPrincipal p) {
        return brand.deleteLogo(p.getId());
    }

    @PutMapping("/api/studio/brand/domain")
    public BrandResponse setDomain(@AuthenticationPrincipal PhotographerPrincipal p, @Valid @RequestBody DomainRequest request) {
        return brand.setDomain(p.getId(), request.domain());
    }

    @PostMapping("/api/studio/brand/domain/verify")
    public BrandResponse verifyDomain(@AuthenticationPrincipal PhotographerPrincipal p) {
        return brand.verifyDomain(p.getId());
    }

    @DeleteMapping("/api/studio/brand/domain")
    public BrandResponse removeDomain(@AuthenticationPrincipal PhotographerPrincipal p) {
        return brand.removeDomain(p.getId());
    }
}
