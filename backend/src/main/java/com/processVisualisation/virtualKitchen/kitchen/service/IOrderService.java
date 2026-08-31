package com.processVisualisation.virtualKitchen.kitchen.service;

import com.processVisualisation.virtualKitchen.kitchen.dto.OrderCreateRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.OrderResponseDTO;

import java.util.List;

public interface IOrderService {

    OrderResponseDTO createOrder(OrderCreateRequestDTO dto);

    List<OrderResponseDTO> getOrdersByUser(Long userId);
}
