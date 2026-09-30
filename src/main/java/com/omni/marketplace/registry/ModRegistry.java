package com.omni.marketplace.registry;

import com.omni.marketplace.OmniMarketplace;
import com.omni.marketplace.entity.TradingPostMerchantEntity;
import com.omni.marketplace.item.MarketplaceTransceiverItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModRegistry {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OmniMarketplace.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, OmniMarketplace.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, OmniMarketplace.MOD_ID);

    public static final DeferredItem<MarketplaceTransceiverItem> MARKETPLACE_TRANSCEIVER = ITEMS.registerItem(
            "marketplace_transceiver",
            MarketplaceTransceiverItem::new,
            new Item.Properties().stacksTo(16)
    );

    public static final DeferredItem<com.omni.marketplace.item.MerchantsLicenseItem> MERCHANTS_LICENSE = ITEMS.registerItem(
            "merchants_license",
            com.omni.marketplace.item.MerchantsLicenseItem::new,
            new Item.Properties().stacksTo(1)
    );

    public static final Supplier<EntityType<TradingPostMerchantEntity>> TRADING_POST_MERCHANT = ENTITY_TYPES.register(
            "trading_post_merchant",
            () -> EntityType.Builder.<TradingPostMerchantEntity>of(TradingPostMerchantEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("trading_post_merchant")
    );

    public static final Supplier<EntityType<com.omni.marketplace.entity.MerchantMasterEntity>> MERCHANT_MASTER = ENTITY_TYPES.register(
            "merchant_master",
            () -> EntityType.Builder.<com.omni.marketplace.entity.MerchantMasterEntity>of(com.omni.marketplace.entity.MerchantMasterEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("merchant_master")
    );

    public static final Supplier<EntityType<com.omni.marketplace.entity.BankerEntity>> BANKER = ENTITY_TYPES.register(
            "banker",
            () -> EntityType.Builder.<com.omni.marketplace.entity.BankerEntity>of(com.omni.marketplace.entity.BankerEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("banker")
    );

    public static final DeferredItem<SpawnEggItem> TRADING_POST_MERCHANT_SPAWN_EGG = ITEMS.registerItem(
            "trading_post_merchant_spawn_egg",
            properties -> new SpawnEggItem(TRADING_POST_MERCHANT.get(), 0x1E3A5F, 0xDAA520, properties)
    );

    public static final DeferredItem<SpawnEggItem> MERCHANT_MASTER_SPAWN_EGG = ITEMS.registerItem(
            "merchant_master_spawn_egg",
            properties -> new SpawnEggItem(MERCHANT_MASTER.get(), 0xFFD700, 0x1E3A5F, properties)
    );

    public static final DeferredItem<SpawnEggItem> BANKER_SPAWN_EGG = ITEMS.registerItem(
            "banker_spawn_egg",
            properties -> new SpawnEggItem(BANKER.get(), 0x2E8B57, 0xFFD700, properties)
    );

    public static final Supplier<CreativeModeTab> MARKET_TAB = CREATIVE_MODE_TABS.register("omni_marketplace_tab", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.omni_marketplace"))
                    .icon(() -> new ItemStack(MARKETPLACE_TRANSCEIVER.get()))
                    .displayItems((params, output) -> {
                        output.accept(MARKETPLACE_TRANSCEIVER.get());
                        output.accept(MERCHANTS_LICENSE.get());
                        output.accept(TRADING_POST_MERCHANT_SPAWN_EGG.get());
                        output.accept(MERCHANT_MASTER_SPAWN_EGG.get());
                        output.accept(BANKER_SPAWN_EGG.get());
                    })
                    .build()
    );

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
