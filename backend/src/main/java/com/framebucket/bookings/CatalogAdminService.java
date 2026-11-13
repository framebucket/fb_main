package com.framebucket.bookings;

import com.framebucket.bookings.BookingDtos.CatalogResponse;
import com.framebucket.bookings.BookingDtos.CatalogSettingsRequest;
import com.framebucket.bookings.BookingDtos.ItemRequest;
import com.framebucket.bookings.BookingDtos.ItemView;
import com.framebucket.bookings.BookingDtos.PackageRequest;
import com.framebucket.bookings.BookingDtos.PackageView;
import com.framebucket.enquiry.service.QuotePricingService;
import com.framebucket.subscription.EntitlementService;
import com.framebucket.subscription.Feature;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** The photographer's editor for their quote catalogue (services, add-ons, packages, pricing settings, contract terms). */
@Service
public class CatalogAdminService {
    private final JdbcTemplate jdbc;
    private final QuotePricingService catalog;
    private final EntitlementService entitlements;

    public CatalogAdminService(JdbcTemplate jdbc, QuotePricingService catalog, EntitlementService entitlements) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.entitlements = entitlements;
    }

    @Transactional
    public CatalogResponse catalog(UUID photographerId) {
        catalog.ensureSeeded(photographerId);
        QuotePricingService.Settings settings = catalog.settings(photographerId);
        List<ItemView> items = catalog.items(photographerId, false).stream()
                .map(i -> new ItemView(i.id(), i.code(), i.kind(), i.name(), i.description(), i.price(), i.active(), i.sortOrder()))
                .toList();
        return new CatalogResponse(settings.albumSheetPrice(), settings.albumSheetsPerEvent(), settings.discountPercent(), settings.advancePercent(),
                settings.contractTerms(), settings.customTerms(),
                items.stream().filter(i -> "SERVICE".equals(i.kind())).toList(),
                items.stream().filter(i -> "ADDON".equals(i.kind())).toList(),
                catalog.packages(photographerId, false).stream().map(p -> new PackageView(p.id(), p.name(), p.description(),
                        p.inclusions(), p.price(), p.highlighted(), p.active(), p.sortOrder())).toList());
    }

    @Transactional
    public CatalogResponse saveSettings(UUID photographerId, CatalogSettingsRequest request) {
        entitlements.require(photographerId, Feature.CUSTOM_PACKAGES);
        String terms = request.contractTerms() == null || request.contractTerms().isBlank()
                || request.contractTerms().strip().equals(QuotePricingService.DEFAULT_TERMS) ? null : request.contractTerms().strip();
        jdbc.update("""
                        INSERT INTO fb_quote_settings (photographer_id, album_sheet_price, album_sheets_per_event, discount_percent, advance_percent, contract_terms)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (photographer_id) DO UPDATE SET album_sheet_price = EXCLUDED.album_sheet_price,
                            album_sheets_per_event = EXCLUDED.album_sheets_per_event,
                            discount_percent = EXCLUDED.discount_percent, advance_percent = EXCLUDED.advance_percent,
                            contract_terms = EXCLUDED.contract_terms, updated_at = CURRENT_TIMESTAMP
                        """, photographerId, request.albumSheetPrice(),
                request.albumSheetsPerEvent() == null ? QuotePricingService.DEFAULT_ALBUM_SHEETS_PER_EVENT : request.albumSheetsPerEvent(),
                request.discountPercent(), request.advancePercent(), terms);
        return catalog(photographerId);
    }

    @Transactional
    public CatalogResponse saveItem(UUID photographerId, UUID itemId, ItemRequest request) {
        entitlements.require(photographerId, Feature.CUSTOM_PACKAGES);
        catalog.ensureSeeded(photographerId);
        if (itemId == null) {
            jdbc.update("""
                            INSERT INTO fb_quote_item (id, photographer_id, code, kind, name, description, price, active, sort_order)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """, UUID.randomUUID(), photographerId, uniqueCode(photographerId, request.name()), request.kind(),
                    request.name().strip(), blankToNull(request.description()), request.price(), request.active(), request.sortOrder());
        } else {
            int updated = jdbc.update("""
                            UPDATE fb_quote_item SET kind = ?, name = ?, description = ?, price = ?, active = ?, sort_order = ?
                            WHERE id = ? AND photographer_id = ?
                            """, request.kind(), request.name().strip(), blankToNull(request.description()), request.price(),
                    request.active(), request.sortOrder(), itemId, photographerId);
            if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found.");
        }
        return catalog(photographerId);
    }

    /** Items are switched off rather than deleted so older quotations still show their names. */
    @Transactional
    public CatalogResponse deactivateItem(UUID photographerId, UUID itemId) {
        entitlements.require(photographerId, Feature.CUSTOM_PACKAGES);
        if (jdbc.update("UPDATE fb_quote_item SET active = FALSE WHERE id = ? AND photographer_id = ?", itemId, photographerId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found.");
        }
        return catalog(photographerId);
    }

    @Transactional
    public CatalogResponse savePackage(UUID photographerId, UUID packageId, PackageRequest request) {
        entitlements.require(photographerId, Feature.CUSTOM_PACKAGES);
        String inclusions = request.inclusions() == null ? null : String.join("\n", request.inclusions().stream().map(String::strip).toList());
        if (packageId == null) {
            Integer count = jdbc.queryForObject("SELECT count(*) FROM fb_quote_package WHERE photographer_id = ?", Integer.class, photographerId);
            if (count != null && count >= 30) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "You can have up to 30 packages.");
            jdbc.update("""
                            INSERT INTO fb_quote_package (id, photographer_id, name, description, inclusions, price, highlighted, active, sort_order)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """, UUID.randomUUID(), photographerId, request.name().strip(), blankToNull(request.description()),
                    inclusions, request.price(), request.highlighted(), request.active(), request.sortOrder());
        } else if (jdbc.update("""
                        UPDATE fb_quote_package SET name = ?, description = ?, inclusions = ?, price = ?, highlighted = ?, active = ?, sort_order = ?
                        WHERE id = ? AND photographer_id = ?
                        """, request.name().strip(), blankToNull(request.description()), inclusions, request.price(),
                request.highlighted(), request.active(), request.sortOrder(), packageId, photographerId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Package not found.");
        }
        return catalog(photographerId);
    }

    @Transactional
    public CatalogResponse deletePackage(UUID photographerId, UUID packageId) {
        entitlements.require(photographerId, Feature.CUSTOM_PACKAGES);
        if (jdbc.update("DELETE FROM fb_quote_package WHERE id = ? AND photographer_id = ?", packageId, photographerId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Package not found.");
        }
        return catalog(photographerId);
    }

    private String uniqueCode(UUID photographerId, String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFKD).replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("(^-|-$)", "").toLowerCase(Locale.ROOT);
        if (base.isEmpty()) base = "item";
        if (base.length() > 60) base = base.substring(0, 60);
        String code = base;
        for (int i = 2; ; i++) {
            Integer exists = jdbc.queryForObject("SELECT count(*) FROM fb_quote_item WHERE photographer_id = ? AND code = ?",
                    Integer.class, photographerId, code);
            if (exists == null || exists == 0) return code;
            code = base + "-" + i;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    static BigDecimal zero() {
        return BigDecimal.ZERO;
    }
}
