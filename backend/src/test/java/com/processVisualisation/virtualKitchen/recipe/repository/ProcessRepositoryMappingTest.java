package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.Process;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessNodeKind;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeTemplate;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.convert.QueryMapper;
import org.springframework.data.mongodb.core.convert.UpdateMapper;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No embedded Mongo is available to these tests, so this checks the string queries of the atomic
 * repository updates against Spring Data's own mapping instead: the field paths they target must be
 * the ones a whole-document save actually writes, or the update would silently match nothing.
 */
class ProcessRepositoryMappingTest {

    private MongoMappingContext context;
    private MappingMongoConverter converter;

    @BeforeEach
    void setUp() {
        MongoCustomConversions conversions = new MongoCustomConversions(List.of());
        context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
    }

    @Test
    void stepVisualizationUpdate_targetsTheStoredNodeIdAndDataFields() {
        Process.ProcessNode node = new Process.ProcessNode();
        node.setId("s1");
        node.setKind(ProcessNodeKind.STEP);
        node.setData(new LinkedHashMap<>(Map.of("action", "chop")));
        Process process = new Process();
        process.setId(5L);
        process.setNodes(List.of(node));
        Document stored = new Document();
        converter.write(process, stored);
        Document storedNode = stored.getList("nodes", Document.class).get(0);
        String storedNodeIdKey = storedNode.containsKey("_id") ? "_id" : "id";

        MongoPersistentEntity<?> entity = context.getRequiredPersistentEntity(Process.class);
        Document query = new QueryMapper(converter).getMappedObject(new Document("_id", 5L).append("nodes.id", "s1"), entity);
        Document update = new UpdateMapper(converter).getMappedObject(
                new Document("$set", new Document("nodes.$.data.imageUrl", "u")), entity);

        assertThat(query).containsEntry("nodes." + storedNodeIdKey, "s1");
        assertThat(storedNode).containsKey("data");
        assertThat(update.get("$set", Document.class)).containsKey("nodes.$.data.imageUrl");
    }

    @Test
    void processRevision_isReadButNeverWrittenByWholeDocumentSaves() {
        RecipeTemplate recipe = new RecipeTemplate();
        recipe.setId(1L);
        recipe.setProcessRevision(7L);

        Document written = new Document();
        converter.write(recipe, written);
        RecipeTemplate read = converter.read(RecipeTemplate.class, new Document("_id", 1L).append("processRevision", 7L));

        // A stale copy saved by the ingredients/nutrition flows must not roll the revision back.
        assertThat(written).doesNotContainKey("processRevision");
        assertThat(read.getProcessRevision()).isEqualTo(7L);
    }
}
