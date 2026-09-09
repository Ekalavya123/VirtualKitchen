package com.processVisualisation.virtualKitchen.common.utils;



import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Generic envelope used to standardize the shape of API responses returned
 * to clients, wrapping a success flag, a human-readable message, the actual
 * response payload, and a timestamp. Lombok generates the getters/setters
 * ({@code @Data}), an all-args and no-args constructor, and a fluent builder
 * ({@code @Builder}) for this class.
 *
 * @param <T> the type of the payload carried in {@link #getData()}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timestamp;
}