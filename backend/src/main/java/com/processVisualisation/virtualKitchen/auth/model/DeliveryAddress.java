package com.processVisualisation.virtualKitchen.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A delivery address: saved on the {@link User} as their default, and copied onto each recipe
 * order as an immutable snapshot of where that order goes.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryAddress {

    private String recipientName;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String postalCode;
    private String phone;
}
