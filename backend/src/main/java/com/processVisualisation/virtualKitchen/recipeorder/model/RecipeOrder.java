package com.processVisualisation.virtualKitchen.recipeorder.model;

import com.processVisualisation.virtualKitchen.auth.model.DeliveryAddress;
import com.processVisualisation.virtualKitchen.recipe.model.NutritionInfo;
import com.processVisualisation.virtualKitchen.recipe.model.Process;
import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A customer's order to have one of their kitchen's recipes prepared and delivered.
 * <p>
 * Kept apart from shop {@code Order}s — the two have different lifecycles — and shown alongside
 * them in the customer's order history. Everything the order needs after confirmation is copied
 * onto it (the recipe and its process graph, the delivery address, the ingredient requirements),
 * so later edits to the recipe never change an order already placed. Changes are written with an
 * optimistic {@link #version} check, so two requests can never both move the same order on.
 */
@Data
@Document(collection = "recipe_orders")
public class RecipeOrder {

    public static final String SEQUENCE_NAME = "recipe_orders_sequence";

    @Id
    private Long id;

    /** Human-readable code, e.g. {@code RO-000123} (shop orders use {@code SO-}). */
    private String orderCode;

    @Indexed
    private Long userId;
    private Long kitchenId;
    private String kitchenName;
    private Long recipeId;

    private RecipeOrderStatus status;

    /** Incremented by every write; a write only succeeds against the version it read. */
    private long version;

    private int servings;
    /** The servings the recipe's quantities are written for, or null when the recipe doesn't say. */
    private Integer baseServings;
    /** The factor every recipe quantity is scaled by: servings / baseServings (servings, when there is no base). */
    private double scale;
    private String notes;

    private DeliveryAddress deliveryAddress;

    /** The recipe as confirmed. Null while the order is a draft. */
    private RecipeSnapshot recipeSnapshot;

    private List<IngredientRequirement> requirements = new ArrayList<>();
    /** Ingredients the recipe uses that can't be checked against inventory; any one blocks the order. */
    private List<RequirementIssue> requirementIssues = new ArrayList<>();

    /** The inventory rows (and amounts) held for this order by the current reservation attempt. */
    private List<Allocation> allocations = new ArrayList<>();
    /** Allocations of earlier, released attempts — kept so the history stays complete. */
    private List<Allocation> releasedAllocations = new ArrayList<>();
    private int reservationAttempt;
    private LocalDateTime reservingStartedAt;

    private Pricing pricing;
    private PaymentAttempt payment;
    private List<PaymentAttempt> paymentAttempts = new ArrayList<>();

    /** Index of the recipe step being (simulated as) prepared, while PREPARING. */
    private Integer currentStepIndex;
    private int totalSteps;

    private Timestamps timestamps = new Timestamps();
    private List<OrderEvent> events = new ArrayList<>();

    @Data
    public static class RecipeSnapshot {
        private Long recipeId;
        private String name;
        private String description;
        private String thumbnailUrl;
        private String fallbackIcon;
        private Long ownerId;
        private String ownerName;
        private NutritionInfo nutrition;
        /** Deep copies of every process of the recipe at confirmation. */
        private List<Process> processes = new ArrayList<>();
        private int stepCount;
        private LocalDateTime capturedAt;
    }

    /** How much of one ingredient the order needs, in the ingredient's shop/inventory unit. */
    @Data
    public static class IngredientRequirement {
        private Long ingredientId;
        private String name;
        private String icon;
        private String imageUrl;
        private double requiredQuantity;
        private UnitType unit;
        /** EXACT, INGREDIENT_OVERRIDE or DEFAULT_FACTOR: the least certain conversion behind this figure. */
        private String basis;
        private List<String> conversionNotes = new ArrayList<>();
        /** Where the recipe uses it: one entry per step ingredient line. */
        private List<RequirementSource> sources = new ArrayList<>();
    }

    @Data
    public static class RequirementSource {
        private Long processId;
        private String processName;
        private String nodeId;
        /** As written in the recipe (unscaled); null for "to taste"-style units. */
        private Double quantity;
        private String unit;
        /** That line's contribution, scaled and converted into the requirement's unit. */
        private double convertedQuantity;
    }

    @Data
    public static class RequirementIssue {
        private String ingredientRef;
        private String name;
        private String processName;
        private String nodeId;
        private String message;
    }

    public enum AllocationState { PLANNED, RESERVED, RELEASED, CONSUMED, FAILED }

    /** One hold on one inventory row. The row's {@code appliedOps} is the source of truth for whether it was applied. */
    @Data
    public static class Allocation {
        private Long inventoryId;
        private Long ingredientId;
        private String ingredientName;
        /** In the inventory row's unit. */
        private double quantity;
        private UnitType unit;
        /** The same amount in the requirement's unit — for the usage summary. */
        private double requirementQuantity;
        private UnitType requirementUnit;
        private String reserveKey;
        private String releaseKey;
        private String consumeKey;
        private AllocationState state;
        private String note;
    }

    @Data
    public static class Pricing {
        private double ingredientsCost;
        private double preparationFee;
        private double deliveryFee;
        private double total;
        private String currency;
        /** Demo pricing (configured, not a real tariff). */
        private boolean demo = true;
    }

    public enum PaymentStatus { PAID, FAILED, REFUNDED }

    /** A simulated payment. No card or account data is ever collected or stored. */
    @Data
    public static class PaymentAttempt {
        private String idempotencyKey;
        private String method;
        private PaymentStatus status;
        private String reference;
        private double amount;
        private String currency;
        private LocalDateTime processedAt;
        private String failureReason;
        private boolean simulated = true;
    }

    @Data
    public static class Timestamps {
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime recipeConfirmedAt;
        private LocalDateTime reservedAt;
        private LocalDateTime paidAt;
        private LocalDateTime paymentFailedAt;
        private LocalDateTime preparationStartedAt;
        private LocalDateTime qualityCheckAt;
        private LocalDateTime outForDeliveryAt;
        private LocalDateTime deliveredAt;
        private LocalDateTime completedAt;
        private LocalDateTime cancelledAt;
    }

    @Data
    public static class OrderEvent {
        private String type;
        private RecipeOrderStatus fromStatus;
        private RecipeOrderStatus toStatus;
        private Integer stepIndex;
        private String message;
        /** True for events produced by the prototype's preparation/delivery simulation. */
        private boolean simulated;
        private LocalDateTime at;
    }
}
