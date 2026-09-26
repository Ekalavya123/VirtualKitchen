package com.processVisualisation.virtualKitchen.ai.routing;

import com.processVisualisation.virtualKitchen.ai.credit.CreditReservation;
import com.processVisualisation.virtualKitchen.ai.credit.CreditService;
import com.processVisualisation.virtualKitchen.ai.registry.AiCapability;
import com.processVisualisation.virtualKitchen.ai.registry.AiModelRegistry;
import com.processVisualisation.virtualKitchen.ai.registry.ModelDefinition;
import com.processVisualisation.virtualKitchen.ai.registry.ModelTier;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Decides which model handles an AI request and, for a {@code PAID} model,
 * reserves the credits for it — the only place fallback logic lives. Neither
 * {@code RecipeProcessGenerationService} nor {@code RecipeProcessVisualizationService}
 * ever reference a provider name, tier, or credit cost directly; they call
 * {@link #select} and act on the returned {@link ModelSelectionOutcome}.
 */
@Service
public class ModelSelectionService {

    private final AiModelRegistry registry;
    private final CreditService creditService;

    public ModelSelectionService(AiModelRegistry registry, CreditService creditService) {
        this.registry = registry;
        this.creditService = creditService;
    }

    /**
     * @param preferredModelKey a specific model the caller asked for, or {@code null} to use the
     *                          configured default for {@code capability}
     * @throws NoAvailableModelException if the resolved model needs credits the user doesn't have
     *         and no enabled fallback is configured
     */
    public ModelSelectionOutcome select(Long userId, AiCapability capability, String preferredModelKey) {
        ModelDefinition candidate = resolveCandidate(capability, preferredModelKey);

        if (candidate.getTier() == ModelTier.OPEN_SOURCE) {
            return new ModelSelectionOutcome(candidate, false, FallbackReason.NONE, null);
        }

        Optional<CreditReservation> reservation =
                creditService.reserve(userId, candidate.getCreditCost(), capability, candidate.getKey());

        if (reservation.isPresent()) {
            return new ModelSelectionOutcome(candidate, false, FallbackReason.NONE, reservation.get());
        }

        Optional<ModelDefinition> fallback = registry.fallbackFor(candidate);
        if (fallback.isPresent()) {
            return new ModelSelectionOutcome(fallback.get(), true, FallbackReason.INSUFFICIENT_CREDITS, null);
        }

        throw new NoAvailableModelException(capability);
    }

    private ModelDefinition resolveCandidate(AiCapability capability, String preferredModelKey) {
        if (preferredModelKey != null) {
            Optional<ModelDefinition> preferred = registry.find(preferredModelKey).filter(ModelDefinition::isEnabled);
            if (preferred.isPresent()) {
                return preferred.get();
            }
        }
        return registry.defaultFor(capability);
    }
}
