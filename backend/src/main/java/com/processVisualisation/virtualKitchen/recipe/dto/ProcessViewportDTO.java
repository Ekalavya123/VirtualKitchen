package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A {@code Process}'s own canvas pan/zoom state. Pure presentation data,
 * carried through unchanged so the frontend can restore the view.
 *
 * <p>{@code @NoArgsConstructor}/{@code @AllArgsConstructor} are required
 * alongside {@code @Builder} so Jackson can deserialize this nested object —
 * see {@link ProcessNodeDTO} for why.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessViewportDTO {
    private Double x;
    private Double y;
    private Double zoom;
}
