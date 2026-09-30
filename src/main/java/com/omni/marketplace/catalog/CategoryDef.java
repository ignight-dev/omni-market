package com.omni.marketplace.catalog;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

public class CategoryDef {

    public enum MainCategory {
        ALL("All Items"),
        WEAPONS_ARMOR("Weapons & Armor"),
        TOOLS("Tools"),
        MATERIALS("Crafting Materials"),
        CONSUMABLES("Food & Potions"),
        BLOCKS("Building Blocks"),
        SPECIAL("Special Items");

        public final String displayName;
        MainCategory(String displayName) { this.displayName = displayName; }
    }

    public enum SubCategory {
        // Global / All
        ALL("All"),

        // Weapons & Armor
        SWORDS_AXES("Weapons"),
        BOWS("Ranged"),
        HELMETS("Helmets"),
        CHESTPLATES("Chestplates"),
        LEGGINGS("Leggings"),
        BOOTS("Boots"),
        SHIELDS("Shields"),

        // Tools
        PICKAXES("Pickaxes"),
        SHOVELS("Shovels"),
        HOES("Hoes"),
        FISHING_SHEARS("Fishing"),
        UTILITY("Utility"),

        // Materials
        ORES_INGOTS("Metals"),
        WOOD_LOGS("Wood"),
        STONE_MINERALS("Stone"),
        MOB_DROPS("Drops"),
        FARMING("Farming"),

        // Consumables
        MEAT_COOKED("Food"),
        FRUITS_CROPS("Crops"),
        POTIONS("Potions"),
        GOLDEN_FOOD("Special"),

        // Blocks
        PLANKS_WOOD("Wood"),
        STONE_BRICKS("Bricks"),
        CONCRETE_CLAY("Clay"),
        GLASS_LIGHTING("Light"),
        DECORATIVE("Decor"),

        // Special Items
        BOSS_RARE("Relics"),
        NETHERITE("Netherite"),
        MUSIC_DISCS("Discs"),
        ENCHANTED_BOOKS("Enchanted"),
        MOD_ITEMS("Modded");

        public final String displayName;
        SubCategory(String displayName) { this.displayName = displayName; }
    }

    public record ItemClassification(MainCategory mainCategory, SubCategory subCategory, boolean isSpecial, boolean isEndgameRestricted) {}

