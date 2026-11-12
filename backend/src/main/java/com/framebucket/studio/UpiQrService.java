package com.framebucket.studio;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Builds standard UPI "pay" QR codes (NPCI deep-link format) that any UPI app can scan.
 * Money goes straight to the photographer's UPI ID; FrameBucket does not handle the funds,
 * so payments are confirmed by the photographer when they record them.
 */
@Service
public class UpiQrService {
    private static final int IMAGE_SIZE = 720;

    public String upiUri(String upiId, String payeeName, BigDecimal amount, String note) {
        if (upiId == null || upiId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The photographer has not added a UPI ID yet. Add it in Studio settings.");
        }
        // The VPA keeps its literal "@" (NPCI linking spec); other characters are percent-encoded.
        StringBuilder uri = new StringBuilder("upi://pay?pa=").append(encode(upiId.trim()).replace("%40", "@"));
        if (payeeName != null && !payeeName.isBlank()) {
            uri.append("&pn=").append(encode(payeeName.trim()));
        }
        if (amount != null && amount.signum() > 0) {
            uri.append("&am=").append(amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
        uri.append("&cu=INR");
        if (note != null && !note.isBlank()) {
            String trimmed = note.trim();
            uri.append("&tn=").append(encode(trimmed.length() > 60 ? trimmed.substring(0, 60) : trimmed));
        }
        return uri.toString();
    }

    public byte[] qrPng(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, IMAGE_SIZE, IMAGE_SIZE, Map.of(
                    EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 2));
            BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < IMAGE_SIZE; y++) {
                for (int x = 0; x < IMAGE_SIZE; x++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0xFF111111 : 0xFFFFFFFF);
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (WriterException | IOException exception) {
            throw new IllegalStateException("Could not generate the payment QR code.", exception);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
