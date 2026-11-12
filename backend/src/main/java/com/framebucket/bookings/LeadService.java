package com.framebucket.bookings;

import com.framebucket.bookings.BookingDtos.EventTypeOption;
import com.framebucket.bookings.BookingDtos.LeadCaptureResponse;
import com.framebucket.bookings.BookingDtos.PublicEnquiryForm;
import com.framebucket.bookings.BookingDtos.PublicEnquiryRequest;
import com.framebucket.bookings.BookingDtos.PublicEnquiryResponse;
import com.framebucket.common.notify.Notifier;
import com.framebucket.delivery.BrandService;
import com.framebucket.enquiry.api.EnquiryDtos.CreateEnquiryRequest;
import com.framebucket.enquiry.service.EnquiryService;
import com.framebucket.subscription.EntitlementService;
import com.framebucket.subscription.Feature;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public enquiry form at /enquire/{slug} (the studio's portfolio address). Works as a standalone page for
 * Instagram link-in-bio, as an iframe on the studio's website, and as a WhatsApp click-to-chat entry point:
 * the enquiry is created first, then the visitor continues to WhatsApp with a prefilled message.
 */
@Service
public class LeadService {
    public static final Set<String> SOURCES = Set.of("MANUAL", "WEBSITE", "INSTAGRAM", "WHATSAPP", "FACEBOOK", "GOOGLE",
            "REFERRAL", "OTHER");
    private static final int PER_IP_LIMIT = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    record StudioRef(UUID photographerId, String slug, String name, String mobile, String email) {
    }

    private final JdbcTemplate jdbc;
    private final EnquiryService enquiries;
    private final EntitlementService entitlements;
    private final Notifier notifier;
    private final BrandService brand;
    private final QuoteTokens tokens;
    private final Map<String, Deque<Instant>> recentByIp = new ConcurrentHashMap<>();

    public LeadService(JdbcTemplate jdbc, EnquiryService enquiries, EntitlementService entitlements, Notifier notifier,
                       BrandService brand, QuoteTokens tokens) {
        this.jdbc = jdbc;
        this.enquiries = enquiries;
        this.entitlements = entitlements;
        this.notifier = notifier;
        this.brand = brand;
        this.tokens = tokens;
    }

    public LeadCaptureResponse links(UUID photographerId) {
        String slug = jdbc.query("SELECT portfolio_slug FROM fb_studio_profile WHERE photographer_id = ?",
                (rs, n) -> rs.getString(1), photographerId).stream().findFirst().orElse(null);
        if (slug == null || slug.isBlank()) {
            return new LeadCaptureResponse(false, null, null, null, null, null, null, null,
                    "Choose your studio address in Settings (Portfolio address) to get your enquiry links.");
        }
        String base = tokens.baseUrl() + "/enquire/" + slug;
        String embed = "<iframe src=\"" + base + "?source=website&embed=1\" title=\"Enquire\" "
                + "style=\"width:100%;max-width:560px;height:760px;border:0;border-radius:16px\" loading=\"lazy\"></iframe>";
        return new LeadCaptureResponse(true, slug, base, base + "?source=website", base + "?source=instagram",
                base + "?source=whatsapp&chat=1", base + "?source=facebook", embed, null);
    }

    public PublicEnquiryForm form(String slug) {
        StudioRef studio = studio(slug);
        List<EventTypeOption> types = jdbc.query("""
                        SELECT id, name FROM fb_event_list WHERE photographer_id IS NULL OR photographer_id = ?
                        ORDER BY photographer_id NULLS FIRST, name
                        """, (rs, n) -> new EventTypeOption(rs.getObject(1, UUID.class), rs.getString(2)), studio.photographerId());
        return new PublicEnquiryForm(studio.name(), studio.slug(), types, studio.mobile() != null && !studio.mobile().isBlank(),
                brand.brandView(studio.photographerId()));
    }

    public PublicEnquiryResponse submit(String slug, PublicEnquiryRequest request, String ip) {
        StudioRef studio = studio(slug);
        if (request.website() != null && !request.website().isBlank()) {
            // Honeypot field filled in: almost certainly a bot. Pretend it worked.
            return new PublicEnquiryResponse("Thank you! We'll be in touch soon.", null);
        }
        throttle(ip);
        Integer typeOk = jdbc.queryForObject("SELECT count(*) FROM fb_event_list WHERE id = ? AND (photographer_id IS NULL OR photographer_id = ?)",
                Integer.class, request.eventTypeId(), studio.photographerId());
        if (typeOk == null || typeOk == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an available event type.");
        String source = request.source() == null ? "WEBSITE" : request.source().trim().toUpperCase(Locale.ROOT);
        if (!SOURCES.contains(source) || "MANUAL".equals(source)) source = "OTHER";
        enquiries.createLead(studio.photographerId(), new CreateEnquiryRequest(request.name().strip(), request.mobileNumber(),
                request.email().strip(), request.eventTypeId()), source, blankToNull(request.message()), request.preferredDate());
        String eventType = jdbc.queryForObject("SELECT name FROM fb_event_list WHERE id = ?", String.class, request.eventTypeId());
        notifier.email(studio.email(), "New enquiry from " + label(source) + ": " + request.name().strip(),
                request.name().strip() + " (" + request.mobileNumber() + ", " + request.email().strip() + ") enquired about "
                        + eventType + (request.preferredDate() == null ? "" : " on " + request.preferredDate()) + ".\n"
                        + (request.message() == null || request.message().isBlank() ? "" : "\nMessage: " + request.message().strip() + "\n")
                        + "\nThey have been sent your quotation link. Open FrameBucket to follow up.");
        String whatsapp = null;
        if (studio.mobile() != null && !studio.mobile().isBlank()) {
            String text = "Hi " + studio.name() + ", I'm " + request.name().strip() + ". I just sent an enquiry for " + eventType
                    + (request.preferredDate() == null ? "" : " on " + request.preferredDate()) + ".";
            whatsapp = "https://wa.me/91" + studio.mobile().replaceAll("\\D", "").replaceFirst("^91(?=\\d{10}$)", "")
                    + "?text=" + URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
        }
        return new PublicEnquiryResponse("Thank you! " + studio.name() + " will get back to you soon. "
                + "We've also emailed you a link to build your quotation.", whatsapp);
    }

    private StudioRef studio(String slug) {
        if (slug == null || !slug.matches("[a-z0-9-]{2,60}")) throw notFound();
        StudioRef studio = jdbc.query("""
                        SELECT u.id, p.portfolio_slug, COALESCE(NULLIF(u.studio_name, ''), u.full_name) AS name, u.mobile_number, u.email
                        FROM fb_studio_profile p JOIN fb_user u ON u.id = p.photographer_id
                        WHERE p.portfolio_slug = ?
                        """, (rs, n) -> new StudioRef(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5)), slug)
                .stream().findFirst().orElseThrow(LeadService::notFound);
        if (!entitlements.has(studio.photographerId(), Feature.LEAD_CAPTURE)) throw notFound();
        return studio;
    }

    private void throttle(String ip) {
        String key = ip == null ? "unknown" : ip;
        Instant now = Instant.now();
        Deque<Instant> recent = recentByIp.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && recent.peekFirst().isBefore(now.minus(WINDOW))) recent.pollFirst();
            if (recent.size() >= PER_IP_LIMIT) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many enquiries from this connection. Please try again in a few minutes.");
            }
            recent.addLast(now);
        }
        if (recentByIp.size() > 10_000) recentByIp.clear();
    }

    static String label(String source) {
        return switch (source) {
            case "WEBSITE" -> "your website";
            case "INSTAGRAM" -> "Instagram";
            case "WHATSAPP" -> "WhatsApp";
            case "FACEBOOK" -> "Facebook";
            case "GOOGLE" -> "Google";
            case "REFERRAL" -> "a referral";
            default -> "your enquiry form";
        };
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "This enquiry form is not available.");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
