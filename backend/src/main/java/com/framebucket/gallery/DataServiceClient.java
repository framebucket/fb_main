package com.framebucket.gallery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
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
import org.springframework.web.multipart.MultipartFile;
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
        URI uri = folderId == null || folderId.isBlank()
                ? query("/api/v1/events/" + eventId + "/photos", "customer_id", customerId)
                : query("/api/v1/events/" + eventId + "/photos", "customer_id", customerId, "folder_id", folderId);
        return getJson(uri);
    }

    public Map<String, Object> upload(String eventId, String photographerId, String customerId,
                                      String folderId, MultipartFile file) {
        URI uri = query("/api/v1/events/" + eventId + "/photos",
                "photographer_id", photographerId,
                "customer_id", customerId,
                "folder_id", folderId == null || folderId.isBlank() ? "root" : folderId);
        return exchange(() -> {
            MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
            Resource resource = file.getResource();
            HttpHeaders partHeaders = new HttpHeaders();
            String filename = file.getOriginalFilename();
            partHeaders.setContentDispositionFormData(
                    "file", filename == null || filename.isBlank() ? "upload" : filename);
            partHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            multipart.add("file", new HttpEntity<>(resource, partHeaders));
            return restClient.post().uri(uri).header(SERVICE_KEY_HEADER, token())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart).retrieve().body(JSON_MAP);
        });
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
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(10))
                .header(SERVICE_KEY_HEADER, token());
        if (range != null && !range.isBlank()) requestBuilder.header(HttpHeaders.RANGE, range);
        HttpRequest request = requestBuilder.GET().build();
        try {
            HttpResponse<InputStream> response = STREAM_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
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
