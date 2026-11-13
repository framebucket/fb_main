package com.framebucket.enquiry.service;

import com.framebucket.enquiry.api.EnquiryDtos.QuoteCalculationResponse;
import com.framebucket.enquiry.api.EnquiryDtos.QuoteEventRequest;
import com.framebucket.enquiry.api.EnquiryDtos.QuotePackageOption;
import com.framebucket.enquiry.api.EnquiryDtos.QuoteServiceOption;
import com.framebucket.enquiry.api.EnquiryDtos.QuoteSubmissionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Each photographer's own quote catalogue: per-event services, one-time add-ons, fixed-price packages and
 * pricing settings (album sheet price, standard discount, advance %). New studios are seeded with the
 * catalogue FrameBucket used to hard-code, so existing quotes keep their service codes and prices.
 */
@Service
public class QuotePricingService {
    public record DefaultItem(String code, String kind, String name, String description, int price) {
    }

    public static final List<DefaultItem> DEFAULTS = List.of(
            new DefaultItem("photo", "SERVICE", "Conventional Photography", "Traditional event coverage", 7000),
            new DefaultItem("video", "SERVICE", "Conventional Videography", "Event video coverage", 8000),
            new DefaultItem("candid", "SERVICE", "Candid Photography", "Natural, candid moments", 20000),
            new DefaultItem("candidvideo", "SERVICE", "Candid Videography", "Cinematic candid film", 25000),
            new DefaultItem("drone", "SERVICE", "Drone Videography", "Aerial coverage where permitted", 8000),
            new DefaultItem("led", "SERVICE", "LED Wall", "On-site display setup", 8000),
            new DefaultItem("mixing", "SERVICE", "Spot Mixing", "Live video mixing setup", 20000),
            new DefaultItem("qr", "SERVICE", "Live Photo QR Scanner", "Guest photo access by QR", 3000),
            new DefaultItem("youtube", "SERVICE", "YouTube Live", "Live streaming coverage", 6000),
            new DefaultItem("prewedding", "ADDON", "Pre-wedding shoot", "Half-day couple shoot at one location", 25000),
            new DefaultItem("sde", "ADDON", "Same-day edit reel", "Short reel delivered on the event day", 12000),
            new DefaultItem("parentsalbum", "ADDON", "Extra album for parents", "Smaller copy of the main album", 9000)
    );

    public static final String DEFAULT_TERMS = """
            1. Booking: The date is reserved only after this agreement is signed and the advance is received. The advance is non-refundable.
            2. Payments: The balance is payable on or before the event date unless agreed otherwise in writing.
            3. Deliverables: Edited photos, films and albums are delivered as described in the quotation. Typical timelines are shared in the delivery tracker.
            4. Changes: Changes to dates, venues or coverage may change the price and depend on availability.
            5. Cancellation: If the client cancels, amounts paid are not refundable. If the studio cannot attend due to unforeseen circumstances, amounts paid for that event are refunded in full.
            6. Usage: The studio may use selected photos and films in its portfolio and social media unless the client asks otherwise in writing.
            7. Album selection: Album designs are prepared after the client selects photos. Up to two rounds of design changes are included.
            """.strip();

    public record CatalogItem(UUID id, String code, String kind, String name, String description, BigDecimal price,
                              boolean active, int sortOrder) {
    }

    public record CatalogPackage(UUID id, String name, String description, List<String> inclusions, BigDecimal price,
                                 boolean highlighted, boolean active, int sortOrder) {
    }

    /**
     * Album pricing: customers only tick "Album"; each event then includes {@code albumSheetsPerEvent} sheets at
     * {@code albumSheetPrice} (default 30 × ₹400 = ₹12,000 per event). The per-sheet price is never shown to customers.
     */
    public record Settings(BigDecimal albumSheetPrice, int albumSheetsPerEvent, BigDecimal discountPercent,
                           BigDecimal advancePercent, String contractTerms, boolean customTerms) {
    }

    public static final int DEFAULT_ALBUM_SHEETS_PER_EVENT = 30;

    static final RowMapper<CatalogItem> ITEM = (rs, n) -> new CatalogItem(rs.getObject("id", UUID.class),
            rs.getString("code"), rs.getString("kind"), rs.getString("name"), rs.getString("description"),
            rs.getBigDecimal("price"), rs.getBoolean("active"), rs.getInt("sort_order"));

    static final RowMapper<CatalogPackage> PACKAGE = (rs, n) -> new CatalogPackage(rs.getObject("id", UUID.class),
            rs.getString("name"), rs.getString("description"), lines(rs.getString("inclusions")), rs.getBigDecimal("price"),
            rs.getBoolean("highlighted"), rs.getBoolean("active"), rs.getInt("sort_order"));

    private final JdbcTemplate jdbc;
    private final QuoteCatalogSeeder seeder;

    public QuotePricingService(JdbcTemplate jdbc, QuoteCatalogSeeder seeder) {
        this.jdbc = jdbc;
        this.seeder = seeder;
    }

    /** Copies the default catalogue for a studio that has none yet (in its own transaction, see the seeder). */
    public void ensureSeeded(UUID photographerId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM fb_quote_item WHERE photographer_id = ?", Integer.class, photographerId);
        if (count == null || count == 0) seeder.seed(photographerId);
    }

