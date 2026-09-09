package com.processVisualisation.virtualKitchen.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for the AI chat endpoint, carrying the free-form prompt
 * text a client wants the AI service to respond to.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIChatRequestDTO {

    @NotBlank(message = "prompt is required")
    private String prompt;
}