    public static ItemClassification classify(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String path = id.getPath().toLowerCase();
        String namespace = id.getNamespace().toLowerCase();

        // 1. Boss / Endgame Relic Drops (NEVER seeded by server, player-listing only!)
        if (path.equals("nether_star") || path.equals("dragon_egg") || path.equals("dragon_breath") ||
            path.equals("elytra") || path.equals("heavy_core") || path.equals("mace") ||
            path.equals("totem_of_undying") || path.equals("wither_skeleton_skull") ||
            path.equals("beacon") || path.equals("conduit") || path.equals("heart_of_the_sea") ||
            path.equals("enchanted_golden_apple") || path.equals("trident") ||
            path.contains("shulker_box") || path.equals("shulker_shell") || path.equals("netherite_upgrade_smithing_template")) {
            return new ItemClassification(MainCategory.SPECIAL, SubCategory.BOSS_RARE, true, true);
        }

        // 2. Netherite items (Special, Endgame Restricted)
        if (path.contains("netherite")) {
            return new ItemClassification(MainCategory.SPECIAL, SubCategory.NETHERITE, true, true);
        }

        // 3. Music Discs & Enchanted Books
        if (item.components().has(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE) || path.startsWith("music_disc")) {
            return new ItemClassification(MainCategory.SPECIAL, SubCategory.MUSIC_DISCS, true, false);
        }
        if (item instanceof EnchantedBookItem) {
            return new ItemClassification(MainCategory.SPECIAL, SubCategory.ENCHANTED_BOOKS, true, false);
        }

        // 4. Modded Items (Non-minecraft namespace)
        if (!namespace.equals("minecraft")) {
            return new ItemClassification(MainCategory.SPECIAL, SubCategory.MOD_ITEMS, true, false);
        }

        // 5. Weapons & Armor
        if (item instanceof ArmorItem armor) {
            SubCategory sub = switch (armor.getType()) {
                case HELMET -> SubCategory.HELMETS;
                case CHESTPLATE -> SubCategory.CHESTPLATES;
                case LEGGINGS -> SubCategory.LEGGINGS;
                case BOOTS -> SubCategory.BOOTS;
                case BODY -> SubCategory.CHESTPLATES;
            };
            return new ItemClassification(MainCategory.WEAPONS_ARMOR, sub, false, false);
        }
        if (item instanceof SwordItem || item instanceof AxeItem) {
            return new ItemClassification(MainCategory.WEAPONS_ARMOR, SubCategory.SWORDS_AXES, false, false);
        }
        if (item instanceof BowItem || item instanceof CrossbowItem || item instanceof ArrowItem) {
            return new ItemClassification(MainCategory.WEAPONS_ARMOR, SubCategory.BOWS, false, false);
        }
        if (item instanceof ShieldItem) {
            return new ItemClassification(MainCategory.WEAPONS_ARMOR, SubCategory.SHIELDS, false, false);
        }

        // 6. Tools
        if (item instanceof PickaxeItem) return new ItemClassification(MainCategory.TOOLS, SubCategory.PICKAXES, false, false);
        if (item instanceof ShovelItem) return new ItemClassification(MainCategory.TOOLS, SubCategory.SHOVELS, false, false);
        if (item instanceof HoeItem) return new ItemClassification(MainCategory.TOOLS, SubCategory.HOES, false, false);
        if (item instanceof FishingRodItem || item instanceof ShearsItem) return new ItemClassification(MainCategory.TOOLS, SubCategory.FISHING_SHEARS, false, false);
        if (item instanceof FlintAndSteelItem || item instanceof CompassItem || item instanceof SpyglassItem || item instanceof BrushItem || item instanceof LeadItem || item instanceof NameTagItem) {
            return new ItemClassification(MainCategory.TOOLS, SubCategory.UTILITY, false, false);
        }

        // 7. Food & Consumables
        if (item.components().has(net.minecraft.core.component.DataComponents.FOOD)) {
            if (path.contains("golden")) return new ItemClassification(MainCategory.CONSUMABLES, SubCategory.GOLDEN_FOOD, false, false);
            if (path.contains("cooked") || path.contains("bread") || path.contains("pie") || path.contains("stew") || path.contains("soup")) {
                return new ItemClassification(MainCategory.CONSUMABLES, SubCategory.MEAT_COOKED, false, false);
            }
            return new ItemClassification(MainCategory.CONSUMABLES, SubCategory.FRUITS_CROPS, false, false);
        }
        if (item instanceof PotionItem || item instanceof SplashPotionItem || item instanceof LingeringPotionItem || item instanceof HoneyBottleItem || item instanceof MilkBucketItem) {
            return new ItemClassification(MainCategory.CONSUMABLES, SubCategory.POTIONS, false, false);
        }

        // 8. Materials
        if (path.contains("ingot") || path.contains("ore") || path.contains("raw_") || path.contains("diamond") || path.contains("coal") || path.contains("quartz") || path.contains("amethyst") || path.contains("redstone") || path.contains("lapis")) {
            return new ItemClassification(MainCategory.MATERIALS, SubCategory.ORES_INGOTS, false, false);
        }
        if (path.contains("log") || path.contains("wood") || path.contains("sapling") || path.contains("leaves") || path.contains("stick") || path.contains("bamboo")) {
            return new ItemClassification(MainCategory.MATERIALS, SubCategory.WOOD_LOGS, false, false);
        }
        if (path.contains("cobble") || path.contains("stone") || path.contains("granite") || path.contains("diorite") || path.contains("andesite") || path.contains("tuff") || path.contains("deepslate") || path.contains("obsidian") || path.contains("clay") || path.contains("flint")) {
            return new ItemClassification(MainCategory.MATERIALS, SubCategory.STONE_MINERALS, false, false);
        }
        if (path.contains("bone") || path.contains("string") || path.contains("feather") || path.contains("gunpowder") || path.contains("spider_eye") || path.contains("rotten_flesh") || path.contains("blaze_rod") || path.contains("pearl") || path.contains("slime_ball") || path.contains("leather")) {
            return new ItemClassification(MainCategory.MATERIALS, SubCategory.MOB_DROPS, false, false);
        }
        if (path.contains("seed") || path.contains("wheat") || path.contains("sugar_cane") || path.contains("dye") || path.contains("kelp") || path.contains("cactus") || path.contains("honeycomb") || path.contains("paper")) {
            return new ItemClassification(MainCategory.MATERIALS, SubCategory.FARMING, false, false);
        }

        // 9. Building Blocks
        if (item instanceof BlockItem) {
            if (path.contains("planks") || path.contains("door") || path.contains("fence") || path.contains("gate") || path.contains("trapdoor") || path.contains("sign") || path.contains("button") || path.contains("pressure_plate")) {
                return new ItemClassification(MainCategory.BLOCKS, SubCategory.PLANKS_WOOD, false, false);
            }
            if (path.contains("brick") || path.contains("sandstone") || path.contains("prismarine") || path.contains("purpur") || path.contains("blackstone")) {
                return new ItemClassification(MainCategory.BLOCKS, SubCategory.STONE_BRICKS, false, false);
            }
            if (path.contains("concrete") || path.contains("terracotta") || path.contains("wool") || path.contains("carpet")) {
                return new ItemClassification(MainCategory.BLOCKS, SubCategory.CONCRETE_CLAY, false, false);
            }
            if (path.contains("glass") || path.contains("lantern") || path.contains("torch") || path.contains("lamp") || path.contains("glowstone") || path.contains("shroomlight")) {
                return new ItemClassification(MainCategory.BLOCKS, SubCategory.GLASS_LIGHTING, false, false);
            }
            return new ItemClassification(MainCategory.BLOCKS, SubCategory.DECORATIVE, false, false);
        }

        return new ItemClassification(MainCategory.MATERIALS, SubCategory.MOB_DROPS, false, false);
    }

