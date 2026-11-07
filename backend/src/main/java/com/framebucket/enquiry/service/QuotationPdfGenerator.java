package com.framebucket.enquiry.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class QuotationPdfGenerator {
    public record EventSummary(String name, List<String> services) {
    }

    private record QuoteRow(List<String> descriptionLines, String quantity, boolean eventHeading) {
        private int height() {
            return (eventHeading ? 26 : 25) + Math.max(0, descriptionLines.size() - 1) * 13;
        }
    }

    private static final float PAGE_WIDTH = 595;
    private static final float PAGE_HEIGHT = 842;
    private static final int FIRST_PAGE_TABLE_TOP = 492;
    private static final int CONTINUATION_TABLE_TOP = 646;
    private static final int SUMMARY_SAFE_BOTTOM = 190;
    private static final int PAGE_SAFE_BOTTOM = 70;
    private static final int REGULAR_FONT_ID = 3;
    private static final int BOLD_FONT_ID = 4;
    private static final int SERIF_FONT_ID = 5;
    private static final int SERIF_BOLD_FONT_ID = 6;
    private static final int FIRST_PAGE_ID = 7;
    private static final DateTimeFormatter CREATED_AT_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm", Locale.ROOT);
    private static final DateTimeFormatter EVENT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    public byte[] generate(
            String studioName,
            String quoteNumber,
            String customerName,
            String customerMobile,
            String customerEmail,
            String occasion,
            String venue,
            String eventDate,
            String preferredTime,
            List<EventSummary> events,
            int albumSheets,
            BigDecimal finalPrice
    ) {
        String safeStudioName = safePdfText(studioName);
        String safeQuoteNumber = safePdfText(quoteNumber);
        List<List<QuoteRow>> pageRows = paginateRows(buildRows(events, albumSheets));
        List<byte[]> pageStreams = new ArrayList<>(pageRows.size());
        for (int pageIndex = 0; pageIndex < pageRows.size(); pageIndex++) {
            pageStreams.add(pageContent(
                    safeStudioName,
                    safeQuoteNumber,
                    customerName,
                    customerMobile,
                    customerEmail,
                    occasion,
                    venue,
                    eventDate,
                    preferredTime,
                    albumSheets,
                    finalPrice,
                    pageRows.get(pageIndex),
                    pageIndex,
                    pageRows.size()
            ));
        }
        return writePdf(pageStreams);
    }

    private static List<QuoteRow> buildRows(List<EventSummary> events, int albumSheets) {
        List<QuoteRow> rows = new ArrayList<>();
        for (int index = 0; index < events.size(); index++) {
            EventSummary event = events.get(index);
            rows.add(new QuoteRow(
                    wrap(String.format(Locale.ROOT, "Event %d: %s", index + 1, event.name()), 68),
                    "",
                    true
            ));
            if (event.services().isEmpty()) {
                rows.add(new QuoteRow(wrap("No photo services selected", 68), "", false));
            } else {
                for (String service : event.services()) {
                    rows.add(new QuoteRow(wrap(service, 68), "1 event", false));
                }
            }
        }
        if (albumSheets > 0) {
            rows.add(new QuoteRow(wrap("Premium photo album", 68), albumSheets + " sheets", false));
        }
        return rows;
    }

    private static List<List<QuoteRow>> paginateRows(List<QuoteRow> rows) {
        List<List<QuoteRow>> pages = new ArrayList<>();
        List<QuoteRow> currentPage = new ArrayList<>();
        int pageIndex = 0;
        int usedHeight = 0;

        for (int rowIndex = 0; rowIndex < rows.size();) {
            int blockEnd = rowIndex + 1;
            if (rows.get(rowIndex).eventHeading()) {
                while (blockEnd < rows.size() && !rows.get(blockEnd).eventHeading()) {
                    blockEnd++;
                }
            }
            int blockHeight = rows.subList(rowIndex, blockEnd).stream().mapToInt(QuoteRow::height).sum();
            int nextUsedHeight = usedHeight + blockHeight;
            boolean finalBlock = blockEnd == rows.size();
            int pageTop = pageIndex == 0 ? FIRST_PAGE_TABLE_TOP : CONTINUATION_TABLE_TOP;
            int safeBottom = finalBlock ? SUMMARY_SAFE_BOTTOM : PAGE_SAFE_BOTTOM;

            if (pageTop - nextUsedHeight < safeBottom && !currentPage.isEmpty()) {
                pages.add(currentPage);
                currentPage = new ArrayList<>();
                pageIndex++;
                usedHeight = 0;
                nextUsedHeight = blockHeight;
            }

            currentPage.addAll(rows.subList(rowIndex, blockEnd));
            usedHeight = nextUsedHeight;
            rowIndex = blockEnd;
        }

        if (!currentPage.isEmpty() || pages.isEmpty()) {
            pages.add(currentPage);
        }
        return pages;
    }

    private static byte[] pageContent(
            String studioName,
            String quoteNumber,
            String customerName,
            String customerMobile,
            String customerEmail,
            String occasion,
            String venue,
            String eventDate,
            String preferredTime,
            int albumSheets,
            BigDecimal finalPrice,
            List<QuoteRow> rows,
            int pageIndex,
            int pageCount
    ) {
        StringBuilder content = new StringBuilder();
        drawPageHeader(content, studioName, quoteNumber, pageIndex == 0);
        int rowTop;
        if (pageIndex == 0) {
            drawFirstPageDetails(content, customerName, customerMobile, customerEmail, occasion, venue, eventDate, preferredTime);
            drawTableHeader(content, 516);
            rowTop = 496;
        } else {
            drawTextFit(content, 50, 699, SERIF_FONT_ID, 23, 18, NAVY, "Photography quotation - continued", 495);
            drawTableHeader(content, 674);
            rowTop = 654;
        }

        int y = rowTop;
        for (QuoteRow row : rows) {
            drawRow(content, row, y);
            y -= row.height();
        }
        if (pageIndex == pageCount - 1) {
            drawSummary(content, y, studioName, finalPrice, albumSheets);
        }
        drawFooter(content, pageIndex + 1, pageCount);
        return ascii(content.toString());
    }

    private static void drawPageHeader(StringBuilder content, String studioName, String quoteNumber, boolean firstPage) {
        drawText(content, 50, 816, REGULAR_FONT_ID, 8, MUTED, CREATED_AT_FORMAT.format(LocalDateTime.now()));
        String wordmark = safePdfText(studioName).toUpperCase(Locale.ROOT);
        drawTextFit(content, 50, 779, BOLD_FONT_ID, 14, 9, NAVY, wordmark, 310);
        drawText(content, 50, 761, REGULAR_FONT_ID, 9, MUTED, "Framing your dreams");
        drawRightText(content, 545, 780, REGULAR_FONT_ID, 9, MUTED, "ESTIMATE");
        drawRightText(content, 545, 760, BOLD_FONT_ID, 12, NAVY, quoteNumber);
        drawRule(content, 50, 739, 545, LIGHT_RULE, 0.7f);
        if (firstPage) {
            drawTextFit(content, 50, 691, SERIF_FONT_ID, 34, 26, NAVY, "Photography quotation", 495);
        }
    }

    private static void drawFirstPageDetails(
            StringBuilder content,
            String customerName,
            String customerMobile,
            String customerEmail,
            String occasion,
            String venue,
            String eventDate,
            String preferredTime
    ) {
        drawText(content, 50, 648, BOLD_FONT_ID, 8, MUTED, "PREPARED FOR");
        drawTextFit(content, 50, 625, BOLD_FONT_ID, 16, 12, NAVY, customerName, 495);
        drawTextFit(content, 50, 605, REGULAR_FONT_ID, 10, 8, BODY, customerMobile + "  |  " + customerEmail, 495);
        drawTextFit(content, 50, 573, REGULAR_FONT_ID, 10, 8, MUTED,
                "Occasion: " + blankFallback(occasion, "To be confirmed")
                        + "    Venue: " + blankFallback(venue, "To be confirmed"), 495);
        drawTextFit(content, 50, 554, REGULAR_FONT_ID, 10, 8, MUTED,
                "Event date: " + formatDate(eventDate) + "    Preferred time: " + blankFallback(preferredTime, "To be confirmed"), 495);
    }

    private static void drawTableHeader(StringBuilder content, int y) {
        drawText(content, 58, y, BOLD_FONT_ID, 9, BODY, "Description");
        drawRightText(content, 536, y, BOLD_FONT_ID, 9, BODY, "Quantity");
        drawRule(content, 50, y - 11, 545, LIGHT_RULE, 0.9f);
    }

    private static void drawRow(StringBuilder content, QuoteRow row, int top) {
        int height = row.height();
        float baseline = top - 19;
        if (row.eventHeading()) {
            fillRectangle(content, 50, top - height + 1, 495, height - 1, PALE);
            for (String line : row.descriptionLines()) {
                drawTextFit(content, 58, (int) baseline, BOLD_FONT_ID, 10, 9, NAVY, line, 475);
                baseline -= 13;
            }
        } else {
            int lineIndex = 0;
            for (String line : row.descriptionLines()) {
                drawTextFit(content, 63, (int) baseline, REGULAR_FONT_ID, 10, 9, BODY, line, 365);
                if (lineIndex == 0 && !row.quantity().isBlank()) {
                    drawRightText(content, 536, (int) baseline, REGULAR_FONT_ID, 9, MUTED, row.quantity());
                }
                baseline -= 13;
                lineIndex++;
            }
        }
        drawRule(content, 50, top - height, 545, LIGHT_RULE, 0.55f);
    }

    private static void drawSummary(StringBuilder content, int tableBottom, String studioName, BigDecimal finalPrice, int albumSheets) {
        drawRule(content, 340, tableBottom - 18, 545, LIGHT_RULE, 0.8f);
        String amount = "INR " + formatAmount(finalPrice);
        String totalLabel = "Estimated total";
        float amountX = 545 - estimateWidth(amount, 19, true);
        float labelX = Math.max(50, amountX - 16 - estimateWidth(totalLabel, 15, false));
        drawText(content, labelX, tableBottom - 47, REGULAR_FONT_ID, 15, BODY, totalLabel);
        drawText(content, amountX, tableBottom - 47, BOLD_FONT_ID, 19, NAVY, amount);

        int noteY = tableBottom - 76;
        List<String> notes = new ArrayList<>(wrap(
                "This estimate is not a booking confirmation. Date availability, deliverables, taxes, travel, overtime and final terms must be confirmed by "
                        + blankFallback(studioName, "the studio") + ".",
                96
        ));
        if (albumSheets > 0) {
            notes.addAll(wrap("Album layout varies by final design; each sheet fits an average of 4-6 photos.", 96));
        }
        for (String note : notes) {
            drawText(content, 50, noteY, REGULAR_FONT_ID, 8, MUTED, note);
            noteY -= 12;
        }

        int thanksY = noteY - 19;
        drawTextFit(content, 50, thanksY, REGULAR_FONT_ID, 11, 9, BODY,
                "Thank you for considering " + blankFallback(studioName, "our studio") + ".", 495);
        drawText(content, 50, thanksY - 19, BOLD_FONT_ID, 10, NAVY, "Framing your dreams.");
    }

    private static void drawFooter(StringBuilder content, int pageNumber, int pageCount) {
        drawRule(content, 50, 43, 545, LIGHT_RULE, 0.6f);
        drawText(content, 50, 27, REGULAR_FONT_ID, 8, MUTED, "PHOTOGRAPHY QUOTATION");
        drawRightText(content, 545, 27, REGULAR_FONT_ID, 8, MUTED, pageNumber + " / " + pageCount);
    }

    private static byte[] writePdf(List<byte[]> pageStreams) {
        int pageCount = pageStreams.size();
        int objectCount = 6 + pageCount * 2;
        List<byte[]> objects = new ArrayList<>(objectCount + 1);
        objects.add(new byte[0]);
        objects.add(ascii("<< /Type /Catalog /Pages 2 0 R >>"));

        StringBuilder kids = new StringBuilder();
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int pageObjectId = FIRST_PAGE_ID + pageIndex * 2;
            kids.append(pageObjectId).append(" 0 R ");
        }
        objects.add(ascii("<< /Type /Pages /Kids [" + kids + "] /Count " + pageCount + " >>"));
        objects.add(ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"));
        objects.add(ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>"));
        objects.add(ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Times-Roman >>"));
        objects.add(ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Times-Bold >>"));

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int pageObjectId = FIRST_PAGE_ID + pageIndex * 2;
            int streamObjectId = pageObjectId + 1;
            byte[] stream = pageStreams.get(pageIndex);
            objects.add(ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + (int) PAGE_WIDTH + " " + (int) PAGE_HEIGHT + "] "
                    + "/Resources << /Font << /F1 " + REGULAR_FONT_ID + " 0 R /F2 " + BOLD_FONT_ID + " 0 R "
                    + "/F3 " + SERIF_FONT_ID + " 0 R /F4 " + SERIF_BOLD_FONT_ID + " 0 R >> >> "
                    + "/Contents " + streamObjectId + " 0 R >>"));
            objects.add(concat(
                    ascii("<< /Length " + stream.length + " >>\nstream\n"),
                    stream,
                    ascii("\nendstream")
            ));
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        write(output, ascii("%PDF-1.4\n"));
        int[] offsets = new int[objectCount + 1];
        for (int objectId = 1; objectId <= objectCount; objectId++) {
            offsets[objectId] = output.size();
            write(output, ascii(objectId + " 0 obj\n"));
            write(output, objects.get(objectId));
            write(output, ascii("\nendobj\n"));
        }
        int xrefOffset = output.size();
        write(output, ascii("xref\n0 " + (objectCount + 1) + "\n"));
        write(output, ascii("0000000000 65535 f \n"));
        for (int objectId = 1; objectId <= objectCount; objectId++) {
            write(output, ascii(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[objectId])));
        }
        write(output, ascii("trailer\n<< /Size " + (objectCount + 1) + " /Root 1 0 R >>\nstartxref\n" + xrefOffset + "\n%%EOF"));
        return output.toByteArray();
    }

    private static void drawText(StringBuilder content, float x, float y, int fontId, int size, String color, String value) {
        String text = escapePdf(safePdfText(value));
        int fontResource = switch (fontId) {
            case REGULAR_FONT_ID -> 1;
            case BOLD_FONT_ID -> 2;
            case SERIF_FONT_ID -> 3;
            case SERIF_BOLD_FONT_ID -> 4;
            default -> throw new IllegalArgumentException("Unknown PDF font resource.");
        };
        content.append("BT /F").append(fontResource).append(' ').append(size).append(" Tf ").append(color)
                .append(" rg 1 0 0 1 ").append(number(x)).append(' ').append(number(y)).append(" Tm (")
                .append(text).append(") Tj ET\n");
    }

    private static void drawRightText(StringBuilder content, float rightX, float y, int fontId, int size, String color, String value) {
        float width = estimateWidth(safePdfText(value), size, fontId == BOLD_FONT_ID || fontId == SERIF_BOLD_FONT_ID);
        drawText(content, Math.max(50, rightX - width), y, fontId, size, color, value);
    }

    private static void drawTextFit(
            StringBuilder content,
            float x,
            float y,
            int fontId,
            int preferredSize,
            int minimumSize,
            String color,
            String value,
            float maximumWidth
    ) {
        int size = preferredSize;
        String safeText = safePdfText(value);
        while (size > minimumSize && estimateWidth(safeText, size, fontId == BOLD_FONT_ID || fontId == SERIF_BOLD_FONT_ID) > maximumWidth) {
            size--;
        }
        drawText(content, x, y, fontId, size, color, safeText);
    }

    private static float estimateWidth(String value, int fontSize, boolean bold) {
        float units = 0;
        for (char character : value.toCharArray()) {
            if ("ilI.,:;'!|".indexOf(character) >= 0) units += 0.26f;
            else if ("mwMW@%&".indexOf(character) >= 0) units += 0.82f;
            else if (Character.isWhitespace(character)) units += 0.29f;
            else if (Character.isUpperCase(character)) units += 0.64f;
            else if (Character.isDigit(character)) units += 0.56f;
            else units += bold ? 0.56f : 0.52f;
        }
        return units * fontSize;
    }

    private static void drawRule(StringBuilder content, float x1, float y, float x2, String color, float thickness) {
        content.append("q ").append(color).append(" RG ").append(number(thickness)).append(" w ")
                .append(number(x1)).append(' ').append(number(y)).append(" m ")
                .append(number(x2)).append(' ').append(number(y)).append(" l S Q\n");
    }

    private static void fillRectangle(StringBuilder content, float x, float y, float width, float height, String color) {
        content.append("q ").append(color).append(" rg ").append(number(x)).append(' ').append(number(y)).append(' ')
                .append(number(width)).append(' ').append(number(height)).append(" re f Q\n");
    }

    private static List<String> wrap(String value, int maximumCharacters) {
        String normalized = safePdfText(value).trim();
        if (normalized.isEmpty()) return List.of("");
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : normalized.split("\\s+")) {
            if (line.length() > 0 && line.length() + word.length() + 1 > maximumCharacters) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    private static String formatDate(String value) {
        if (value == null || value.isBlank()) return "To be confirmed";
        try {
            return LocalDate.parse(value).format(EVENT_DATE_FORMAT);
        } catch (DateTimeParseException ignored) {
            return safePdfText(value);
        }
    }

    private static String formatAmount(BigDecimal amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
        formatter.setMaximumFractionDigits(2);
        formatter.setMinimumFractionDigits(amount.stripTrailingZeros().scale() > 0 ? 2 : 0);
        return formatter.format(amount);
    }

    private static String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String safePdfText(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value.replace("₹", "INR "), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "");
        return normalized.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private static String escapePdf(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static String number(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static byte[] ascii(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] part : parts) write(output, part);
        return output.toByteArray();
    }

    private static void write(ByteArrayOutputStream output, byte[] value) {
        output.write(value, 0, value.length);
    }

    private static final String NAVY = "0.035 0.095 0.260";
    private static final String BODY = "0.105 0.125 0.180";
    private static final String MUTED = "0.390 0.405 0.445";
    private static final String LIGHT_RULE = "0.835 0.845 0.860";
    private static final String PALE = "0.960 0.966 0.975";
}
