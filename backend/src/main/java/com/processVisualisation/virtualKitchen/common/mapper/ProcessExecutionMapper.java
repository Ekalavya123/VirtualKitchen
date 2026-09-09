package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeExecution;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeStatus;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeExecutionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeExecutionResponseDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Centralizes conversion between the {@link RecipeExecution} entity (a
 * single run of a recipe template) and its
 * {@link RecipeExecutionRequestDTO}/{@link RecipeExecutionResponseDTO}
 * representations.
 */
@Component
public class ProcessExecutionMapper {

    /**
     * Converts an incoming request DTO into a new {@link RecipeExecution}
     * entity. Lossy/derived fields: {@code status} is always hardcoded to
     * {@link RecipeStatus#NOT_STARTED} and {@code startedAt} is set to the
     * current time — neither is read from the DTO. {@code completedAt} and
     * {@code generatedMediaUrl} are left unset here, since they are only
     * populated later by the execution logic and appear only in the response.
     *
     * @param dto the request payload describing the recipe execution to create
     * @return a new, unpersisted {@link RecipeExecution} entity populated from {@code dto}
     */
    public RecipeExecution toEntity(RecipeExecutionRequestDTO dto){
        RecipeExecution pe = new RecipeExecution();
        pe.setProcessTemplateId(dto.getProcessTemplateId());
        pe.setUserId(dto.getUserId());
        pe.setKitchenId(dto.getKitchenId());
        pe.setStatus(RecipeStatus.NOT_STARTED);
        pe.setStartedAt(LocalDateTime.now());
        return pe;
    }

    /**
     * Converts a {@link RecipeExecution} entity into its response DTO
     * representation for returning to clients.
     *
     * @param pe the entity to convert
     * @return a fully populated {@link RecipeExecutionResponseDTO}
     */
    public RecipeExecutionResponseDTO toDTO(RecipeExecution pe){
        return RecipeExecutionResponseDTO.builder()
                .id(pe.getId())
                .processTemplateId(pe.getProcessTemplateId())
                .userId(pe.getUserId())
                .kitchenId(pe.getKitchenId())
                .status(pe.getStatus())
                .startedAt(pe.getStartedAt())
                .completedAt(pe.getCompletedAt())
                .generatedMediaUrl(pe.getGeneratedMediaUrl())
                .build();
    }
}
