package com.framebucket.studio;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Dependency-free, single-page A4 payment receipt (same approach as QuotationPdfGenerator). */
public final class ReceiptPdfGenerator {
    public record ReceiptData(
            String studioName,
            String businessAddress,
            String gstin,
            String studioEmail,
            String studioMobile,
            String receiptNumber,
            LocalDate paidOn,
            String customerName,
            String customerMobile,
            String eventCode,
            String eventTypeName,
            LocalDate eventDate,
            String purpose,
            String method,
            String reference,
            BigDecimal amount,
            BigDecimal eventTotal,
            BigDecimal paidToDate,
            BigDecimal balance,
            String footer
    ) {
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private ReceiptPdfGenerator() {
    }

    public static byte[] generate(ReceiptData data) {
        StringBuilder c = new StringBuilder();
        // Header band
        c.append("0.11 0.11 0.12 rg 0 742 595 100 re f\n");
        text(c, "F2", 22, 48, 795, data.studioName(), 1);
        text(c, "F1", 10, 48, 775, joinNonBlank(" | ", data.studioMobile(), data.studioEmail()), 1);
        if (notBlank(data.businessAddress())) text(c, "F1", 9, 48, 760, data.businessAddress(), 1);
        text(c, "F2", 16, 405, 795, "PAYMENT RECEIPT", 1);
        if (notBlank(data.gstin())) text(c, "F1", 9, 405, 775, "GSTIN: " + data.gstin(), 1);

        int y = 700;
        text(c, "F2", 11, 48, y, "Receipt No.", 0);
        text(c, "F1", 11, 150, y, data.receiptNumber(), 0);
        text(c, "F2", 11, 330, y, "Date", 0);
        text(c, "F1", 11, 400, y, data.paidOn() == null ? "" : DATE.format(data.paidOn()), 0);
        y -= 22;
        text(c, "F2", 11, 48, y, "Received from", 0);
        text(c, "F1", 11, 150, y, joinNonBlank("  ", data.customerName(), data.customerMobile()), 0);
        y -= 22;
        text(c, "F2", 11, 48, y, "Event", 0);
        text(c, "F1", 11, 150, y, joinNonBlank("  |  ", data.eventCode(), data.eventTypeName(),
                data.eventDate() == null ? null : DATE.format(data.eventDate())), 0);

        // Amount box
        y -= 50;
        c.append("0.96 0.94 0.89 rg 48 ").append(y - 18).append(" 499 58 re f\n");
        text(c, "F1", 11, 64, y + 18, "Amount received", 0);
        text(c, "F2", 22, 64, y - 6, "Rs. " + money(data.amount()), 0);
        text(c, "F1", 11, 330, y + 18, "Towards", 0);
        text(c, "F2", 12, 330, y, purposeLabel(data.purpose()), 0);
        text(c, "F1", 10, 330, y - 12, "Paid via " + methodLabel(data.method())
                + (notBlank(data.reference()) ? "  (Ref: " + data.reference() + ")" : ""), 0);

        // Account summary
        y -= 70;
        text(c, "F2", 12, 48, y, "Account summary for this event", 0);
        y -= 8;
        c.append("0.75 0.75 0.75 RG 0.6 w 48 ").append(y).append(" m 547 ").append(y).append(" l S\n");
        y -= 20;
        row(c, y, "Agreed event price", data.eventTotal());
        y -= 20;
        row(c, y, "Received towards event price (to date)", data.paidToDate());
        y -= 20;
        c.append("48 ").append(y + 12).append(" m 547 ").append(y + 12).append(" l S\n");
        text(c, "F2", 11, 48, y - 2, "Balance due", 0);
        text(c, "F2", 11, 440, y - 2, "Rs. " + money(data.balance()), 0);

        y -= 60;
        text(c, "F1", 9, 48, y, "This is a computer-generated receipt and does not need a signature.", 0);
        if (notBlank(data.footer())) {
            y -= 14;
            for (String line : wrap(data.footer(), 100)) {
                text(c, "F1", 9, 48, y, line, 0);
                y -= 12;
            }
        }
        text(c, "F1", 8, 48, 40, "Generated with FrameBucket", 0);
        return writePdf(c.toString());
    }

    private static void row(StringBuilder c, int y, String label, BigDecimal value) {
        text(c, "F1", 11, 48, y, label, 0);
        text(c, "F1", 11, 440, y, "Rs. " + money(value), 0);
    }

    /** colour 1 = white text (for the header band), 0 = dark text. */
    private static void text(StringBuilder c, String font, int size, int x, int y, String value, int colour) {
        if (value == null || value.isBlank()) return;
        c.append(colour == 1 ? "1 1 1 rg" : "0.12 0.12 0.12 rg")
                .append(" BT /").append(font).append(' ').append(size).append(" Tf ")
                .append(x).append(' ').append(y).append(" Td (").append(escape(value)).append(") Tj ET\n");
    }

    static String money(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(value == null ? BigDecimal.ZERO : value);
    }

    private static String purposeLabel(String purpose) {
        return switch (purpose == null ? "" : purpose) {
            case "ADVANCE" -> "Advance / booking amount";
            case "BALANCE" -> "Balance payment";
            case "DOWNLOAD" -> "Photo downloads";
            default -> "Photography services";
        };
    }

    private static String methodLabel(String method) {
        return switch (method == null ? "" : method) {
            case "BANK_TRANSFER" -> "bank transfer";
            case "UPI" -> "UPI";
            case "CASH" -> "cash";
            case "CARD" -> "card";
            case "CHEQUE" -> "cheque";
            default -> "other";
        };
    }

    private static List<String> wrap(String value, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : value.trim().split("\\s+")) {
            if (line.length() + word.length() + 1 > width && !line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines.size() > 6 ? lines.subList(0, 6) : lines;
    }

    private static String joinNonBlank(String separator, String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) if (notBlank(value)) parts.add(value.trim());
        return String.join(separator, parts);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String escape(String value) {
        String ascii = Normalizer.normalize(value.replace("₹", "Rs."), Normalizer.Form.NFKD)
                .replaceAll("[^\\x20-\\x7E]", "");
        return ascii.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static byte[] writePdf(String content) {
        byte[] stream = content.getBytes(StandardCharsets.US_ASCII);
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(out, "%PDF-1.4\n");
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        offsets.add(out.size());
        write(out, "6 0 obj\n<< /Length " + stream.length + " >>\nstream\n");
        out.writeBytes(stream);
        write(out, "\nendstream\nendobj\n");
        int xref = out.size();
        StringBuilder trailer = new StringBuilder("xref\n0 7\n0000000000 65535 f \n");
        for (int offset : offsets) trailer.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        trailer.append("trailer\n<< /Size 7 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        write(out, trailer.toString());
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
    }
}
