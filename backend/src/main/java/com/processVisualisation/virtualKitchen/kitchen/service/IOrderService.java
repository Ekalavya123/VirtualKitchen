package com.processVisualisation.virtualKitchen.kitchen.service;

import com.processVisualisation.virtualKitchen.kitchen.dto.OrderCreateRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.OrderResponseDTO;

import java.util.List;

/**
 * Service contract for placing orders and retrieving a user's order history.
 */
public interface IOrderService {

    /**
     * Creates a new order for the requested items, computing the order total
     * and, on success, applying the ordered items to the user's kitchen
     * inventory.
     *
     * @param dto the ordering user id and the requested line items
     * @return the created order
     * @throws IllegalArgumentException if the user id or items are missing, or
     *                                  the user does not exist
     * @throws IllegalStateException    if the user has no kitchen to receive
     *                                  the ordered inventory
     */
    OrderResponseDTO createOrder(OrderCreateRequestDTO dto);

    /**
     * Fetches all orders placed by the given user, most recently created first.
     *
     * @param userId id of the user
     * @return the user's orders
     */
    List<OrderResponseDTO> getOrdersByUser(Long userId);
}
