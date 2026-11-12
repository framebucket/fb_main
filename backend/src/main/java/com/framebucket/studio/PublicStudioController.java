package com.framebucket.studio;

import com.framebucket.studio.StudioDtos.PublicFlipbookResponse;
import com.framebucket.studio.StudioDtos.PublicPortfolioResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

/** Unauthenticated pages: a photographer's published portfolio and shared e-album flipbooks. */
@RestController
public class PublicStudioController {
    private final PortfolioService portfolio;
    private final FlipbookService flipbooks;

    public PublicStudioController(PortfolioService portfolio, FlipbookService flipbooks) {
        this.portfolio = portfolio;
        this.flipbooks = flipbooks;
    }

    @GetMapping("/api/public/portfolio/{slug}")
    public PublicPortfolioResponse portfolio(@PathVariable String slug) {
        return portfolio.publicPortfolio(slug);
    }

    @GetMapping("/api/public/portfolio/{slug}/items/{itemId}/image")
    public ResponseEntity<StreamingResponseBody> portfolioImage(@PathVariable String slug, @PathVariable UUID itemId) {
        return portfolio.publicImage(slug, itemId);
    }

    @GetMapping("/api/public/flipbooks/{token}")
    public PublicFlipbookResponse flipbook(@PathVariable String token) {
        return flipbooks.publicFlipbook(token);
    }

    @GetMapping("/api/public/flipbooks/{token}/pages/{pageIndex}")
    public ResponseEntity<StreamingResponseBody> flipbookPage(@PathVariable String token, @PathVariable int pageIndex) {
        return flipbooks.page(token, pageIndex);
    }
}
