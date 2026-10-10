package com.processVisualisation.virtualKitchen.recipeorder;

import com.processVisualisation.virtualKitchen.common.mapper.ProcessMapper;
import com.processVisualisation.virtualKitchen.recipeorder.dto.RecipeOrderResponseDTO;
import com.processVisualisation.virtualKitchen.recipeorder.dto.RecipeOrderResponseDTO.AllocationView;
import com.processVisualisation.virtualKitchen.recipeorder.dto.RecipeOrderResponseDTO.UsageLine;
import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrder;
import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrder.Allocation;
import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrder.AllocationState;
import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrderStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrderStatus.*;

@Component
public class RecipeOrderMapper {

    private final ProcessMapper processMapper;
    private final RecipeOrderProperties properties;

    public RecipeOrderMapper(ProcessMapper processMapper, RecipeOrderProperties properties) {
        this.processMapper = processMapper;
        this.properties = properties;
    }

    /** Full detail, including the confirmed process graphs. */
    public RecipeOrderResponseDTO toDTO(RecipeOrder order) {
        return toDTO(order, true);
    }

    /** For order lists: everything but the process graphs. */
    public RecipeOrderResponseDTO toSummaryDTO(RecipeOrder order) {
        return toDTO(order, false);
    }

    private RecipeOrderResponseDTO toDTO(RecipeOrder order, boolean withProcesses) {
        RecipeOrder.RecipeSnapshot snapshot = order.getRecipeSnapshot();
        RecipeOrderResponseDTO.RecipeView recipe = snapshot == null ? null : RecipeOrderResponseDTO.RecipeView.builder()
                .recipeId(snapshot.getRecipeId())
                .name(snapshot.getName())
                .description(snapshot.getDescription())
                .thumbnailUrl(snapshot.getThumbnailUrl())
                .fallbackIcon(snapshot.getFallbackIcon())
                .ownerId(snapshot.getOwnerId())
                .ownerName(snapshot.getOwnerName())
                .nutrition(snapshot.getNutrition())
                .stepCount(snapshot.getStepCount())
                .capturedAt(snapshot.getCapturedAt())
                .processes(withProcesses && snapshot.getProcesses() != null
                        ? snapshot.getProcesses().stream().map(processMapper::toDTO).toList()
                        : null)
                .build();

        List<Allocation> visible = new ArrayList<>(order.getAllocations());
        return RecipeOrderResponseDTO.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .status(order.getStatus())
                .kitchenId(order.getKitchenId())
                .kitchenName(order.getKitchenName())
                .recipeId(order.getRecipeId())
                .servings(order.getServings())
                .baseServings(order.getBaseServings())
                .scale(order.getScale())
                .notes(order.getNotes())
                .deliveryAddress(order.getDeliveryAddress())
                .recipe(recipe)
                .requirements(order.getRequirements())
                .requirementIssues(order.getRequirementIssues())
                .allocations(visible.stream().map(RecipeOrderMapper::view).toList())
                .ingredientUsage(order.getStatus() == COMPLETED ? usage(visible) : null)
                .pricing(order.getPricing())
                .payment(order.getPayment())
                .paymentAttempts(order.getPaymentAttempts())
                .currentStepIndex(order.getCurrentStepIndex())
                .totalSteps(order.getTotalSteps())
                .timestamps(order.getTimestamps())
                .events(order.getEvents())
                .allowedActions(allowedActions(order))
                .demo(RecipeOrderResponseDTO.DemoInfo.builder()
                        .estimatedPreparationMinutes(order.getTotalSteps() * properties.getDemoMinutesPerStep()
                                + properties.getDemoQualityCheckMinutes())
                        .estimatedDeliveryMinutes(properties.getDemoDeliveryMinutes())
                        .snapshotRequests(properties.getDemoSnapshotRequests())
                        .build())
                .build();
    }

    private static AllocationView view(Allocation allocation) {
        return AllocationView.builder()
                .inventoryId(allocation.getInventoryId())
                .ingredientId(allocation.getIngredientId())
                .ingredientName(allocation.getIngredientName())
                .quantity(allocation.getQuantity())
                .unit(allocation.getUnit())
                .requirementQuantity(allocation.getRequirementQuantity())
                .requirementUnit(allocation.getRequirementUnit())
                .state(allocation.getState())
                .note(allocation.getNote())
                .build();
    }

    private static List<UsageLine> usage(List<Allocation> allocations) {
        Map<Long, UsageLine> byIngredient = new LinkedHashMap<>();
        for (Allocation allocation : allocations) {
            if (allocation.getState() != AllocationState.CONSUMED) continue;
            UsageLine line = byIngredient.computeIfAbsent(allocation.getIngredientId(), id -> UsageLine.builder()
                    .ingredientId(id)
                    .ingredientName(allocation.getIngredientName())
                    .unit(allocation.getRequirementUnit())
                    .build());
            line.setQuantity(RecipeRequirementCalculator.round(line.getQuantity() + allocation.getRequirementQuantity()));
        }
        return new ArrayList<>(byIngredient.values());
    }

    static List<String> allowedActions(RecipeOrder order) {
        RecipeOrderStatus status = order.getStatus();
        List<String> actions = new ArrayList<>();
        if (RecipeOrderLifecycle.EDITABLE.contains(status)) actions.add("EDIT");
        if (RecipeOrderLifecycle.CONFIRMABLE.contains(status)) actions.add("CONFIRM");
        if (RecipeOrderLifecycle.RESERVABLE.contains(status) && order.getRequirementIssues().isEmpty()) actions.add("RESERVE");
        if (status == AWAITING_PAYMENT) actions.add("PAY");
        if (RecipeOrderLifecycle.ADVANCEABLE.contains(status)) actions.add("ADVANCE");
        if (RecipeOrderLifecycle.CANCELLABLE.contains(status)) actions.add("CANCEL");
        return actions;
    }
}
