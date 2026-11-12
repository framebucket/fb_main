package com.framebucket.enquiry.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/** Seeds a studio's quote catalogue in a separate read-write transaction (callers may be read-only). */
@Component
public class QuoteCatalogSeeder {
    private final JdbcTemplate jdbc;

    public QuoteCatalogSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void seed(UUID photographerId) {
        int sort = 10;
        for (QuotePricingService.DefaultItem item : QuotePricingService.DEFAULTS) {
            jdbc.update("""
                            INSERT INTO fb_quote_item (id, photographer_id, code, kind, name, description, price, sort_order)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (photographer_id, code) DO NOTHING
                            """, UUID.randomUUID(), photographerId, item.code(), item.kind(), item.name(), item.description(),
                    BigDecimal.valueOf(item.price()), sort);
            sort += 10;
        }
    }
}
