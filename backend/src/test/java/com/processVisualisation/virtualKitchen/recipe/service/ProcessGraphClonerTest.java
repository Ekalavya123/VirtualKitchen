package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.model.Process;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessGraphClonerTest {

    @Test
    void copyGetsNewIdsAndItsInternalReferencesFollowThem() {
        Process source = new Process();
        source.setNodes(List.of(node("group", null, Map.of()), node("step", "group", stepData("group", 7L))));
        Process.ProcessEdge edge = new Process.ProcessEdge();
        edge.setId("e1");
        edge.setSource("group");
        edge.setTarget("step");
        source.setEdges(List.of(edge));

        Process copy = ProcessGraphCloner.cloneGraph(source, null);

        Process.ProcessNode group = copy.getNodes().get(0);
        Process.ProcessNode step = copy.getNodes().get(1);
        assertThat(group.getId()).isNotEqualTo("group");
        assertThat(step.getParentId()).isEqualTo(group.getId());
        assertThat(copy.getEdges().get(0).getSource()).isEqualTo(group.getId());
        assertThat(copy.getEdges().get(0).getTarget()).isEqualTo(step.getId());
        assertThat(actionOn(step).get("steps")).isEqualTo(List.of(Map.of("stepId", group.getId())));
        // Within the same recipe, subprocess references stay as they are.
        assertThat(actionOn(step).get("processes")).isEqualTo(List.of(Map.of("processId", 7L)));
    }

    @Test
    void recipeCopyRewritesSubprocessReferencesAndDropsOutsideOnes() {
        Process source = new Process();
        Map<String, Object> data = stepData("x", 7L);
        actionOn(data).put("processes", new ArrayList<>(List.of(Map.of("processId", 7L), Map.of("processId", 99L))));
        source.setNodes(List.of(node("step", null, data)));

        Process copy = ProcessGraphCloner.cloneGraph(source, Map.of(7L, 70L));

        assertThat(actionOn(copy.getNodes().get(0)).get("processes")).isEqualTo(List.of(Map.of("processId", 70L)));
    }

    @Test
    void copiedDataSharesNothingWithTheSource() {
        Process source = new Process();
        Map<String, Object> data = stepData("x", 7L);
        source.setNodes(List.of(node("step", null, data)));

        Process copy = ProcessGraphCloner.cloneGraph(source, null);
        actionOn(copy.getNodes().get(0)).put("extra", true);

        assertThat(actionOn(data)).doesNotContainKey("extra");
    }

    private static Process.ProcessNode node(String id, String parentId, Map<String, Object> data) {
        Process.ProcessNode node = new Process.ProcessNode();
        node.setId(id);
        node.setParentId(parentId);
        node.setData(new LinkedHashMap<>(data));
        return node;
    }

    private static Map<String, Object> stepData(String referencedStepId, Long referencedProcessId) {
        Map<String, Object> actionOn = new LinkedHashMap<>();
        actionOn.put("steps", new ArrayList<>(List.of(Map.of("stepId", referencedStepId))));
        actionOn.put("processes", new ArrayList<>(List.of(Map.of("processId", referencedProcessId))));
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("actionOn", actionOn);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("step", step);
        return data;
    }

    private static Map<String, Object> actionOn(Process.ProcessNode node) {
        return actionOn(node.getData());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> actionOn(Map<String, Object> data) {
        return (Map<String, Object>) ((Map<String, Object>) data.get("step")).get("actionOn");
    }
}
