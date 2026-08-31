package com.processVisualisation.virtualKitchen.restclient.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIResponse {

    private String content;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String finishReason;
    private String rawResponse;
}
