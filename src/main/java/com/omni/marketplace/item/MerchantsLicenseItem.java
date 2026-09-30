package com.omni.marketplace.item;

import com.omni.marketplace.db.DatabaseManager;
import com.omni.marketplace.registry.ModRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

public class MerchantsLicenseItem extends Item {

    public MerchantsLicenseItem(Properties properties) {
        super(properties);
    }

    public static ItemStack createForPlayer(Player player) {
        ItemStack stack = new ItemStack(ModRegistry.MERCHANTS_LICENSE.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("OwnerUUID", player.getUUID().toString());
        tag.putString("OwnerName", player.getScoreboardName());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        player.displayClientMessage(Component.literal("§c[Imperial Registry] The Merchant License cannot be dropped! Right-click to consume it."), true);

        // Put the item right back into the selected hotbar slot (or inventory) on both client and server!
        if (player.getInventory().getSelected().isEmpty()) {
            player.getInventory().setItem(player.getInventory().selected, item);
        } else if (!player.getInventory().add(item)) {
            player.containerMenu.setCarried(item);
        }

        player.containerMenu.broadcastChanges();
        if (player instanceof ServerPlayer sp) {
            sp.inventoryMenu.broadcastFullState();
            if (sp.containerMenu != null) {
                sp.containerMenu.broadcastFullState();
            }
        }
        return false;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action, Player player) {
        if (!slot.getItem().isEmpty() && slot.getItem().getItem() instanceof BundleItem) {
            player.displayClientMessage(Component.literal("§c[Imperial Registry] The Merchant License is soulbound and cannot be stored in a bundle!"), true);
            return true;
        }
        return super.overrideStackedOnOther(stack, slot, action, player);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (!other.isEmpty() && other.getItem() instanceof BundleItem) {
            player.displayClientMessage(Component.literal("§c[Imperial Registry] The Merchant License is soulbound and cannot be stored in a bundle!"), true);
            return true;
        }
        return super.overrideOtherStackedOnMe(stack, other, slot, action, player, access);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        if (!level.isClientSide) {
            DatabaseManager db = DatabaseManager.getInstance();
            if (db.isPlayerLicensed(player.getUUID())) {
                player.displayClientMessage(Component.literal("§e[Imperial Registry] You already have an active Imperial Merchant License!"), true);
                return InteractionResultHolder.fail(held);
            }

            // Consume 1 license item
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }

            // Permanently record license in SQLite database
            db.setPlayerLicensed(player.getUUID(), player.getScoreboardName(), true);

            // Play triumphant celebration sounds
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.2F);

            // Particles
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.6, 0.4, 0.1);
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
            }

            player.sendSystemMessage(Component.literal("§6❖ [Imperial Registry] §aYou have consumed the §6Imperial Merchant License§a deed!"));
            player.sendSystemMessage(Component.literal("§e❖ Your merchant license is now permanently registered in guild archives! You can now freely list goods and trade without carrying a deed."));
            player.displayClientMessage(Component.literal("§6❖ Imperial Merchant License Permanently Activated!"), true);

            if (player instanceof ServerPlayer sp) {
                sp.inventoryMenu.broadcastFullState();
            }
        }

        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§6❖ Official Imperial Merchant License ❖"));
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.contains("OwnerName")) {
            String ownerName = customData.copyTag().getString("OwnerName");
            tooltip.add(Component.literal("§7Issued to: §e" + ownerName));
        }
        tooltip.add(Component.literal("§e✦ CONSUMABLE DEED"));
        tooltip.add(Component.literal("§a▶ Right-Click to consume & activate"));
        tooltip.add(Component.literal("§7Once consumed, your trading rights are"));
        tooltip.add(Component.literal("§7permanently recorded in the guild archives."));
        tooltip.add(Component.literal("§8❖ No inventory space needed after activation"));
        tooltip.add(Component.literal("§c🔒 Cannot be dropped, traded, or stored in blocks."));
    }
}

