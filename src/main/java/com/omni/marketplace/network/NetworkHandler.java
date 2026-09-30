package com.omni.marketplace.network;

import com.omni.marketplace.network.MarketPackets.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // C2S Packets
        registrar.playToServer(
                RequestCatalogC2S.TYPE,
                RequestCatalogC2S.STREAM_CODEC,
                ServerPayloadHandler::handleRequestCatalog
        );
        registrar.playToServer(
                CreateListingC2S.TYPE,
                CreateListingC2S.STREAM_CODEC,
                ServerPayloadHandler::handleCreateListing
        );
        registrar.playToServer(
                CreateBuyOrderC2S.TYPE,
                CreateBuyOrderC2S.STREAM_CODEC,
                ServerPayloadHandler::handleCreateBuyOrder
        );
        registrar.playToServer(
                InstantBuyC2S.TYPE,
                InstantBuyC2S.STREAM_CODEC,
                ServerPayloadHandler::handleInstantBuy
        );
        registrar.playToServer(
                InstantSellC2S.TYPE,
                InstantSellC2S.STREAM_CODEC,
                ServerPayloadHandler::handleInstantSell
        );
        registrar.playToServer(
                CancelOrderC2S.TYPE,
                CancelOrderC2S.STREAM_CODEC,
                ServerPayloadHandler::handleCancelOrder
        );
        registrar.playToServer(
                ClaimVaultC2S.TYPE,
                ClaimVaultC2S.STREAM_CODEC,
                ServerPayloadHandler::handleClaimVault
        );
        registrar.playToServer(
                CurrencyExchangeC2S.TYPE,
                CurrencyExchangeC2S.STREAM_CODEC,
                ServerPayloadHandler::handleCurrencyExchange
        );
        registrar.playToServer(
                RequestItemDetailsC2S.TYPE,
                RequestItemDetailsC2S.STREAM_CODEC,
                ServerPayloadHandler::handleRequestItemDetails
        );
        registrar.playToServer(
                RequestTransactionsC2S.TYPE,
                RequestTransactionsC2S.STREAM_CODEC,
                ServerPayloadHandler::handleRequestTransactions
        );
        registrar.playToServer(
                BuyMasterOfferC2S.TYPE,
                BuyMasterOfferC2S.STREAM_CODEC,
                ServerPayloadHandler::handleBuyMasterOffer
        );
        registrar.playToServer(
                IncinerateCarriedItemC2S.TYPE,
                IncinerateCarriedItemC2S.STREAM_CODEC,
                ServerPayloadHandler::handleIncinerateItem
        );
        registrar.playToServer(
                BankEmeraldActionC2S.TYPE,
                BankEmeraldActionC2S.STREAM_CODEC,
                ServerPayloadHandler::handleBankEmeraldAction
        );
        registrar.playToServer(
                DropPocketEmeraldC2S.TYPE,
                DropPocketEmeraldC2S.STREAM_CODEC,
                ServerPayloadHandler::handleDropPocketEmerald
        );
        registrar.playToServer(
                ToggleFavoriteC2S.TYPE,
                ToggleFavoriteC2S.STREAM_CODEC,
                ServerPayloadHandler::handleToggleFavorite
        );

        // S2C Packets
        registrar.playToClient(
                OpenMarketplaceS2C.TYPE,
                OpenMarketplaceS2C.STREAM_CODEC,
                ClientPayloadHandler::handleOpenMarketplace
        );
        registrar.playToClient(
                OpenMerchantMasterS2C.TYPE,
                OpenMerchantMasterS2C.STREAM_CODEC,
                ClientPayloadHandler::handleOpenMerchantMaster
        );
        registrar.playToClient(
                SyncAccountS2C.TYPE,
                SyncAccountS2C.STREAM_CODEC,
                ClientPayloadHandler::handleSyncAccount
        );
        registrar.playToClient(
                SyncEmeraldsS2C.TYPE,
                SyncEmeraldsS2C.STREAM_CODEC,
                ClientPayloadHandler::handleSyncEmeralds
        );
        registrar.playToClient(
                SyncCatalogS2C.TYPE,
                SyncCatalogS2C.STREAM_CODEC,
                ClientPayloadHandler::handleSyncCatalog
        );
        registrar.playToClient(
                SyncOrderBookS2C.TYPE,
                SyncOrderBookS2C.STREAM_CODEC,
                ClientPayloadHandler::handleSyncOrderBook
        );
        registrar.playToClient(
                SyncFavoritesS2C.TYPE,
                SyncFavoritesS2C.STREAM_CODEC,
                ClientPayloadHandler::handleSyncFavorites
        );
        registrar.playToClient(
                SyncTransactionsS2C.TYPE,
                SyncTransactionsS2C.STREAM_CODEC,
                ClientPayloadHandler::handleSyncTransactions
        );
    }
}
