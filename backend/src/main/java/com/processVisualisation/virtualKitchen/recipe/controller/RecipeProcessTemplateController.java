package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessTemplateRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessTemplateResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessTemplateUpdateDTO;
import com.processVisualisation.virtualKitchen.recipe.service.RecipeProcessTemplateService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/process-templates")
public class RecipeProcessTemplateController {

    @Autowired
    private RecipeProcessTemplateService service;

    @PostMapping
    public ApiResponse<RecipeProcessTemplateResponseDTO> create(@RequestBody RecipeProcessTemplateRequestDTO dto){
        return build(service.create(dto), "created");
    }

    @GetMapping("/{id}")
    public ApiResponse<RecipeProcessTemplateResponseDTO> get(@PathVariable Long id){
        return build(service.get(id), "fetched");
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<RecipeProcessTemplateResponseDTO>> getByUser(@PathVariable Long userId){
        return build(service.getByUser(userId), "fetched");
    }

    @PutMapping("/{id}")
    public ApiResponse<RecipeProcessTemplateResponseDTO> update(@PathVariable Long id,
                                                         @RequestBody RecipeProcessTemplateUpdateDTO dto){
        return build(service.update(id, dto), "updated");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id){
        service.delete(id);
        return build(null, "deleted");
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

