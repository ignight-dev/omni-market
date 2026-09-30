package com.omni.marketplace;

import com.omni.marketplace.command.MarketplaceCommands;
import com.omni.marketplace.db.DatabaseManager;
import com.omni.marketplace.db.model.MarketModels.AccountSummary;
import com.omni.marketplace.entity.TradingPostMerchantEntity;
import com.omni.marketplace.network.NetworkHandler;
import com.omni.marketplace.network.ServerPayloadHandler;
import com.omni.marketplace.registry.ModRegistry;
import com.omni.marketplace.util.CurrencyUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

@Mod(OmniMarketplace.MOD_ID)
public class OmniMarketplace {
    public static final String MOD_ID = "omni_marketplace";
    private static final Logger LOGGER = LoggerFactory.getLogger(OmniMarketplace.class);

    public OmniMarketplace(IEventBus modEventBus) {
        LOGGER.info("Initializing Omni Marketplace (GW2 Trading Post for NeoForge 1.21.1)...");

        // Register registries & networking
        ModRegistry.register(modEventBus);
        modEventBus.addListener(NetworkHandler::register);
        modEventBus.addListener(OmniMarketplace::registerAttributes);

        // Register NeoForge game events
        NeoForge.EVENT_BUS.register(this);
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModRegistry.TRADING_POST_MERCHANT.get(), TradingPostMerchantEntity.createAttributes().build());
        event.put(ModRegistry.MERCHANT_MASTER.get(), com.omni.marketplace.entity.MerchantMasterEntity.createAttributes().build());
        event.put(ModRegistry.BANKER.get(), com.omni.marketplace.entity.BankerEntity.createAttributes().build());
    }

    @SubscribeEvent
    public void onItemPickup(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre event) {
        ItemStack stack = event.getItemEntity().getItem();
        if (stack.is(net.minecraft.world.item.Items.EMERALD) || stack.is(net.minecraft.world.item.Items.EMERALD_BLOCK)) {
            net.minecraft.world.entity.player.Player player = event.getPlayer();
            int current = com.omni.marketplace.util.EmeraldHelper.getPocketEmeralds(player);
            int space = com.omni.marketplace.util.EmeraldHelper.MAX_EMERALD_CAPACITY - current;

            // If pocket counter is already at max capacity (999/999), excess emeralds are stored as items in player inventory!
            if (space <= 0) {
                return; // Let vanilla pick up physical items into inventory slots
            }

            if (player instanceof ServerPlayer serverPlayer) {
                if (stack.is(net.minecraft.world.item.Items.EMERALD)) {
                    int toTake = Math.min(stack.getCount(), space);
                    if (toTake > 0) {
                        com.omni.marketplace.util.EmeraldHelper.addPocketEmeralds(serverPlayer, toTake);
                        serverPlayer.take(event.getItemEntity(), toTake);
                        serverPlayer.level().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                                net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.2F,
                                (serverPlayer.getRandom().nextFloat() - serverPlayer.getRandom().nextFloat()) * 0.2F + 1.0F);
                        stack.shrink(toTake);
                        if (stack.isEmpty()) {
                            event.getItemEntity().discard();
                            // Whole stack went to counter, prevent vanilla from adding empty stack
                            event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
                        }
                        // If stack still has items, remainder is excess and vanilla will pick it up into inventory!
                    }
                } else {
                    int blocksToTake = Math.min(stack.getCount(), space / 9);
                    if (blocksToTake > 0) {
                        com.omni.marketplace.util.EmeraldHelper.addPocketEmeralds(serverPlayer, blocksToTake * 9);
                        serverPlayer.take(event.getItemEntity(), blocksToTake);
                        serverPlayer.level().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                                net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.2F,
                                (serverPlayer.getRandom().nextFloat() - serverPlayer.getRandom().nextFloat()) * 0.2F + 1.0F);
                        stack.shrink(blocksToTake);
                        if (stack.isEmpty()) {
                            event.getItemEntity().discard();
                            event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
                        }
                        // If blocks remain, remainder is excess and vanilla will pick it up into inventory!
                    }
                    // If space < 9, cannot absorb a block, so vanilla will pick it up into inventory!
                }
            }
        } else if (stack.is(ModRegistry.MERCHANTS_LICENSE.get())) {
            net.minecraft.world.entity.player.Player player = event.getPlayer();
            net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (customData != null && customData.contains("OwnerUUID")) {
                String ownerUUID = customData.copyTag().getString("OwnerUUID");
                if (!ownerUUID.isEmpty() && !ownerUUID.equalsIgnoreCase(player.getUUID().toString())) {
                    event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
                }
            }
        }
    }

    @SubscribeEvent
    public void onItemToss(net.neoforged.neoforge.event.entity.item.ItemTossEvent event) {
        if (event.getEntity().getItem().is(ModRegistry.MERCHANTS_LICENSE.get())) {
            event.setCanceled(true);
            net.minecraft.world.entity.player.Player player = event.getPlayer();
            if (player != null) {
                player.displayClientMessage(Component.literal("§c[Imperial Registry] The Merchant License cannot be dropped! Right-click to consume it."), true);
                ItemStack stack = event.getEntity().getItem().copy();
                if (player.getInventory().getSelected().isEmpty()) {
                    player.getInventory().setItem(player.getInventory().selected, stack);
                } else if (!player.getInventory().add(stack)) {
                    player.containerMenu.setCarried(stack);
                }
                player.containerMenu.broadcastChanges();
                if (player instanceof ServerPlayer sp) {
                    sp.inventoryMenu.broadcastFullState();
                    if (sp.containerMenu != null) {
                        sp.containerMenu.broadcastFullState();
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onEntityInteract(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        ItemStack item = event.getItemStack();
        if (!item.isEmpty() && item.is(ModRegistry.MERCHANTS_LICENSE.get())) {
            net.minecraft.world.entity.Entity target = event.getTarget();
            // Block placing on item frames, armor stands, or giving to any mob
            if (target instanceof net.minecraft.world.entity.decoration.ItemFrame ||
                target instanceof net.minecraft.world.entity.decoration.ArmorStand ||
                target instanceof net.minecraft.world.entity.animal.allay.Allay ||
                target instanceof net.minecraft.world.entity.animal.Fox ||
                target instanceof net.minecraft.world.entity.monster.piglin.Piglin ||
                target instanceof net.minecraft.world.entity.npc.Villager) {
                event.setCanceled(true);
                event.getEntity().displayClientMessage(Component.literal("§c[Imperial Registry] The Merchant License is soulbound and cannot be transferred or given to entities!"), true);
            }
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        ItemStack item = event.getItemStack();
        if (!item.isEmpty() && item.is(ModRegistry.MERCHANTS_LICENSE.get())) {
            net.minecraft.world.level.block.entity.BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
            if (be != null) {
                // Prevent depositing license into Decorated Pots, Campfires, Jukeboxes, Hoppers, etc.
                event.setCanceled(true);
                event.getEntity().displayClientMessage(Component.literal("§c[Imperial Registry] The Merchant License is soulbound and cannot be stored inside blocks!"), true);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            event.getDrops().removeIf(itemEntity -> itemEntity.getItem().is(ModRegistry.MERCHANTS_LICENSE.get()));
        }
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath() && event.getEntity() instanceof ServerPlayer newPlayer) {
            // If player died while holding an unconsumed Merchant License deed, preserve it
            net.minecraft.world.entity.player.Player oldPlayer = event.getOriginal();
            for (int i = 0; i < oldPlayer.getInventory().getContainerSize(); i++) {
                ItemStack stack = oldPlayer.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.is(ModRegistry.MERCHANTS_LICENSE.get())) {
                    newPlayer.getInventory().add(stack.copy());
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 1. Auto-absorb any physical emeralds in inventory into the pocket counter (max 999)
            absorbPhysicalEmeralds(player);

            // 2. Guard against depositing soulbound license into external containers
            if (player.containerMenu != player.inventoryMenu) {
                int totalSlots = player.containerMenu.slots.size();
                int externalSlots = totalSlots - 36;
                for (int i = 0; i < externalSlots; i++) {
                    net.minecraft.world.inventory.Slot slot = player.containerMenu.getSlot(i);
                    ItemStack stack = slot.getItem();
                    if (!stack.isEmpty() && stack.is(ModRegistry.MERCHANTS_LICENSE.get())) {
                        net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                        String ownerUUID = (customData != null && customData.contains("OwnerUUID"))
                                ? customData.copyTag().getString("OwnerUUID") : "";

                        slot.set(ItemStack.EMPTY);
                        player.containerMenu.broadcastChanges();

                        if (ownerUUID.isEmpty() || ownerUUID.equalsIgnoreCase(player.getUUID().toString())) {
                            if (!player.getInventory().add(stack)) {
                                player.containerMenu.setCarried(stack);
                            }
                            player.displayClientMessage(Component.literal("§c[Imperial Registry] The Merchant License is soulbound to you and cannot be stored in external containers!"), true);
                        } else {
                            player.displayClientMessage(Component.literal("§c[Imperial Registry] Confiscated a soulbound Merchant License belonging to another citizen!"), true);
                        }
                    }
                }
            }
        }
    }

    private void absorbPhysicalEmeralds(ServerPlayer player) {
        int currentPocket = com.omni.marketplace.util.EmeraldHelper.getPocketEmeralds(player);
        int space = com.omni.marketplace.util.EmeraldHelper.MAX_EMERALD_CAPACITY - currentPocket;
        if (space <= 0) return;

        int totalAbsorbed = 0;
        for (int i = 0; i < player.getInventory().getContainerSize() && space > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.is(net.minecraft.world.item.Items.EMERALD)) {
                int toTake = Math.min(stack.getCount(), space);
                stack.shrink(toTake);
                totalAbsorbed += toTake;
                space -= toTake;
                if (stack.isEmpty()) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                }
            } else if (stack.is(net.minecraft.world.item.Items.EMERALD_BLOCK)) {
                int blocksToTake = Math.min(stack.getCount(), space / 9);
                if (blocksToTake > 0) {
                    stack.shrink(blocksToTake);
                    int val = blocksToTake * 9;
                    totalAbsorbed += val;
                    space -= val;
                    if (stack.isEmpty()) {
                        player.getInventory().setItem(i, ItemStack.EMPTY);
                    }
                }
            }
        }

        if (totalAbsorbed > 0) {
            com.omni.marketplace.util.EmeraldHelper.addPocketEmeralds(player, totalAbsorbed);
            player.containerMenu.broadcastChanges();
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.4F, 1.2F);
            player.displayClientMessage(Component.literal("§6❖ [Emerald Pouch] §aAbsorbed physical emeralds into pocket counter! (" + (currentPocket + totalAbsorbed) + "/999)"), true);
        }
    }

    private static net.minecraft.server.MinecraftServer currentServer = null;

    public static net.minecraft.server.MinecraftServer getServer() {
        return currentServer;
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        currentServer = event.getServer();
        com.omni.marketplace.config.MarketConfig.get().initialize(event.getServer().getServerDirectory());
        Path worldDir = event.getServer().getWorldPath(LevelResource.ROOT);
        LOGGER.info("Starting Omni Marketplace SQLite Database at world directory: {}", worldDir);
        DatabaseManager.getInstance().initialize(worldDir);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Shutting down Omni Marketplace SQLite Database...");
        DatabaseManager.getInstance().close();
        currentServer = null;
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        MarketplaceCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerPayloadHandler.syncAccountAndCatalog(player);
            com.omni.marketplace.util.EmeraldHelper.syncEmeralds(player);

            DatabaseManager db = DatabaseManager.getInstance();
            AccountSummary acc = db.getOrCreateAccount(player.getUUID(), player.getScoreboardName());
            if (acc.vaultItemCount() > 0 || acc.vaultCopperAmount() > 0) {
                player.sendSystemMessage(Component.literal(String.format(
                        "§6[Trading Post] 📥 Welcome back! You have §e%d item(s) §6and §e%s §6waiting in your Guild Vault. Visit a Trading Post Merchant to claim them!",
                        acc.vaultItemCount(),
                        CurrencyUtils.format(acc.vaultCopperAmount())
                )));
            }
        }
    }


    @SubscribeEvent
    public void onLootTableLoad(LootTableLoadEvent event) {
        String path = event.getName().getPath();
        if (path.startsWith("chests/")) {
            float chance = getChestLootChance(path);
            if (chance > 0.0F) {
                LootPool pool = LootPool.lootPool()
                        .name("omni_marketplace_dispatch_book")
                        .setRolls(ConstantValue.exactly(1.0F))
                        .when(LootItemRandomChanceCondition.randomChance(chance))
                        .add(LootItem.lootTableItem(ModRegistry.MARKETPLACE_TRANSCEIVER.get()))
                        .build();
                event.getTable().addPool(pool);
            }
        }
    }

    private static float getChestLootChance(String path) {
        if (path.contains("ancient_city") || path.contains("end_city")) return 0.40F;
        if (path.contains("stronghold") || path.contains("woodland_mansion")) return 0.35F;
        if (path.contains("bastion") || path.contains("jungle_temple") || path.contains("buried_treasure")) return 0.30F;
        if (path.contains("simple_dungeon") || path.contains("desert_pyramid") || path.contains("shipwreck") || path.contains("pillager_outpost") || path.contains("nether_bridge")) return 0.25F;
        if (path.contains("abandoned_mineshaft") || path.contains("trial_chambers")) return 0.20F;
        if (path.contains("village")) return 0.15F;
        if (!path.contains("spawn_bonus_chest")) return 0.15F;
        return 0.0F;
    }
}
