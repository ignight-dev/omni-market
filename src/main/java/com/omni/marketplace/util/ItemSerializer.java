package com.omni.marketplace.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItemSerializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemSerializer.class);

    public static String serialize(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) return "";
        try {
            CompoundTag tag = (CompoundTag) stack.saveOptional(registries);
            return tag.toString();
        } catch (Exception e) {
            LOGGER.error("Failed to serialize item stack: {}", stack, e);
            return "";
        }
    }

    public static ItemStack deserialize(String nbtString, String fallbackItemId, int count, HolderLookup.Provider registries) {
        if (nbtString != null && !nbtString.isBlank()) {
            try {
                CompoundTag tag = TagParser.parseTag(nbtString);
                ItemStack parsed = ItemStack.parseOptional(registries, tag);
                if (!parsed.isEmpty()) {
                    if (count > 0) parsed.setCount(count);
                    return parsed;
                }
            } catch (CommandSyntaxException e) {
                LOGGER.warn("Failed to parse item NBT string: {}, falling back to item id", nbtString, e);
            }
        }

        if (fallbackItemId != null && !fallbackItemId.isBlank()) {
            ResourceLocation loc = ResourceLocation.tryParse(fallbackItemId);
            if (loc != null) {
                Item item = BuiltInRegistries.ITEM.get(loc);
                if (item != null && item != Items.AIR) {
                    return new ItemStack(item, Math.max(1, count));
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
