package com.framebucket.delivery;

import com.framebucket.delivery.DeliveryDtos.DomainResolution;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

@RestController
public class PublicBrandController {
    private final BrandService brand;

    public PublicBrandController(BrandService brand) {
        this.brand = brand;
    }

    @GetMapping("/api/public/brand/{photographerId}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable UUID photographerId) {
        BrandService.Logo logo = brand.logo(photographerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No logo."));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(logo.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic()).body(logo.bytes());
    }

    /** The studio a verified custom domain belongs to; the SPA calls this to render that studio's page at "/". */
    @GetMapping("/api/public/domains/resolve")
    public DomainResolution resolve(HttpServletRequest request) {
        return brand.resolveHost(request.getServerName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not a studio domain."));
    }

    /** Caddy's on-demand TLS "ask" endpoint: 200 only for verified studio domains. */
    @GetMapping("/api/public/domains/check")
    public ResponseEntity<Void> check(@RequestParam String domain) {
        return brand.resolveHost(domain).isPresent() ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
