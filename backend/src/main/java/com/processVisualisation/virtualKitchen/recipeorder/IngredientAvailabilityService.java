package com.processVisualisation.virtualKitchen.recipeorder;

import com.processVisualisation.virtualKitchen.kitchen.reservation.InventoryReservationStore;
import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrder;
import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrder.Allocation;
import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrder.AllocationState;
import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrder.IngredientRequirement;
import com.processVisualisation.virtualKitchen.store.model.Ingredient;
import com.processVisualisation.virtualKitchen.store.model.Inventory;
import com.processVisualisation.virtualKitchen.store.repository.IngredientRepository;
import com.processVisualisation.virtualKitchen.store.units.UnitConversionException;
import com.processVisualisation.virtualKitchen.store.units.UnitConversionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Compares a recipe order's ingredient requirements with the kitchen's inventory. Read-only: it
 * never changes stock. The same comparison produces the plan of which inventory rows a reservation
 * will hold; the reservation itself is applied by {@link RecipeOrderService} through
 * {@link InventoryReservationStore}, whose atomic updates re-check every amount.
 */
@Service
public class IngredientAvailabilityService {

    private final InventoryReservationStore inventory;
    private final IngredientRepository ingredientRepository;
    private final UnitConversionService conversions;

    public IngredientAvailabilityService(
            InventoryReservationStore inventory,
            IngredientRepository ingredientRepository,
            UnitConversionService conversions
    ) {
        this.inventory = inventory;
        this.ingredientRepository = ingredientRepository;
        this.conversions = conversions;
    }

    /** What the shop sells to cover a shortage: whole units of the ingredient's shop unit. */
    public record PurchaseSuggestion(Long ingredientId, String name, double quantity, UnitType unit,
                                     double neededQuantity, UnitType neededUnit) {}

    public record AvailabilityLine(
            Long ingredientId, String name, String icon, String imageUrl, UnitType unit,
            double required, double available, double missing, boolean sufficient,
            /** Held for this order (once reserved). */
            double reservedForOrder,
            String basis, List<String> conversionNotes, PurchaseSuggestion purchase
    ) {}

    public record AvailabilityReport(List<AvailabilityLine> lines, List<RecipeOrder.RequirementIssue> issues,
                                     boolean allAvailable, boolean canReserve) {}

    /** Free stock per requirement, read now — no writes. */
    public AvailabilityReport check(RecipeOrder order) {
        Context context = load(order);
        List<AvailabilityLine> lines = new ArrayList<>();
        for (IngredientRequirement requirement : order.getRequirements()) {
            Ingredient ingredient = context.ingredients().get(requirement.getIngredientId());
            double available = 0;
            for (Inventory row : context.rowsFor(requirement.getIngredientId())) {
                available += freeIn(row, requirement.getUnit(), ingredient);
            }
            double reservedForOrder = order.getAllocations().stream()
                    .filter(allocation -> allocation.getState() == AllocationState.RESERVED
                            && requirement.getIngredientId().equals(allocation.getIngredientId()))
                    .mapToDouble(Allocation::getRequirementQuantity)
                    .sum();
            double required = requirement.getRequiredQuantity();
            double missing = Math.max(0, required - reservedForOrder - available);
            if (missing < InventoryReservationStore.EPSILON) missing = 0;
            lines.add(new AvailabilityLine(
                    requirement.getIngredientId(), requirement.getName(), requirement.getIcon(), requirement.getImageUrl(),
                    requirement.getUnit(), required, RecipeRequirementCalculator.round(available),
                    RecipeRequirementCalculator.round(missing), missing == 0,
                    RecipeRequirementCalculator.round(reservedForOrder),
                    requirement.getBasis(), requirement.getConversionNotes(),
                    missing > 0 ? purchaseFor(requirement, ingredient, missing) : null));
        }
        boolean allAvailable = lines.stream().allMatch(AvailabilityLine::sufficient);
        return new AvailabilityReport(lines, order.getRequirementIssues(),
                allAvailable, allAvailable && order.getRequirementIssues().isEmpty());
    }

    /** The plan for one reservation attempt: which rows to hold and how much, or the shortages if stock is short. */
    public record AllocationPlan(List<Allocation> allocations, List<AvailabilityLine> shortages) {
        public boolean feasible() {
            return shortages.isEmpty();
        }
    }

