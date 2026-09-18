package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One AI-generated STEP or CONDITION within a {@link GeneratedProcessDTO} —
 * semantic fields only (see {@code ProcessStepFields} on the frontend for
 * the equivalent shape once converted). No node id, position, dimensions, or
 * any other React Flow presentation field: the application generates those
 * when converting this into the Process working snapshot.
 * <p>
 * {@code action}/{@code actionOn}/{@code temperature}/{@code flameLevel}/
 * {@code duration} apply to a STEP; {@code title}/{@code expectedResult}
 * apply to a CONDITION. Both node kinds carry {@code actionDescription} and
 * {@code expectedOutput} — the core, always-shown Step summary fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedProcessStepDTO {

    /** "STEP" or "CONDITION" — the only two node kinds a Process graph supports. */
    private String nodeType;

    // --- STEP-only fields ---
    private String action;
    private GeneratedActionOnDTO actionOn;
    private String temperature;
    private String flameLevel;
    private String duration;

    // --- CONDITION-only fields ---
    private String title;
    /** "success" or "failure". */
    private String expectedResult;

    // --- shared core fields (both STEP and CONDITION) ---
    private String actionDescription;
    private String expectedOutput;
}
