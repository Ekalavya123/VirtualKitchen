package com.processVisualisation.virtualKitchen.ai.recipe.edit.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Something an edited step may act on, named so the model can recognise it: a Preparation step's output
 * ({@code processId} + {@code stepId}), or a whole process (older recipes).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EditSubprocessRefDTO {
    private Long processId;
    /** A step of that process whose output this is (a Preparation flow's output); null for the whole process. */
    private String stepId;
    private String name;

    /** A whole process. */
    public EditSubprocessRefDTO(Long processId, String name) {
        this(processId, null, name);
    }
}
