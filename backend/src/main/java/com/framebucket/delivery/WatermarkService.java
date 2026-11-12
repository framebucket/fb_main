package com.framebucket.delivery;

import com.framebucket.customer.model.CustomerPrincipal;
import com.framebucket.gallery.DataGalleryService;
import com.framebucket.gallery.DataServiceClient;
import com.framebucket.studio.StudioEvents;
import com.framebucket.subscription.EntitlementService;
import com.framebucket.subscription.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Watermarks client-facing previews (gallery and album proofs) with the studio's name or logo.
 * Originals and paid downloads are never touched. Results are cached in memory.
 */
@Service
public class WatermarkService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WatermarkService.class);
    private static final long CACHE_BYTES = 96L * 1024 * 1024;

    /** Either watermarked image bytes, or the untouched media stream (videos, or when no watermark applies). */
    public record Result(byte[] image, DataServiceClient.MediaStream raw) {
    }

    private final JdbcTemplate jdbc;
    private final BrandService brand;
    private final EntitlementService entitlements;
    private final StudioEvents events;
    private final DataGalleryService gallery;
    private final DataServiceClient dataService;
    private final Map<String, byte[]> cache = new LinkedHashMap<>(256, 0.75f, true);
    private long cachedBytes;

    public WatermarkService(JdbcTemplate jdbc, BrandService brand, EntitlementService entitlements, StudioEvents events,
                            DataGalleryService gallery, DataServiceClient dataService) {
        this.jdbc = jdbc;
        this.brand = brand;
        this.entitlements = entitlements;
        this.events = events;
        this.gallery = gallery;
        this.dataService = dataService;
    }

    /** Null when no watermark applies, so the caller keeps its normal (possibly redirecting) path. */
    public Result customerGalleryPreview(UUID eventId, String mediaId, String range, CustomerPrincipal customer) {
        StudioEvents.EventInfo event = events.forCustomer(eventId, customer.getCustomerIds());
        BrandService.Settings settings = brand.settings(event.photographerId());
        if (!applies(settings, event)) return null;
        String key = eventId + "/" + mediaId + "/" + settings.version();
        byte[] cached = get(key);
        if (cached != null) return new Result(cached, null);
        DataServiceClient.MediaStream media = gallery.previewForCustomer(eventId, mediaId, range, customer);
        if (media.contentType() == null || !media.contentType().startsWith("image/") || media.status() != 200) {
            return new Result(null, media);
        }
        byte[] marked = mark(media, settings, event.studioDisplayName(), event.photographerId());
        if (marked == null) return new Result(null, gallery.previewForCustomer(eventId, mediaId, range, customer));
        put(key, marked);
        return new Result(marked, null);
    }

    /** Album proof spreads are watermarked whenever the studio has watermarking on (they are not final). */
    public Result proofSpread(StudioEvents.EventInfo event, String mediaId) {
        BrandService.Settings settings = brand.settings(event.photographerId());
        boolean on = !"OFF".equals(settings.watermarkMode()) && entitlements.has(event.photographerId(), Feature.WATERMARK);
        DataServiceClient.MediaStream media = dataService.openPreview(event.id().toString(), mediaId, event.customerId().toString(), null);
        if (!on || media.contentType() == null || !media.contentType().startsWith("image/")) return new Result(null, media);
        String key = "proof/" + event.id() + "/" + mediaId + "/" + settings.version();
        byte[] cached = get(key);
        if (cached != null) {
            try (InputStream ignored = media.body()) {
                // already cached; close the stream we opened
            } catch (IOException ignored) {
                // nothing to do
            }
            return new Result(cached, null);
        }
        byte[] marked = mark(media, settings, event.studioDisplayName(), event.photographerId());
        if (marked == null) {
            return new Result(null, dataService.openPreview(event.id().toString(), mediaId, event.customerId().toString(), null));
        }
        put(key, marked);
        return new Result(marked, null);
    }

    boolean applies(BrandService.Settings settings, StudioEvents.EventInfo event) {
        if ("OFF".equals(settings.watermarkMode())) return false;
        if (!entitlements.has(event.photographerId(), Feature.WATERMARK)) return false;
        if ("ALWAYS".equals(settings.watermarkMode())) return true;
        // UNTIL_PAID: drop the watermark once downloads are unlocked or the agreed price is fully paid.
        Boolean unlocked = jdbc.query("SELECT unlocked_at IS NOT NULL FROM fb_event_download_policy WHERE event_id = ?",
                (rs, n) -> rs.getBoolean(1), event.id()).stream().findFirst().orElse(false);
        if (Boolean.TRUE.equals(unlocked)) return false;
        BigDecimal total = event.totalAmount();
        if (total.signum() <= 0) return true;
        BigDecimal paid = jdbc.queryForObject(
                "SELECT COALESCE(sum(amount), 0) FROM fb_event_payment WHERE event_id = ? AND purpose <> 'DOWNLOAD'",
                BigDecimal.class, event.id());
        return paid == null || paid.compareTo(total) < 0;
    }

    private byte[] mark(DataServiceClient.MediaStream media, BrandService.Settings settings, String studioName, UUID photographerId) {
        try (InputStream input = media.body()) {
            BufferedImage source = ImageIO.read(input);
            if (source == null) return null;
            BufferedImage logo = null;
            if (settings.watermarkUseLogo()) {
                logo = brand.logo(photographerId).map(l -> {
                    try {
                        return ImageIO.read(new ByteArrayInputStream(l.bytes()));
                    } catch (IOException exception) {
                        return null;
                    }
                }).orElse(null);
            }
            String text = settings.watermarkText() == null || settings.watermarkText().isBlank() ? "© " + studioName : settings.watermarkText();
            return encode(apply(source, text, logo, settings.watermarkPosition(), settings.watermarkOpacity() / 100f));
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not watermark a preview; serving it unchanged.", exception);
            return null;
        }
    }

    static BufferedImage apply(BufferedImage source, String text, BufferedImage logo, String position, float opacity) {
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, null);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0.05f, Math.min(0.8f, opacity))));
        int w = out.getWidth();
        int h = out.getHeight();
        int shortSide = Math.min(w, h);
        if (logo != null) {
            int logoW = (int) (shortSide * ("BOTTOM_RIGHT".equals(position) ? 0.18 : 0.35));
            int logoH = Math.max(1, logoW * logo.getHeight() / Math.max(1, logo.getWidth()));
            if ("TILED".equals(position)) {
                int stepX = logoW * 2;
                int stepY = logoH * 3;
                for (int y = -logoH; y < h + logoH; y += stepY) {
                    for (int x = ((y / stepY) % 2) * logoW - logoW; x < w; x += stepX) g.drawImage(logo, x, y, logoW, logoH, null);
                }
            } else if ("BOTTOM_RIGHT".equals(position)) {
                int margin = shortSide / 30;
                g.drawImage(logo, w - logoW - margin, h - logoH - margin, logoW, logoH, null);
            } else {
                g.drawImage(logo, (w - logoW) / 2, (h - logoH) / 2, logoW, logoH, null);
            }
        } else {
            int size = Math.max(12, (int) (shortSide * ("BOTTOM_RIGHT".equals(position) ? 0.035 : "TILED".equals(position) ? 0.05 : 0.08)));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
            FontMetrics metrics = g.getFontMetrics();
            int textW = metrics.stringWidth(text);
            if ("TILED".equals(position)) {
                AffineTransform original = g.getTransform();
                g.rotate(Math.toRadians(-30), w / 2.0, h / 2.0);
                int stepX = textW + size * 3;
                int stepY = size * 5;
                int span = (int) Math.hypot(w, h);
                for (int y = -span / 2; y < h + span / 2; y += stepY) {
                    for (int x = -span / 2 + ((y / stepY) % 2) * stepX / 2; x < w + span / 2; x += stepX) shadowed(g, text, x, y, size);
                }
                g.setTransform(original);
            } else if ("BOTTOM_RIGHT".equals(position)) {
                int margin = shortSide / 30;
                shadowed(g, text, w - textW - margin, h - margin, size);
            } else {
                shadowed(g, text, (w - textW) / 2, h / 2 + metrics.getAscent() / 2, size);
            }
        }
        g.dispose();
        return out;
    }

    private static void shadowed(Graphics2D g, String text, int x, int y, int size) {
        int offset = Math.max(1, size / 18);
        g.setColor(new Color(0, 0, 0, 140));
        g.drawString(text, x + offset, y + offset);
        g.setColor(Color.WHITE);
        g.drawString(text, x, y);
    }

    static byte[] encode(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(0.86f);
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private synchronized byte[] get(String key) {
        return cache.get(key);
    }

    private synchronized void put(String key, byte[] value) {
        if (value.length > CACHE_BYTES / 8) return;
        byte[] previous = cache.put(key, value);
        if (previous != null) cachedBytes -= previous.length;
        cachedBytes += value.length;
        var iterator = cache.entrySet().iterator();
        while (cachedBytes > CACHE_BYTES && iterator.hasNext()) {
            cachedBytes -= iterator.next().getValue().length;
            iterator.remove();
        }
    }
}
