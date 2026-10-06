package com.processVisualisation.virtualKitchen.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an admin global-asset request can't be honoured: an unknown resource type or an
 * invalid id list (400), or a resource that isn't eligible for image generation (422). Handled
 * by {@link GlobalExceptionHandler}.
 */
public class GlobalAssetException extends RuntimeException {

    private final HttpStatus status;

    public GlobalAssetException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static GlobalAssetException badRequest(String message) {
        return new GlobalAssetException(HttpStatus.BAD_REQUEST, message);
    }

    public static GlobalAssetException notEligible(String message) {
        return new GlobalAssetException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
