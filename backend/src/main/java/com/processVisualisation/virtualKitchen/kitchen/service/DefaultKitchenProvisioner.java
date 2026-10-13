package com.processVisualisation.virtualKitchen.kitchen.service;

import com.processVisualisation.virtualKitchen.auth.event.UserRegisteredEvent;
import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenRequestDTO;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Gives every new user a default kitchen they own. Runs synchronously inside the signup request, so a failure
 * here fails the signup just as it did when auth created the kitchen itself.
 */
@Component
public class DefaultKitchenProvisioner {

    private final KitchenService kitchenService;

    public DefaultKitchenProvisioner(KitchenService kitchenService) {
        this.kitchenService = kitchenService;
    }

    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        KitchenRequestDTO kitchenRequest = new KitchenRequestDTO();
        kitchenRequest.setName(event.name() + "'s Virtual Kitchen");
        kitchenRequest.setOwnerId(event.userId());
        kitchenService.create(kitchenRequest);
    }
}
