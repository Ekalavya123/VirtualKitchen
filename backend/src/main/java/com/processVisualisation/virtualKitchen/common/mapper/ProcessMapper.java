package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.recipe.dto.ProcessEdgeDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessMeasuredDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessNodeDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessPositionDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessViewportDTO;
import com.processVisualisation.virtualKitchen.recipe.model.Process;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Centralizes conversion between the {@link Process} entity (and its
 * embedded node/edge/viewport documents) and the Process API's DTOs.
 * Presentation fields (position, size, handles, style) are passed through
 * unchanged in both directions — this mapper does not interpret them.
 */
@Component
public class ProcessMapper {

    /**
     * Converts a {@link Process} entity into its full response DTO.
     *
     * @param process the entity to convert
     * @return a fully populated {@link ProcessResponseDTO}
     */
    public ProcessResponseDTO toDTO(Process process) {
        return ProcessResponseDTO.builder()
                .id(process.getId())
                .type(process.getType())
                .recipeId(process.getRecipeId())
                .name(process.getName())
                .description(process.getDescription())
                .nodes(toNodeDTOs(process.getNodes()))
                .edges(toEdgeDTOs(process.getEdges()))
                .viewport(toViewportDTO(process.getViewport()))
                .createdAt(process.getCreatedAt())
                .updatedAt(process.getUpdatedAt())
                .build();
    }

    /**
     * Converts a list of {@link Process.ProcessNode} entities into DTOs.
     *
     * @param nodes the entities to convert, may be null
     * @return the converted DTOs, empty if nodes was null
     */
    public List<ProcessNodeDTO> toNodeDTOs(List<Process.ProcessNode> nodes) {
        List<ProcessNodeDTO> result = new ArrayList<>();
        if (nodes == null) {
            return result;
        }
        for (Process.ProcessNode node : nodes) {
            result.add(toNodeDTO(node));
        }
        return result;
    }

    /**
     * Converts a single {@link Process.ProcessNode} entity into its DTO.
     *
     * @param node the entity to convert
     * @return the converted {@link ProcessNodeDTO}
     */
    public ProcessNodeDTO toNodeDTO(Process.ProcessNode node) {
        return ProcessNodeDTO.builder()
                .id(node.getId())
                .kind(node.getKind())
                .data(node.getData())
                .type(node.getType())
                .position(toPositionDTO(node.getPosition()))
                .measured(toMeasuredDTO(node.getMeasured()))
                .width(node.getWidth())
                .height(node.getHeight())
                .parentId(node.getParentId())
                .extent(node.getExtent())
                .draggable(node.getDraggable())
                .selectable(node.getSelectable())
                .deletable(node.getDeletable())
                .build();
    }

    /**
     * Converts a list of node DTOs into new, unpersisted
     * {@link Process.ProcessNode} entities, for replacing a process's node
     * list on update.
     *
     * @param dtos the DTOs to convert, may be null
     * @return the converted entities, empty if dtos was null
     */
    public List<Process.ProcessNode> toNodeEntities(List<ProcessNodeDTO> dtos) {
        List<Process.ProcessNode> result = new ArrayList<>();
        if (dtos == null) {
            return result;
        }
        for (ProcessNodeDTO dto : dtos) {
            result.add(toNodeEntity(dto));
        }
        return result;
    }

    /**
     * Converts a single node DTO into a new, unpersisted
     * {@link Process.ProcessNode} entity.
     *
     * @param dto the DTO to convert
     * @return the converted entity
     */
    public Process.ProcessNode toNodeEntity(ProcessNodeDTO dto) {
        Process.ProcessNode node = new Process.ProcessNode();
        node.setId(dto.getId());
        node.setKind(dto.getKind());
        node.setData(dto.getData() != null ? new LinkedHashMap<>(dto.getData()) : new LinkedHashMap<>());
        node.setType(dto.getType());
        node.setPosition(toPositionEntity(dto.getPosition()));
        node.setMeasured(toMeasuredEntity(dto.getMeasured()));
        node.setWidth(dto.getWidth());
        node.setHeight(dto.getHeight());
        node.setParentId(dto.getParentId());
        node.setExtent(dto.getExtent());
        node.setDraggable(dto.getDraggable());
        node.setSelectable(dto.getSelectable());
        node.setDeletable(dto.getDeletable());
        return node;
    }

    /**
     * Converts a list of {@link Process.ProcessEdge} entities into DTOs.
     *
     * @param edges the entities to convert, may be null
     * @return the converted DTOs, empty if edges was null
     */
    public List<ProcessEdgeDTO> toEdgeDTOs(List<Process.ProcessEdge> edges) {
        List<ProcessEdgeDTO> result = new ArrayList<>();
        if (edges == null) {
            return result;
        }
        for (Process.ProcessEdge edge : edges) {
            result.add(toEdgeDTO(edge));
        }
        return result;
    }

