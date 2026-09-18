package com.processVisualisation.virtualKitchen.recipe.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessNodeKind;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regression test for the reported save failure: Jackson couldn't construct
 * {@link ProcessNodeDTO} ("no Creators, like default constructor, exist")
 * because {@code @Builder} alone suppresses Lombok's implicit no-args
 * constructor. Exercises real JSON deserialization (unlike the Mockito-based
 * {@code ProcessServiceImplTest}, which builds DTOs directly in Java and
 * never went through Jackson, so it never would have caught this).
 */
class ProcessUpdateDTOJacksonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void processUpdateDTO_withNestedNodesEdgesViewport_deserializesFromJson() throws Exception {
        String json = """
                {
                  "name": "Prepare Chicken",
                  "description": "Marinate and cook",
                  "nodes": [
                    {
                      "id": "n1",
                      "kind": "STEP",
                      "data": { "action": "marinate" },
                      "type": "processStepNode",
                      "position": { "x": 10.0, "y": 20.0 },
                      "measured": { "width": 280.0, "height": 160.0 },
                      "width": 280.0,
                      "height": 160.0,
                      "draggable": true,
                      "selectable": true,
                      "deletable": true
                    }
                  ],
                  "edges": [
                    { "id": "e1", "source": "n1", "target": "n2", "type": "smoothstep" }
                  ],
                  "viewport": { "x": 0.0, "y": 0.0, "zoom": 1.0 }
                }
                """;

        ProcessUpdateDTO dto = objectMapper.readValue(json, ProcessUpdateDTO.class);

        assertEquals("Prepare Chicken", dto.getName());
        assertNotNull(dto.getNodes());
        assertEquals(1, dto.getNodes().size());

        ProcessNodeDTO node = dto.getNodes().get(0);
        assertEquals("n1", node.getId());
        assertEquals(ProcessNodeKind.STEP, node.getKind());
        assertEquals(Map.of("action", "marinate"), node.getData());
        assertEquals(10.0, node.getPosition().getX());
        assertEquals(280.0, node.getMeasured().getWidth());

        assertEquals(1, dto.getEdges().size());
        assertEquals("n1", dto.getEdges().get(0).getSource());

        assertNotNull(dto.getViewport());
        assertEquals(1.0, dto.getViewport().getZoom());
    }
}
