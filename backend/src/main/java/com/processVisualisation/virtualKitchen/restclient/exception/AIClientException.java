package com.processVisualisation.virtualKitchen.restclient.exception;

public class AIClientException extends RuntimeException {

    public AIClientException(String message) {
        super(message);
    }

    public AIClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
