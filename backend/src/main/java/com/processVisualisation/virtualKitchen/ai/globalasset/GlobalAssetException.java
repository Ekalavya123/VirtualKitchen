package com.processVisualisation.virtualKitchen.ai.globalasset;

import com.processVisualisation.virtualKitchen.common.web.ApiException;
import com.processVisualisation.virtualKitchen.common.web.GlobalExceptionHandler;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an admin global-asset request can't be honoured: an unknown resource type or an
 * invalid id list (400), or a resource that isn't eligible for image generation (422). Handled
 * by {@link GlobalExceptionHandler}.
 */
public class GlobalAssetException extends ApiException {

    public GlobalAssetException(HttpStatus status, String message) {
        super(message, status, null, Outcome.CLIENT_ERROR);
    }

    public static GlobalAssetException badRequest(String message) {
        return new GlobalAssetException(HttpStatus.BAD_REQUEST, message);
    }

    public static GlobalAssetException notEligible(String message) {
        return new GlobalAssetException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
