package com.processVisualisation.virtualKitchen.store.service;

import com.processVisualisation.virtualKitchen.common.mapper.EquipmentMapper;
import com.processVisualisation.virtualKitchen.store.model.Equipment;
import com.processVisualisation.virtualKitchen.store.dto.EquipmentRequestDTO;
import com.processVisualisation.virtualKitchen.store.dto.EquipmentResponseDTO;
import com.processVisualisation.virtualKitchen.store.repository.EquipmentRepository;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Default {@link IEquipmentService} implementation backed by MongoDB via
 * {@link EquipmentRepository}. Assigns identifiers using
 * {@link SequenceGeneratorService} and converts between entities and DTOs
 * using {@link EquipmentMapper}.
 */
@Service
public class EquipmentServiceImpl implements IEquipmentService {

    @Autowired
    private EquipmentRepository repo;

    @Autowired
    private EquipmentMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    /**
     * Creates a new equipment catalog entry after checking that the name
     * is not already in use, assigning it a generated sequence id.
     *
     * @param dto the equipment name and description to create
     * @return the created equipment
     * @throws RuntimeException if equipment with the same name already exists
     */
    @Override
    public EquipmentResponseDTO create(EquipmentRequestDTO dto){
        if(repo.existsByName(dto.getName())){
            throw new RuntimeException("Already exists");
        }
        Equipment e = mapper.toEntity(dto);
        e.setId(seq.generateSequence(Equipment.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(e));
    }

    /**
     * Fetches a single equipment entry by its identifier.
     *
     * @param id the equipment identifier
     * @return the matching equipment
     * @throws java.util.NoSuchElementException if no equipment exists with the given id
     */
    @Override
    public EquipmentResponseDTO get(Long id){
        return mapper.toDTO(repo.findById(id).orElseThrow());
    }

    /**
     * Fetches every equipment entry in the catalog.
     *
     * @return the list of all equipment
     */
    @Override
    public List<EquipmentResponseDTO> getAll(){
        return repo.findAll().stream().map(mapper::toDTO).collect(Collectors.toList());
    }

    /**
     * Deletes an equipment entry by its identifier. No-op if the id does
     * not exist.
     *
     * @param id the equipment identifier to delete
     */
    @Override
    public void delete(Long id){
        repo.deleteById(id);
    }
}
