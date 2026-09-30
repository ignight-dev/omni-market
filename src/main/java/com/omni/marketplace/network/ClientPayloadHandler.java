package com.omni.marketplace.network;

import com.omni.marketplace.client.screen.MarketplaceScreen;
import com.omni.marketplace.network.MarketPackets.*;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClientPayloadHandler {

    public static void handleOpenMarketplace(OpenMarketplaceS2C payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            MarketplaceScreen screen = new MarketplaceScreen();
            screen.updateAccount(payload.balance(), payload.vaultCount(), payload.vaultCoins());
            mc.setScreen(screen);
        });
    }

    public static void handleOpenMerchantMaster(OpenMerchantMasterS2C payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof com.omni.marketplace.client.screen.MerchantMasterScreen screen) {
                screen.updateData(payload.totalEmeralds(), payload.emeraldBlocks(), payload.hasLicense(), payload.bankRows());
            } else {
                mc.setScreen(new com.omni.marketplace.client.screen.MerchantMasterScreen(
                        payload.totalEmeralds(),
                        payload.emeraldBlocks(),
                        payload.hasLicense(),
                        payload.bankRows()
                ));
            }
        });
    }

    public static void handleSyncAccount(SyncAccountS2C payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof MarketplaceScreen screen) {
                screen.updateAccount(payload.balance(), payload.vaultCount(), payload.vaultCoins());
            }
        });
    }

    public static void handleSyncEmeralds(SyncEmeraldsS2C payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.omni.marketplace.util.EmeraldHelper.setClientEmeralds(payload.pocketEmeralds(), payload.vaultEmeralds());
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof com.omni.marketplace.client.screen.MerchantMasterScreen screen) {
                screen.updateEmeralds(payload.pocketEmeralds());
            }
        });
    }

    public static void handleSyncCatalog(SyncCatalogS2C payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof MarketplaceScreen screen) {
                screen.updateCatalog(payload.entries());
            }
        });
    }

    public static void handleSyncOrderBook(SyncOrderBookS2C payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof MarketplaceScreen screen) {
                screen.updateOrderBook(payload.itemId(), payload.asks(), payload.bids());
            }
        });
    }

    public static void handleSyncTransactions(SyncTransactionsS2C payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof MarketplaceScreen screen) {
                screen.updateTransactions(payload.playerListings(), payload.playerBuyOrders());
            }
        });
    }
}
