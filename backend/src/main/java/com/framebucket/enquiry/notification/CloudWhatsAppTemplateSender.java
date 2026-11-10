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
public class CloudWhatsAppTemplateSender implements WhatsAppTemplateSender, WhatsAppPhotoSender {
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

    @Override
    public void sendPhotoTemplate(String recipientNumber, String templateName, String languageCode,
                                  byte[] jpegBytes, String filename, List<String> bodyParameters) {
        validateCloudConfiguration(templateName, languageCode);
        if (jpegBytes == null || jpegBytes.length == 0) {
            throw new IllegalArgumentException("The matched photo is empty.");
        }
        if (jpegBytes.length > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("The matched photo exceeds WhatsApp's 5 MB image limit.");
        }
        try {
            String mediaId = uploadImage(jpegBytes, filename);
            Map<String, Object> template = Map.of(
                    "name", templateName,
                    "language", Map.of("code", languageCode),
                    "components", List.of(
                            Map.of("type", "header", "parameters", List.of(Map.of(
                                    "type", "image", "image", Map.of("id", mediaId)
                            ))),
                            Map.of("type", "body", "parameters", bodyParameters.stream()
                                    .map(value -> Map.of("type", "text", "text", value)).toList())
                    )
            );
            Map<String, Object> payload = Map.of(
                    "messaging_product", "whatsapp",
                    "to", recipientNumber.replaceAll("[^0-9]", ""),
                    "type", "template",
                    "template", template
            );
            postJson(messagesUrl, payload);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not upload the matched photo to WhatsApp.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("WhatsApp photo delivery was interrupted.", exception);
        }
    }

    private String uploadImage(byte[] jpegBytes, String filename) throws IOException, InterruptedException {
        String boundary = "----FrameBucket" + java.util.UUID.randomUUID().toString().replace("-", "");
        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        writeMultipartField(body, boundary, "messaging_product", "whatsapp".getBytes(StandardCharsets.UTF_8), "text/plain; charset=UTF-8", null);
        writeMultipartField(body, boundary, "type", "image/jpeg".getBytes(StandardCharsets.UTF_8), "text/plain; charset=UTF-8", null);
        String safeFilename = (filename == null || filename.isBlank() ? "framebucket-photo.jpg" : filename)
                .replaceAll("[^A-Za-z0-9._-]", "_");
        writeMultipartField(body, boundary, "file", jpegBytes, "image/jpeg", safeFilename);
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(mediaUploadUri())
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("WhatsApp media upload returned HTTP " + response.statusCode()
                    + metaErrorDetail(response.body()));
        }
        try {
            String id = objectMapper.readTree(response.body()).path("id").asText();
            if (id.isBlank()) throw new IllegalStateException("WhatsApp did not return a media ID.");
            return id;
        } catch (IOException exception) {
            throw new IllegalStateException("WhatsApp returned an invalid media upload response.", exception);
        }
    }

    private void postJson(String url, Map<String, Object> payload) throws IOException, InterruptedException {
        String requestBody = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("WhatsApp delivery returned HTTP " + response.statusCode()
                    + metaErrorDetail(response.body()));
        }
    }

    private URI mediaUploadUri() {
        String normalized = messagesUrl.replaceAll("/+$", "");
        if (!normalized.matches(".*/messages$")) {
            throw new IllegalStateException("WHATSAPP_MESSAGES_URL must end with /messages so media uploads can use /media.");
        }
        return URI.create(normalized.substring(0, normalized.length() - "/messages".length()) + "/media");
    }

    private static void writeMultipartField(java.io.ByteArrayOutputStream output, String boundary,
                                            String name, byte[] value, String contentType, String filename) throws IOException {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        String disposition = "Content-Disposition: form-data; name=\"" + name + "\""
                + (filename == null ? "" : "; filename=\"" + filename + "\"") + "\r\n";
        output.write(disposition.getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(value);
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void validateCloudConfiguration(String templateName, String languageCode) {
        if (messagesUrl.isBlank() || accessToken.isBlank()) {
            throw new IllegalStateException("Configure WHATSAPP_MESSAGES_URL and WHATSAPP_ACCESS_TOKEN to enable WhatsApp delivery.");
        }
        if (templateName == null || templateName.isBlank() || languageCode == null || languageCode.isBlank()) {
            throw new IllegalStateException("Configure a WhatsApp template name and its exact Meta language code.");
        }
    }

    private String metaErrorDetail(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode error = root.path("error");
            if (error.isMissingNode() || error.isNull()) {
                error = root;
            }
            String message = error.path("message").asText();
            String type = error.path("type").asText();
            String code = error.path("code").asText();
            String subcode = error.path("error_subcode").asText();
            String traceId = error.path("fbtrace_id").asText();
            String userMessage = error.path("error_user_msg").asText();

            StringBuilder detail = new StringBuilder();
            if (!message.isBlank()) {
                detail.append(message);
            } else if (!userMessage.isBlank()) {
                detail.append(userMessage);
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
            return detail.length() == 0 ? ": Meta response " + compactResponse(responseBody) : ": " + detail;
        } catch (IOException ignored) {
            return ": Meta response " + compactResponse(responseBody);
        }
    }

    private String compactResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "(empty body)";
        }
        String compact = responseBody.replaceAll("\\s+", " ").trim();
        int limit = 500;
        return compact.length() <= limit ? compact : compact.substring(0, limit) + "…";
    }
}
