package com.processVisualisation.virtualKitchen.recipeorder.dto;

import com.processVisualisation.virtualKitchen.auth.model.DeliveryAddress;
import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrderStatus;

/**
 * Request bodies of the recipe-order API. None carries a user id, status, amount or payment
 * result: identity comes from the authenticated session and every state change is decided by the
 * server.
 */
public final class RecipeOrderRequests {

    private RecipeOrderRequests() {
    }

    /** Starts a draft order for one of the caller's recipes. */
    public record Create(Long recipeId, Integer servings, DeliveryAddress deliveryAddress, String notes) {}

    /** Changes draft details; omitted fields stay as they are. */
    public record Update(Integer servings, DeliveryAddress deliveryAddress, String notes) {}

    /** Confirms the recipe snapshot and order details. */
    public record Confirm(boolean saveAddressAsDefault) {}

    /**
     * Runs the simulated payment. {@code method} is one of the demo options
     * ({@code DEMO_APPROVE}, {@code DEMO_DECLINE}); {@code idempotencyKey} identifies this attempt
     * so a retried request returns the original result instead of paying twice.
     */
    public record Pay(String method, String idempotencyKey) {}

    /**
     * Moves the simulation one stage on. The expected status/step is what the client last showed;
     * if the order has moved on since (a double click, a second tab) the request is rejected
     * instead of skipping a stage.
     */
    public record Advance(RecipeOrderStatus expectedStatus, Integer expectedStepIndex) {}
}
