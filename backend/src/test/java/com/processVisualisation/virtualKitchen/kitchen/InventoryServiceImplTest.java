package com.processVisualisation.virtualKitchen.kitchen;

import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;
import com.processVisualisation.virtualKitchen.common.mapper.InventoryMapper;
import com.processVisualisation.virtualKitchen.kitchen.dto.InventoryRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.repository.InventoryRepository;
import com.processVisualisation.virtualKitchen.kitchen.service.InventoryServiceImpl;
import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import com.processVisualisation.virtualKitchen.store.model.Inventory;
import com.processVisualisation.virtualKitchen.store.model.ItemType;
import com.processVisualisation.virtualKitchen.store.repository.EquipmentRepository;
import com.processVisualisation.virtualKitchen.store.repository.IngredientRepository;
import com.processVisualisation.virtualKitchen.store.units.UnitConversionException;
import com.processVisualisation.virtualKitchen.store.units.UnitConversionService;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Adding stock (shop checkout) converts units into the existing row and applies an atomic $inc. */
class InventoryServiceImplTest {

    private final InventoryRepository repository = mock(InventoryRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final IngredientRepository ingredients = mock(IngredientRepository.class);
    private final InventoryServiceImpl service = new InventoryServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "repo", repository);
        ReflectionTestUtils.setField(service, "mapper", new InventoryMapper());
        ReflectionTestUtils.setField(service, "ingredientRepository", ingredients);
        ReflectionTestUtils.setField(service, "equipmentRepository", mock(EquipmentRepository.class));
        ReflectionTestUtils.setField(service, "seq", mock(SequenceGeneratorService.class));
        ReflectionTestUtils.setField(service, "mongoTemplate", mongoTemplate);
        ReflectionTestUtils.setField(service, "unitConversionService", UnitConversionService.withDefaults());
        when(ingredients.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    void convertsTheAddedAmountIntoTheRowsUnitAndIncrementsAtomically() {
        Inventory flour = row(UnitType.KG, 2);
        when(repository.findByKitchenIdAndItemTypeAndItemId(70L, ItemType.INGREDIENT, 101L)).thenReturn(Optional.of(flour));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Inventory.class)))
                .thenReturn(row(UnitType.KG, 2.5));

        service.addOrUpdate(request(500, UnitType.GRAM));

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), update.capture(), any(FindAndModifyOptions.class), eq(Inventory.class));
        Document inc = (Document) update.getValue().getUpdateObject().get("$inc");
        assertEquals(0.5, ((Number) inc.get("quantity")).doubleValue(), 1e-9, "500 g adds 0.5 kg, not 500 kg");
        verify(repository, never()).save(any());
    }

    @Test
    void validationRejectsUnitsThatCannotBeAddedWithoutChangingAnything() {
        Inventory flour = row(UnitType.KG, 2);
        when(repository.findByKitchenIdAndItemTypeAndItemId(70L, ItemType.INGREDIENT, 101L)).thenReturn(Optional.of(flour));
        InventoryRequestDTO odd = request(1, UnitType.GRAM);
        odd.setUnit(null);
        service.validateAdd(odd); // same-unit/unknown-unit requests pass through unchanged

        ReflectionTestUtils.setField(service, "unitConversionService", new UnitConversionService(
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode())); // a config with no units at all
        assertThrows(UnitConversionException.class, () -> service.validateAdd(request(1, UnitType.GRAM)));
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Inventory.class));
    }

    private static Inventory row(UnitType unit, double quantity) {
        Inventory row = new Inventory();
        row.setId(9L);
        row.setKitchenId(70L);
        row.setUserId(7L);
        row.setItemType(ItemType.INGREDIENT);
        row.setItemId(101L);
        row.setQuantity(quantity);
        row.setUnit(unit);
        return row;
    }

    private static InventoryRequestDTO request(double quantity, UnitType unit) {
        InventoryRequestDTO dto = new InventoryRequestDTO();
        dto.setUserId(7L);
        dto.setKitchenId(70L);
        dto.setItemType(ItemType.INGREDIENT);
        dto.setItemId(101L);
        dto.setQuantity(quantity);
        dto.setUnit(unit);
        return dto;
    }
}
