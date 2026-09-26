package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A {@code Process.ProcessNode}'s last-rendered width/height. Pure
 * presentation data, carried through unchanged so the frontend can restore
 * node layout.
 *
 * <p>{@code @NoArgsConstructor}/{@code @AllArgsConstructor} are required
 * alongside {@code @Builder} so Jackson can deserialize this nested object —
 * see {@link ProcessNodeDTO} for why.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessMeasuredDTO {
    private Double width;
    private Double height;
}
