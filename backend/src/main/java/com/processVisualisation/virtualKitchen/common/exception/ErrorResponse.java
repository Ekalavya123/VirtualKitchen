package com.processVisualisation.virtualKitchen.common.exception;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Standard error payload returned to clients by {@link GlobalExceptionHandler}
 * whenever a request fails: carries when the error occurred, the HTTP status
 * code, a short error label, and a human-readable message describing what
 * went wrong.
 */
@Data
@AllArgsConstructor
public class ErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
}
