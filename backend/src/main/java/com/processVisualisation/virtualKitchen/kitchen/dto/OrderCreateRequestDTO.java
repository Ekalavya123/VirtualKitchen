package com.processVisualisation.virtualKitchen.kitchen.dto;

import lombok.Data;

import java.util.List;

/**
 * Client payload for placing a new order, carrying the requesting user's id and
 * the list of requested {@code OrderItemRequestDTO} line items.
 */
@Data
public class OrderCreateRequestDTO {

    private Long userId;
    private List<OrderItemRequestDTO> items;
}
