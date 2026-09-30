package com.omni.marketplace.catalog;

import com.omni.marketplace.catalog.CategoryDef.ItemClassification;
import com.omni.marketplace.catalog.CategoryDef.MainCategory;
import com.omni.marketplace.util.CurrencyUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public class PriceEngine {

    /**
     * Compute dynamic suggested price based on real-time market order book spread.
     */
    public static long computeSuggestedPrice(long lowestAsk, long highestBid, Item item) {
        if (lowestAsk > 0 && highestBid > 0) {
            // Competitive mid-market price
            long spread = lowestAsk - highestBid;
            if (spread > 1) {
                return highestBid + Math.max(1, (spread * 60) / 100);
            } else {
                return lowestAsk;
            }
        } else if (lowestAsk > 0) {
            return lowestAsk;
        } else if (highestBid > 0) {
            return highestBid;
        } else {
            return getBaselinePrice(item);
        }
    }

    /**
     * Baseline reasonable prices for items before player trades establish market prices.
     */
    public static long getBaselinePrice(Item item) {
        ItemClassification classification = CategoryDef.classify(item);
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();

        if (classification.isEndgameRestricted()) {
            if (path.contains("netherite")) return 250000L; // 25g
            if (path.equals("nether_star") || path.equals("dragon_egg")) return 500000L; // 50g
            if (path.equals("elytra") || path.equals("mace") || path.equals("heavy_core")) return 300000L; // 30g
            return 100000L; // 10g
        }

        if (classification.mainCategory() == MainCategory.SPECIAL) {
            return 50000L; // 5g
        }

        if (path.contains("diamond")) return 500L; // 5s
        if (path.contains("gold")) return 150L; // 1s 50c
        if (path.contains("iron")) return 50L; // 50c
        if (path.contains("copper")) return 15L; // 15c
        if (path.contains("coal")) return 10L; // 10c
        if (path.contains("log") || path.contains("wood")) return 5L; // 5c
        if (path.contains("cobble") || path.contains("stone")) return 2L; // 2c
        if (path.contains("cooked") || path.contains("bread")) return 8L; // 8c

        return 20L; // Default 20c
    }

    /**
     * Real Net Profit calculation (after 15% expenses for custom listings, or 10% for instant sell).
     */
    public static long calculateRealProfit(long unitPrice, int quantity, boolean isInstantSell) {
        long gross = unitPrice * (long) quantity;
        if (gross <= 0) return 0L;

        if (isInstantSell) {
            // Instant sell has NO listing fee (matches existing buy order directly)
            long exchangeFee = CurrencyUtils.calculateExchangeFee(unitPrice, quantity);
            return Math.max(0L, gross - exchangeFee);
        } else {
            // Custom listing pays 5% listing fee + 10% exchange fee (15% total)
            long listingFee = CurrencyUtils.calculateListingFee(unitPrice, quantity);
            long exchangeFee = CurrencyUtils.calculateExchangeFee(unitPrice, quantity);
            return Math.max(0L, gross - listingFee - exchangeFee);
        }
    }
}
