package com.processVisualisation.virtualKitchen.kitchen.service;

import com.processVisualisation.virtualKitchen.auth.event.UserRegisteredEvent;
import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenRequestDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DefaultKitchenProvisionerTest {

    @Test
    void newUserGetsADefaultKitchenTheyOwn() {
        KitchenService kitchenService = mock(KitchenService.class);

        new DefaultKitchenProvisioner(kitchenService).onUserRegistered(new UserRegisteredEvent(42L, "Asha"));

        ArgumentCaptor<KitchenRequestDTO> request = ArgumentCaptor.forClass(KitchenRequestDTO.class);
        verify(kitchenService).create(request.capture());
        assertThat(request.getValue().getName()).isEqualTo("Asha's Virtual Kitchen");
        assertThat(request.getValue().getOwnerId()).isEqualTo(42L);
    }
}
