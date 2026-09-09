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

/**
 * Centralizes conversion between the {@link Order}/{@link OrderItem}
 * entities and their {@link OrderCreateRequestDTO}/{@link OrderResponseDTO}
 * (with nested {@link OrderItemRequestDTO}/{@link OrderItemResponseDTO})
 * representations.
 */
@Component
public class OrderMapper {

    /**
     * Converts an incoming order-creation request DTO into a new
     * {@link Order} entity. This mapping is lossy/derived rather than a
     * straight copy: {@code orderStatus} is always hardcoded to
     * {@link OrderStatus#CONFIRMED} and {@code paymentStatus} to
     * {@link PaymentStatus#PAID} regardless of the DTO's contents, and
     * {@code totalAmount}/{@code id}/{@code createdAt} are left unset for
     * the persistence layer to populate.
     *
     * @param dto the request payload describing the order to create
     * @return a new, unpersisted {@link Order} entity populated from {@code dto}
     */
    public Order toEntity(OrderCreateRequestDTO dto) {
        Order order = new Order();
        order.setUserId(dto.getUserId());
        order.setItems(toEntityItems(dto.getItems()));
        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);
        return order;
    }

    /**
     * Converts an {@link Order} entity (including its line items) into its
     * response DTO representation for returning to clients.
     *
     * @param order the entity to convert
     * @return a fully populated {@link OrderResponseDTO}
     */
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

    /**
     * Converts a list of request-side order item DTOs into entity items,
     * via {@link #toEntityItem}.
     *
     * @param items the request line items, may be {@code null}
     * @return the converted {@link OrderItem} list, or an empty list if {@code items} is {@code null}
     */
    private List<OrderItem> toEntityItems(List<OrderItemRequestDTO> items) {
        if (items == null) {
            return Collections.emptyList();
        }

        return items.stream().map(this::toEntityItem).collect(Collectors.toList());
    }

    /**
     * Converts a single request-side order item DTO into an {@link OrderItem}
     * entity. {@code subTotal} is not copied from the DTO — it is derived as
     * {@code price * quantity}.
     *
     * @param dto the request line item to convert
     * @return a new {@link OrderItem} entity populated from {@code dto}, with a computed {@code subTotal}
     */
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

    /**
     * Converts a list of {@link OrderItem} entities into their response DTO
     * representation.
     *
     * @param items the line item entities, may be {@code null}
     * @return the converted {@link OrderItemResponseDTO} list, or an empty list if {@code items} is {@code null}
     */
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
