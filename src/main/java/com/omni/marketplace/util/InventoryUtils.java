package com.omni.marketplace.util;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class InventoryUtils {

    /**
     * Calculates the maximum number of items matching the probe ItemStack that can fit into the player's
     * main inventory (hotbar and storage slots 0..35). Takes into account empty slots and partial stacks.
     *
     * @param inv The player inventory
     * @param probe The ItemStack to measure space for
     * @return Total quantity of this item that can be accommodated
     */
    public static int getFreeSpaceForItem(Inventory inv, ItemStack probe) {
        if (inv == null || probe == null || probe.isEmpty()) {
            return 0;
        }

        int maxStack = probe.getMaxStackSize();
        int freeSpace = 0;

        // In Minecraft Inventory, slots 0-35 represent the hotbar (0-8) and main storage (9-35).
        // Slots 36-39 are armor and 40 is offhand, which are not targets for regular item additions.
        int mainInventorySize = Math.min(36, inv.getContainerSize());
        for (int i = 0; i < mainInventorySize; i++) {
            ItemStack slot = inv.getItem(i);
            if (slot.isEmpty()) {
                freeSpace += maxStack;
            } else if (ItemStack.isSameItemSameComponents(slot, probe)) {
                freeSpace += Math.max(0, maxStack - slot.getCount());
            }
        }

        return freeSpace;
    }

    /**
     * Checks whether the inventory can fit the entire given stack without dropping any on the ground.
     */
    public static boolean hasRoomFor(Inventory inv, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        return getFreeSpaceForItem(inv, stack) >= stack.getCount();
    }
}