    public static boolean isEmeraldCurrency(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return isEmeraldCurrency(id.toString());
    }

    public static boolean isEmeraldCurrency(String itemId) {
        if (itemId == null) return false;
        String normalized = itemId.toLowerCase().trim();
        return normalized.equals("minecraft:emerald") || normalized.equals("minecraft:emerald_block") ||
               normalized.equals("emerald") || normalized.equals("emerald_block");
    }

    public static boolean isEndgameRestricted(String itemId) {
        if (itemId == null) return false;
        String lower = itemId.toLowerCase().trim();
        int colon = lower.indexOf(':');
        String path = colon >= 0 ? lower.substring(colon + 1) : lower;

        if (path.contains("netherite")) return true;
        return path.equals("nether_star") || path.equals("dragon_egg") || path.equals("dragon_breath") ||
               path.equals("elytra") || path.equals("heavy_core") || path.equals("mace") ||
               path.equals("totem_of_undying") || path.equals("wither_skeleton_skull") ||
               path.equals("beacon") || path.equals("conduit") || path.equals("heart_of_the_sea") ||
               path.equals("enchanted_golden_apple") || path.equals("trident") ||
               path.contains("shulker_box") || path.equals("shulker_shell") || path.equals("netherite_upgrade_smithing_template");
    }

    public static boolean isSpecialItem(String itemId) {
        if (itemId == null) return false;
        String lower = itemId.toLowerCase().trim();
        int colon = lower.indexOf(':');
        String namespace = colon >= 0 ? lower.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? lower.substring(colon + 1) : lower;

        if (isEndgameRestricted(itemId)) return true;
        if (path.startsWith("music_disc") || path.equals("enchanted_book")) return true;
        return !namespace.equals("minecraft");
    }

    public static List<SubCategory> getSubCategories(MainCategory mainCategory) {
        return switch (mainCategory) {
            case ALL -> List.of(SubCategory.ALL);
            case WEAPONS_ARMOR -> List.of(SubCategory.ALL, SubCategory.SWORDS_AXES, SubCategory.BOWS, SubCategory.HELMETS, SubCategory.CHESTPLATES, SubCategory.LEGGINGS, SubCategory.BOOTS, SubCategory.SHIELDS);
            case TOOLS -> List.of(SubCategory.ALL, SubCategory.PICKAXES, SubCategory.SHOVELS, SubCategory.HOES, SubCategory.FISHING_SHEARS, SubCategory.UTILITY);
            case MATERIALS -> List.of(SubCategory.ALL, SubCategory.ORES_INGOTS, SubCategory.WOOD_LOGS, SubCategory.STONE_MINERALS, SubCategory.MOB_DROPS, SubCategory.FARMING);
            case CONSUMABLES -> List.of(SubCategory.ALL, SubCategory.MEAT_COOKED, SubCategory.FRUITS_CROPS, SubCategory.POTIONS, SubCategory.GOLDEN_FOOD);
            case BLOCKS -> List.of(SubCategory.ALL, SubCategory.PLANKS_WOOD, SubCategory.STONE_BRICKS, SubCategory.CONCRETE_CLAY, SubCategory.GLASS_LIGHTING, SubCategory.DECORATIVE);
            case SPECIAL -> List.of(SubCategory.ALL, SubCategory.BOSS_RARE, SubCategory.NETHERITE, SubCategory.MUSIC_DISCS, SubCategory.ENCHANTED_BOOKS, SubCategory.MOD_ITEMS);
        };
    }
}
