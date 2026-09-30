package com.omni.marketplace.catalog;

import com.omni.marketplace.catalog.CategoryDef.ItemClassification;
import com.omni.marketplace.catalog.CategoryDef.MainCategory;
import com.omni.marketplace.catalog.CategoryDef.SubCategory;
import com.omni.marketplace.db.DatabaseManager;
import com.omni.marketplace.network.MarketPackets.CatalogEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

public class ItemCatalog {

    public static List<CatalogEntry> searchItems(String query, String categoryStr, String subCategoryStr, int page, int pageSize) {
        String cleanQuery = (query == null) ? "" : query.trim().toLowerCase();

        MainCategory selectedMain = MainCategory.ALL;
        if (categoryStr != null && !categoryStr.isBlank()) {
            try {
                selectedMain = MainCategory.valueOf(categoryStr.toUpperCase());
            } catch (Exception ignored) {}
        }

        SubCategory selectedSub = SubCategory.ALL;
        if (subCategoryStr != null && !subCategoryStr.isBlank()) {
            try {
                selectedSub = SubCategory.valueOf(subCategoryStr.toUpperCase());
            } catch (Exception ignored) {}
        }

        DatabaseManager db = DatabaseManager.getInstance();
        Map<String, DatabaseManager.ItemMarketStats> statsMap = db.getAllItemMarketStats();
        Set<String> discoveredItems = db.getDiscoveredItems();

        List<ItemCandidate> matched = new ArrayList<>();

        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;

            ResourceLocation loc = BuiltInRegistries.ITEM.getKey(item);
            String itemId = loc.toString();

            // Strictly filter out emerald currency items
            if (CategoryDef.isEmeraldCurrency(itemId)) continue;

            ItemClassification classification = CategoryDef.classify(item);

            // If item is endgame restricted (Netherite, Boss Relics), only show if discovered/listed by a player
            if (classification.isEndgameRestricted()) {
                boolean hasEverBeenListed = discoveredItems.contains(itemId) || statsMap.containsKey(itemId);
                if (!hasEverBeenListed) {
                    continue;
                }
            }

            // Category filter
            if (selectedMain != MainCategory.ALL && classification.mainCategory() != selectedMain) {
                continue;
            }

            // SubCategory filter
            if (selectedSub != SubCategory.ALL && classification.subCategory() != selectedSub) {
                continue;
            }

            // Query text filter
            if (!cleanQuery.isEmpty()) {
                String displayName = item.getDescription().getString().toLowerCase();
                String path = loc.getPath().toLowerCase();
                if (!displayName.contains(cleanQuery) && !path.contains(cleanQuery) && !itemId.contains(cleanQuery)) {
                    continue;
                }
            }

            DatabaseManager.ItemMarketStats stats = statsMap.get(itemId);
            long lowestSell = stats != null ? stats.lowestSell() : 0L;
            long highestBuy = stats != null ? stats.highestBuy() : 0L;
            int supply = stats != null ? stats.supply() : 0;
            int demand = stats != null ? stats.demand() : 0;

            long suggestedPrice = PriceEngine.computeSuggestedPrice(lowestSell, highestBuy, item);

            matched.add(new ItemCandidate(itemId, item, lowestSell, highestBuy, suggestedPrice, supply, demand));
        }

        // Sort: Items with active market activity (supply or demand) first, then by supply desc, then alphabetically
        matched.sort((a, b) -> {
            int activityA = (a.supply > 0 || a.demand > 0) ? 1 : 0;
            int activityB = (b.supply > 0 || b.demand > 0) ? 1 : 0;
            if (activityA != activityB) return Integer.compare(activityB, activityA);

            int totalVolumeA = a.supply + a.demand;
            int totalVolumeB = b.supply + b.demand;
            if (totalVolumeA != totalVolumeB) return Integer.compare(totalVolumeB, totalVolumeA);

            return a.itemId.compareTo(b.itemId);
        });

        // Pagination
        int offset = Math.max(0, page * pageSize);
        if (offset >= matched.size()) {
            return Collections.emptyList();
        }

        int toIndex = Math.min(offset + pageSize, matched.size());
        List<ItemCandidate> paged = matched.subList(offset, toIndex);

        List<CatalogEntry> result = new ArrayList<>(paged.size());
        for (ItemCandidate c : paged) {
            result.add(new CatalogEntry(
                    c.itemId,
                    "", // Sample NBT
                    c.lowestSell,
                    c.highestBuy,
                    c.suggestedPrice,
                    c.supply,
                    c.demand
            ));
        }

        return result;
    }

    private record ItemCandidate(
            String itemId,
            Item item,
            long lowestSell,
            long highestBuy,
            long suggestedPrice,
            int supply,
            int demand
    ) {}
}
