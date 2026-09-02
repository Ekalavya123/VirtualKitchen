package com.processVisualisation.virtualKitchen.store.controller;

import com.processVisualisation.virtualKitchen.store.dto.EquipmentRequestDTO;
import com.processVisualisation.virtualKitchen.store.dto.EquipmentResponseDTO;
import com.processVisualisation.virtualKitchen.store.service.IEquipmentService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/equipments")
public class EquipmentController {

    @Autowired
    private IEquipmentService service;

    @PostMapping
    public ApiResponse<EquipmentResponseDTO> create(@RequestBody EquipmentRequestDTO dto){
        return build(service.create(dto), "created");
    }

    @GetMapping
    public ApiResponse<List<EquipmentResponseDTO>> getAll(){
        return build(service.getAll(), "fetched");
    }

    private <T> ApiResponse<T> build(T data, String msg){
        return ApiResponse.<T>builder()
                .success(true)
                .message(msg)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
