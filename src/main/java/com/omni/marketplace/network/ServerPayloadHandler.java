package com.omni.marketplace.network;

import com.omni.marketplace.catalog.CategoryDef;
import com.omni.marketplace.catalog.ItemCatalog;
import com.omni.marketplace.db.DatabaseManager;
import com.omni.marketplace.db.model.MarketModels.*;
import com.omni.marketplace.network.MarketPackets.*;
import com.omni.marketplace.util.CurrencyUtils;
import com.omni.marketplace.util.EmeraldHelper;
import com.omni.marketplace.util.InventoryUtils;
import com.omni.marketplace.util.ItemSerializer;
import com.omni.marketplace.util.LicenseHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.omni.marketplace.registry.ModRegistry;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public class ServerPayloadHandler {

    public static void handleRequestCatalog(RequestCatalogC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            int limit = 50;
            List<CatalogEntry> entries = ItemCatalog.searchItems(
                    payload.query(),
                    payload.category(),
                    payload.subCategory(),
                    payload.page(),
                    limit
            );
            PacketDistributor.sendToPlayer(player, new SyncCatalogS2C(entries));
        });
    }

    public static void handleCreateListing(CreateListingC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!LicenseHelper.hasLicense(player)) {
                player.sendSystemMessage(Component.literal("§c[Trading Post] Access Denied: You must possess a §6Merchant's License §cto list goods for sale! Purchase one from the Grand Merchant Master."));
                return;
            }
            if (payload.count() <= 0 || payload.count() > 64000 || payload.unitPrice() <= 0 || payload.unitPrice() > 1_000_000_000L) {
                player.sendSystemMessage(Component.literal("§cInvalid price or quantity."));
                return;
            }

            int slot = payload.inventorySlot();
            if (slot < 0 || slot >= player.getInventory().getContainerSize()) return;

            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || stack.getCount() < payload.count()) {
                player.sendSystemMessage(Component.literal("§cItem not found in inventory."));
                return;
            }

            if (CategoryDef.isEmeraldCurrency(stack.getItem())) {
                player.sendSystemMessage(Component.literal("§cEmeralds cannot be listed on the market. Use Currency Exchange."));
                return;
            }

            if (stack.is(ModRegistry.MERCHANTS_LICENSE.get())) {
                player.sendSystemMessage(Component.literal("§cThe Imperial Merchant License is soulbound and cannot be listed on the market!"));
                return;
            }

            // Check if player has funds for the 5% listing fee BEFORE touching the items!
            long listingFee = CurrencyUtils.calculateListingFee(payload.unitPrice(), payload.count());
            DatabaseManager db = DatabaseManager.getInstance();
            AccountSummary acc = db.getOrCreateAccount(player.getUUID(), player.getScoreboardName());
            if (acc.copperBalance() < listingFee) {
                player.sendSystemMessage(Component.literal("§cCannot list item: Insufficient funds to pay the 5% listing fee (" + CurrencyUtils.format(listingFee) + "). Your wallet: " + CurrencyUtils.format(acc.copperBalance())));
                return; // DO NOT remove the item!
            }

            ResourceLocation itemLoc = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String itemId = itemLoc.toString();
            String itemNbt = ItemSerializer.serialize(stack, player.level().registryAccess());

            DatabaseManager.ListingResult result = db.createSellListing(
                    player.getUUID(),
                    player.getScoreboardName(),
                    itemId,
                    itemNbt,
                    payload.unitPrice(),
                    payload.count()
            );

            if (result.success()) {
                // ONLY remove items from player inventory after listing is successfully registered!
                stack.shrink(payload.count());
                if (stack.isEmpty()) {
                    player.getInventory().setItem(slot, ItemStack.EMPTY);
                }
                player.sendSystemMessage(Component.literal(result.message()));
            } else {
                player.sendSystemMessage(Component.literal("§c" + result.message()));
            }

            syncAccountAndCatalog(player);
        });
    }

    public static void handleCreateBuyOrder(CreateBuyOrderC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (payload.count() <= 0 || payload.count() > 64000 || payload.unitPrice() <= 0 || payload.unitPrice() > 1_000_000_000L) {
                player.sendSystemMessage(Component.literal("§cInvalid price or quantity."));
                return;
            }

            if (payload.itemId().equals("omni_marketplace:merchants_license") || CategoryDef.isEmeraldCurrency(payload.itemId())) {
                player.sendSystemMessage(Component.literal("§cThis item is non-transferable and cannot be traded on the open market!"));
                return;
            }

            DatabaseManager db = DatabaseManager.getInstance();
            DatabaseManager.ListingResult result = db.createBuyOrder(
                    player.getUUID(),
                    player.getScoreboardName(),
                    payload.itemId(),
                    payload.itemNbt(),
                    payload.unitPrice(),
                    payload.count()
            );

            if (result.success()) {
                player.sendSystemMessage(Component.literal(result.message()));
            } else {
                player.sendSystemMessage(Component.literal("§c" + result.message()));
            }
            syncAccountAndCatalog(player);
        });
    }

    public static void handleInstantBuy(InstantBuyC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (payload.count() <= 0 || payload.count() > 64000) {
                player.sendSystemMessage(Component.literal("§cInvalid quantity."));
                return;
            }

            DatabaseManager db = DatabaseManager.getInstance();
            DatabaseManager.ListingResult result;
            if (payload.listingId() > 0) {
                result = db.instantBuyFromListing(player.getUUID(), player.getScoreboardName(), payload.listingId(), payload.count());
            } else {
                result = db.instantBuyByItem(player.getUUID(), player.getScoreboardName(), payload.itemId(), payload.count());
            }

            if (result.success()) {
                player.sendSystemMessage(Component.literal(result.message()));
            } else {
                player.sendSystemMessage(Component.literal("§c" + result.message()));
            }
            syncAccountAndCatalog(player);
        });
    }

    public static void handleInstantSell(InstantSellC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!LicenseHelper.hasLicense(player)) {
                player.sendSystemMessage(Component.literal("§c[Trading Post] Access Denied: You must possess a §6Merchant's License §cto sell goods on the market! Purchase one from the Grand Merchant Master."));
                return;
            }
            if (payload.count() <= 0 || payload.count() > 64000) {
                player.sendSystemMessage(Component.literal("§cInvalid quantity."));
                return;
            }

            int slot = payload.inventorySlot();
            if (slot < 0 || slot >= player.getInventory().getContainerSize()) return;

            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || stack.getCount() < payload.count()) {
                player.sendSystemMessage(Component.literal("§cItem not found in inventory."));
                return;
            }

            if (CategoryDef.isEmeraldCurrency(stack.getItem())) {
                player.sendSystemMessage(Component.literal("§cEmeralds cannot be traded on the open market."));
                return;
            }

            if (stack.is(ModRegistry.MERCHANTS_LICENSE.get())) {
                player.sendSystemMessage(Component.literal("§cThe Imperial Merchant License is soulbound and cannot be sold!"));
                return;
            }

            String itemNbt = ItemSerializer.serialize(stack, player.level().registryAccess());
            ResourceLocation itemLoc = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String itemId = itemLoc.toString();

            DatabaseManager db = DatabaseManager.getInstance();
            DatabaseManager.ListingResult result;
            if (payload.buyOrderId() > 0) {
                result = db.instantSellToBuyOrder(
                        player.getUUID(),
                        player.getScoreboardName(),
                        payload.buyOrderId(),
                        payload.count(),
                        itemNbt
                );
            } else {
                result = db.instantSellByItem(
                        player.getUUID(),
                        player.getScoreboardName(),
                        itemId,
                        payload.count(),
                        itemNbt
                );
            }

            if (result.success()) {
                // ONLY remove items from inventory that were actually fulfilled!
                int actuallySold = payload.count() - result.remainingQuantity();
                if (actuallySold > 0) {
                    stack.shrink(actuallySold);
                    if (stack.isEmpty()) {
                        player.getInventory().setItem(slot, ItemStack.EMPTY);
                    }
                }
                player.sendSystemMessage(Component.literal(result.message()));
            } else {
                player.sendSystemMessage(Component.literal("§c" + result.message()));
            }

            syncAccountAndCatalog(player);
        });
    }

    public static void handleCancelOrder(CancelOrderC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DatabaseManager db = DatabaseManager.getInstance();
            boolean success;
            if (payload.isBuyOrder()) {
                success = db.cancelBuyOrder(player.getUUID(), payload.orderId());
            } else {
                success = db.cancelListing(player.getUUID(), payload.orderId());
            }

            if (success) {
                player.sendSystemMessage(Component.translatable("message.omni_marketplace.order_cancelled"));
            } else {
                player.sendSystemMessage(Component.literal("§cFailed to cancel order or order already fulfilled."));
            }
            syncAccountAndCatalog(player);
            sendTransactions(player);
        });
    }

    public static void handleClaimVault(ClaimVaultC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DatabaseManager db = DatabaseManager.getInstance();
            DatabaseManager.VaultClaimResult claimResult = db.claimAllVault(player, player.level().registryAccess());

            if (claimResult.itemsClaimed() > 0 || claimResult.coinsClaimed() > 0) {
                player.sendSystemMessage(Component.translatable("message.omni_marketplace.vault_claimed",
                        claimResult.itemsClaimed(),
                        CurrencyUtils.format(claimResult.coinsClaimed())));
            } else if (!claimResult.inventoryFull()) {
                player.sendSystemMessage(Component.literal("§7Your Guild Vault is empty."));
            }

            if (claimResult.inventoryFull()) {
                player.sendSystemMessage(Component.translatable("message.omni_marketplace.inventory_full_vault", claimResult.itemsRemaining()));
            }
            syncAccountAndCatalog(player);
        });
    }

    public static void handleCurrencyExchange(CurrencyExchangeC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DatabaseManager db = DatabaseManager.getInstance();

            switch (payload.action()) {
                case 0 -> { // Deposit 1 Emerald (+100c)
                    if (EmeraldHelper.consumeEmeralds(player, 1)) {
                        db.depositCopper(player.getUUID(), CurrencyUtils.COPPER_PER_EMERALD);
                        player.sendSystemMessage(Component.literal("§aDeposited 1 Emerald (+100c) into market wallet."));
                    } else {
                        player.sendSystemMessage(Component.literal("§cYou don't have an Emerald to deposit!"));
                    }
                }
                case 1 -> { // Deposit 1 Emerald Block (+900c)
                    if (EmeraldHelper.consumeEmeralds(player, 9)) {
                        db.depositCopper(player.getUUID(), CurrencyUtils.COPPER_PER_EMERALD_BLOCK);
                        player.sendSystemMessage(Component.literal("§aDeposited 1 Emerald Block value (+900c) into market wallet."));
                    } else {
                        player.sendSystemMessage(Component.literal("§cYou don't have enough emeralds for a block (Requires 9 Emeralds)!"));
                    }
                }
                case 2 -> { // Withdraw Emerald (-100c)
                    if (!EmeraldHelper.canAcceptEmeralds(player, 1)) {
                        player.sendSystemMessage(Component.literal("§cCannot withdraw: Maximum emerald capacity reached (999 Emeralds max)! Deposit into Bank Vault."));
                        break;
                    }
                    if (db.withdrawCopper(player.getUUID(), CurrencyUtils.COPPER_PER_EMERALD)) {
                        EmeraldHelper.addPocketEmeralds(player, 1);
                        player.sendSystemMessage(Component.literal("§aWithdrew 1 Emerald (-100c) into pocket counter."));
                    } else {
                        player.sendSystemMessage(Component.literal("§cInsufficient copper (Requires 100c / 1s)!"));
                    }
                }
                case 3 -> { // Withdraw Emerald Block (-900c)
                    if (!EmeraldHelper.canAcceptEmeralds(player, 9)) {
                        player.sendSystemMessage(Component.literal("§cCannot withdraw: Maximum emerald capacity reached (999 Emeralds max)! Deposit into Bank Vault."));
                        break;
                    }
                    if (db.withdrawCopper(player.getUUID(), CurrencyUtils.COPPER_PER_EMERALD_BLOCK)) {
                        EmeraldHelper.addPocketEmeralds(player, 9);
                        player.sendSystemMessage(Component.literal("§aWithdrew 1 Emerald Block value (+9 Emeralds, -900c) into pocket counter."));
                    } else {
                        player.sendSystemMessage(Component.literal("§cInsufficient copper (Requires 900c / 9s)!"));
                    }
                }
                case 4 -> { // Deposit ALL Emeralds in pocket
                    int pocket = EmeraldHelper.getPocketEmeralds(player);
                    if (pocket > 0 && EmeraldHelper.consumeEmeralds(player, pocket)) {
                        long total = pocket * CurrencyUtils.COPPER_PER_EMERALD;
                        db.depositCopper(player.getUUID(), total);
                        player.sendSystemMessage(Component.literal("§aDeposited " + pocket + " Emeralds (+" + CurrencyUtils.format(total) + ") into market wallet."));
                    } else {
                        player.sendSystemMessage(Component.literal("§cYou don't have any pocket emeralds to deposit!"));
                    }
                }
                case 5 -> { // Deposit ALL Emerald Blocks in pocket
                    int pocket = EmeraldHelper.getPocketEmeralds(player);
                    int blocks = pocket / 9;
                    if (blocks > 0 && EmeraldHelper.consumeEmeralds(player, blocks * 9)) {
                        long total = blocks * CurrencyUtils.COPPER_PER_EMERALD_BLOCK;
                        db.depositCopper(player.getUUID(), total);
                        player.sendSystemMessage(Component.literal("§aDeposited " + blocks + " Emerald Blocks worth (+" + CurrencyUtils.format(total) + ") into market wallet."));
                    } else {
                        player.sendSystemMessage(Component.literal("§cYou don't have enough emeralds to deposit a block (Requires 9 Emeralds)!"));
                    }
                }
                case 6 -> { // Withdraw 64 Emeralds (-6400c)
                    if (!EmeraldHelper.canAcceptEmeralds(player, 64)) {
                        player.sendSystemMessage(Component.literal("§cCannot withdraw: Pocket capacity cannot fit 64 Emeralds (Limit: 999)! Deposit into Bank Vault first."));
                        break;
                    }
                    long cost = CurrencyUtils.COPPER_PER_EMERALD * 64;
                    if (db.withdrawCopper(player.getUUID(), cost)) {
                        EmeraldHelper.addPocketEmeralds(player, 64);
                        player.sendSystemMessage(Component.literal("§aWithdrew 64 Emeralds (-" + CurrencyUtils.format(cost) + ") into pocket counter."));
                    } else {
                        player.sendSystemMessage(Component.literal("§cInsufficient copper (Requires " + CurrencyUtils.format(cost) + ")!"));
                    }
                }
                case 7 -> { // Withdraw 64 Emerald Blocks (-57600c)
                    if (!EmeraldHelper.canAcceptEmeralds(player, 576)) {
                        player.sendSystemMessage(Component.literal("§cCannot withdraw: Pocket capacity cannot fit 576 Emeralds (Limit: 999)! Deposit into Bank Vault first."));
                        break;
                    }
                    long cost = CurrencyUtils.COPPER_PER_EMERALD_BLOCK * 64;
                    if (db.withdrawCopper(player.getUUID(), cost)) {
                        EmeraldHelper.addPocketEmeralds(player, 576);
                        player.sendSystemMessage(Component.literal("§aWithdrew 64 Blocks value (+576 Emeralds, -" + CurrencyUtils.format(cost) + ") into pocket counter."));
                    } else {
                        player.sendSystemMessage(Component.literal("§cInsufficient copper (Requires " + CurrencyUtils.format(cost) + ")!"));
                    }
                }
            }
            syncAccountAndCatalog(player);
        });
    }

    public static void handleRequestItemDetails(RequestItemDetailsC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DatabaseManager db = DatabaseManager.getInstance();

            List<ListingRecord> listings = db.getListingsForItem(payload.itemId());
            List<BuyOrderRecord> buyOrders = db.getBuyOrdersForItem(payload.itemId());

            List<OrderEntry> asks = new ArrayList<>();
            for (ListingRecord l : listings) {
                asks.add(new OrderEntry(l.id(), l.sellerName(), l.itemId(), l.itemNbt() != null ? l.itemNbt() : "", l.priceCopper(), l.quantity(), l.createdAt()));
            }

            List<OrderEntry> bids = new ArrayList<>();
            for (BuyOrderRecord b : buyOrders) {
                bids.add(new OrderEntry(b.id(), b.buyerName(), b.itemId(), b.itemNbt() != null ? b.itemNbt() : "", b.priceCopper(), b.quantity(), b.createdAt()));
            }

            PacketDistributor.sendToPlayer(player, new SyncOrderBookS2C(payload.itemId(), asks, bids));
        });
    }

    public static void handleRequestTransactions(RequestTransactionsC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            sendTransactions(player);
        });
    }

    public static void sendTransactions(ServerPlayer player) {
        DatabaseManager db = DatabaseManager.getInstance();
        List<ListingRecord> listings = db.getPlayerListings(player.getUUID());
        List<BuyOrderRecord> buyOrders = db.getPlayerBuyOrders(player.getUUID());

        List<OrderEntry> asks = new ArrayList<>();
        for (ListingRecord l : listings) {
            asks.add(new OrderEntry(l.id(), l.sellerName(), l.itemId(), l.itemNbt() != null ? l.itemNbt() : "", l.priceCopper(), l.quantity(), l.createdAt()));
        }

        List<OrderEntry> bids = new ArrayList<>();
        for (BuyOrderRecord b : buyOrders) {
            bids.add(new OrderEntry(b.id(), b.buyerName(), b.itemId(), b.itemNbt() != null ? b.itemNbt() : "", b.priceCopper(), b.quantity(), b.createdAt()));
        }

        PacketDistributor.sendToPlayer(player, new SyncTransactionsS2C(asks, bids));
    }

    public static void syncAccountAndCatalog(ServerPlayer player) {
        DatabaseManager db = DatabaseManager.getInstance();
        AccountSummary acc = db.getOrCreateAccount(player.getUUID(), player.getScoreboardName());
        PacketDistributor.sendToPlayer(player, new SyncAccountS2C(acc.copperBalance(), acc.vaultItemCount(), acc.vaultCopperAmount()));
    }

    private static boolean removeOneItem(ServerPlayer player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                }
                return true;
            }
        }
        return false;
    }

    private static int removeAllItems(ServerPlayer player, Item item) {
        int removed = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                removed += stack.getCount();
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        return removed;
    }

    private static void givePlayerItem(ServerPlayer player, ItemStack stack) {
        boolean added = player.getInventory().add(stack);
        if (!added) {
            if (stack.is(ModRegistry.MERCHANTS_LICENSE.get())) {
                player.containerMenu.setCarried(stack);
                player.containerMenu.broadcastChanges();
                player.sendSystemMessage(Component.literal("§e[Imperial Registry] Your inventory was full! The soulbound license has been placed on your cursor."));
            } else {
                player.drop(stack, false);
            }
        }
    }

    public static void handleBuyMasterOffer(BuyMasterOfferC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            if (payload.offerId() == 1) { // Imperial Merchant License (64 Emerald Blocks)
                DatabaseManager db = DatabaseManager.getInstance();
                if (db.isPlayerLicensed(player.getUUID())) {
                    player.sendSystemMessage(Component.literal("§e[Merchant Master] You have already purchased and consumed an Imperial Merchant License! You are permanently recognized in our guild records."));
                    return;
                }

                // Check if player is already carrying an unconsumed license deed
                boolean hasUnconsumedDeed = false;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    if (player.getInventory().getItem(i).is(ModRegistry.MERCHANTS_LICENSE.get())) {
                        hasUnconsumedDeed = true;
                        break;
                    }
                }
                if (hasUnconsumedDeed) {
                    player.sendSystemMessage(Component.literal("§e[Merchant Master] You already possess an unconsumed Imperial Merchant License deed! Right-click with it in your hand to consume and activate it."));
                    return;
                }

                if (EmeraldHelper.consumeEmeralds(player, 576)) {
                    ItemStack license = com.omni.marketplace.item.MerchantsLicenseItem.createForPlayer(player);
                    givePlayerItem(player, license);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
                    player.sendSystemMessage(Component.literal("§6❖ [Grand Merchant Master] §aCongratulations! You have obtained an §6Imperial Merchant License§a deed! Right-click with it in your hand to consume and permanently activate your license."));
                } else {
                    player.sendSystemMessage(Component.literal("§c[Merchant Master] You need 576 Emeralds in your pocket pouch (64 Emerald Blocks worth) to purchase the Imperial Merchant License!"));
                }
            } else if (payload.offerId() == 2) { // Trader's Dispatch Book (200 Emeralds)
                if (EmeraldHelper.consumeEmeralds(player, 200)) {
                    ItemStack book = new ItemStack(ModRegistry.MARKETPLACE_TRANSCEIVER.get());
                    givePlayerItem(player, book);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);
                    player.sendSystemMessage(Component.literal("§6❖ [Grand Merchant Master] §aYou have purchased a §eTrader's Dispatch Book§a for 200 Emeralds! Use it anywhere to summon a personal Trading Post merchant."));
                } else {
                    player.sendSystemMessage(Component.literal("§c[Merchant Master] You need 200 Emeralds in your pocket pouch (emerald counter) to purchase a Trader's Dispatch Book!"));
                }
            } else if (payload.offerId() == 3) { // Imperial Vault Expansion (Bank Space)
                DatabaseManager db = DatabaseManager.getInstance();
                int currentRows = db.getBankRows(player.getUUID());
                if (currentRows >= 6) {
                    player.sendSystemMessage(Component.literal("§e[Merchant Master] Your Imperial Bank Vault is already at the maximum capacity (6 Rows / 54 Slots)!"));
                } else {
                    int cost = switch (currentRows) {
                        case 2 -> 300;
                        case 3 -> 500;
                        case 4 -> 700;
                        case 5 -> 999;
                        default -> 999;
                    };

                    if (EmeraldHelper.consumeEmeralds(player, cost)) {
                        db.upgradeBankRows(player.getUUID(), player.getScoreboardName());
                        int newRows = db.getBankRows(player.getUUID());
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 1.0F, 1.0F);
                        player.sendSystemMessage(Component.literal("§6❖ [Grand Merchant Master] §aVault Expansion granted! Your Imperial Bank Vault has been upgraded to §e" + newRows + " Rows §a(" + (newRows * 9) + " Slots)."));
                    } else {
                        player.sendSystemMessage(Component.literal("§c[Merchant Master] You need " + cost + " Emeralds in your pocket pouch (emerald counter) to purchase this Vault Expansion!"));
                    }
                }
            }

            // Sync updated wealth to the open master screen
            int totalEmeralds = EmeraldHelper.getTotalEmeralds(player);
            int emeraldBlocks = EmeraldHelper.getEmeraldBlocks(player);
            boolean hasLicense = LicenseHelper.hasLicense(player);
            int bankRows = DatabaseManager.getInstance().getBankRows(player.getUUID());
            PacketDistributor.sendToPlayer(player, new OpenMerchantMasterS2C(totalEmeralds, emeraldBlocks, hasLicense, bankRows));
        });
    }

    public static void handleIncinerateItem(IncinerateCarriedItemC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (player.containerMenu instanceof com.omni.marketplace.bank.BankMenu bankMenu) {
                    ItemStack carried = bankMenu.getCarried();
                    if (!carried.isEmpty()) {
                        String itemName = carried.getHoverName().getString();
                        int count = carried.getCount();
                        bankMenu.setCarried(ItemStack.EMPTY);
                        player.containerMenu.broadcastChanges();
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.9F, 1.2F);
                        player.sendSystemMessage(Component.literal("§c[Vault Incinerator] §e" + itemName + " x" + count + " §cwas permanently incinerated and deleted! §7(Reminder: Once destroyed, items are permanently deleted.)"));
                    } else {
                        player.sendSystemMessage(Component.literal("§e[Vault Incinerator] §7Pick up an item with your cursor and click the Incinerator to destroy it. §c(Reminder: Once destroyed, it will be permanently deleted!)"));
                    }
                }
            }
        });
    }

    public static void handleBankEmeraldAction(BankEmeraldActionC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DatabaseManager db = DatabaseManager.getInstance();

            // First check if carried item on cursor has emeralds or blocks
            if (player.containerMenu instanceof com.omni.marketplace.bank.BankMenu bankMenu) {
                ItemStack carried = bankMenu.getCarried();
                if (!carried.isEmpty()) {
                    if (carried.is(Items.EMERALD)) {
                        int count = carried.getCount();
                        int dep = db.depositDirectToVault(player.getUUID(), count);
                        if (dep > 0) {
                            carried.shrink(dep);
                            if (carried.isEmpty()) bankMenu.setCarried(ItemStack.EMPTY);
                            player.containerMenu.broadcastChanges();
                            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.2F);
                            player.sendSystemMessage(Component.literal("§6❖ [Imperial Vault] §aDeposited §e" + dep + " Emeralds §afrom cursor into Vault Reserve! (Total: §e" + String.format("%,d", db.getVaultEmeralds(player.getUUID())) + " §7/ §e9,999,999§7)"));
                            EmeraldHelper.syncEmeralds(player);
                            return;
                        }
                    } else if (carried.is(Items.EMERALD_BLOCK)) {
                        int count = carried.getCount();
                        int depBlocks = db.depositDirectToVault(player.getUUID(), count * 9) / 9;
                        if (depBlocks > 0) {
                            carried.shrink(depBlocks);
                            if (carried.isEmpty()) bankMenu.setCarried(ItemStack.EMPTY);
                            player.containerMenu.broadcastChanges();
                            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.2F);
                            player.sendSystemMessage(Component.literal("§6❖ [Imperial Vault] §aDeposited §e" + (depBlocks * 9) + " Emeralds §a(" + depBlocks + " Blocks) from cursor into Vault Reserve! (Total: §e" + String.format("%,d", db.getVaultEmeralds(player.getUUID())) + " §7/ §e9,999,999§7)"));
                            EmeraldHelper.syncEmeralds(player);
                            return;
                        }
                    }
                }
            }

            int currentPocket = EmeraldHelper.getPocketEmeralds(player);
            int currentVault = db.getVaultEmeralds(player.getUUID());

            switch (payload.action()) {
                case 0 -> { // Deposit ALL pocket emeralds AND excess inventory emeralds into vault (up to 9,999,999)
                    int totalDeposited = 0;

                    // 1. Deposit from pocket
                    if (currentPocket > 0) {
                        int depPocket = db.depositEmeraldsToVault(player.getUUID(), currentPocket);
                        totalDeposited += depPocket;
                    }

                    // 2. Deposit excess physical emeralds or blocks from player inventory into vault reserve
                    int vaultSpace = 9_999_999 - db.getVaultEmeralds(player.getUUID());
                    if (vaultSpace > 0) {
                        int invAbsorbed = 0;
                        for (int i = 0; i < player.getInventory().getContainerSize() && vaultSpace > 0; i++) {
                            ItemStack s = player.getInventory().getItem(i);
                            if (s.isEmpty()) continue;
                            if (s.is(Items.EMERALD)) {
                                int take = Math.min(s.getCount(), vaultSpace);
                                s.shrink(take);
                                vaultSpace -= take;
                                invAbsorbed += take;
                                if (s.isEmpty()) player.getInventory().setItem(i, ItemStack.EMPTY);
                            } else if (s.is(Items.EMERALD_BLOCK)) {
                                int blocksToTake = Math.min(s.getCount(), vaultSpace / 9);
                                if (blocksToTake > 0) {
                                    s.shrink(blocksToTake);
                                    int val = blocksToTake * 9;
                                    vaultSpace -= val;
                                    invAbsorbed += val;
                                    if (s.isEmpty()) player.getInventory().setItem(i, ItemStack.EMPTY);
                                }
                            }
                        }
                        if (invAbsorbed > 0) {
                            db.depositDirectToVault(player.getUUID(), invAbsorbed);
                            totalDeposited += invAbsorbed;
                            player.containerMenu.broadcastChanges();
                        }
                    }

                    if (totalDeposited > 0) {
                        EmeraldHelper.syncEmeralds(player);
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.2F);
                        player.sendSystemMessage(Component.literal("§6❖ [Imperial Vault] §aDeposited §e" + totalDeposited + " Emeralds §ainto Vault Reserve! §7(Vault Reserve: §e" + String.format("%,d", db.getVaultEmeralds(player.getUUID())) + " §7/ §e9,999,999§7)"));
                    } else if (db.getVaultEmeralds(player.getUUID()) >= 9_999_999) {
                        player.sendSystemMessage(Component.literal("§c[Imperial Vault] Vault reserve is full (9,999,999 / 9,999,999)!"));
                    } else {
                        player.sendSystemMessage(Component.literal("§e[Imperial Vault] You don't have any emeralds to deposit!"));
                    }
                }
                case 1 -> { // Withdraw up to 64 emeralds to pocket
                    int space = EmeraldHelper.MAX_EMERALD_CAPACITY - currentPocket;
                    if (space <= 0) {
                        player.sendSystemMessage(Component.literal("§c[Imperial Vault] Pocket pouch is already full (999/999)!"));
                        return;
                    }
                    if (currentVault <= 0) {
                        player.sendSystemMessage(Component.literal("§e[Imperial Vault] Vault emerald reserve is empty!"));
                        return;
                    }
                    int toWithdraw = Math.min(64, space);
                    int withdrawn = db.withdrawEmeraldsFromVault(player.getUUID(), toWithdraw);
                    if (withdrawn > 0) {
                        EmeraldHelper.syncEmeralds(player);
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 1.2F);
                        player.sendSystemMessage(Component.literal("§6❖ [Imperial Vault] §aWithdrew §e" + withdrawn + " Emeralds §ato Pocket Pouch! §7(Pocket: §a" + db.getPocketEmeralds(player.getUUID()) + " §7/ §e999§7)"));
                    }
                }
                case 2 -> { // Withdraw max to fill pocket (to 999)
                    int space = EmeraldHelper.MAX_EMERALD_CAPACITY - currentPocket;
                    if (space <= 0) {
                        player.sendSystemMessage(Component.literal("§c[Imperial Vault] Pocket pouch is already full (999/999)!"));
                        return;
                    }
                    if (currentVault <= 0) {
                        player.sendSystemMessage(Component.literal("§e[Imperial Vault] Vault emerald reserve is empty!"));
                        return;
                    }
                    int withdrawn = db.withdrawEmeraldsFromVault(player.getUUID(), space);
                    if (withdrawn > 0) {
                        EmeraldHelper.syncEmeralds(player);
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 1.2F);
                        player.sendSystemMessage(Component.literal("§6❖ [Imperial Vault] §aWithdrew §e" + withdrawn + " Emeralds §ato fill Pocket Pouch! §7(Pocket: §a" + db.getPocketEmeralds(player.getUUID()) + " §7/ §e999§7)"));
                    }
                }
            }
        });
    }

    public static void handleDropPocketEmerald(DropPocketEmeraldC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            int count = Math.max(1, Math.min(64, payload.count()));
            int currentPocket = EmeraldHelper.getPocketEmeralds(player);
            if (currentPocket <= 0) {
                player.sendSystemMessage(Component.literal("§c[Emerald Pouch] You don't have any emeralds in your pocket pouch to drop!"));
                return;
            }

            int toDrop = Math.min(count, currentPocket);
            if (EmeraldHelper.consumeEmeralds(player, toDrop)) {
                ItemStack dropStack = new ItemStack(Items.EMERALD, toDrop);
                net.minecraft.world.entity.item.ItemEntity itemEntity = player.drop(dropStack, false, false);
                if (itemEntity != null) {
                    itemEntity.setPickUpDelay(40); // 2 second delay so player doesn't instantly reabsorb it
                    itemEntity.setThrower(player);
                }
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.4F);
                player.displayClientMessage(Component.literal("§6❖ [Emerald Pouch] §7Dropped §e" + toDrop + " Emerald(s) §7to the ground."), true);
                EmeraldHelper.syncEmeralds(player);
            }
        });
    }

}
