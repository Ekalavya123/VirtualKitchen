package com.processVisualisation.virtualKitchen.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response payload returned from the AI chat endpoint, containing the
 * generated content produced by the AI service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIChatResponseDTO {

    private String content;
}
