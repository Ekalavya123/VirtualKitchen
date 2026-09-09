package com.processVisualisation.virtualKitchen.ai.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data transfer object used to create/persist a record of an AI interaction,
 * capturing the invocation context, the input prompt, whether it succeeded,
 * and the raw response data returned by the AI provider.
 */
@Data
public class AIResponseRecordDTO {
    private String context;
    private String input;
    private Boolean success;
    private Map<String, Object> responseData = new LinkedHashMap<>();
}

