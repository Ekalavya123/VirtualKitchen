package com.processVisualisation.virtualKitchen.service;

import com.processVisualisation.virtualKitchen.ai.model.RecipeFlowGenerationJob;
import com.processVisualisation.virtualKitchen.ai.model.RecipeFlowGenerationJobStatus;
import com.processVisualisation.virtualKitchen.ai.model.RecipeFlowGenerationStage;
import com.processVisualisation.virtualKitchen.ai.repository.RecipeFlowGenerationJobRepository;
import com.processVisualisation.virtualKitchen.ai.service.AIRecipeGenerationService;
import com.processVisualisation.virtualKitchen.ai.service.RecipeFlowGenerationJobService;
import com.processVisualisation.virtualKitchen.common.concurrent.ThreadPoolTaskPool;
import com.processVisualisation.virtualKitchen.common.exception.RecipeFlowGenerationException;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeFlowGenerationJobResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeFlowGenerationResponseDTO;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecipeFlowGenerationJobServiceTest {

    private AIRecipeGenerationService aiRecipeGenerationService;
    private RecipeFlowGenerationJobRepository jobRepository;
    private MongoTemplate mongoTemplate;
    private RecipeFlowGenerationJobService service;

    @BeforeEach
    void setUp() {
        aiRecipeGenerationService = mock(AIRecipeGenerationService.class);

        jobRepository = mock(RecipeFlowGenerationJobRepository.class);
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jobRepository.findByUserIdAndClientRequestId(any(), any())).thenReturn(Optional.empty());

        mongoTemplate = mock(MongoTemplate.class);

        service = new RecipeFlowGenerationJobService(
                aiRecipeGenerationService,
                jobRepository,
                mongoTemplate,
                new ThreadPoolTaskPool(2, "test-flow-generation-orchestrator"));
    }

    @Test
    void startJob_happyPath_reachesCompletedWithResult() {
        RecipeFlowGenerationResponseDTO generated = RecipeFlowGenerationResponseDTO.builder()
                .steps(List.of())
                .edges(List.of())
                .modelUsed("test-text")
                .build();

        when(aiRecipeGenerationService.generateFlow(eq(1L), eq("a recipe"), eq("req-1"), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    Consumer<RecipeFlowGenerationStage> onStage = inv.getArgument(3);
                    onStage.accept(RecipeFlowGenerationStage.BUILDING_PROMPT);
                    onStage.accept(RecipeFlowGenerationStage.CALLING_MODEL);
                    onStage.accept(RecipeFlowGenerationStage.VALIDATING_RESPONSE);
                    onStage.accept(RecipeFlowGenerationStage.PERSISTING);
                    return generated;
                });

        RecipeFlowGenerationJobResponseDTO started = service.startJob(1L, "a recipe", "req-1");
        assertEquals("QUEUED", started.getStatus());

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, timeout(5000).atLeast(5))
                .updateFirst(any(Query.class), updateCaptor.capture(), eq(RecipeFlowGenerationJob.class));

        List<Update> updates = updateCaptor.getAllValues();
        boolean sawCompleted = updates.stream().anyMatch(u -> statusValue(u) == RecipeFlowGenerationJobStatus.COMPLETED);
        assertTrue(sawCompleted, "expected the job to reach COMPLETED");

        boolean sawResult = updates.stream().anyMatch(u -> resultValue(u) != null);
        assertTrue(sawResult, "expected the completed job's update to carry the generated result");
    }

    @Test
    void startJob_generationFails_marksJobFailedWithErrorMessage() {
        when(aiRecipeGenerationService.generateFlow(eq(1L), eq("bad recipe"), eq("req-2"), any()))
                .thenThrow(new RecipeFlowGenerationException("Unable to generate valid recipe flow: bad json"));

        service.startJob(1L, "bad recipe", "req-2");

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, timeout(5000).atLeast(2))
                .updateFirst(any(Query.class), updateCaptor.capture(), eq(RecipeFlowGenerationJob.class));

        List<Update> updates = updateCaptor.getAllValues();
        boolean sawFailed = updates.stream().anyMatch(u -> statusValue(u) == RecipeFlowGenerationJobStatus.FAILED);
        assertTrue(sawFailed, "expected the job to reach FAILED rather than hang IN_PROGRESS forever");
    }

    @Test
    void startJob_duplicateClientRequestId_returnsExistingJobWithoutStartingANewOne() {
        RecipeFlowGenerationJob existing = new RecipeFlowGenerationJob();
        existing.setId("job-existing");
        existing.setStatus(RecipeFlowGenerationJobStatus.IN_PROGRESS);
        existing.setStage(RecipeFlowGenerationStage.CALLING_MODEL);
        when(jobRepository.findByUserIdAndClientRequestId(1L, "req-dup")).thenReturn(Optional.of(existing));

        RecipeFlowGenerationJobResponseDTO result = service.startJob(1L, "a recipe", "req-dup");

        assertEquals("job-existing", result.getJobId());
        assertEquals("IN_PROGRESS", result.getStatus());
        verify(jobRepository, never()).save(any());
    }

    @Test
    void getJobStatus_unknownJobId_throws() {
        when(jobRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(RecipeFlowGenerationException.class, () -> service.getJobStatus("missing"));
    }

    private RecipeFlowGenerationJobStatus statusValue(Update update) {
        Document updateObject = update.getUpdateObject();
        Document set = (Document) updateObject.get("$set");
        if (set == null || !set.containsKey("status")) {
            return null;
        }
        return (RecipeFlowGenerationJobStatus) set.get("status");
    }

    private Object resultValue(Update update) {
        Document updateObject = update.getUpdateObject();
        Document set = (Document) updateObject.get("$set");
        return set == null ? null : set.get("result");
    }
}