    public AllocationPlan plan(RecipeOrder order, int attempt) {
        Context context = load(order);
        List<Allocation> allocations = new ArrayList<>();
        List<AvailabilityLine> shortages = new ArrayList<>();
        int index = 0;
        for (IngredientRequirement requirement : order.getRequirements()) {
            Ingredient ingredient = context.ingredients().get(requirement.getIngredientId());
            double needed = requirement.getRequiredQuantity();
            for (Inventory row : context.rowsFor(requirement.getIngredientId())) {
                if (needed <= InventoryReservationStore.EPSILON) break;
                double free = freeIn(row, requirement.getUnit(), ingredient);
                if (free <= InventoryReservationStore.EPSILON) continue;
                double take = Math.min(needed, free);
                double inRowUnit = convert(take, requirement.getUnit(), row.getUnit(), ingredient);
                allocations.add(allocation(order, attempt, index++, row, requirement, inRowUnit, take));
                needed -= take;
            }
            if (needed > InventoryReservationStore.EPSILON) {
                double missing = RecipeRequirementCalculator.round(needed);
                shortages.add(new AvailabilityLine(requirement.getIngredientId(), requirement.getName(), requirement.getIcon(),
                        requirement.getImageUrl(), requirement.getUnit(), requirement.getRequiredQuantity(),
                        RecipeRequirementCalculator.round(requirement.getRequiredQuantity() - needed), missing, false, 0,
                        requirement.getBasis(), requirement.getConversionNotes(), purchaseFor(requirement, ingredient, missing)));
            }
        }
        return new AllocationPlan(allocations, shortages);
    }

    private static Allocation allocation(RecipeOrder order, int attempt, int index, Inventory row,
                                         IngredientRequirement requirement, double quantity, double requirementQuantity) {
        String prefix = "ro:" + order.getId() + ":a" + attempt + ":" + index + ":";
        Allocation allocation = new Allocation();
        allocation.setInventoryId(row.getId());
        allocation.setIngredientId(requirement.getIngredientId());
        allocation.setIngredientName(requirement.getName());
        allocation.setQuantity(RecipeRequirementCalculator.round(quantity));
        allocation.setUnit(row.getUnit());
        allocation.setRequirementQuantity(RecipeRequirementCalculator.round(requirementQuantity));
        allocation.setRequirementUnit(requirement.getUnit());
        allocation.setReserveKey(prefix + "reserve");
        allocation.setReleaseKey(prefix + "release");
        allocation.setConsumeKey(prefix + "consume");
        allocation.setState(AllocationState.PLANNED);
        return allocation;
    }

    private PurchaseSuggestion purchaseFor(IngredientRequirement requirement, Ingredient ingredient, double missing) {
        UnitType shopUnit = ingredient != null && ingredient.getDefaultUnit() != null ? ingredient.getDefaultUnit() : requirement.getUnit();
        double inShopUnit = convert(missing, requirement.getUnit(), shopUnit, ingredient);
        // The shop sells whole units of its unit (quantity step 1), so round the shortage up.
        double quantity = Math.max(1, Math.ceil(inShopUnit - 1e-9));
        return new PurchaseSuggestion(requirement.getIngredientId(), requirement.getName(), quantity, shopUnit,
                RecipeRequirementCalculator.round(missing), requirement.getUnit());
    }

    private double freeIn(Inventory row, UnitType unit, Ingredient ingredient) {
        double free = row.getQuantity() - row.getReservedQuantity();
        if (free <= 0) return 0;
        try {
            return convert(free, row.getUnit(), unit, ingredient);
        } catch (UnitConversionException e) {
            return 0; // a row whose unit can't be compared is not counted as available
        }
    }

    private double convert(double quantity, UnitType from, UnitType to, Ingredient ingredient) {
        if (from == null || to == null || from == to) return quantity;
        return conversions.convert(quantity, from.name(), to, ingredient).value();
    }

    private record Context(Map<Long, Ingredient> ingredients, Map<Long, List<Inventory>> rows) {
        List<Inventory> rowsFor(Long ingredientId) {
            return rows.getOrDefault(ingredientId, List.of());
        }
    }

    private Context load(RecipeOrder order) {
        List<Long> ids = order.getRequirements().stream().map(IngredientRequirement::getIngredientId).toList();
        Map<Long, Ingredient> ingredients = new HashMap<>();
        if (!ids.isEmpty()) {
            ingredientRepository.findAllById(ids).forEach(ingredient -> ingredients.put(ingredient.getId(), ingredient));
        }
        // Kitchen rows first, then the owner's legacy rows; oldest row first within each.
        Map<Long, List<Inventory>> rows = inventory.findIngredientRows(order.getKitchenId(), order.getUserId(), ids).stream()
                .sorted(Comparator.comparing((Inventory row) -> row.getKitchenId() == null ? 1 : 0).thenComparing(Inventory::getId))
                .collect(Collectors.groupingBy(Inventory::getItemId, Collectors.toList()));
        return new Context(ingredients, rows);
    }
}