    /**
     * Converts a single {@link Process.ProcessEdge} entity into its DTO.
     *
     * @param edge the entity to convert
     * @return the converted {@link ProcessEdgeDTO}
     */
    public ProcessEdgeDTO toEdgeDTO(Process.ProcessEdge edge) {
        return ProcessEdgeDTO.builder()
                .id(edge.getId())
                .source(edge.getSource())
                .target(edge.getTarget())
                .label(edge.getLabel())
                .sourceHandle(edge.getSourceHandle())
                .targetHandle(edge.getTargetHandle())
                .type(edge.getType())
                .animated(edge.getAnimated())
                .style(edge.getStyle())
                .data(edge.getData())
                .build();
    }

    /**
     * Converts a list of edge DTOs into new, unpersisted
     * {@link Process.ProcessEdge} entities, for replacing a process's edge
     * list on update.
     *
     * @param dtos the DTOs to convert, may be null
     * @return the converted entities, empty if dtos was null
     */
    public List<Process.ProcessEdge> toEdgeEntities(List<ProcessEdgeDTO> dtos) {
        List<Process.ProcessEdge> result = new ArrayList<>();
        if (dtos == null) {
            return result;
        }
        for (ProcessEdgeDTO dto : dtos) {
            result.add(toEdgeEntity(dto));
        }
        return result;
    }

    /**
     * Converts a single edge DTO into a new, unpersisted
     * {@link Process.ProcessEdge} entity.
     *
     * @param dto the DTO to convert
     * @return the converted entity
     */
    public Process.ProcessEdge toEdgeEntity(ProcessEdgeDTO dto) {
        Process.ProcessEdge edge = new Process.ProcessEdge();
        edge.setId(dto.getId());
        edge.setSource(dto.getSource());
        edge.setTarget(dto.getTarget());
        edge.setLabel(dto.getLabel());
        edge.setSourceHandle(dto.getSourceHandle());
        edge.setTargetHandle(dto.getTargetHandle());
        edge.setType(dto.getType());
        edge.setAnimated(dto.getAnimated());
        edge.setStyle(dto.getStyle() != null ? new LinkedHashMap<>(dto.getStyle()) : null);
        edge.setData(dto.getData() != null ? new LinkedHashMap<>(dto.getData()) : new LinkedHashMap<>());
        return edge;
    }

    private ProcessPositionDTO toPositionDTO(Process.PositionDocument position) {
        if (position == null) {
            return null;
        }
        return ProcessPositionDTO.builder().x(position.getX()).y(position.getY()).build();
    }

    private Process.PositionDocument toPositionEntity(ProcessPositionDTO dto) {
        if (dto == null) {
            return null;
        }
        Process.PositionDocument position = new Process.PositionDocument();
        position.setX(dto.getX());
        position.setY(dto.getY());
        return position;
    }

    private ProcessMeasuredDTO toMeasuredDTO(Process.MeasuredDocument measured) {
        if (measured == null) {
            return null;
        }
        return ProcessMeasuredDTO.builder().width(measured.getWidth()).height(measured.getHeight()).build();
    }

    private Process.MeasuredDocument toMeasuredEntity(ProcessMeasuredDTO dto) {
        if (dto == null) {
            return null;
        }
        Process.MeasuredDocument measured = new Process.MeasuredDocument();
        measured.setWidth(dto.getWidth());
        measured.setHeight(dto.getHeight());
        return measured;
    }

    /**
     * Converts a {@link Process.ProcessViewport} entity into its DTO.
     *
     * @param viewport the entity to convert, may be null
     * @return the converted DTO, or null if viewport was null
     */
    public ProcessViewportDTO toViewportDTO(Process.ProcessViewport viewport) {
        if (viewport == null) {
            return null;
        }
        return ProcessViewportDTO.builder().x(viewport.getX()).y(viewport.getY()).zoom(viewport.getZoom()).build();
    }

    /**
     * Converts a viewport DTO into a new, unpersisted
     * {@link Process.ProcessViewport} entity.
     *
     * @param dto the DTO to convert, may be null
     * @return the converted entity, or null if dto was null
     */
    public Process.ProcessViewport toViewportEntity(ProcessViewportDTO dto) {
        if (dto == null) {
            return null;
        }
        Process.ProcessViewport viewport = new Process.ProcessViewport();
        viewport.setX(dto.getX());
        viewport.setY(dto.getY());
        viewport.setZoom(dto.getZoom());
        return viewport;
    }
}
