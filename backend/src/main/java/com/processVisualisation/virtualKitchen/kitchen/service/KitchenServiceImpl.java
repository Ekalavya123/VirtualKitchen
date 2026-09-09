package com.processVisualisation.virtualKitchen.kitchen.service;

import com.processVisualisation.virtualKitchen.common.mapper.KitchenMapper;
import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenResponseDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenUpdateDTO;
import com.processVisualisation.virtualKitchen.kitchen.model.Kitchen;
import com.processVisualisation.virtualKitchen.kitchen.repository.KitchenRepository;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link IKitchenService}, backed by
 * {@link KitchenRepository}. Translates between {@link Kitchen} entities and
 * DTOs via {@link KitchenMapper} and generates new kitchen ids using
 * {@link SequenceGeneratorService}.
 */
@Service
public class KitchenServiceImpl implements IKitchenService {

    @Autowired
    private KitchenRepository repo;

    @Autowired
    private KitchenMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    /**
     * Creates a new kitchen with a generated id and persists it.
     *
     * @param dto the kitchen's name and owner id
     * @return the created kitchen
     */
    @Override
    public KitchenResponseDTO create(KitchenRequestDTO dto){
        Kitchen k = mapper.toEntity(dto);
        k.setId(seq.generateSequence(Kitchen.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(k));
    }

    /**
     * Fetches a single kitchen by its id.
     *
     * @param id the kitchen id
     * @return the matching kitchen
     * @throws java.util.NoSuchElementException if no kitchen exists with the given id
     */
    @Override
    public KitchenResponseDTO get(Long id){
        return mapper.toDTO(repo.findById(id).orElseThrow());
    }

    /**
     * Fetches all kitchens owned by the given owner.
     *
     * @param ownerId id of the owning user
     * @return the owner's kitchens
     */
    @Override
    public List<KitchenResponseDTO> getByOwner(Long ownerId){
        return repo.findByOwnerId(ownerId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Updates an existing kitchen's name and persists the change.
     *
     * @param id  id of the kitchen to update
     * @param dto the new field values
     * @return the updated kitchen
     * @throws java.util.NoSuchElementException if no kitchen exists with the given id
     */
    @Override
    public KitchenResponseDTO update(Long id, KitchenUpdateDTO dto){
        Kitchen k = repo.findById(id).orElseThrow();
        k.setName(dto.getName());
        return mapper.toDTO(repo.save(k));
    }

    /**
     * Deletes the kitchen with the given id.
     *
     * @param id id of the kitchen to delete
     */
    @Override
    public void delete(Long id){
        repo.deleteById(id);
    }
}
