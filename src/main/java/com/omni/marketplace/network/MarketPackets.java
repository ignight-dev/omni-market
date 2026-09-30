package com.omni.marketplace.network;

import com.omni.marketplace.OmniMarketplace;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class MarketPackets {

    /* ========================================================================= */
    /* Data Transfer Objects (DTOs)                                              */
    /* ========================================================================= */

    public record CatalogEntry(
            String itemId,
            String sampleNbt,
            long lowestSell,
            long highestBuy,
            long suggestedPrice,
            int supply,
            int demand
    ) {
        public static final StreamCodec<ByteBuf, CatalogEntry> STREAM_CODEC = StreamCodec.of(
                (buf, val) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, val.itemId());
                    ByteBufCodecs.STRING_UTF8.encode(buf, val.sampleNbt());
                    ByteBufCodecs.VAR_LONG.encode(buf, val.lowestSell());
                    ByteBufCodecs.VAR_LONG.encode(buf, val.highestBuy());
                    ByteBufCodecs.VAR_LONG.encode(buf, val.suggestedPrice());
                    ByteBufCodecs.VAR_INT.encode(buf, val.supply());
                    ByteBufCodecs.VAR_INT.encode(buf, val.demand());
                },
                buf -> new CatalogEntry(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.VAR_LONG.decode(buf),
                        ByteBufCodecs.VAR_LONG.decode(buf),
                        ByteBufCodecs.VAR_LONG.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf)
                )
        );
    }

    public record OrderEntry(
            long id,
            String ownerName,
            String itemId,
            String itemNbt,
            long unitPrice,
            int quantity,
            String createdAt
    ) {
        public static final StreamCodec<ByteBuf, OrderEntry> STREAM_CODEC = StreamCodec.of(
                (buf, val) -> {
                    ByteBufCodecs.VAR_LONG.encode(buf, val.id());
                    ByteBufCodecs.STRING_UTF8.encode(buf, val.ownerName());
                    ByteBufCodecs.STRING_UTF8.encode(buf, val.itemId());
                    ByteBufCodecs.STRING_UTF8.encode(buf, val.itemNbt());
                    ByteBufCodecs.VAR_LONG.encode(buf, val.unitPrice());
                    ByteBufCodecs.VAR_INT.encode(buf, val.quantity());
                    ByteBufCodecs.STRING_UTF8.encode(buf, val.createdAt());
                },
                buf -> new OrderEntry(
                        ByteBufCodecs.VAR_LONG.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.VAR_LONG.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf)
                )
        );
    }

    /* ========================================================================= */
    /* C2S Payloads                                                              */
    /* ========================================================================= */

    public record RequestCatalogC2S(String query, String category, String subCategory, int page) implements CustomPacketPayload {
        public static final Type<RequestCatalogC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OmniMarketplace.MOD_ID, "request_catalog"));
        public static final StreamCodec<ByteBuf, RequestCatalogC2S> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, RequestCatalogC2S::query,
                ByteBufCodecs.STRING_UTF8, RequestCatalogC2S::category,
                ByteBufCodecs.STRING_UTF8, RequestCatalogC2S::subCategory,
                ByteBufCodecs.VAR_INT, RequestCatalogC2S::page,
                RequestCatalogC2S::new
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CreateListingC2S(int inventorySlot, int count, long unitPrice) implements CustomPacketPayload {
        public static final Type<CreateListingC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OmniMarketplace.MOD_ID, "create_listing"));
        public static final StreamCodec<ByteBuf, CreateListingC2S> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, CreateListingC2S::inventorySlot,
                ByteBufCodecs.VAR_INT, CreateListingC2S::count,
                ByteBufCodecs.VAR_LONG, CreateListingC2S::unitPrice,
                CreateListingC2S::new
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CreateBuyOrderC2S(String itemId, String itemNbt, int count, long unitPrice) implements CustomPacketPayload {
        public static final Type<CreateBuyOrderC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OmniMarketplace.MOD_ID, "create_buy_order"));
        public static final StreamCodec<ByteBuf, CreateBuyOrderC2S> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, CreateBuyOrderC2S::itemId,
                ByteBufCodecs.STRING_UTF8, CreateBuyOrderC2S::itemNbt,
                ByteBufCodecs.VAR_INT, CreateBuyOrderC2S::count,
                ByteBufCodecs.VAR_LONG, CreateBuyOrderC2S::unitPrice,
                CreateBuyOrderC2S::new
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record InstantBuyC2S(String itemId, long listingId, int count) implements CustomPacketPayload {
        public static final Type<InstantBuyC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OmniMarketplace.MOD_ID, "instant_buy"));
        public static final StreamCodec<ByteBuf, InstantBuyC2S> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, InstantBuyC2S::itemId,
                ByteBufCodecs.VAR_LONG, InstantBuyC2S::listingId,
                ByteBufCodecs.VAR_INT, InstantBuyC2S::count,
                InstantBuyC2S::new
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record InstantSellC2S(long buyOrderId, int count, int inventorySlot) implements CustomPacketPayload {
        public static final Type<InstantSellC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OmniMarketplace.MOD_ID, "instant_sell"));
        public static final StreamCodec<ByteBuf, InstantSellC2S> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, InstantSellC2S::buyOrderId,
                ByteBufCodecs.VAR_INT, InstantSellC2S::count,
                ByteBufCodecs.VAR_INT, InstantSellC2S::inventorySlot,
                InstantSellC2S::new
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CancelOrderC2S(boolean isBuyOrder, long orderId) implements CustomPacketPayload {
        public static final Type<CancelOrderC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OmniMarketplace.MOD_ID, "cancel_order"));
        public static final StreamCodec<ByteBuf, CancelOrderC2S> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, CancelOrderC2S::isBuyOrder,
                ByteBufCodecs.VAR_LONG, CancelOrderC2S::orderId,
                CancelOrderC2S::new
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ClaimVaultC2S() implements CustomPacketPayload {
        public static final Type<ClaimVaultC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OmniMarketplace.MOD_ID, "claim_vault"));
        public static final StreamCodec<ByteBuf, ClaimVaultC2S> STREAM_CODEC = StreamCodec.unit(new ClaimVaultC2S());
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CurrencyExchangeC2S(int action) implements CustomPacketPayload {
        public static final Type<CurrencyExchangeC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OmniMarketplace.MOD_ID, "currency_exchange"));
        public static final StreamCodec<ByteBuf, CurrencyExchangeC2S> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, CurrencyExchangeC2S::action,
                CurrencyExchangeC2S::new
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RequestItemDetailsC2S(String itemId) implements CustomPacketPayload {
        public static final Type<RequestItemDetailsC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OmniMarketplace.MOD_ID, "request_item_details"));
        public static final StreamCodec<ByteBuf, RequestItemDetailsC2S> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, RequestItemDetailsC2S::itemId,
                RequestItemDetailsC2S::new
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RequestTransactionsC2S() implements CustomPacketPayload {
        public static final Type<RequestTransactionsC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OmniMarketplace.MOD_ID, "request_transactions"));
        public static final StreamCodec<ByteBuf, RequestTransactionsC2S> STREAM_CODEC = StreamCodec.unit(new RequestTransactionsC2S());
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /* ========================================================================= */
    /* S2C Payloads                                                              */
    /* ========================================================================= */

    public record OpenMarketplaceS2C(long balance, int vaultCount, long vaultCoins) implements CustomPacketPayload {
        public static final Type<OpenMarketplaceS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OmniMarketplace.MOD_ID, "open_marketplace"));
        public static final StreamCodec<ByteBuf, OpenMarketplaceS2C> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, OpenMarketplaceS2C::balance,
                ByteBufCodecs.VAR_INT, OpenMarketplaceS2C::vaultCount,
                ByteBufCodecs.VAR_LONG, OpenMarketplaceS2C::vaultCoins,
                OpenMarketplaceS2C::new
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SyncAccountS2C(long balance, int vaultCount, long vaultCoins) implements CustomPacketPayload {
        public static final Type<SyncAccountS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OmniMarketplace.MOD_ID, "sync_account"));
        public static final StreamCodec<ByteBuf, SyncAccountS2C> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, SyncAccountS2C::balance,
                ByteBufCodecs.VAR_INT, SyncAccountS2C::vaultCount,
                ByteBufCodecs.VAR_LONG, SyncAccountS2C::vaultCoins,
                SyncAccountS2C::new
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SyncCatalogS2C(List<CatalogEntry> entries) implements CustomPacketPayload {
        public static final Type<SyncCatalogS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OmniMarketplace.MOD_ID, "sync_catalog"));
        public static final StreamCodec<ByteBuf, SyncCatalogS2C> STREAM_CODEC = StreamCodec.composite(
                CatalogEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncCatalogS2C::entries,
                SyncCatalogS2C::new
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SyncOrderBookS2C(String itemId, List<OrderEntry> asks, List<OrderEntry> bids) implements CustomPacketPayload {
        public static final Type<SyncOrderBookS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OmniMarketplace.MOD_ID, "sync_order_book"));
        public static final StreamCodec<ByteBuf, SyncOrderBookS2C> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SyncOrderBookS2C::itemId,
                OrderEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncOrderBookS2C::asks,
                OrderEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncOrderBookS2C::bids,
                SyncOrderBookS2C::new
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SyncTransactionsS2C(List<OrderEntry> playerListings, List<OrderEntry> playerBuyOrders) implements CustomPacketPayload {
        public static final Type<SyncTransactionsS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OmniMarketplace.MOD_ID, "sync_transactions"));
        public static final StreamCodec<ByteBuf, SyncTransactionsS2C> STREAM_CODEC = StreamCodec.composite(
                OrderEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncTransactionsS2C::playerListings,
                OrderEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncTransactionsS2C::playerBuyOrders,
                SyncTransactionsS2C::new
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenMerchantMasterS2C(int totalEmeralds, int emeraldBlocks, boolean hasLicense, int bankRows) implements CustomPacketPayload {
        public static final Type<OpenMerchantMasterS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OmniMarketplace.MOD_ID, "open_merchant_master"));
        public static final StreamCodec<ByteBuf, OpenMerchantMasterS2C> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OpenMerchantMasterS2C::totalEmeralds,
                ByteBufCodecs.VAR_INT, OpenMerchantMasterS2C::emeraldBlocks,
                ByteBufCodecs.BOOL, OpenMerchantMasterS2C::hasLicense,
                ByteBufCodecs.VAR_INT, OpenMerchantMasterS2C::bankRows,
                OpenMerchantMasterS2C::new
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record BuyMasterOfferC2S(int offerId) implements CustomPacketPayload {
        public static final Type<BuyMasterOfferC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OmniMarketplace.MOD_ID, "buy_master_offer"));
        public static final StreamCodec<ByteBuf, BuyMasterOfferC2S> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BuyMasterOfferC2S::offerId,
                BuyMasterOfferC2S::new
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record IncinerateCarriedItemC2S() implements CustomPacketPayload {
        public static final Type<IncinerateCarriedItemC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OmniMarketplace.MOD_ID, "incinerate_item"));
        public static final StreamCodec<ByteBuf, IncinerateCarriedItemC2S> STREAM_CODEC = StreamCodec.unit(new IncinerateCarriedItemC2S());
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record BankEmeraldActionC2S(int action) implements CustomPacketPayload {
        public static final Type<BankEmeraldActionC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OmniMarketplace.MOD_ID, "bank_emerald_action"));
        public static final StreamCodec<ByteBuf, BankEmeraldActionC2S> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BankEmeraldActionC2S::action,
                BankEmeraldActionC2S::new
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SyncEmeraldsS2C(int pocketEmeralds, int vaultEmeralds) implements CustomPacketPayload {
        public static final Type<SyncEmeraldsS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OmniMarketplace.MOD_ID, "sync_emeralds"));
        public static final StreamCodec<ByteBuf, SyncEmeraldsS2C> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SyncEmeraldsS2C::pocketEmeralds,
                ByteBufCodecs.VAR_INT, SyncEmeraldsS2C::vaultEmeralds,
                SyncEmeraldsS2C::new
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record DropPocketEmeraldC2S(int count) implements CustomPacketPayload {
        public static final Type<DropPocketEmeraldC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OmniMarketplace.MOD_ID, "drop_pocket_emerald"));
        public static final StreamCodec<ByteBuf, DropPocketEmeraldC2S> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, DropPocketEmeraldC2S::count,
                DropPocketEmeraldC2S::new
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
