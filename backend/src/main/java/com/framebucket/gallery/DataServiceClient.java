package com.framebucket.gallery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class DataServiceClient {
    private static final String SERVICE_KEY_HEADER = "X-FrameBucket-Service-Key";
    private static final HttpClient STREAM_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String serviceToken;

    public DataServiceClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${app.data-service.base-url:http://127.0.0.1:8001}") String baseUrl,
            @Value("${app.data-service.token:}") String serviceToken
    ) {
        this.restClient = builder.requestFactory(new JdkClientHttpRequestFactory(STREAM_CLIENT)).build();
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.serviceToken = serviceToken;
    }

    public Map<String, Object> createEvent(String eventId, String eventCode, String photographerId, String customerId) {
        return postJson("/api/v1/events", Map.of(
                "event_id", eventId,
                "event_code", eventCode,
                "photographer_id", photographerId,
                "customer_id", customerId
        ));
    }

    public Map<String, Object> listFolders(String eventId, String photographerId, String customerId) {
        return getJson(query("/api/v1/events/" + eventId + "/folders",
                "photographer_id", photographerId, "customer_id", customerId));
    }

    public Map<String, Object> albumSelection(String eventId, String photographerId, String customerId) {
        return getJson(query("/api/v1/events/" + eventId + "/album-selection",
                "photographer_id", photographerId, "customer_id", customerId));
    }

    public Map<String, Object> saveAlbumSelection(String eventId, String photographerId,
                                                   String customerId, List<String> photoIds) {
        URI uri = query("/api/v1/events/" + eventId + "/album-selection",
                "photographer_id", photographerId, "customer_id", customerId);
        return putJson(uri, Map.of("photographer_id", photographerId,
                "customer_id", customerId, "photo_ids", photoIds));
    }

    public Map<String, Object> createFolder(String eventId, String photographerId, String customerId, String name) {
        URI uri = query("/api/v1/events/" + eventId + "/folders",
                "photographer_id", photographerId, "customer_id", customerId);
        return postJson(uri, Map.of("name", name));
    }

    public Map<String, Object> listMedia(String eventId, String customerId, String folderId) {
        return listMedia(eventId, customerId, folderId, false);
    }

    public Map<String, Object> listMedia(String eventId, String customerId, String folderId, boolean includePending) {
        String pending = Boolean.toString(includePending);
        URI uri = folderId == null || folderId.isBlank()
                ? query("/api/v1/events/" + eventId + "/photos", "customer_id", customerId, "include_pending", pending)
                : query("/api/v1/events/" + eventId + "/photos", "customer_id", customerId, "folder_id", folderId,
                        "include_pending", pending);
        return getJson(uri);
    }

    public Map<String, Object> initUpload(String eventId, String photographerId, String customerId,
                                          String folderId, String filename, long sizeBytes, String contentType) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("photographer_id", photographerId);
        body.put("customer_id", customerId);
        body.put("folder_id", folderId == null || folderId.isBlank() ? "root" : folderId);
        body.put("filename", filename);
        body.put("size_bytes", sizeBytes);
        if (contentType != null && !contentType.isBlank()) body.put("content_type", contentType);
        return postJson("/api/v1/events/" + eventId + "/uploads", body);
    }

    /** Streams upload bytes to the data service when direct storage uploads are unavailable. */
    public void uploadContent(String eventId, String mediaId, String photographerId, String customerId,
                              InputStream content, long contentLength) {
        URI uri = query("/api/v1/events/" + eventId + "/uploads/" + mediaId + "/content",
                "photographer_id", photographerId, "customer_id", customerId);
        HttpRequest.BodyPublisher publisher = contentLength >= 0
                ? HttpRequest.BodyPublishers.fromPublisher(HttpRequest.BodyPublishers.ofInputStream(() -> content), contentLength)
                : HttpRequest.BodyPublishers.ofInputStream(() -> content);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(30))
                .header(SERVICE_KEY_HEADER, token())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .PUT(publisher)
                .build();
        try {
            HttpResponse<String> response = STREAM_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw serviceError(response.statusCode(), response.body());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The upload was interrupted.", exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The Python data service is unavailable. Start it on " + baseUrl + ".", exception);
        }
    }

    public Map<String, Object> completeUpload(String eventId, String mediaId, String photographerId, String customerId) {
        return postJson(query("/api/v1/events/" + eventId + "/uploads/" + mediaId + "/complete",
                "photographer_id", photographerId, "customer_id", customerId), Map.of());
    }

    /** A short-lived direct storage URL for processed media, or null when it must be streamed. */
    public String previewUrl(String eventId, String mediaId, String customerId) {
        return urlFrom(getJson(query("/api/v1/events/" + eventId + "/photos/" + mediaId + "/preview-url",
                "customer_id", customerId)));
    }

    public String originalUrl(String eventId, String mediaId, String photographerId, String customerId) {
        return urlFrom(getJson(query("/api/v1/events/" + eventId + "/photos/" + mediaId + "/original-url",
                "photographer_id", photographerId, "customer_id", customerId)));
    }

    public List<Map<String, Object>> claimOutbox(int limit, int leaseSeconds) {
        Object items = postJson("/api/v1/outbox/claim", Map.of("limit", limit, "lease_seconds", leaseSeconds)).get("items");
        if (!(items instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> copy = new java.util.LinkedHashMap<>();
                map.forEach((key, value) -> copy.put(String.valueOf(key), value));
                result.add(copy);
            }
        }
        return result;
    }

    public void ackOutbox(List<Long> ids) {
        if (ids.isEmpty()) return;
        postJson("/api/v1/outbox/ack", Map.of("ids", ids));
    }

    private static String urlFrom(Map<String, Object> response) {
        return response.get("url") instanceof String url && !url.isBlank() ? url : null;
    }

    public Map<String, Object> registerGuestFace(String eventId, String guestId, String customerId,
                                                  byte[] selfieBytes, String filename, MediaType mediaType) {
        URI uri = query("/api/v1/events/" + eventId + "/guests/" + guestId + "/face",
                "customer_id", customerId);
        return exchange(() -> {
            MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
            ByteArrayResource resource = new ByteArrayResource(selfieBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            HttpHeaders partHeaders = new HttpHeaders();
            partHeaders.setContentDispositionFormData("file", filename);
            partHeaders.setContentType(mediaType);
            multipart.add("file", new HttpEntity<>(resource, partHeaders));
            return restClient.post().uri(uri).header(SERVICE_KEY_HEADER, token())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart).retrieve().body(JSON_MAP);
        });
    }

    // ---- studio editing look (learned from reference photos) and logo stamping ----

    public Map<String, Object> studioStyle(UUID photographerId) {
        return getJson(uri("/api/v1/studios/" + photographerId + "/style"));
    }

    public Map<String, Object> saveStyleReferences(UUID photographerId, List<NamedBytes> references) {
        return exchange(() -> {
            MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
            for (NamedBytes reference : references) {
                multipart.add("files", filePart("files", reference));
            }
            return restClient.post().uri(uri("/api/v1/studios/" + photographerId + "/style/references"))
                    .header(SERVICE_KEY_HEADER, token())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart).retrieve().body(JSON_MAP);
        });
    }

    public Map<String, Object> updateStyle(UUID photographerId, Map<String, Object> settings) {
        return putJson(uri("/api/v1/studios/" + photographerId + "/style"), settings);
    }

    public Map<String, Object> clearStyle(UUID photographerId) {
        return sendJson(uri("/api/v1/studios/" + photographerId + "/style"), "DELETE", Map.of());
    }

    public byte[] stylePreview(UUID photographerId, NamedBytes sample, Integer strength) {
        URI target = strength == null
                ? uri("/api/v1/studios/" + photographerId + "/style/preview")
                : query("/api/v1/studios/" + photographerId + "/style/preview", "strength", String.valueOf(strength));
        String serviceKey = token();
        try {
            MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
            multipart.add("file", filePart("file", sample));
            return restClient.post().uri(target).header(SERVICE_KEY_HEADER, serviceKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart).retrieve().body(byte[].class);
        } catch (RestClientResponseException exception) {
            throw serviceError(exception.getStatusCode().value(), exception.getResponseBodyAsString());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The Python data service is unavailable. Start it on " + baseUrl + ".", exception);
        }
    }

    private static HttpEntity<ByteArrayResource> filePart(String field, NamedBytes file) {
        ByteArrayResource resource = new ByteArrayResource(file.bytes()) {
            @Override
            public String getFilename() {
                return file.filename();
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentDispositionFormData(field, file.filename());
        partHeaders.setContentType(file.mediaType());
        return new HttpEntity<>(resource, partHeaders);
    }

    public record NamedBytes(String filename, MediaType mediaType, byte[] bytes) {
    }

    public void deleteGuestFace(String eventId, String guestId, String customerId) {
        sendJson(query("/api/v1/events/" + eventId + "/guests/" + guestId + "/face",
                "customer_id", customerId), "DELETE", Map.of());
    }

    public byte[] whatsappPhotoBytes(String eventId, String photoId, String customerId) {
        MediaStream media = openStream(query(
                "/api/v1/events/" + eventId + "/photos/" + photoId + "/whatsapp-preview",
                "customer_id", customerId), "inline", null);
        try (InputStream input = media.body()) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not read the WhatsApp-ready event photo.", exception);
        }
    }

    public Map<String, Object> submitAlbum(String eventId, String photographerId, String customerId, List<String> photoIds) {
        return postJson("/api/v1/events/" + eventId + "/album-submissions", Map.of(
                "photographer_id", photographerId,
                "customer_id", customerId,
                "photo_ids", photoIds
        ));
    }

    // ---- customer wedding invitations ----

    public Map<String, Object> invitation(String eventId, String photographerId, String customerId) {
        return getJson(query("/api/v1/events/" + eventId + "/invitation",
                "photographer_id", photographerId, "customer_id", customerId));
    }

    public Map<String, Object> saveInvitation(String eventId, String photographerId, String customerId, Map<String, Object> details) {
        return putJson(uri("/api/v1/events/" + eventId + "/invitation"),
                Map.of("photographer_id", photographerId, "customer_id", customerId, "details", details));
    }

    public Map<String, Object> saveInvitationPhoto(String eventId, String photographerId, String customerId, NamedBytes photo) {
        URI target = query("/api/v1/events/" + eventId + "/invitation/photo",
                "photographer_id", photographerId, "customer_id", customerId);
        return exchange(() -> {
            MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
            multipart.add("file", filePart("file", photo));
            return restClient.post().uri(target).header(SERVICE_KEY_HEADER, token())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart).retrieve().body(JSON_MAP);
        });
    }

    public Map<String, Object> deleteInvitationPhoto(String eventId, String photographerId, String customerId) {
        return sendJson(query("/api/v1/events/" + eventId + "/invitation/photo",
                "photographer_id", photographerId, "customer_id", customerId), "DELETE", Map.of());
    }

    public Map<String, Object> generateInvitationDesigns(String eventId, String photographerId, String customerId) {
        return postJson("/api/v1/events/" + eventId + "/invitation/designs",
                Map.of("photographer_id", photographerId, "customer_id", customerId));
    }

    public MediaStream invitationDesign(String eventId, String customerId, int index) {
        return openStream(query("/api/v1/events/" + eventId + "/invitation/designs/" + index,
                "customer_id", customerId), "inline", null);
    }

    public Map<String, Object> finalizeInvitation(String eventId, String photographerId, String customerId, int index) {
        return postJson("/api/v1/events/" + eventId + "/invitation/finalize",
                Map.of("photographer_id", photographerId, "customer_id", customerId, "index", index));
    }

    public MediaStream invitationFile(String eventId, String customerId, String kind, String range) {
        return openStream(query("/api/v1/events/" + eventId + "/invitation/" + kind,
                "customer_id", customerId), "attachment", range);
    }

    // ---- album auto-design ----

    public Map<String, Object> albumDesigns(String eventId, String photographerId, String customerId) {
        return getJson(query("/api/v1/events/" + eventId + "/album-designs",
                "photographer_id", photographerId, "customer_id", customerId));
    }

    public Map<String, Object> createAlbumDesign(String eventId, String photographerId, String customerId,
                                                 String submissionId, double heightIn, double widthIn,
                                                 int sheets, int dpi, String sizeLabel) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("photographer_id", photographerId);
        body.put("customer_id", customerId);
        body.put("submission_id", submissionId);
        body.put("height_in", heightIn);
        body.put("width_in", widthIn);
        body.put("sheets", sheets);
        body.put("dpi", dpi);
        body.put("size_label", sizeLabel);
        return postJson("/api/v1/events/" + eventId + "/album-designs", body);
    }

    public Map<String, Object> albumDesign(String eventId, String designId, String photographerId, String customerId) {
        return getJson(query("/api/v1/events/" + eventId + "/album-designs/" + designId,
                "photographer_id", photographerId, "customer_id", customerId));
    }

    public MediaStream albumDesignSpread(String eventId, String designId, int index,
                                         String photographerId, String customerId) {
        return openStream(query("/api/v1/events/" + eventId + "/album-designs/" + designId + "/spreads/" + index,
                "photographer_id", photographerId, "customer_id", customerId), "inline", null);
    }

    /** The archive as a stream, or a short-lived direct URL (MediaStream with status 302 and the URL in contentRange). */
    public MediaStream albumDesignArchive(String eventId, String designId, String kind,
                                          String photographerId, String customerId) {
        return openStream(query("/api/v1/events/" + eventId + "/album-designs/" + designId + "/download/" + kind,
                "photographer_id", photographerId, "customer_id", customerId), "attachment", null, true);
    }

    public Map<String, Object> publishAlbumDesign(String eventId, String designId, String photographerId, String customerId) {
        return postJson("/api/v1/events/" + eventId + "/album-designs/" + designId + "/publish",
                Map.of("photographer_id", photographerId, "customer_id", customerId));
    }

    public MediaStream openPreview(String eventId, String mediaId, String customerId, String range) {
        return openStream(query("/api/v1/events/" + eventId + "/photos/" + mediaId + "/preview",
                "customer_id", customerId), "inline", range);
    }

    public MediaStream openOriginal(String eventId, String mediaId, String photographerId, String customerId) {
        return openStream(query("/api/v1/events/" + eventId + "/photos/" + mediaId + "/original",
                "photographer_id", photographerId, "customer_id", customerId), "attachment", null);
    }

    public MediaStream downloadCustomerGallery(String eventId, String customerId, List<String> photoIds) {
        URI uri = uri("/api/v1/events/" + eventId + "/downloads");
        byte[] json;
        try {
            json = objectMapper.writeValueAsBytes(Map.of(
                    "customer_id", customerId,
                    "photo_ids", photoIds == null ? List.of() : photoIds
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not encode the gallery download request.", exception);
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(10))
                .header(SERVICE_KEY_HEADER, token())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(json))
                .build();
        try {
            HttpResponse<InputStream> response = STREAM_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message;
                try (InputStream body = response.body()) {
                    message = new String(body.readAllBytes(), StandardCharsets.UTF_8);
                }
                throw serviceError(response.statusCode(), message);
            }
            return new MediaStream(
                    response.statusCode(),
                    response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE),
                    response.headers().firstValue(HttpHeaders.CONTENT_DISPOSITION).orElse("attachment"),
                    null,
                    null,
                    response.headers().firstValueAsLong(HttpHeaders.CONTENT_LENGTH).orElse(-1L),
                    response.body()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The data-service download was interrupted.", exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The Python data service is unavailable. Start it on " + baseUrl + ".", exception);
        }
    }

    private Map<String, Object> postJson(String path, Object body) {
        return postJson(uri(path), body);
    }

    private Map<String, Object> postJson(URI uri, Object body) {
        return sendJson(uri, "POST", body);
    }

    private Map<String, Object> putJson(URI uri, Object body) {
        return sendJson(uri, "PUT", body);
    }

    private Map<String, Object> sendJson(URI uri, String method, Object body) {
        String serviceKey = token();
        try {
            byte[] json = objectMapper.writeValueAsBytes(body);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMinutes(2))
                    .header(SERVICE_KEY_HEADER, serviceKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .method(method, HttpRequest.BodyPublishers.ofByteArray(json))
                    .build();
            HttpResponse<String> response = STREAM_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw serviceError(response.statusCode(), response.body());
            }
            Map<String, Object> result = objectMapper.readValue(
                    response.body(), new TypeReference<Map<String, Object>>() {});
            return result == null ? Map.of() : result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The data-service request was interrupted.", exception);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not encode or decode the Python data-service JSON.", exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The Python data service is unavailable. Start it on " + baseUrl + ".", exception);
        }
    }

    private Map<String, Object> getJson(URI uri) {
        return exchange(() -> restClient.get().uri(uri).header(SERVICE_KEY_HEADER, token())
                .retrieve().body(JSON_MAP));
    }

    private Map<String, Object> exchange(JsonCall call) {
        token();
        try {
            Map<String, Object> result = call.run();
            return result == null ? Map.of() : result;
        } catch (RestClientResponseException exception) {
            throw serviceError(exception.getStatusCode().value(), exception.getResponseBodyAsString());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The Python data service is unavailable. Start it on " + baseUrl + ".", exception);
        }
    }

    private MediaStream openStream(URI uri, String defaultDisposition, String range) {
        return openStream(uri, defaultDisposition, range, false);
    }

    private MediaStream openStream(URI uri, String defaultDisposition, String range, boolean allowRedirect) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(allowRedirect ? 60 : 10))
                .header(SERVICE_KEY_HEADER, token());
        if (range != null && !range.isBlank()) requestBuilder.header(HttpHeaders.RANGE, range);
        HttpRequest request = requestBuilder.GET().build();
        try {
            HttpResponse<InputStream> response = STREAM_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (allowRedirect && response.statusCode() == 302) {
                response.body().close();
                String location = response.headers().firstValue(HttpHeaders.LOCATION).orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The data service sent an empty redirect."));
                return new MediaStream(302, MediaType.APPLICATION_OCTET_STREAM_VALUE, defaultDisposition, location, null, -1L,
                        InputStream.nullInputStream());
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message;
                try (InputStream body = response.body()) {
                    message = new String(body.readAllBytes());
                }
                throw serviceError(response.statusCode(), message);
            }
            return new MediaStream(
                    response.statusCode(),
                    response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE),
                    response.headers().firstValue(HttpHeaders.CONTENT_DISPOSITION).orElse(defaultDisposition),
                    response.headers().firstValue(HttpHeaders.CONTENT_RANGE).orElse(null),
                    response.headers().firstValue(HttpHeaders.ACCEPT_RANGES).orElse(null),
                    response.headers().firstValueAsLong(HttpHeaders.CONTENT_LENGTH).orElse(-1L),
                    response.body()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The data-service request was interrupted.", exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The Python data service is unavailable. Start it on " + baseUrl + ".", exception);
        }
    }

    private ResponseStatusException serviceError(int status, String responseBody) {
        HttpStatus httpStatus = HttpStatus.resolve(status);
        String message = responseBody == null || responseBody.isBlank()
                ? "The data-service request failed."
                : responseBody;
        return new ResponseStatusException(httpStatus == null ? HttpStatus.BAD_GATEWAY : httpStatus, message);
    }

    private String token() {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Set DATA_SERVICE_TOKEN and FB_SERVICE_TOKEN to connect the gallery service.");
        }
        return serviceToken;
    }

    private URI uri(String path) {
        return URI.create(baseUrl + path);
    }

    private URI query(String path, String... query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + path);
        for (int index = 0; index < query.length; index += 2) {
            builder.queryParam(query[index], query[index + 1]);
        }
        return builder.build().encode().toUri();
    }

    @FunctionalInterface
    private interface JsonCall {
        Map<String, Object> run();
    }

    public record MediaStream(int status, String contentType, String contentDisposition,
                              String contentRange, String acceptRanges, long contentLength,
                              InputStream body) {}
}
