package com.omni.marketplace.util;

import net.minecraft.network.chat.Component;

public class CurrencyUtils {
    public static final long COPPER_PER_SILVER = 100L;
    public static final long SILVER_PER_GOLD = 100L;
    public static final long COPPER_PER_GOLD = COPPER_PER_SILVER * SILVER_PER_GOLD; // 10,000

    public static final long COPPER_PER_EMERALD = 100L; // 1 Emerald = 1 Silver (100c)
    public static final long COPPER_PER_EMERALD_BLOCK = 900L; // 9 Emeralds = 9 Silver (900c)

    public record CurrencyBreakdown(long gold, long silver, long copper) {}

    public static CurrencyBreakdown breakdown(long totalCopper) {
        if (totalCopper < 0) totalCopper = 0;
        long gold = totalCopper / COPPER_PER_GOLD;
        long remainder = totalCopper % COPPER_PER_GOLD;
        long silver = remainder / COPPER_PER_SILVER;
        long copper = remainder % COPPER_PER_SILVER;
        return new CurrencyBreakdown(gold, silver, copper);
    }

    public static long toCopper(long gold, long silver, long copper) {
        return Math.max(0L, (gold * COPPER_PER_GOLD) + (silver * COPPER_PER_SILVER) + copper);
    }

    public static String format(long totalCopper) {
        if (totalCopper <= 0) return "§c0c";
        CurrencyBreakdown b = breakdown(totalCopper);

        if (b.gold() > 0) {
            if (b.silver() == 0 && b.copper() == 0) {
                return String.format("§6%dg", b.gold());
            } else if (b.copper() == 0) {
                return String.format("§6%dg §f%ds", b.gold(), b.silver());
            } else if (b.silver() == 0) {
                return String.format("§6%dg §c%dc", b.gold(), b.copper());
            } else {
                return String.format("§6%dg §f%ds §c%dc", b.gold(), b.silver(), b.copper());
            }
        } else if (b.silver() > 0) {
            if (b.copper() == 0) {
                return String.format("§f%ds", b.silver());
            } else {
                return String.format("§f%ds §c%dc", b.silver(), b.copper());
            }
        } else {
            return String.format("§c%dc", b.copper());
        }
    }

    public static Component formatComponent(long totalCopper) {
        return Component.literal(format(totalCopper));
    }

    /**
     * Guild Wars 2 Listing Fee: 5% non-refundable, minimum 1 copper.
     */
    public static long calculateListingFee(long unitPrice, int quantity) {
        if (unitPrice <= 0 || quantity <= 0) return 0;
        long total = unitPrice * (long) quantity;
        long fee = (total * 5L) / 100L;
        return Math.max(1L, fee);
    }

    /**
     * Guild Wars 2 Exchange / Transaction Fee: 10% deducted from seller on completion.
     */
    public static long calculateExchangeFee(long unitPrice, int quantity) {
        if (unitPrice <= 0 || quantity <= 0) return 0;
        long total = unitPrice * (long) quantity;
        return (total * 10L) / 100L;
    }

    /**
     * Projected net profit: total - 5% listing fee - 10% exchange fee (total 15% fee).
     */
    public static long calculateNetProfit(long unitPrice, int quantity) {
        long total = unitPrice * (long) quantity;
        long listingFee = calculateListingFee(unitPrice, quantity);
        long exchangeFee = calculateExchangeFee(unitPrice, quantity);
        return Math.max(0L, total - listingFee - exchangeFee);
    }
}
