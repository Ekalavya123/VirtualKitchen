package com.processVisualisation.virtualKitchen.store.service;

import com.processVisualisation.virtualKitchen.store.dto.EquipmentRequestDTO;
import com.processVisualisation.virtualKitchen.store.dto.EquipmentResponseDTO;

import java.util.List;

/**
 * Service contract for managing the equipment catalog: creating equipment
 * entries and reading/deleting them by identifier.
 */
public interface IEquipmentService {

    /**
     * Creates a new equipment catalog entry.
     *
     * @param dto the equipment name and description to create
     * @return the created equipment
     * @throws RuntimeException if equipment with the same name already exists
     */
    EquipmentResponseDTO create(EquipmentRequestDTO dto);

    /**
     * Fetches a single equipment entry by its identifier.
     *
     * @param id the equipment identifier
     * @return the matching equipment
     * @throws java.util.NoSuchElementException if no equipment exists with the given id
     */
    EquipmentResponseDTO get(Long id);

    /**
     * Fetches every equipment entry in the catalog.
     *
     * @return the list of all equipment
     */
    List<EquipmentResponseDTO> getAll();

    /**
     * Deletes an equipment entry by its identifier.
     *
     * @param id the equipment identifier to delete
     */
    void delete(Long id);
}
