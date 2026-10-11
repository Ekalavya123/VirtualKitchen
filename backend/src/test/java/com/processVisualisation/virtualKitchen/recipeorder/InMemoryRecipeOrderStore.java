package com.processVisualisation.virtualKitchen.recipeorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrder;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link RecipeOrderStore} with the Mongo store's compare-and-set semantics. Orders are
 * stored as JSON copies, so — as with a real database — a caller holding an order object can't
 * change the stored one without writing it, and every read returns a fresh copy.
 */
class InMemoryRecipeOrderStore implements RecipeOrderStore {

    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Map<Long, String> documents = new LinkedHashMap<>();
    int writes;

    @Override
    public synchronized RecipeOrder insert(RecipeOrder order) {
        if (documents.containsKey(order.getId())) throw new IllegalStateException("duplicate id " + order.getId());
        documents.put(order.getId(), write(order));
        writes++;
        return order;
    }

    @Override
    public synchronized Optional<RecipeOrder> findById(Long id) {
        String document = documents.get(id);
        return document == null ? Optional.empty() : Optional.of(read(document));
    }

    @Override
    public synchronized List<RecipeOrder> findByUserId(Long userId) {
        return documents.values().stream().map(this::read)
                .filter(order -> userId.equals(order.getUserId()))
                .sorted(Comparator.comparing(RecipeOrder::getId).reversed())
                .toList();
    }

    @Override
    public synchronized boolean replaceIfVersion(RecipeOrder order) {
        String stored = documents.get(order.getId());
        if (stored == null || read(stored).getVersion() != order.getVersion()) return false;
        order.setVersion(order.getVersion() + 1);
        documents.put(order.getId(), write(order));
        writes++;
        return true;
    }

    private String write(RecipeOrder order) {
        try {
            return json.writeValueAsString(order);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private RecipeOrder read(String document) {
        try {
            return json.readValue(document, RecipeOrder.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
