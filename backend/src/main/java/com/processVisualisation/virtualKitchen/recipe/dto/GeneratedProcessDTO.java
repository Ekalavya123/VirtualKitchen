package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One AI-generated process (the MAIN process, or one SUBPROCESS) within a
 * {@link ProcessGenerationResultDTO}. {@code ref} is a temporary,
 * generation-scoped identifier a STEP's {@code actionOn.processes} can point
 * to (absent/null for the MAIN process, which nothing can reference) — not a
 * database id. The frontend resolves every ref once it assigns real or
 * client-temporary process ids while building the Recipe working snapshot.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedProcessDTO {

    /** Null for the MAIN process; a short unique slug (e.g. "marinate_chicken") for a subprocess. */
    private String ref;

    private String name;

    private List<GeneratedProcessStepDTO> steps;
}
