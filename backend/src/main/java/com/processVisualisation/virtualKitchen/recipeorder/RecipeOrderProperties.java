package com.processVisualisation.virtualKitchen.recipeorder;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Prototype settings for recipe orders. The fees are demo values — the product has no tariff yet —
 * and are shown as such in the UI.
 */
@Data
@Component
@ConfigurationProperties(prefix = "vk.recipe-order")
public class RecipeOrderProperties {

    /** Demo fee for preparing the recipe. */
    private double preparationFee = 4.99;

    /** Demo delivery fee. */
    private double deliveryFee = 2.99;

    private String currency = "USD";

    /** When false, the demo payment endpoint refuses every payment. */
    private boolean demoPaymentEnabled = true;

    /** A RESERVING order older than this is treated as an interrupted attempt and recovered. */
    private long staleReservationSeconds = 120;

    /** Upper bound on servings per order. */
    private int maxServings = 50;

    /** Illustrative per-stage minutes for the demo estimate shown on receipts. */
    private int demoMinutesPerStep = 4;
    private int demoQualityCheckMinutes = 5;
    private int demoDeliveryMinutes = 25;

    /** How many (illustrative) preparation snapshots the tracking page offers. */
    private int demoSnapshotRequests = 2;
}
