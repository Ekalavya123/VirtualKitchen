package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.store.model.Equipment;
import com.processVisualisation.virtualKitchen.store.dto.EquipmentRequestDTO;
import com.processVisualisation.virtualKitchen.store.dto.EquipmentResponseDTO;
import org.springframework.stereotype.Component;

/**
 * Centralizes conversion between the {@link Equipment} entity and its
 * {@link EquipmentRequestDTO}/{@link EquipmentResponseDTO} representations.
 */
@Component
public class EquipmentMapper {

    /**
     * Converts an incoming request DTO into a new {@link Equipment} entity.
     * The entity's {@code id}, {@code createdAt} and {@code updatedAt} are
     * left unset, since they are assigned at persistence time.
     *
     * @param dto the request payload describing the equipment to create
     * @return a new, unpersisted {@link Equipment} entity populated from {@code dto}
     */
    public Equipment toEntity(EquipmentRequestDTO dto){
        Equipment e = new Equipment();
        e.setName(dto.getName());
        e.setDescription(dto.getDescription());
        return e;
    }

    /**
     * Converts an {@link Equipment} entity into its response DTO
     * representation for returning to clients.
     *
     * @param e the entity to convert
     * @return a fully populated {@link EquipmentResponseDTO}
     */
    public EquipmentResponseDTO toDTO(Equipment e){
        return EquipmentResponseDTO.builder()
                .id(e.getId())
                .name(e.getName())
                .description(e.getDescription())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
