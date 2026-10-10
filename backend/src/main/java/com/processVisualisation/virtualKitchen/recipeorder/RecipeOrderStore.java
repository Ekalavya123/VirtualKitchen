package com.processVisualisation.virtualKitchen.recipeorder;

import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrder;

import java.util.List;
import java.util.Optional;

/** Persistence for {@link RecipeOrder}s, with compare-and-set writes on {@link RecipeOrder#getVersion()}. */
public interface RecipeOrderStore {

    RecipeOrder insert(RecipeOrder order);

    Optional<RecipeOrder> findById(Long id);

    /** The user's orders, newest first. */
    List<RecipeOrder> findByUserId(Long userId);

    /**
     * Writes {@code order} only if the stored copy is still at {@code order.getVersion()}; on
     * success the order's version is incremented.
     *
     * @return false when another request changed the order first (nothing is written)
     */
    boolean replaceIfVersion(RecipeOrder order);
}
