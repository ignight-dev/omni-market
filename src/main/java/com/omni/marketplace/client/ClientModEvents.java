package com.omni.marketplace.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.omni.marketplace.OmniMarketplace;
import com.omni.marketplace.registry.ModRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.WanderingTraderRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = OmniMarketplace.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    public static final KeyMapping DROP_EMERALD_KEY = new KeyMapping(
            "key.omni_marketplace.drop_emerald",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            "key.categories.omni_marketplace"
    );

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistry.TRADING_POST_MERCHANT.get(), WanderingTraderRenderer::new);
        event.registerEntityRenderer(ModRegistry.MERCHANT_MASTER.get(), WanderingTraderRenderer::new);
        event.registerEntityRenderer(ModRegistry.BANKER.get(), WanderingTraderRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(DROP_EMERALD_KEY);
    }
}
