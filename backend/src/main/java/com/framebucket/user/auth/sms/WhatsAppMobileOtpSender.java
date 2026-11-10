package com.framebucket.user.auth.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framebucket.common.phone.IndianMobileNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Sends mobile verification codes with a WhatsApp Cloud API authentication template. */
@Component
@ConditionalOnProperty(name = "app.sms.mode", havingValue = "whatsapp")
public class WhatsAppMobileOtpSender implements MobileOtpSender {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String messagesUrl;
    private final String accessToken;
    private final String templateName;
    private final String languageCode;

    public WhatsAppMobileOtpSender(
            ObjectMapper objectMapper,
            @Value("${app.whatsapp.messages-url:}") String messagesUrl,
            @Value("${app.whatsapp.access-token:}") String accessToken,
            @Value("${app.whatsapp.mobile-verification-template:framebucket_mobile_verification}") String templateName,
            @Value("${app.whatsapp.language:en}") String languageCode
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.messagesUrl = messagesUrl;
        this.accessToken = accessToken;
        this.templateName = templateName;
        this.languageCode = languageCode;
    }

    @Override
    public void sendVerificationCode(String mobileNumber, String code) {
        if (messagesUrl.isBlank() || accessToken.isBlank()) {
            throw new IllegalStateException(
                    "Configure WHATSAPP_MESSAGES_URL and WHATSAPP_ACCESS_TOKEN to send WhatsApp verification codes."
            );
        }

        String recipientNumber = "91" + IndianMobileNumber.normalize(mobileNumber);
        List<Map<String, String>> otpParameter = List.of(Map.of("type", "text", "text", code));
        List<Map<String, Object>> components = List.of(
                Map.of("type", "body", "parameters", otpParameter),
                Map.of(
                        "type", "button",
                        "sub_type", "url",
                        "index", "0",
                        "parameters", otpParameter
                )
        );
        Map<String, Object> template = Map.of(
                "name", templateName,
                "language", Map.of("code", languageCode),
                "components", components
        );
        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", recipientNumber,
                "type", "template",
                "template", template
        );

        try {
            String requestBody = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(messagesUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "WhatsApp OTP delivery returned HTTP " + response.statusCode()
                                + metaErrorDetail(response.body())
                );
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not send the WhatsApp verification code.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("WhatsApp verification-code delivery was interrupted.", exception);
        }
    }

    private String metaErrorDetail(String responseBody) {
        try {
            JsonNode error = objectMapper.readTree(responseBody).path("error");
            String message = error.path("message").asText();
            String type = error.path("type").asText();
            String code = error.path("code").asText();
            String subcode = error.path("error_subcode").asText();
            String traceId = error.path("fbtrace_id").asText();

            StringBuilder detail = new StringBuilder();
            if (!message.isBlank()) {
                detail.append(message);
            }
            if (!type.isBlank() || !code.isBlank() || !subcode.isBlank()) {
                if (detail.length() > 0) {
                    detail.append(" (");
                } else {
                    detail.append("Meta error (");
                }
                if (!type.isBlank()) {
                    detail.append(type);
                }
                if (!code.isBlank()) {
                    if (!type.isBlank()) detail.append(' ');
                    detail.append("code ").append(code);
                }
                if (!subcode.isBlank()) {
                    detail.append(", subcode ").append(subcode);
                }
                detail.append(')');
            }
            if (!traceId.isBlank()) {
                if (detail.length() > 0) detail.append("; ");
                detail.append("fbtrace_id ").append(traceId);
            }
            return detail.length() == 0 ? "." : ": " + detail;
        } catch (IOException ignored) {
            // Keep the HTTP status as the diagnostic if Meta returns a non-JSON response.
        }
        return ".";
    }
}
