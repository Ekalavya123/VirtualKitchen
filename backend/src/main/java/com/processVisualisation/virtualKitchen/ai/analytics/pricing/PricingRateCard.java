package com.processVisualisation.virtualKitchen.ai.analytics.pricing;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One version of one model's prices. Cards are append-only: a price change closes the active card
 * ({@code effectiveTo}, {@code active=false}) and opens a new version, so every analytics cost keeps pointing at
 * the exact card ({@link #id}) and {@link #version} it was calculated with. Rates are USD unless
 * {@link #currency} says otherwise; a null rate means "not priced", never zero.
 */
@Data
@Document(collection = "ai_pricing_rates")
public class PricingRateCard {

    /** {@code <modelKey>@<version>}. */
    @Id
    private String id;

    private String version;
    @Indexed
    private String modelKey;
    private String provider;
    private String providerModelId;
    /** The registry capability, e.g. {@code TEXT_TO_IMAGE}. */
    private String modality;
    /** PAID or OPEN_SOURCE. */
    private String tier;

    private Double inputPerMTok;
    private Double cachedInputPerMTok;
    private Double outputPerMTok;
    /** Falls back to {@link #outputPerMTok} when null (reasoning tokens are billed as output). */
    private Double reasoningPerMTok;
    private Double perImage;
    private Double perAudioSecond;
    /** Estimated local compute cost per second of call duration; unset by default. */
    private Double infraPerComputeSecond;

    private String currency = "USD";
    private Instant effectiveFrom;
    private Instant effectiveTo;
    private boolean active;
    private String source;
    private Instant createdAt;

    public boolean isLocal() {
        return "OPEN_SOURCE".equals(tier);
    }

    public boolean coversInstant(Instant at) {
        return (effectiveFrom == null || !at.isBefore(effectiveFrom)) && (effectiveTo == null || at.isBefore(effectiveTo));
    }

    /** True when this card prices the same things at the same rates as {@code other} (ids and dates ignored). */
    public boolean samePrices(PricingRateCard other) {
        return java.util.Objects.equals(inputPerMTok, other.inputPerMTok)
                && java.util.Objects.equals(cachedInputPerMTok, other.cachedInputPerMTok)
                && java.util.Objects.equals(outputPerMTok, other.outputPerMTok)
                && java.util.Objects.equals(reasoningPerMTok, other.reasoningPerMTok)
                && java.util.Objects.equals(perImage, other.perImage)
                && java.util.Objects.equals(perAudioSecond, other.perAudioSecond)
                && java.util.Objects.equals(infraPerComputeSecond, other.infraPerComputeSecond)
                && java.util.Objects.equals(currency, other.currency)
                && java.util.Objects.equals(tier, other.tier);
    }
}
