package com.processVisualisation.virtualKitchen.recipeorder.dto;

import com.processVisualisation.virtualKitchen.auth.model.DeliveryAddress;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.model.NutritionInfo;
import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrder;
import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrderStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A recipe order as the customer sees it. Inventory operation keys and other internals are left out.
 */
@Data
@Builder
public class RecipeOrderResponseDTO {

    private Long id;
    private String orderCode;
    private RecipeOrderStatus status;
    private Long kitchenId;
    private String kitchenName;
    private Long recipeId;
    private int servings;
    private Integer baseServings;
    private double scale;
    private String notes;
    private DeliveryAddress deliveryAddress;
    private RecipeView recipe;
    private List<RecipeOrder.IngredientRequirement> requirements;
    private List<RecipeOrder.RequirementIssue> requirementIssues;
    /** What this order holds now (reserved) or used (consumed). */
    private List<AllocationView> allocations;
    /** Per-ingredient totals actually consumed — set once the order is completed. */
    private List<UsageLine> ingredientUsage;
    private RecipeOrder.Pricing pricing;
    private RecipeOrder.PaymentAttempt payment;
    private List<RecipeOrder.PaymentAttempt> paymentAttempts;
    private Integer currentStepIndex;
    private int totalSteps;
    private RecipeOrder.Timestamps timestamps;
    private List<RecipeOrder.OrderEvent> events;
    /** What the customer can do next: EDIT, CONFIRM, RESERVE, PAY, ADVANCE, CANCEL. */
    private List<String> allowedActions;
    private DemoInfo demo;

    @Data
    @Builder
    public static class RecipeView {
        private Long recipeId;
        private String name;
        private String description;
        private String thumbnailUrl;
        private String fallbackIcon;
        private Long ownerId;
        private String ownerName;
        private NutritionInfo nutrition;
        private int stepCount;
        private LocalDateTime capturedAt;
        /** The process graphs as confirmed (same shape as the Process API). Omitted from list responses. */
        private List<ProcessResponseDTO> processes;
    }

    @Data
    @Builder
    public static class AllocationView {
        private Long inventoryId;
        private Long ingredientId;
        private String ingredientName;
        private double quantity;
        private UnitType unit;
        private double requirementQuantity;
        private UnitType requirementUnit;
        private RecipeOrder.AllocationState state;
        private String note;
    }

    @Data
    @Builder
    public static class UsageLine {
        private Long ingredientId;
        private String ingredientName;
        private double quantity;
        private UnitType unit;
    }

    /** Illustrative values for the prototype UI, labelled as such. */
    @Data
    @Builder
    public static class DemoInfo {
        private int estimatedPreparationMinutes;
        private int estimatedDeliveryMinutes;
        private int snapshotRequests;
    }
}
