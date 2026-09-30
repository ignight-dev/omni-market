package com.omni.marketplace.client.gui;

import com.omni.marketplace.OmniMarketplace;
import com.omni.marketplace.network.MarketPackets.BankEmeraldActionC2S;
import com.omni.marketplace.network.MarketPackets.IncinerateCarriedItemC2S;
import com.omni.marketplace.util.EmeraldHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

@EventBusSubscriber(modid = OmniMarketplace.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class BankVaultOverlay {

    private static final ItemStack LAVA_BUCKET_ICON = new ItemStack(Items.LAVA_BUCKET);
    private static final ItemStack EMERALD_ICON = new ItemStack(Items.EMERALD);

    private static boolean isBankScreen(AbstractContainerScreen<?> screen) {
        if (screen == null) return false;
        Component title = screen.getTitle();
        return title != null && title.getString().contains("Imperial Bank Vault");
    }

    @SubscribeEvent
    public static void onContainerBackground(ContainerScreenEvent.Render.Background event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        if (isBankScreen(screen)) {
            GuiGraphics graphics = event.getGuiGraphics();
            int left = screen.getGuiLeft();
            int top = screen.getGuiTop();

            int boxW = 20;
            int boxH = 20;

            // 1. Draw dark crimson medieval incinerator slot box
            int boxX = left + 179;
            int boxY = top + 17;
            graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xD01C0808);
            graphics.renderOutline(boxX, boxY, boxW, boxH, 0xFFFF3333);
            graphics.renderItem(LAVA_BUCKET_ICON, boxX + 2, boxY + 2);

            // 2. Draw dark emerald green bank vault emerald deposit button box below trash
            int gemX = left + 179;
            int gemY = top + 44;
            graphics.fill(gemX, gemY, gemX + boxW, gemY + boxH, 0xD00A1F10);
            graphics.renderOutline(gemX, gemY, boxW, boxH, 0xFF00E676);
            graphics.renderItem(EMERALD_ICON, gemX + 2, gemY + 2);
        }
    }

    @SubscribeEvent
    public static void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        if (isBankScreen(screen)) {
            GuiGraphics graphics = event.getGuiGraphics();
            Minecraft mc = Minecraft.getInstance();

            // Label above incinerator slot (relative to container left/top)
            graphics.drawString(mc.font, "§cTrash", 180, 6, 0xFF6666, false);

            // Label above emerald deposit button (relative to container left/top)
            graphics.drawString(mc.font, "§aGems", 180, 34, 0x55FF55, false);

            int mouseX = event.getMouseX();
            int mouseY = event.getMouseY();
            int left = screen.getGuiLeft();
            int top = screen.getGuiTop();

            int relX = mouseX - left;
            int relY = mouseY - top;

            if (relX >= 178 && relX <= 200 && relY >= 16 && relY <= 38) {
                List<Component> tooltip = List.of(
                        Component.literal("§c❖ Vault Incinerator ❖"),
                        Component.literal("§7Click with an item on your cursor to destroy it."),
                        Component.literal("§4⚠ Once destroyed, items are PERMANENTLY deleted!"),
                        Component.literal("§aFree permanent guild service.")
                );
                graphics.renderComponentTooltip(mc.font, tooltip, relX, relY);
            } else if (relX >= 178 && relX <= 200 && relY >= 43 && relY <= 65) {
                int vaultEmeralds = EmeraldHelper.getVaultEmeralds(mc.player);
                int pocketEmeralds = EmeraldHelper.getPocketEmeralds(mc.player);
                List<Component> tooltip = List.of(
                        Component.literal("§6❖ Imperial Vault Emerald Reserve ❖"),
                        Component.literal(String.format("§7Vault Storage: §a%,d §7/ §e9,999,999 Emeralds", vaultEmeralds)),
                        Component.literal(String.format("§7Pocket Pouch: §a%d §7/ §e999 Emeralds", pocketEmeralds)),
                        Component.literal(""),
                        Component.literal("§e[Left-Click] §fDeposit Pocket Emeralds into Vault"),
                        Component.literal("§b[Right-Click] §fWithdraw 64 Emeralds to Pocket"),
                        Component.literal("§d[Shift + Right-Click] §fWithdraw Max to fill Pocket (999)"),
                        Component.literal("§7(Or click with Emeralds on cursor to deposit them)")
                );
                graphics.renderComponentTooltip(mc.font, tooltip, relX, relY);
            }
        }
    }

    @SubscribeEvent
    public static void onScreenMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen && isBankScreen(containerScreen)) {
            int left = containerScreen.getGuiLeft();
            int top = containerScreen.getGuiTop();
            double mouseX = event.getMouseX();
            double mouseY = event.getMouseY();

            // 1. Click on Incinerator (Trash)
            if (event.getButton() == 0) { // Left click
                if (mouseX >= left + 178 && mouseX <= left + 202 && mouseY >= top + 15 && mouseY <= top + 39) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    PacketDistributor.sendToServer(new IncinerateCarriedItemC2S());
                    event.setCanceled(true);
                    return;
                }
            }

            // 2. Click on Emerald Reserve Button (below Trash)
            if (mouseX >= left + 178 && mouseX <= left + 202 && mouseY >= top + 43 && mouseY <= top + 65) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                int action;
                if (event.getButton() == 0) { // Left-click: Deposit
                    action = 0;
                } else if (event.getButton() == 1) { // Right-click: Withdraw 64 or Max if shift
                    boolean isShift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                    action = isShift ? 2 : 1;
                } else {
                    return;
                }
                PacketDistributor.sendToServer(new BankEmeraldActionC2S(action));
                event.setCanceled(true);
            }
        }
    }
}
