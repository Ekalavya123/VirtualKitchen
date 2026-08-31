package com.processVisualisation.virtualKitchen.restclient.client;

import com.processVisualisation.virtualKitchen.restclient.dto.AIRequest;
import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;

public interface AIClient {

    AIResponse chat(AIRequest request);
}
