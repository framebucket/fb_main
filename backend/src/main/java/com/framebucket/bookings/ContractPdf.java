package com.framebucket.bookings;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;

/** Dependency-free multi-page PDF for a booking agreement, with the client's drawn signature embedded. */
final class ContractPdf {
    record Data(
            String studioName,
            String studioContact,
            String reference,
            String clientName,
            String clientMobile,
            String clientEmail,
            String occasion,
            String venue,
            LocalDate eventDate,
            String packageSummary,
            BigDecimal totalAmount,
            BigDecimal advanceAmount,
            String terms,
            String status,
            String signerName,
            Instant signedAt,
            String signerIp,
            String contentSha256,
            byte[] signaturePng
    ) {
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a 'IST'", Locale.ENGLISH)
            .withZone(ZoneId.of("Asia/Kolkata"));
    private static final int TOP = 790;
    private static final int BOTTOM = 60;

    private final List<StringBuilder> pages = new ArrayList<>();
    private StringBuilder page;
    private int y;
    private Integer imagePage;
    private int imageX;
    private int imageY;
    private int imageW;
    private int imageH;
    private byte[] imageData;
    private int imagePixelW;
    private int imagePixelH;

    private ContractPdf() {
        newPage();
    }

    static byte[] generate(Data data) {
        ContractPdf pdf = new ContractPdf();
        pdf.render(data);
        return pdf.build();
    }

    private void render(Data d) {
        page.append("0.11 0.11 0.12 rg 0 752 595 90 re f\n");
        text("F2", 20, 48, 800, d.studioName(), true);
        text("F1", 9, 48, 783, d.studioContact(), true);
        text("F2", 14, 380, 800, "BOOKING AGREEMENT", true);
        text("F1", 9, 380, 783, "Ref: " + d.reference(), true);
        y = 725;
        if ("SENT".equals(d.status())) {
            line("F2", 10, "DRAFT - not yet signed by the client.");
            y -= 6;
        }
        section("Client");
        line("F1", 10, d.clientName() + "   |   " + nonNull(d.clientMobile()) + "   |   " + nonNull(d.clientEmail()));
        section("Event");
        line("F1", 10, nonNull(d.occasion()) + (d.eventDate() == null ? "" : "   |   " + DATE.format(d.eventDate()))
                + (d.venue() == null || d.venue().isBlank() ? "" : "   |   " + d.venue()));
        if (d.packageSummary() != null && !d.packageSummary().isBlank()) {
            section("Package");
            paragraph("F1", 10, d.packageSummary(), 95);
        }
        section("Payment");
        line("F1", 10, "Agreed total: Rs. " + money(d.totalAmount()));
        line("F1", 10, "Advance to confirm the booking: Rs. " + money(d.advanceAmount()));
        line("F1", 10, "Balance: Rs. " + money(d.totalAmount().subtract(d.advanceAmount()).max(BigDecimal.ZERO)));
        section("Terms and conditions");
        for (String paragraph : d.terms().split("\\r?\\n")) {
            if (paragraph.isBlank()) {
                y -= 6;
                continue;
            }
            paragraph("F1", 9, paragraph.trim(), 110);
        }
        section("Acceptance");
        if (d.signedAt() != null) {
            ensure(110);
            line("F1", 10, "Signed electronically by " + d.signerName() + " on " + STAMP.format(d.signedAt())
                    + (d.signerIp() == null ? "" : " from " + d.signerIp()) + ".");
            placeSignature(d.signaturePng());
            line("F1", 8, "Document fingerprint (SHA-256): " + nonNull(d.contentSha256()));
        } else {
            line("F1", 10, "Awaiting the client's signature.");
        }
        for (int i = 0; i < pages.size(); i++) {
            pages.get(i).append("0.45 0.45 0.45 rg BT /F1 8 Tf 48 36 Td (")
                    .append(escape("Generated with FrameBucket  |  Page " + (i + 1) + " of " + pages.size())).append(") Tj ET\n");
        }
    }

    private void section(String title) {
        ensure(40);
        y -= 10;
        line("F2", 11, title);
        page.append("0.8 0.8 0.8 RG 0.6 w 48 ").append(y + 8).append(" m 547 ").append(y + 8).append(" l S\n");
        y -= 4;
    }

    private void line(String font, int size, String value) {
        ensure(size + 6);
        text(font, size, 48, y, value, false);
        y -= size + 5;
    }

    private void paragraph(String font, int size, String value, int width) {
        for (String wrapped : wrap(value, width)) line(font, size, wrapped);
    }

    private void ensure(int needed) {
        if (y - needed < BOTTOM) newPage();
    }

    private void newPage() {
        page = new StringBuilder();
        pages.add(page);
        y = TOP;
    }

    private void placeSignature(byte[] png) {
        if (png == null) return;
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(png));
            if (source == null) return;
            BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(source, 0, 0, null);
            g.dispose();
            byte[] raw = new byte[rgb.getWidth() * rgb.getHeight() * 3];
            int i = 0;
            for (int row = 0; row < rgb.getHeight(); row++) {
                for (int col = 0; col < rgb.getWidth(); col++) {
                    int p = rgb.getRGB(col, row);
                    raw[i++] = (byte) (p >> 16);
                    raw[i++] = (byte) (p >> 8);
                    raw[i++] = (byte) p;
                }
            }
            imageData = deflate(raw);
            imagePixelW = rgb.getWidth();
            imagePixelH = rgb.getHeight();
            imageH = 60;
            imageW = Math.max(1, Math.min(260, Math.round(imageH * (float) imagePixelW / imagePixelH)));
            ensure(imageH + 10);
            imagePage = pages.size() - 1;
            imageX = 48;
            imageY = y - imageH;
            page.append("q ").append(imageW).append(" 0 0 ").append(imageH).append(' ').append(imageX).append(' ')
                    .append(imageY).append(" cm /Im1 Do Q\n");
            page.append("0.6 0.6 0.6 RG 0.5 w 48 ").append(imageY - 2).append(" m 310 ").append(imageY - 2).append(" l S\n");
            y = imageY - 16;
        } catch (IOException exception) {
            // Signature image unreadable: the text record above still identifies the signer.
        }
    }

    private void text(String font, int size, int x, int yPos, String value, boolean white) {
        if (value == null || value.isBlank()) return;
        page.append(white ? "1 1 1 rg" : "0.12 0.12 0.12 rg").append(" BT /").append(font).append(' ').append(size)
                .append(" Tf ").append(x).append(' ').append(yPos).append(" Td (").append(escape(value)).append(") Tj ET\n");
    }

    private byte[] build() {
        // Objects: 1 catalog, 2 pages, 3 F1, 4 F2, 5 image (optional), then page/content pairs.
        List<byte[]> objects = new ArrayList<>();
        int pageCount = pages.size();
        int firstPageObject = 6;
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageCount; i++) kids.append(firstPageObject + i * 2).append(" 0 R ");
        objects.add(ascii("<< /Type /Catalog /Pages 2 0 R >>"));
        objects.add(ascii("<< /Type /Pages /Kids [" + kids + "] /Count " + pageCount + " >>"));
        objects.add(ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
        objects.add(ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"));
        if (imageData != null) {
            ByteArrayOutputStream image = new ByteArrayOutputStream();
            image.writeBytes(ascii("<< /Type /XObject /Subtype /Image /Width " + imagePixelW + " /Height " + imagePixelH
                    + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length " + imageData.length + " >>\nstream\n"));
            image.writeBytes(imageData);
            image.writeBytes(ascii("\nendstream"));
            objects.add(image.toByteArray());
        } else {
            objects.add(ascii("<< >>"));
        }
        for (int i = 0; i < pageCount; i++) {
            String resources = "<< /Font << /F1 3 0 R /F2 4 0 R >>"
                    + (imagePage != null && imagePage == i ? " /XObject << /Im1 5 0 R >>" : "") + " >>";
            objects.add(ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources " + resources
                    + " /Contents " + (firstPageObject + i * 2 + 1) + " 0 R >>"));
            byte[] content = pages.get(i).toString().getBytes(StandardCharsets.US_ASCII);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            stream.writeBytes(ascii("<< /Length " + content.length + " >>\nstream\n"));
            stream.writeBytes(content);
            stream.writeBytes(ascii("\nendstream"));
            objects.add(stream.toByteArray());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ascii("%PDF-1.4\n"));
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            out.writeBytes(ascii((i + 1) + " 0 obj\n"));
            out.writeBytes(objects.get(i));
            out.writeBytes(ascii("\nendobj\n"));
        }
        int xref = out.size();
        StringBuilder trailer = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) trailer.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        trailer.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        out.writeBytes(ascii(trailer.toString()));
        return out.toByteArray();
    }

    private static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer));
        deflater.end();
        return out.toByteArray();
    }

    static String money(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(value == null ? BigDecimal.ZERO : value);
    }

    private static List<String> wrap(String value, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : value.trim().split("\\s+")) {
            if (current.length() + word.length() + 1 > width && !current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append(' ');
            current.append(word);
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }

    private static String escape(String value) {
        String ascii = Normalizer.normalize(value.replace("₹", "Rs."), Normalizer.Form.NFKD).replaceAll("[^\\x20-\\x7E]", "");
        return ascii.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
