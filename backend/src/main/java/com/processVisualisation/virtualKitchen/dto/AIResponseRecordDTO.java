package com.processVisualisation.virtualKitchen.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AIResponseRecordDTO {
    private String context;
    private String input;
    private Boolean success;
    private Map<String, Object> responseData = new LinkedHashMap<>();
}

