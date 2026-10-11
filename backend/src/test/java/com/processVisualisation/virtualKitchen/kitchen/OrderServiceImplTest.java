package com.processVisualisation.virtualKitchen.kitchen;

import com.processVisualisation.virtualKitchen.auth.model.User;
import com.processVisualisation.virtualKitchen.auth.repository.UserRepository;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;
import com.processVisualisation.virtualKitchen.common.mapper.OrderMapper;
import com.processVisualisation.virtualKitchen.kitchen.dto.OrderCreateRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.OrderItemRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.OrderResponseDTO;
import com.processVisualisation.virtualKitchen.kitchen.model.Kitchen;
import com.processVisualisation.virtualKitchen.kitchen.model.Order;
import com.processVisualisation.virtualKitchen.kitchen.repository.KitchenRepository;
import com.processVisualisation.virtualKitchen.kitchen.repository.OrderRepository;
import com.processVisualisation.virtualKitchen.kitchen.service.IInventoryService;
import com.processVisualisation.virtualKitchen.kitchen.service.OrderServiceImpl;
import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import com.processVisualisation.virtualKitchen.store.model.ItemType;
import com.processVisualisation.virtualKitchen.store.units.UnitConversionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Shop checkout: the order is saved and its items added to the kitchen inventory. */
class OrderServiceImplTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final IInventoryService inventory = mock(IInventoryService.class);
    private final OrderServiceImpl service = new OrderServiceImpl();

    @BeforeEach
    void setUp() {
        UserRepository users = mock(UserRepository.class);
        when(users.findById(7L)).thenReturn(Optional.of(new User()));
        KitchenRepository kitchens = mock(KitchenRepository.class);
        Kitchen kitchen = new Kitchen();
        kitchen.setId(70L);
        when(kitchens.findByOwnerId(7L)).thenReturn(List.of(kitchen));
        SequenceGeneratorService sequences = mock(SequenceGeneratorService.class);
        when(sequences.generateSequence(anyString())).thenReturn(45L);
        when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReflectionTestUtils.setField(service, "orderRepository", orders);
        ReflectionTestUtils.setField(service, "orderMapper", new OrderMapper());
        ReflectionTestUtils.setField(service, "sequenceGeneratorService", sequences);
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "inventoryService", inventory);
        ReflectionTestUtils.setField(service, "kitchenRepository", kitchens);
    }

    @Test
    void checkoutSavesTheOrderAndStocksTheKitchen() {
        OrderResponseDTO created = service.createOrder(request());

        assertEquals("SO-000045", created.getOrderCode());
        assertEquals(10.0, created.getTotalAmount(), 1e-9);
        verify(orders).save(any(Order.class));
        verify(inventory, times(1)).addOrUpdate(any());
    }

    @Test
    void aPurchaseWhoseUnitsCannotBeStockedIsRejectedBeforeAnythingIsSaved() {
        doThrow(new UnitConversionException("Unit \"COUNT\" has no conversion defined")).when(inventory).validateAdd(any());

        assertThrows(UnitConversionException.class, () -> service.createOrder(request()));

        verify(orders, never()).save(any());
        verify(inventory, never()).addOrUpdate(any());
    }

    private static OrderCreateRequestDTO request() {
        OrderItemRequestDTO item = new OrderItemRequestDTO();
        item.setItemId(101L);
        item.setItemType(ItemType.INGREDIENT);
        item.setItemName("Flour");
        item.setQuantity(2);
        item.setUnit(UnitType.KG);
        item.setPrice(5);
        OrderCreateRequestDTO dto = new OrderCreateRequestDTO();
        dto.setUserId(7L);
        dto.setItems(List.of(item));
        return dto;
    }
}
