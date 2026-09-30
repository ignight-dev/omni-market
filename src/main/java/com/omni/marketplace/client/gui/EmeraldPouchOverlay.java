package com.omni.marketplace.client.gui;

import com.omni.marketplace.OmniMarketplace;
import com.omni.marketplace.client.ClientModEvents;
import com.omni.marketplace.network.MarketPackets.DropPocketEmeraldC2S;
import com.omni.marketplace.util.EmeraldHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

@EventBusSubscriber(modid = OmniMarketplace.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class EmeraldPouchOverlay {

    private static final ItemStack EMERALD_ICON = new ItemStack(Items.EMERALD);

    private static int getPouchX(int screenWidth) {
        return (screenWidth / 2) + 98;
    }

    private static int getPouchY(int screenHeight) {
        return screenHeight - 22;
    }

    private static int getPouchWidth(Font font, int totalEmeralds) {
        int textW = font.width(String.valueOf(totalEmeralds));
        return Math.max(38, 24 + textW + 6);
    }

    private static int getPouchHeight() {
        return 22;
    }

    private static void renderPouch(GuiGraphics graphics, Font font, int totalEmeralds, int x, int y, int width, int height, boolean isHovered) {
        int borderColor;
        int textColor;
        if (isHovered) {
            borderColor = 0xFFFFFFFF; // Glowing white/bright border when hovered
            textColor = 0xFFFFFF55;
        } else if (totalEmeralds >= EmeraldHelper.MAX_EMERALD_CAPACITY) {
            borderColor = 0xFFFF3333; // Full / alert red
            textColor = 0xFFFF5555;
        } else if (totalEmeralds >= 800) {
            borderColor = 0xFFFFAA00; // Near capacity amber
            textColor = 0xFFFFAA00;
        } else {
            borderColor = 0xFFDAA520; // Imperial gold
            textColor = 0xFF55FF55;   // Fresh emerald green
        }

        // Pouch background
        graphics.fill(x, y, x + width, y + height, 0xD012100E);
        graphics.renderOutline(x, y, width, height, borderColor);

        // Render emerald item icon
        graphics.renderItem(EMERALD_ICON, x + 3, y + 3);

        // Render counter number vertically centered beside emerald icon
        String countStr = String.valueOf(totalEmeralds);
        int textY = y + (height - font.lineHeight) / 2 + 1;
        graphics.drawString(font, countStr, x + 22, textY, textColor, false);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (mc.player.isSpectator()) return;
        // If a container or chat screen is open, ScreenEvent.Render.Post will render it on top
        if (mc.screen instanceof AbstractContainerScreen || mc.screen instanceof ChatScreen) return;

        int totalEmeralds = EmeraldHelper.getTotalEmeralds(mc.player);
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int pouchX = getPouchX(screenWidth);
        int pouchY = getPouchY(screenHeight);
        int pouchW = getPouchWidth(mc.font, totalEmeralds);
        int pouchH = getPouchHeight();

        renderPouch(event.getGuiGraphics(), mc.font, totalEmeralds, pouchX, pouchY, pouchW, pouchH, false);
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (mc.player.isSpectator()) return;

        // Render on top when inventory or container or chat is open
        if (mc.screen instanceof AbstractContainerScreen || mc.screen instanceof ChatScreen) {
            int totalEmeralds = EmeraldHelper.getTotalEmeralds(mc.player);
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();

            int pouchX = getPouchX(screenWidth);
            int pouchY = getPouchY(screenHeight);
            int pouchW = getPouchWidth(mc.font, totalEmeralds);
            int pouchH = getPouchHeight();

            int mouseX = event.getMouseX();
            int mouseY = event.getMouseY();
            boolean isHovered = mouseX >= pouchX && mouseX <= pouchX + pouchW && mouseY >= pouchY && mouseY <= pouchY + pouchH;

            GuiGraphics graphics = event.getGuiGraphics();
            renderPouch(graphics, mc.font, totalEmeralds, pouchX, pouchY, pouchW, pouchH, isHovered);

            if (isHovered) {
                List<Component> tooltip = List.of(
                        Component.literal("§6❖ Imperial Emerald Pouch ❖"),
                        Component.literal(String.format("§7Stored in Pocket: §a%d §7/ §e999 Emeralds", totalEmeralds)),
                        Component.literal("§e[Click] §fDrop 1 Emerald to the ground"),
                        Component.literal("§d[Shift + Click] §fDrop 64 Emeralds to the ground"),
                        Component.literal("§8(Keybind: [Z] drops 1 Emerald anytime)")
                );
                graphics.renderComponentTooltip(mc.font, tooltip, mouseX, mouseY);
            }
        }
    }

    @SubscribeEvent
    public static void onScreenMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (event.getButton() == 0) { // Left-click
            int totalEmeralds = EmeraldHelper.getTotalEmeralds(mc.player);
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();

            int pouchX = getPouchX(screenWidth);
            int pouchY = getPouchY(screenHeight);
            int pouchW = getPouchWidth(mc.font, totalEmeralds);
            int pouchH = getPouchHeight();

            double mouseX = event.getMouseX();
            double mouseY = event.getMouseY();

            if (mouseX >= pouchX && mouseX <= pouchX + pouchW && mouseY >= pouchY && mouseY <= pouchY + pouchH) {
                if (totalEmeralds > 0) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
                    boolean isShift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                    int dropCount = isShift ? 64 : 1;
                    PacketDistributor.sendToServer(new DropPocketEmeraldC2S(dropCount));
                } else {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
                }
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (ClientModEvents.DROP_EMERALD_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                int total = EmeraldHelper.getTotalEmeralds(mc.player);
                if (total > 0) {
                    boolean isShift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                    PacketDistributor.sendToServer(new DropPocketEmeraldC2S(isShift ? 64 : 1));
                } else {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
                }
            }
        }
    }
}
