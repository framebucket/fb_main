package com.framebucket.gallery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
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
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final String baseUrl;
    private final String serviceToken;

    public DataServiceClient(
            RestClient.Builder builder,
            @Value("${app.data-service.base-url:http://127.0.0.1:8001}") String baseUrl,
            @Value("${app.data-service.token:}") String serviceToken
    ) {
        this.restClient = builder.build();
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

    public Map<String, Object> createFolder(String eventId, String photographerId, String customerId, String name) {
        URI uri = query("/api/v1/events/" + eventId + "/folders",
                "photographer_id", photographerId, "customer_id", customerId);
        return exchange(() -> restClient.post().uri(uri).header(SERVICE_KEY_HEADER, token())
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("name", name)).retrieve().body(JSON_MAP));
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
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            Resource resource = file.getResource();
            builder.part("file", resource).filename(file.getOriginalFilename());
            return restClient.post().uri(uri).header(SERVICE_KEY_HEADER, token())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(builder.build()).retrieve().body(JSON_MAP);
        });
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

    private Map<String, Object> postJson(String path, Object body) {
        return exchange(() -> restClient.post().uri(uri(path)).header(SERVICE_KEY_HEADER, token())
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JSON_MAP));
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
