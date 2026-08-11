package com.processVisualisation.virtualKitchen.dto;

import lombok.Data;

import java.util.List;

@Data
public class OrderCreateRequestDTO {

    private Long userId;
    private List<OrderItemRequestDTO> items;
}
