package com.processVisualisation.virtualKitchen.recipeorder.service;

import com.processVisualisation.virtualKitchen.recipe.model.Process;
import com.processVisualisation.virtualKitchen.recipe.service.ProcessGraphCloner;

import java.util.ArrayList;
import java.util.List;

/**
 * Deep copies of process graphs, so an order's snapshot shares no mutable state with the live recipe. Unlike a
 * recipe copy, a snapshot keeps the source ids and only the fields reading an order needs (no editor layout).
 */
final class ProcessSnapshots {

    private ProcessSnapshots() {
    }

    static List<Process> copyAll(List<Process> processes) {
        List<Process> copies = new ArrayList<>();
        if (processes != null) processes.forEach(process -> copies.add(copy(process)));
        return copies;
    }

    static Process copy(Process source) {
        Process copy = new Process();
        copy.setId(source.getId());
        copy.setType(source.getType());
        copy.setRecipeId(source.getRecipeId());
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        List<Process.ProcessNode> nodes = new ArrayList<>();
        if (source.getNodes() != null) {
            for (Process.ProcessNode node : source.getNodes()) {
                if (node == null) continue;
                Process.ProcessNode nodeCopy = new Process.ProcessNode();
                nodeCopy.setId(node.getId());
                nodeCopy.setKind(node.getKind());
                nodeCopy.setData(ProcessGraphCloner.deepCopy(node.getData()));
                nodeCopy.setType(node.getType());
                nodeCopy.setParentId(node.getParentId());
                if (node.getPosition() != null) {
                    // Reading order falls back to layout position, so keep it.
                    Process.PositionDocument position = new Process.PositionDocument();
                    position.setX(node.getPosition().getX());
                    position.setY(node.getPosition().getY());
                    nodeCopy.setPosition(position);
                }
                nodes.add(nodeCopy);
            }
        }
        copy.setNodes(nodes);
        List<Process.ProcessEdge> edges = new ArrayList<>();
        if (source.getEdges() != null) {
            for (Process.ProcessEdge edge : source.getEdges()) {
                if (edge == null) continue;
                Process.ProcessEdge edgeCopy = new Process.ProcessEdge();
                edgeCopy.setId(edge.getId());
                edgeCopy.setSource(edge.getSource());
                edgeCopy.setTarget(edge.getTarget());
                edgeCopy.setSourceHandle(edge.getSourceHandle());
                edgeCopy.setTargetHandle(edge.getTargetHandle());
                edgeCopy.setType(edge.getType());
                edgeCopy.setLabel(edge.getLabel());
                edgeCopy.setData(ProcessGraphCloner.deepCopy(edge.getData()));
                edges.add(edgeCopy);
            }
        }
        copy.setEdges(edges);
        return copy;
    }
}
