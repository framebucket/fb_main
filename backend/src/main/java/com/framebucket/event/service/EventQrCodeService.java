package com.framebucket.event.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class EventQrCodeService {
    private static final int IMAGE_SIZE = 900;
    private final String frontendBaseUrl;

    public EventQrCodeService(@Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    public byte[] guestEntryQrCode(String guestAccessToken) {
        if (frontendBaseUrl.isBlank()) {
            throw new IllegalStateException("Configure APP_FRONTEND_BASE_URL before downloading event QR codes.");
        }
        String guestUrl = frontendBaseUrl + "/event-checkin/" + guestAccessToken;
        try {
            BitMatrix matrix = new QRCodeWriter().encode(
                    guestUrl,
                    BarcodeFormat.QR_CODE,
                    IMAGE_SIZE,
                    IMAGE_SIZE,
                    Map.of(
                            EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
                            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H,
                            EncodeHintType.MARGIN, 3
                    )
            );
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
            throw new IllegalStateException("Could not generate the event QR code.", exception);
        }
    }
}
