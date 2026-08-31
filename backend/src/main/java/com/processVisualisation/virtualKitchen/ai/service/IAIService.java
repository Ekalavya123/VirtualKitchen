package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;

public interface IAIService {

    AIResponse chat(String prompt);
}
