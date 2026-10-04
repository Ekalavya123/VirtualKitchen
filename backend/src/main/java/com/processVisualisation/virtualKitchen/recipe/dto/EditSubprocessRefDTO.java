package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Another process of the recipe that an edited step may act on (by id), named so the model can recognise it. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EditSubprocessRefDTO {
    private Long processId;
    private String name;
}
