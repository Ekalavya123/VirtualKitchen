package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.kitchen.dto.OrderCreateRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.OrderItemRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.OrderItemResponseDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.OrderResponseDTO;
import com.processVisualisation.virtualKitchen.kitchen.model.Order;
import com.processVisualisation.virtualKitchen.kitchen.model.OrderItem;
import com.processVisualisation.virtualKitchen.kitchen.model.OrderStatus;
import com.processVisualisation.virtualKitchen.kitchen.model.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class OrderMapper {

    public Order toEntity(OrderCreateRequestDTO dto) {
        Order order = new Order();
        order.setUserId(dto.getUserId());
        order.setItems(toEntityItems(dto.getItems()));
        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);
        return order;
    }

    public OrderResponseDTO toDTO(Order order) {
        return OrderResponseDTO.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .items(toResponseItems(order.getItems()))
                .totalAmount(order.getTotalAmount())
                .orderStatus(order.getOrderStatus())
                .paymentStatus(order.getPaymentStatus())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private List<OrderItem> toEntityItems(List<OrderItemRequestDTO> items) {
        if (items == null) {
            return Collections.emptyList();
        }

        return items.stream().map(this::toEntityItem).collect(Collectors.toList());
    }

    private OrderItem toEntityItem(OrderItemRequestDTO dto) {
        OrderItem item = new OrderItem();
        item.setItemId(dto.getItemId());
        item.setItemType(dto.getItemType());
        item.setItemName(dto.getItemName());
        item.setQuantity(dto.getQuantity());
        item.setUnit(dto.getUnit());
        item.setPrice(dto.getPrice());
        item.setSubTotal(dto.getPrice() * dto.getQuantity());
        return item;
    }

    private List<OrderItemResponseDTO> toResponseItems(List<OrderItem> items) {
        if (items == null) {
            return Collections.emptyList();
        }

        return items.stream()
                .map(item -> OrderItemResponseDTO.builder()
                        .itemId(item.getItemId())
                        .itemType(item.getItemType())
                        .itemName(item.getItemName())
                        .quantity(item.getQuantity())
                        .unit(item.getUnit())
                        .price(item.getPrice())
                        .subTotal(item.getSubTotal())
                        .build())
                .collect(Collectors.toList());
    }
}