    public List<CatalogItem> items(UUID photographerId, boolean activeOnly) {
        return jdbc.query("SELECT * FROM fb_quote_item WHERE photographer_id = ?" + (activeOnly ? " AND active" : "")
                + " ORDER BY kind DESC, sort_order, name", ITEM, photographerId);
    }

    public List<CatalogPackage> packages(UUID photographerId, boolean activeOnly) {
        return jdbc.query("SELECT * FROM fb_quote_package WHERE photographer_id = ?" + (activeOnly ? " AND active" : "")
                + " ORDER BY sort_order, price, name", PACKAGE, photographerId);
    }

    public Settings settings(UUID photographerId) {
        return jdbc.query("SELECT * FROM fb_quote_settings WHERE photographer_id = ?", (rs, n) -> {
                    String terms = rs.getString("contract_terms");
                    boolean custom = terms != null && !terms.isBlank();
                    return new Settings(rs.getBigDecimal("album_sheet_price"), rs.getInt("album_sheets_per_event"),
                            rs.getBigDecimal("discount_percent"), rs.getBigDecimal("advance_percent"),
                            custom ? terms : DEFAULT_TERMS, custom);
                }, photographerId).stream().findFirst()
                .orElse(new Settings(new BigDecimal("400.00"), DEFAULT_ALBUM_SHEETS_PER_EVENT, new BigDecimal("5.00"),
                        new BigDecimal("30.00"), DEFAULT_TERMS, false));
    }

    /** Album sheets for a quote: the studio's sheets-per-event for every event, if the customer wants an album. */
    public int albumSheets(UUID photographerId, QuoteSubmissionRequest request) {
        if (!request.wantsAlbum()) return 0;
        return settings(photographerId).albumSheetsPerEvent() * Math.max(1, request.events().size());
    }

    public List<QuoteServiceOption> serviceOptions(UUID photographerId) {
        ensureSeeded(photographerId);
        return items(photographerId, true).stream().filter(item -> "SERVICE".equals(item.kind()))
                .map(item -> new QuoteServiceOption(item.code(), item.name(), item.description())).toList();
    }

    public List<QuoteServiceOption> addonOptions(UUID photographerId) {
        return items(photographerId, true).stream().filter(item -> "ADDON".equals(item.kind()))
                .map(item -> new QuoteServiceOption(item.code(), item.name(), item.description())).toList();
    }

    public List<QuotePackageOption> packageOptions(UUID photographerId) {
        return packages(photographerId, true).stream()
                .map(p -> new QuotePackageOption(p.id(), p.name(), p.description(), p.inclusions(), p.price(), p.highlighted()))
                .toList();
    }

    public QuoteCalculationResponse calculate(UUID photographerId, QuoteSubmissionRequest request) {
        ensureSeeded(photographerId);
        Map<String, CatalogItem> active = items(photographerId, true).stream()
                .collect(Collectors.toMap(CatalogItem::code, Function.identity()));
        BigDecimal subtotal = BigDecimal.ZERO;
        for (QuoteEventRequest event : request.events()) {
            for (String code : new LinkedHashSet<>(event.serviceIds())) {
                CatalogItem item = active.get(code);
                if (item == null || !"SERVICE".equals(item.kind())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A selected photo service is not available.");
                }
                subtotal = subtotal.add(item.price());
            }
        }
        for (String code : addonCodes(request)) {
            CatalogItem item = active.get(code);
            if (item == null || !"ADDON".equals(item.kind())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A selected add-on is not available.");
            }
            subtotal = subtotal.add(item.price());
        }
        if (request.packageId() != null) {
            subtotal = subtotal.add(packageById(photographerId, request.packageId(), true).price());
        }
        Settings settings = settings(photographerId);
        subtotal = subtotal.add(settings.albumSheetPrice().multiply(BigDecimal.valueOf(albumSheets(photographerId, request))));
        BigDecimal discount = subtotal.multiply(settings.discountPercent())
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.subtract(discount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return new QuoteCalculationResponse(total);
    }

    /** Service names for a saved quote: inactive items still resolve so old quotations can be regenerated. */
    public List<String> serviceNames(UUID photographerId, QuoteEventRequest event) {
        Map<String, CatalogItem> all = byCode(photographerId);
        return event.serviceIds().stream().map(code -> all.containsKey(code) ? all.get(code).name() : code).toList();
    }

    public List<String> addonNames(UUID photographerId, List<String> codes) {
        if (codes == null || codes.isEmpty()) return List.of();
        Map<String, CatalogItem> all = byCode(photographerId);
        return codes.stream().map(code -> all.containsKey(code) ? all.get(code).name() : code).toList();
    }

    public CatalogPackage packageById(UUID photographerId, UUID packageId, boolean requireActive) {
        return jdbc.query("SELECT * FROM fb_quote_package WHERE id = ? AND photographer_id = ?", PACKAGE, packageId, photographerId)
                .stream().filter(p -> !requireActive || p.active()).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected package is not available."));
    }

    public static List<String> addonCodes(QuoteSubmissionRequest request) {
        return request.addonIds() == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(request.addonIds()));
    }

    private Map<String, CatalogItem> byCode(UUID photographerId) {
        ensureSeeded(photographerId);
        return items(photographerId, false).stream().collect(Collectors.toMap(CatalogItem::code, Function.identity()));
    }

    static List<String> lines(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("\\r?\\n")).map(String::trim).filter(line -> !line.isEmpty()).toList();
    }
}
