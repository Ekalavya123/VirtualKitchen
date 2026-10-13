package com.processVisualisation.virtualKitchen.ai.analytics.query;

import com.processVisualisation.virtualKitchen.ai.analytics.model.AiRequestAnalytics;
import com.processVisualisation.virtualKitchen.ai.queue.AiRequestProperties;
import com.processVisualisation.virtualKitchen.ai.registry.AiCapability;
import com.processVisualisation.virtualKitchen.ai.registry.AiModelProperties;
import com.processVisualisation.virtualKitchen.ai.registry.ModelDefinition;
import com.processVisualisation.virtualKitchen.ai.registry.ModelTier;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Filters are usable before any request has been recorded, and costs come with their recorded conversion. */
class AnalyticsOptionsTest {

    private static ModelDefinition model(String key, String bean, ModelTier tier) {
        ModelDefinition model = new ModelDefinition();
        model.setKey(key);
        model.setProviderBean(bean);
        model.setTier(tier);
        model.setCapability(AiCapability.TEXT_TO_TEXT);
        return model;
    }

    private AnalyticsQueryService service(MongoTemplate mongo) {
        AiModelProperties models = new AiModelProperties();
        models.setModels(List.of(model("gemini-flash", "geminiAiClient", ModelTier.PAID),
                model("ollama-qwen", "ollamaAiClient", ModelTier.OPEN_SOURCE)));
        AiRequestProperties request = new AiRequestProperties();
        request.setUsdToInr(84.5);
        return new AnalyticsQueryService(mongo, models, request);
    }

    @Test
    void emptyDatabase_stillOffersEveryOperationTypeStatusSourceAndConfiguredModel() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findDistinct(any(Query.class), anyString(), eq(AiRequestAnalytics.class), eq(Object.class))).thenReturn(List.of());

        AnalyticsQueryService.AnalyticsOptions options = service(mongo).filterOptions();

        assertThat(options.values().get("operationContext")).contains("RECIPE_FLOW_GENERATION", "STEP_NARRATION", "CHAT")
                .doesNotContain("UNKNOWN");
        assertThat(options.values().get("model")).containsExactly("gemini-flash", "ollama-qwen");
        assertThat(options.values().get("provider")).containsExactly("gemini", "ollama");
        assertThat(options.values().get("tier")).containsExactly("PAID", "OPEN_SOURCE");
        assertThat(options.values().get("status")).contains("SUCCEEDED", "FAILED", "TIMED_OUT", "REUSED");
        assertThat(options.values().get("source")).contains("LIVE", "BACKFILL_REQUEST_JOB");
        assertThat(options.displayCurrency()).isEqualTo("INR");
        assertThat(options.usdToDisplay()).isEqualTo(84.5);
    }

    @Test
    void distinctQueries_excludeNullValues_whichFindDistinctCannotMap() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findDistinct(any(Query.class), anyString(), eq(AiRequestAnalytics.class), eq(Object.class))).thenReturn(List.of());

        service(mongo).filterOptions();

        org.mockito.ArgumentCaptor<Query> queries = org.mockito.ArgumentCaptor.forClass(Query.class);
        org.mockito.ArgumentCaptor<String> fields = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mongo, org.mockito.Mockito.atLeastOnce())
                .findDistinct(queries.capture(), fields.capture(), eq(AiRequestAnalytics.class), eq(Object.class));
        for (int i = 0; i < queries.getAllValues().size(); i++) {
            assertThat(queries.getAllValues().get(i).getQueryObject().toJson())
                    .contains(fields.getAllValues().get(i)).contains("$ne");
        }
    }

    @Test
    void valuesOnlyPresentInTheData_areAlsoOffered() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findDistinct(any(Query.class), anyString(), eq(AiRequestAnalytics.class), eq(Object.class))).thenReturn(List.of());
        when(mongo.findDistinct(any(Query.class), eq("resolvedModelKey"), eq(AiRequestAnalytics.class), eq(Object.class)))
                .thenReturn(List.of("retired-model", "gemini-flash"));

        assertThat(service(mongo).filterOptions().values().get("model"))
                .containsExactly("gemini-flash", "ollama-qwen", "retired-model");
    }
}
