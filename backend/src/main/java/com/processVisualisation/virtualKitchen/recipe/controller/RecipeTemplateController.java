package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateUpdateDTO;
import com.processVisualisation.virtualKitchen.recipe.model.Visibility;
import com.processVisualisation.virtualKitchen.recipe.service.IProcessTemplateService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/process-templates")
public class RecipeTemplateController {

    @Autowired
    private IProcessTemplateService service;

    @PostMapping
    public ApiResponse<RecipeTemplateResponseDTO> create(@RequestBody RecipeTemplateRequestDTO dto){
        return build(service.create(dto), "created");
    }

    @GetMapping("/{id}")
    public ApiResponse<RecipeTemplateResponseDTO> get(@PathVariable Long id){
        return build(service.get(id), "fetched");
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<RecipeTemplateResponseDTO>> getByUser(@PathVariable Long userId){
        return build(service.getByUser(userId), "fetched");
    }

    @GetMapping("/global/{userId}")
    public ApiResponse<List<RecipeTemplateResponseDTO>> getGlobalRecipes(@PathVariable Long userId){
        return build(service.getGlobalRecipes(userId), "fetched");
    }

    @PutMapping("/{id}")
    public ApiResponse<RecipeTemplateResponseDTO> update(@PathVariable Long id,
                                                         @RequestParam Long userId,
                                                         @RequestBody RecipeTemplateUpdateDTO dto){
        return build(service.update(id, userId, dto), "updated");
    }

    @PutMapping("/{id}/visibility")
    public ApiResponse<RecipeTemplateResponseDTO> updateVisibility(@PathVariable Long id,
                                                                   @RequestParam Long userId,
                                                                   @RequestParam Visibility visibility){
        return build(service.updateVisibility(id, userId, visibility), "updated");
    }

    @PostMapping("/{id}/copy")
    public ApiResponse<RecipeTemplateResponseDTO> copy(@PathVariable Long id,
                                                       @RequestParam Long userId){
        return build(service.copyToUser(id, userId), "created");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam Long userId){
        service.delete(id, userId);
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
