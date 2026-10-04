package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.ai.dispatch.AiClientResolver;
import com.processVisualisation.virtualKitchen.ai.queue.AiRequestOutcome;
import com.processVisualisation.virtualKitchen.ai.queue.AiRequestQueueService;
import com.processVisualisation.virtualKitchen.ai.queue.AiWork;
import com.processVisualisation.virtualKitchen.ai.registry.ModelDefinition;
import com.processVisualisation.virtualKitchen.ai.registry.ModelTier;
import com.processVisualisation.virtualKitchen.ai.routing.FallbackReason;
import com.processVisualisation.virtualKitchen.ai.routing.ModelSelectionOutcome;
import com.processVisualisation.virtualKitchen.common.exception.RecipeProcessAiException;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessGenerationMode;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessGenerationResultDTO;
import com.processVisualisation.virtualKitchen.restclient.client.AIClient;
import com.processVisualisation.virtualKitchen.restclient.dto.AIRequest;
import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static com.processVisualisation.virtualKitchen.ai.service.EditTestFixtures.COOK_ID;
import static com.processVisualisation.virtualKitchen.ai.service.EditTestFixtures.PROCESS_ID;
import static com.processVisualisation.virtualKitchen.ai.service.EditTestFixtures.pastaProcess;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@link RecipeProcessEditService} end to end with a scripted model behind a pass-through queue. */
class RecipeProcessEditServiceTest {

    private static final RecipeStepVocabularyProvider VOCABULARY = new RecipeStepVocabularyProvider();

    private final Deque<String> scriptedResponses = new ArrayDeque<>();
    private final List<AIRequest> requests = new ArrayList<>();
    private RecipeProcessEditService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        ModelDefinition model = new ModelDefinition();
        model.setKey("gemini-flash");
        model.setTier(ModelTier.PAID);
        model.setProviderModelId("gemini-test");
        ModelSelectionOutcome selection = new ModelSelectionOutcome(model, false, FallbackReason.NONE, null);

        AiRequestQueueService queue = mock(AiRequestQueueService.class);
        when(queue.executeBounded(eq(7L), any(), any(), any(), eq("process-edit"), any(), any(AiWork.class)))
                .thenAnswer(invocation -> {
                    AiWork<Object> work = invocation.getArgument(6);
                    return AiRequestOutcome.fresh("job-1", work.run(selection), selection, null);
                });

        AIClient client = request -> {
            requests.add(request);
            return AIResponse.builder().content(scriptedResponses.poll()).build();
        };
        AiClientResolver resolver = mock(AiClientResolver.class);
        when(resolver.resolveTextClient(model)).thenReturn(client);

        RecipeProcessOutputSchema generationSchema = new RecipeProcessOutputSchema(VOCABULARY);
        RecipeProcessEditOutputSchema editSchema = new RecipeProcessEditOutputSchema(VOCABULARY, generationSchema);
        service = new RecipeProcessEditService(
                queue,
                resolver,
                new RecipeProcessEditPromptBuilder(new RecipeProcessGenerationPromptBuilder(VOCABULARY, generationSchema), editSchema),
                new RecipeProcessEditValidator(new RecipeProcessGenerationValidator(VOCABULARY), new RecipeProcessOutputNormalizer()),
                editSchema,
                new AiJsonResponseSupport(mock(IAIResponseService.class)));
    }

    @Test
    void validFirstAnswer_isTranslatedAndReturnedWithModelInfo() {
        scriptedResponses.add("{\"summary\":\"Cooking time is now 8 minutes.\",\"operations\":[{\"op\":\"UPDATE_STEP\",\"target\":\"s3\",\"set\":{\"duration\":\"8 minutes\"}}]}");

        RecipeProcessGenerationResultDTO result = edit("Cook for 8 minutes instead of 10");

        assertThat(result.getMode()).isEqualTo(ProcessGenerationMode.EDIT);
        assertThat(result.getModelUsed()).isEqualTo("gemini-flash");
        assertThat(result.getEdit().getTargetProcessId()).isEqualTo(PROCESS_ID);
        assertThat(result.getEdit().getSummary()).isEqualTo("Cooking time is now 8 minutes.");
        assertThat(result.getEdit().getOperations()).singleElement().satisfies(op -> {
            assertThat(op.getTarget()).isEqualTo(COOK_ID);
            assertThat(op.getStep().getDuration()).isEqualTo("8 minutes");
        });
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.getOperation()).isEqualTo("RECIPE_EDIT_FLOW");
            assertThat(request.getResponseSchemaName()).isEqualTo(RecipeProcessEditOutputSchema.SCHEMA_NAME);
            assertThat(request.getUserPrompt()).contains("SELECTED STEP: none").doesNotContain(COOK_ID);
        });
    }

    @Test
    void invalidFirstAnswer_isRetriedWithItsErrors() {
        scriptedResponses.add("{\"summary\":\"Removed.\",\"operations\":[{\"op\":\"DELETE_NODE\",\"target\":\"s9\"}]}");
        scriptedResponses.add("```json\n{\"summary\":\"Removed the last step.\",\"operations\":[{\"op\":\"DELETE_NODE\",\"target\":\"s4\"}]}\n```");

        RecipeProcessGenerationResultDTO result = edit("Remove the last step");

        assertThat(result.getEdit().getOperations()).hasSize(1);
        assertThat(requests).hasSize(2);
        assertThat(requests.get(1).getUserPrompt()).contains("VALIDATION ERRORS IN THAT OUTPUT").contains("unknown node: s9");
    }

    @Test
    void clarification_isPassedThroughWithoutOperations() {
        scriptedResponses.add("{\"summary\":\"I need the amount.\",\"operations\":[],\"clarification\":\"What quantity of salt should it be?\"}");

        RecipeProcessGenerationResultDTO result = edit("The quantity is incorrect");

        assertThat(result.getEdit().getClarification()).isEqualTo("What quantity of salt should it be?");
        assertThat(result.getEdit().getOperations()).isEmpty();
    }

    @Test
    void twoInvalidAnswers_fail() {
        scriptedResponses.add("not json");
        scriptedResponses.add("{\"summary\":\"x\",\"operations\":[]}");

        assertThatThrownBy(() -> edit("Do something"))
                .isInstanceOf(RecipeProcessAiException.class)
                .hasMessageContaining("Unable to apply the requested change")
                .hasMessageContaining("operations is empty");
    }

    private RecipeProcessGenerationResultDTO edit(String instruction) {
        return service.edit(7L, instruction, pastaProcess(), null, "req-1", stage -> { });
    }
}
