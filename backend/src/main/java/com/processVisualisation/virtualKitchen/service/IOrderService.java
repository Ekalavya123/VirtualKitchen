package com.processVisualisation.virtualKitchen.service;

import com.processVisualisation.virtualKitchen.dto.OrderCreateRequestDTO;
import com.processVisualisation.virtualKitchen.dto.OrderResponseDTO;

import java.util.List;

public interface IOrderService {

    OrderResponseDTO createOrder(OrderCreateRequestDTO dto);

    List<OrderResponseDTO> getOrdersByUser(Long userId);
}
