package com.processVisualisation.virtualKitchen.restclient.exception;

public class AIAuthenticationException extends AIClientException {

    public AIAuthenticationException(String message) {
        super(message);
    }

    public AIAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
