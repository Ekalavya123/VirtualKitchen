package com.processVisualisation.virtualKitchen.restclient.exception;

public class AIInvalidResponseException extends AIClientException {

    public AIInvalidResponseException(String message) {
        super(message);
    }

    public AIInvalidResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
