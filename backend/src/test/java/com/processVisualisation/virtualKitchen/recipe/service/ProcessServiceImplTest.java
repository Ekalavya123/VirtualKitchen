package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;
import com.processVisualisation.virtualKitchen.common.exception.ProcessValidationException;
import com.processVisualisation.virtualKitchen.common.exception.RecipeAccessDeniedException;
import com.processVisualisation.virtualKitchen.common.mapper.ProcessMapper;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessNodeDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessUpdateDTO;
import com.processVisualisation.virtualKitchen.recipe.model.Process;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessNodeKind;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessType;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeTemplate;
import com.processVisualisation.virtualKitchen.recipe.model.Visibility;
import com.processVisualisation.virtualKitchen.recipe.repository.ProcessRepository;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeTemplateRepository;
import com.processVisualisation.virtualKitchen.recipe.validation.ProcessValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Service-level tests for {@link ProcessServiceImpl}, backed by
 * Mockito-stubbed repositories over simple in-memory maps (no embedded
 * Mongo), matching this project's existing service test style (see e.g.
 * {@code VisualizationJobServiceTest}). Uses a real {@link ProcessValidator}
 * (constructed over the same fake repositories) rather than mocking it away,
 * so validation rules are exercised end-to-end through the service.
 */
class ProcessServiceImplTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private final Map<Long, Process> processStore = new ConcurrentHashMap<>();
    private final Map<Long, RecipeTemplate> recipeStore = new ConcurrentHashMap<>();

    private ProcessRepository processRepository;
    private RecipeTemplateRepository recipeTemplateRepository;
    private ProcessServiceImpl service;

    private Long recipeId;
    private Long otherRecipeId;

    @BeforeEach
    void setUp() {
        processStore.clear();
        recipeStore.clear();

        processRepository = mock(ProcessRepository.class);
        recipeTemplateRepository = mock(RecipeTemplateRepository.class);
        SequenceGeneratorService sequenceGeneratorService = mock(SequenceGeneratorService.class);

        AtomicLong idCounter = new AtomicLong(1000);
        when(sequenceGeneratorService.generateSequence(anyString())).thenAnswer(inv -> idCounter.incrementAndGet());

        when(processRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(processStore.get(inv.<Long>getArgument(0))));
        when(processRepository.save(any())).thenAnswer(inv -> {
            Process process = inv.getArgument(0);
            processStore.put(process.getId(), process);
            return process;
        });
        when(processRepository.findByRecipeId(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return processStore.values().stream()
                    .filter(p -> id.equals(p.getRecipeId()))
                    .collect(Collectors.toList());
        });
        when(processRepository.findByRecipeIdAndType(anyLong(), any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            ProcessType type = inv.getArgument(1);
            return processStore.values().stream()
                    .filter(p -> id.equals(p.getRecipeId()) && p.getType() == type)
                    .collect(Collectors.toList());
        });
        doAnswer(inv -> {
            processStore.remove(inv.<Long>getArgument(0));
            return null;
        }).when(processRepository).deleteById(anyLong());

        when(recipeTemplateRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(recipeStore.get(inv.<Long>getArgument(0))));

        ProcessValidator validator = new ProcessValidator(processRepository, recipeTemplateRepository);
        ProcessMapper mapper = new ProcessMapper();
        service = new ProcessServiceImpl(processRepository, recipeTemplateRepository, sequenceGeneratorService, validator, mapper);

        recipeId = seedRecipe(OWNER_ID, Visibility.PRIVATE);
        otherRecipeId = seedRecipe(OTHER_USER_ID, Visibility.PRIVATE);
    }

    @Test
    void create_main_persistsAndReturnsMainProcess() {
        ProcessResponseDTO created = service.create(recipeId, OWNER_ID, request(ProcessType.MAIN, "Main"));

        assertEquals(ProcessType.MAIN, created.getType());
        assertEquals(recipeId, created.getRecipeId());
        assertTrue(processStore.containsKey(created.getId()));
    }

    @Test
    void create_subprocess_persistsAndReturnsSubprocess() {
        ProcessResponseDTO created = service.create(recipeId, OWNER_ID, request(ProcessType.SUBPROCESS, "Prepare Chicken"));

        assertEquals(ProcessType.SUBPROCESS, created.getType());
        assertEquals("Prepare Chicken", created.getName());
    }

    @Test
    void create_nonOwner_isRejected() {
        assertThrows(RecipeAccessDeniedException.class,
                () -> service.create(recipeId, OTHER_USER_ID, request(ProcessType.MAIN, "Main")));
    }

    @Test
    void create_secondMainProcess_isRejected() {
        service.create(recipeId, OWNER_ID, request(ProcessType.MAIN, "Main"));

        assertThrows(ProcessValidationException.class,
                () -> service.create(recipeId, OWNER_ID, request(ProcessType.MAIN, "Main 2")));
    }

    @Test
    void create_invalidRequest_unknownRecipe_isRejected() {
        assertThrows(NoSuchElementException.class,
                () -> service.create(999L, OWNER_ID, request(ProcessType.MAIN, "Main")));
    }

    @Test
    void get_returnsMatchingProcess() {
        ProcessResponseDTO created = service.create(recipeId, OWNER_ID, request(ProcessType.SUBPROCESS, "Prep"));

        ProcessResponseDTO fetched = service.get(recipeId, created.getId(), OWNER_ID);

        assertEquals(created.getId(), fetched.getId());
        assertEquals("Prep", fetched.getName());
    }

    @Test
    void get_processFromAnotherRecipe_isNotFound() {
        ProcessResponseDTO created = service.create(recipeId, OWNER_ID, request(ProcessType.SUBPROCESS, "Prep"));

        assertThrows(NoSuchElementException.class, () -> service.get(otherRecipeId, created.getId(), OTHER_USER_ID));
    }

    @Test
    void get_privateRecipe_nonOwner_isForbidden() {
        ProcessResponseDTO created = service.create(recipeId, OWNER_ID, request(ProcessType.SUBPROCESS, "Prep"));

        assertThrows(RecipeAccessDeniedException.class, () -> service.get(recipeId, created.getId(), OTHER_USER_ID));
    }

    @Test
    void get_publicRecipe_anyoneCanRead() {
        Long publicRecipeId = seedRecipe(OWNER_ID, Visibility.PUBLIC);
        ProcessResponseDTO created = service.create(publicRecipeId, OWNER_ID, request(ProcessType.SUBPROCESS, "Prep"));

        ProcessResponseDTO fetched = service.get(publicRecipeId, created.getId(), OTHER_USER_ID);

        assertEquals(created.getId(), fetched.getId());
    }

    @Test
    void get_publicRecipe_unauthenticatedCanRead() {
        Long publicRecipeId = seedRecipe(OWNER_ID, Visibility.PUBLIC);
        ProcessResponseDTO created = service.create(publicRecipeId, OWNER_ID, request(ProcessType.SUBPROCESS, "Prep"));

        ProcessResponseDTO fetched = service.get(publicRecipeId, created.getId(), null);

        assertEquals(created.getId(), fetched.getId());
    }

    @Test
    void listByRecipe_returnsOnlyThatRecipesProcesses() {
        service.create(recipeId, OWNER_ID, request(ProcessType.MAIN, "Main"));
        service.create(recipeId, OWNER_ID, request(ProcessType.SUBPROCESS, "Sub"));
        service.create(otherRecipeId, OTHER_USER_ID, request(ProcessType.MAIN, "Other Main"));

        List<ProcessResponseDTO> processes = service.listByRecipe(recipeId, OWNER_ID);

        assertEquals(2, processes.size());
    }

    @Test
    void update_replacesNameDescriptionNodesAndEdges() {
        ProcessResponseDTO created = service.create(recipeId, OWNER_ID, request(ProcessType.SUBPROCESS, "Prep"));

        ProcessUpdateDTO update = new ProcessUpdateDTO();
        update.setName("Prep v2");
        update.setDescription("updated");
        update.setNodes(List.of(stepNodeDTO("n1")));

        ProcessResponseDTO updated = service.update(recipeId, created.getId(), OWNER_ID, update);

        assertEquals("Prep v2", updated.getName());
        assertEquals(1, updated.getNodes().size());
        assertEquals("n1", updated.getNodes().get(0).getId());
    }

    @Test
    void update_invalidGraph_isRejected() {
        ProcessResponseDTO created = service.create(recipeId, OWNER_ID, request(ProcessType.SUBPROCESS, "Prep"));

        ProcessUpdateDTO update = new ProcessUpdateDTO();
        update.setName("Prep");
        // a STEP node with no data is structurally invalid
        ProcessNodeDTO invalidNode = ProcessNodeDTO.builder().id("n1").kind(ProcessNodeKind.STEP).build();
        update.setNodes(List.of(invalidNode));

        assertThrows(ProcessValidationException.class, () -> service.update(recipeId, created.getId(), OWNER_ID, update));
    }

    @Test
    void update_nonOwner_isRejected() {
        ProcessResponseDTO created = service.create(recipeId, OWNER_ID, request(ProcessType.SUBPROCESS, "Prep"));
        ProcessUpdateDTO update = new ProcessUpdateDTO();
        update.setName("hijacked");

        assertThrows(RecipeAccessDeniedException.class, () -> service.update(recipeId, created.getId(), OTHER_USER_ID, update));
    }

    @Test
    void delete_subprocess_removesIt() {
        ProcessResponseDTO created = service.create(recipeId, OWNER_ID, request(ProcessType.SUBPROCESS, "Prep"));

        service.delete(recipeId, created.getId(), OWNER_ID);

        assertFalse(processStore.containsKey(created.getId()));
    }

    @Test
    void delete_referencedMainProcess_isRejected() {
        ProcessResponseDTO main = service.create(recipeId, OWNER_ID, request(ProcessType.MAIN, "Main"));
        RecipeTemplate recipe = recipeStore.get(recipeId);
        recipe.setMainProcessId(main.getId());

        assertThrows(ProcessValidationException.class, () -> service.delete(recipeId, main.getId(), OWNER_ID));
        assertTrue(processStore.containsKey(main.getId()), "the referenced MAIN process must not be deleted");
    }

    @Test
    void delete_unreferencedMainProcess_succeeds() {
        // mainProcessId was never assigned on the recipe, so nothing references this MAIN process yet.
        ProcessResponseDTO main = service.create(recipeId, OWNER_ID, request(ProcessType.MAIN, "Main"));

        service.delete(recipeId, main.getId(), OWNER_ID);

        assertFalse(processStore.containsKey(main.getId()));
    }

    @Test
    void copy_subprocessWithinSameRecipe_createsIndependentCopy() {
        ProcessResponseDTO source = service.create(recipeId, OWNER_ID, request(ProcessType.SUBPROCESS, "Prepare Chicken"));
        ProcessUpdateDTO update = new ProcessUpdateDTO();
        update.setName("Prepare Chicken");
        update.setNodes(List.of(stepNodeDTO("n1")));
        service.update(recipeId, source.getId(), OWNER_ID, update);

        ProcessResponseDTO copy = service.copy(recipeId, source.getId(), OWNER_ID);

        assertNotEquals(source.getId(), copy.getId());
        assertEquals("Prepare Chicken (copy)", copy.getName());
        assertEquals(1, copy.getNodes().size());
        assertNotEquals("n1", copy.getNodes().get(0).getId(), "copied node ids must be regenerated, not reused");
        assertEquals(recipeId, copy.getRecipeId());
    }

    @Test
    void copy_mainProcess_isRejected() {
        ProcessResponseDTO main = service.create(recipeId, OWNER_ID, request(ProcessType.MAIN, "Main"));

        assertThrows(ProcessValidationException.class, () -> service.copy(recipeId, main.getId(), OWNER_ID));
    }

    @Test
    void copy_crossRecipe_isRejected() {
        ProcessResponseDTO source = service.create(recipeId, OWNER_ID, request(ProcessType.SUBPROCESS, "Prep"));

        // otherRecipeId is a different recipe than the one source belongs to
        assertThrows(ProcessValidationException.class, () -> service.copy(otherRecipeId, source.getId(), OTHER_USER_ID));
    }

    @Test
    void copy_nonOwner_isRejected() {
        ProcessResponseDTO source = service.create(recipeId, OWNER_ID, request(ProcessType.SUBPROCESS, "Prep"));

        assertThrows(RecipeAccessDeniedException.class, () -> service.copy(recipeId, source.getId(), OTHER_USER_ID));
    }

    private Long seedRecipe(Long ownerId, Visibility visibility) {
        long id = 100 + recipeStore.size();
        RecipeTemplate recipe = new RecipeTemplate();
        recipe.setId(id);
        recipe.setName("Recipe " + id);
        recipe.setCreatedBy(ownerId);
        recipe.setVisibility(visibility);
        recipeStore.put(id, recipe);
        return id;
    }

    private static ProcessRequestDTO request(ProcessType type, String name) {
        ProcessRequestDTO dto = new ProcessRequestDTO();
        dto.setType(type);
        dto.setName(name);
        return dto;
    }

    private static ProcessNodeDTO stepNodeDTO(String id) {
        return ProcessNodeDTO.builder().id(id).kind(ProcessNodeKind.STEP).data(Map.of("action", "chop")).build();
    }
}
