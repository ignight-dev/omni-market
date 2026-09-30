package com.omni.marketplace.bank;

import com.omni.marketplace.db.DatabaseManager;
import com.omni.marketplace.util.EmeraldHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class BankMenu extends ChestMenu {

    private final UUID playerUuid;
    private final Container bankContainer;
    private final int rows;

    public BankMenu(int containerId, Inventory playerInventory) {
        this(MenuType.GENERIC_9x3, containerId, playerInventory, new SimpleContainer(27), playerInventory.player.getUUID(), 3);
    }

    public static BankMenu create(int containerId, Inventory playerInventory, Container bankContainer, UUID playerUuid, int rows) {
        MenuType<?> type = switch (rows) {
            case 2 -> MenuType.GENERIC_9x2;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            case 6 -> MenuType.GENERIC_9x6;
            default -> MenuType.GENERIC_9x3;
        };
        return new BankMenu(type, containerId, playerInventory, bankContainer, playerUuid, rows);
    }

    public BankMenu(MenuType<?> type, int containerId, Inventory playerInventory, Container bankContainer, UUID playerUuid, int rows) {
        super(type, containerId, playerInventory, bankContainer, rows);
        this.bankContainer = bankContainer;
        this.playerUuid = playerUuid;
        this.rows = rows;

        if (!playerInventory.player.level().isClientSide && playerInventory.player instanceof ServerPlayer serverPlayer) {
            if (this.bankContainer instanceof SimpleContainer sc) {
                sc.addListener(c -> {
                    DatabaseManager.getInstance().saveBankVault(this.playerUuid, this.bankContainer, serverPlayer.registryAccess());
                });
            }
        }
    }

    public int getRows() {
        return rows;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        int bankSlotCount = this.rows * 9;
        ItemStack itemInSlot = slot.getItem();

        // If moving FROM Bank (slots 0 .. bankSlotCount - 1) TO Player Inventory
        if (index < bankSlotCount) {
            if (itemInSlot.is(Items.EMERALD) || itemInSlot.is(Items.EMERALD_BLOCK)) {
                int pocket = EmeraldHelper.getPocketEmeralds(player);
                int space = EmeraldHelper.MAX_EMERALD_CAPACITY - pocket;
                if (space <= 0) {
                    if (player instanceof ServerPlayer serverPlayer) {
                        serverPlayer.sendSystemMessage(Component.literal("§c[Bank Vault] Pocket pouch is already full (999/999)! Deposit emeralds using the Emerald Reserve button."));
                    }
                    return ItemStack.EMPTY;
                }

                if (player instanceof ServerPlayer serverPlayer) {
                    if (itemInSlot.is(Items.EMERALD)) {
                        int take = Math.min(itemInSlot.getCount(), space);
                        EmeraldHelper.addPocketEmeralds(serverPlayer, take);
                        itemInSlot.shrink(take);
                    } else {
                        int blocksToTake = Math.min(itemInSlot.getCount(), space / 9);
                        if (blocksToTake > 0) {
                            EmeraldHelper.addPocketEmeralds(serverPlayer, blocksToTake * 9);
                            itemInSlot.shrink(blocksToTake);
                        } else {
                            serverPlayer.sendSystemMessage(Component.literal("§c[Bank Vault] Need at least 9 pocket space for an Emerald Block (Space: " + space + "/999)!"));
                            return ItemStack.EMPTY;
                        }
                    }
                    if (itemInSlot.isEmpty()) {
                        slot.set(ItemStack.EMPTY);
                    } else {
                        slot.setChanged();
                    }
                    this.broadcastChanges();
                    serverPlayer.level().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                            net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.4F, 1.2F);
                }
                return itemInSlot;
            }
            if (!this.moveItemStackTo(itemInSlot, bankSlotCount, bankSlotCount + 36, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Moving FROM Player Inventory TO Bank: disallow storing soulbound license
            if (itemInSlot.is(com.omni.marketplace.registry.ModRegistry.MERCHANTS_LICENSE.get())) {
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.sendSystemMessage(Component.literal("§c[Imperial Registry] The Merchant License is soulbound to your person and cannot be stored in containers!"));
                }
                return ItemStack.EMPTY;
            }
            // Move strictly into bank slots
            if (!this.moveItemStackTo(itemInSlot, 0, bankSlotCount, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (itemInSlot.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return itemInSlot;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        int bankSlotCount = this.rows * 9;

        // Disallow placing soulbound license into bank slots
        if (slotId >= 0 && slotId < bankSlotCount) {
            ItemStack cursorStack = this.getCarried();
            if (!cursorStack.isEmpty() && cursorStack.is(com.omni.marketplace.registry.ModRegistry.MERCHANTS_LICENSE.get())) {
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.sendSystemMessage(Component.literal("§c[Imperial Registry] The Merchant License is soulbound to your person and cannot be stored in containers!"));
                }
                return;
            }
        }

        // Guard against clicking cursor with emeralds into player inventory if it exceeds capacity
        if (slotId >= bankSlotCount && slotId < bankSlotCount + 36 && (clickType == ClickType.PICKUP || clickType == ClickType.SWAP)) {
            ItemStack cursorStack = this.getCarried();
            if (!cursorStack.isEmpty() && (cursorStack.is(Items.EMERALD) || cursorStack.is(Items.EMERALD_BLOCK))) {
                int incoming = cursorStack.is(Items.EMERALD_BLOCK) ? cursorStack.getCount() * 9 : cursorStack.getCount();
                if (!EmeraldHelper.canAcceptEmeralds(player, incoming)) {
                    if (player instanceof ServerPlayer serverPlayer) {
                        serverPlayer.sendSystemMessage(Component.literal("§c[Bank Vault] Cannot withdraw: Emerald capacity reached (999 Emeralds max)!"));
                    }
                    return;
                }
            }
        }

        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            DatabaseManager.getInstance().saveBankVault(this.playerUuid, this.bankContainer, serverPlayer.registryAccess());
            serverPlayer.level().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.9F, 1.0F);
        }
    }
}
