package com.framebucket.subscription;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Thrown when the photographer's plan doesn't include a feature or a limit is reached. The API error
 * handler returns it as JSON with code PLAN_UPGRADE_REQUIRED so the app can show an upgrade prompt.
 */
public class PlanRestrictionException extends ResponseStatusException {
    private final Feature feature;
    private final String limit;

    public PlanRestrictionException(String message, Feature feature, String limit) {
        super(HttpStatus.FORBIDDEN, message);
        this.feature = feature;
        this.limit = limit;
    }

    public static PlanRestrictionException missingFeature(Feature feature) {
        return new PlanRestrictionException(
                feature.label() + " is not included in your current plan. Upgrade your plan to use it.",
                feature, null);
    }

    public static PlanRestrictionException limitReached(String limit, String message) {
        return new PlanRestrictionException(message, null, limit);
    }

    public Feature getFeature() {
        return feature;
    }

    public String getLimit() {
        return limit;
    }
}
