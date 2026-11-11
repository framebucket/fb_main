package com.framebucket.dashboard.api;

import com.framebucket.customer.repository.CustomerRepository;
import com.framebucket.enquiry.repository.QuotationRepository;
import com.framebucket.event.repository.EventBookingRepository;
import com.framebucket.user.auth.repository.PhotographerUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

/** Platform-wide totals shown on the public landing page. Counts only; no personal data. */
@RestController
@RequestMapping("/api/public/stats")
public class PublicStatsController {
    private static final Duration CACHE_FOR = Duration.ofMinutes(1);

    private final PhotographerUserRepository photographers;
    private final QuotationRepository quotations;
    private final CustomerRepository customers;
    private final EventBookingRepository events;
    private final long minPhotographers;
    private volatile PlatformStats cached;
    private volatile Instant cachedAt = Instant.EPOCH;

    public PublicStatsController(PhotographerUserRepository photographers, QuotationRepository quotations,
                                 CustomerRepository customers, EventBookingRepository events,
                                 @Value("${app.public-stats.min-photographers:50}") long minPhotographers) {
        this.minPhotographers = minPhotographers;
        this.photographers = photographers;
        this.quotations = quotations;
        this.customers = customers;
        this.events = events;
    }

    @GetMapping
    public ResponseEntity<PlatformStats> stats() {
        if (cached == null || Instant.now().isAfter(cachedAt.plus(CACHE_FOR))) {
            long joined = photographers.countByActiveTrueAndEmailVerifiedTrueAndMobileVerifiedTrue();
            // Keep early, small numbers private: the landing page shows the charts only past the threshold.
            cached = joined >= minPhotographers
                    ? new PlatformStats(true, joined, quotations.count(), customers.count(), events.count())
                    : new PlatformStats(false, null, null, null, null);
            cachedAt = Instant.now();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(CACHE_FOR).cachePublic())
                .body(cached);
    }

    public record PlatformStats(boolean visible, Long photographers, Long quotations, Long customers, Long events) {}
}
