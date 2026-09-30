package com.omni.marketplace.util;

import com.omni.marketplace.db.DatabaseManager;
import com.omni.marketplace.network.MarketPackets.SyncEmeraldsS2C;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

public class EmeraldHelper {

    public static final int MAX_EMERALD_CAPACITY = 999;
    public static final int MAX_VAULT_CAPACITY = 9_999_999;

    private static int clientPocketEmeralds = 0;
    private static int clientVaultEmeralds = 0;

    public static void setClientEmeralds(int pocket, int vault) {
        clientPocketEmeralds = Math.max(0, Math.min(MAX_EMERALD_CAPACITY, pocket));
        clientVaultEmeralds = Math.max(0, Math.min(MAX_VAULT_CAPACITY, vault));
    }

    public static int getPocketEmeralds(Player player) {
        if (player == null) return 0;
        if (player.level().isClientSide) {
            return clientPocketEmeralds;
        }
        return DatabaseManager.getInstance().getPocketEmeralds(player.getUUID());
    }

    public static int getVaultEmeralds(Player player) {
        if (player == null) return 0;
        if (player.level().isClientSide) {
            return clientVaultEmeralds;
        }
        return DatabaseManager.getInstance().getVaultEmeralds(player.getUUID());
    }

    public static void setPocketEmeralds(Player player, int amount) {
        if (player == null) return;
        int clamped = Math.max(0, Math.min(MAX_EMERALD_CAPACITY, amount));
        if (player.level().isClientSide) {
            clientPocketEmeralds = clamped;
        } else {
            DatabaseManager.getInstance().setPocketEmeralds(player.getUUID(), clamped);
            if (player instanceof ServerPlayer serverPlayer) {
                syncEmeralds(serverPlayer);
            }
        }
    }

    public static int addPocketEmeralds(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) return 0;
        int current = getPocketEmeralds(player);
        int space = MAX_EMERALD_CAPACITY - current;
        int added = Math.min(amount, space);
        if (added > 0) {
            setPocketEmeralds(player, current + added);
        }
        return added;
    }

    public static int getRawEmeralds(Player player) {
        if (player == null) return 0;
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(Items.EMERALD)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static int getEmeraldBlockItems(Player player) {
        if (player == null) return 0;
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(Items.EMERALD_BLOCK)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * Calculates the total emeralds held by the player:
     * Stored in virtual pocket pouch + any remaining physical inventory emeralds.
     */
    public static int getTotalEmeralds(Player player) {
        return getPocketEmeralds(player) + getRawEmeralds(player) + (getEmeraldBlockItems(player) * 9);
    }

    /**
     * Calculates the equivalent number of 9-emerald blocks from the player's total emeralds.
     */
    public static int getEmeraldBlocks(Player player) {
        return getTotalEmeralds(player) / 9;
    }

    /**
     * Returns true if the player has capacity for the given number of emeralds without exceeding 999.
     */
    public static boolean canAcceptEmeralds(Player player, int emeraldsToAdd) {
        return (getPocketEmeralds(player) + emeraldsToAdd) <= MAX_EMERALD_CAPACITY;
    }

    /**
     * Consumes the specified total count of emeralds from the player.
     * Consumes from virtual pocket emeralds first, then leftover physical items if necessary.
     */
    public static boolean consumeEmeralds(ServerPlayer player, int emeraldsNeeded) {
        if (getTotalEmeralds(player) < emeraldsNeeded) {
            return false;
        }

        int remaining = emeraldsNeeded;
        int pocket = getPocketEmeralds(player);
        int takeFromPocket = Math.min(pocket, remaining);
        if (takeFromPocket > 0) {
            setPocketEmeralds(player, pocket - takeFromPocket);
            remaining -= takeFromPocket;
        }

        // If more is needed and there are still physical emeralds in inventory:
        if (remaining > 0) {
            // 1. Consume raw emeralds first
            for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.is(Items.EMERALD)) {
                    int toTake = Math.min(stack.getCount(), remaining);
                    stack.shrink(toTake);
                    remaining -= toTake;
                    if (stack.isEmpty()) {
                        player.getInventory().setItem(i, ItemStack.EMPTY);
                    }
                }
            }

            // 2. Consume emerald blocks if still needed
            while (remaining > 0) {
                boolean foundBlock = false;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (!stack.isEmpty() && stack.is(Items.EMERALD_BLOCK)) {
                        stack.shrink(1);
                        if (stack.isEmpty()) {
                            player.getInventory().setItem(i, ItemStack.EMPTY);
                        }
                        if (remaining >= 9) {
                            remaining -= 9;
                        } else {
                            int change = 9 - remaining;
                            remaining = 0;
                            addPocketEmeralds(player, change);
                        }
                        foundBlock = true;
                        break;
                    }
                }
                if (!foundBlock) break;
            }

            player.containerMenu.broadcastChanges();
        }

        syncEmeralds(player);
        return true;
    }

    /**
     * Consumes the equivalent number of emerald blocks (blocksNeeded * 9).
     */
    public static boolean consumeEmeraldBlocks(ServerPlayer player, int blocksNeeded) {
        return consumeEmeralds(player, blocksNeeded * 9);
    }

    /**
     * Syncs pocket and vault emerald balances to the client.
     */
    public static void syncEmeralds(ServerPlayer player) {
        if (player == null) return;
        int pocket = getPocketEmeralds(player);
        int vault = getVaultEmeralds(player);
        PacketDistributor.sendToPlayer(player, new SyncEmeraldsS2C(pocket, vault));
    }
}
