package com.processVisualisation.virtualKitchen.kitchen.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KitchenUpdateDTO {

    @NotBlank
    private String name;
}
