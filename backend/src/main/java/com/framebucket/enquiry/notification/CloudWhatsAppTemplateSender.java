package com.framebucket.enquiry.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.whatsapp.mode", havingValue = "cloud")
public class CloudWhatsAppTemplateSender implements WhatsAppTemplateSender {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String messagesUrl;
    private final String accessToken;

    public CloudWhatsAppTemplateSender(
            ObjectMapper objectMapper,
            @Value("${app.whatsapp.messages-url:}") String messagesUrl,
            @Value("${app.whatsapp.access-token:}") String accessToken
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.messagesUrl = messagesUrl;
        this.accessToken = accessToken;
    }

    @Override
    public void sendTemplate(String recipientNumber, String templateName, String languageCode, List<String> bodyParameters) {
        if (messagesUrl.isBlank() || accessToken.isBlank()) {
            throw new IllegalStateException("Configure WHATSAPP_MESSAGES_URL and WHATSAPP_ACCESS_TOKEN to enable WhatsApp delivery.");
        }
        if (templateName == null || templateName.isBlank() || languageCode == null || languageCode.isBlank()) {
            throw new IllegalStateException("Configure a WhatsApp template name and its exact Meta language code.");
        }
        List<Map<String, String>> parameters = bodyParameters.stream()
                .map(value -> Map.of("type", "text", "text", value))
                .toList();
        Map<String, Object> template = Map.of(
                "name", templateName,
                "language", Map.of("code", languageCode),
                "components", List.of(Map.of("type", "body", "parameters", parameters))
        );
        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", recipientNumber.replaceAll("[^0-9]", ""),
                "type", "template",
                "template", template
        );
        try {
            String requestBody = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(messagesUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "WhatsApp delivery returned HTTP " + response.statusCode()
                                + metaErrorDetail(response.body())
                );
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not send the WhatsApp template message.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("WhatsApp message delivery was interrupted.", exception);
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
