package com.framebucket.config;

import com.framebucket.subscription.PlanRestrictionException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Returns the reason of a ResponseStatusException to the browser as JSON ({"detail": ...}), which the
 * frontend already displays. Spring's default error page drops these messages. Only
 * ResponseStatusException reasons are exposed; they are written for end users. Other exceptions keep
 * Spring's generic error response.
 */
@RestControllerAdvice
public class ApiErrorAdvice {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handle(ResponseStatusException exception, HttpServletRequest request) {
        // Endpoints that produce images/PDFs would otherwise reject a JSON error body.
        request.removeAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE);
        HttpStatusCode status = exception.getStatusCode();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("detail", exception.getReason() != null ? exception.getReason() : "The request could not be completed.");
        if (exception instanceof PlanRestrictionException restriction) {
            body.put("code", "PLAN_UPGRADE_REQUIRED");
            if (restriction.getFeature() != null) body.put("feature", restriction.getFeature().name());
            if (restriction.getLimit() != null) body.put("limit", restriction.getLimit());
        }
        return ResponseEntity.status(status)
                .headers(exception.getHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
