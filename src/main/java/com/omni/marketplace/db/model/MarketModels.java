package com.omni.marketplace.db.model;

import java.util.UUID;

public class MarketModels {
    public record ListingRecord(
        long id,
        UUID sellerUuid,
        String sellerName,
        String itemId,
        String itemNbt,
        long priceCopper,
        int quantity,
        String createdAt
    ) {}

    public record BuyOrderRecord(
        long id,
        UUID buyerUuid,
        String buyerName,
        String itemId,
        String itemNbt,
        long priceCopper,
        int quantity,
        String createdAt
    ) {}

    public record MarketSummaryRecord(
        String itemId,
        String sampleNbt,
        String displayName,
        long lowestSellCopper,
        long highestBuyCopper,
        int supplyCount,
        int demandCount
    ) {}

    public record VaultItemRecord(
        long id,
        UUID playerUuid,
        String itemId,
        String itemNbt,
        int quantity,
        String source
    ) {}

    public record AccountSummary(
        UUID uuid,
        String name,
        long copperBalance,
        int vaultItemCount,
        long vaultCopperAmount
    ) {}
}
