package com.omni.marketplace.client.screen;

import com.omni.marketplace.client.gui.MarketTheme;
import com.omni.marketplace.network.MarketPackets.BuyMasterOfferC2S;
import com.omni.marketplace.registry.ModRegistry;
import com.omni.marketplace.util.EmeraldHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

public class MerchantMasterScreen extends Screen {

    private int totalEmeralds;
    private int emeraldBlocks;
    private boolean hasLicense;
    private int bankRows;

    private ItemStack hoveredTooltipStack = ItemStack.EMPTY;
    private Component hoveredTooltip = null;

    private static final ItemStack LICENSE_STACK = new ItemStack(ModRegistry.MERCHANTS_LICENSE.get());
    private static final ItemStack BOOK_STACK = new ItemStack(ModRegistry.MARKETPLACE_TRANSCEIVER.get());
    private static final ItemStack CHEST_STACK = new ItemStack(Items.CHEST);
    private static final ItemStack EMERALD_STACK = new ItemStack(Items.EMERALD);

    public MerchantMasterScreen(int totalEmeralds, int emeraldBlocks, boolean hasLicense, int bankRows) {
        super(Component.literal("Grand Merchant Master"));
        this.totalEmeralds = totalEmeralds;
        this.emeraldBlocks = emeraldBlocks;
        this.hasLicense = hasLicense;
        this.bankRows = bankRows;
    }

    public void updateData(int totalEmeralds, int emeraldBlocks, boolean hasLicense, int bankRows) {
        this.totalEmeralds = totalEmeralds;
        this.emeraldBlocks = emeraldBlocks;
        this.hasLicense = hasLicense;
        this.bankRows = bankRows;
    }

    public void updateEmeralds(int pocketEmeralds) {
        this.totalEmeralds = pocketEmeralds;
        this.emeraldBlocks = pocketEmeralds / 9;
    }

    public static int getUpgradePrice(int currentRows) {
        return switch (currentRows) {
            case 2 -> 300;
            case 3 -> 500;
            case 4 -> 700;
            case 5 -> 999;
            default -> -1;
        };
    }

    // Dynamic responsive dimensions that adapt to any aspect ratio and GUI scale
    private int getPanelW() {
        return Math.min(480, Math.max(320, this.width - 24));
    }

    private int getPanelH() {
        return Math.min(270, Math.max(210, this.height - 16));
    }

    private int getPanelX() {
        return (this.width - getPanelW()) / 2;
    }

    private int getPanelY() {
        return (this.height - getPanelH()) / 2;
    }

    private int cardX() {
        return getPanelX() + 12;
    }

    private int cardW() {
        return getPanelW() - 24;
    }

    private int cardH() {
        int avail = getPanelH() - 80;
        int gap = cardGap();
        return Math.max(42, (avail - (gap * 2)) / 3);
    }

    private int cardGap() {
        int avail = getPanelH() - 80;
        return (avail >= 160) ? 6 : ((avail >= 140) ? 4 : 2);
    }

    private int card1Y() {
        return getPanelY() + 50;
    }

    private int card2Y() {
        return card1Y() + cardH() + cardGap();
    }

    private int card3Y() {
        return card2Y() + cardH() + cardGap();
    }

    private int getBtnW() {
        return Math.min(104, Math.max(88, cardW() / 4));
    }

    private int getBtnH() {
        return Math.min(26, Math.max(22, cardH() - 26));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Deep medieval dusk backdrop without post-processing blur shader
        graphics.fillGradient(0, 0, this.width, this.height, 0x99000000, 0xBB000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        this.hoveredTooltipStack = ItemStack.EMPTY;
        this.hoveredTooltip = null;

        // Live sync pocket funds from local player inventory (emerald counter) so it is always 100% in sync
        if (Minecraft.getInstance().player != null) {
            this.totalEmeralds = EmeraldHelper.getTotalEmeralds(Minecraft.getInstance().player);
            this.emeraldBlocks = EmeraldHelper.getEmeraldBlocks(Minecraft.getInstance().player);
        }

        int panelX = getPanelX();
        int panelY = getPanelY();
        int panelW = getPanelW();
        int panelH = getPanelH();

        // Main modal container with medieval ornate border
        MarketTheme.drawMedievalFrame(graphics, panelX, panelY, panelW, panelH);

        // Header bar
        graphics.fill(panelX + 2, panelY + 2, panelX + panelW - 2, panelY + 24, 0xFF181512);
        graphics.hLine(panelX + 2, panelX + panelW - 3, panelY + 24, MarketTheme.PANEL_BORDER_GOLD);

        graphics.drawCenteredString(font, "§6❖ GRAND IMPERIAL MERCHANT MASTER ❖", panelX + panelW / 2, panelY + 6, MarketTheme.TEXT_GOLD);
        graphics.drawCenteredString(font, "§7Royal Charter Registry & Mercantile Overseer", panelX + panelW / 2, panelY + 15, MarketTheme.TEXT_MUTED);

        // Pocket Funds & Status Bar
        int barX = panelX + 12;
        int barY = panelY + 26;
        int barW = panelW - 24;
        int barH = 20;

        MarketTheme.drawMedievalCard(graphics, barX, barY, barW, barH, MarketTheme.SLOT_BG);
        graphics.renderItem(EMERALD_STACK, barX + 3, barY + 2);

        String fundsText = String.format("§7Pocket Funds: §a%d §fEmeralds", totalEmeralds);
        graphics.drawString(font, fundsText, barX + 22, barY + 6, 0xFFFFFF, false);

        if (hasLicense) {
            String licText = "§a✔ LICENSED MERCHANT";
            graphics.drawString(font, licText, barX + barW - font.width(licText) - 8, barY + 6, 0x55FF55, false);
        } else {
            String licText = "§c✖ UNLICENSED";
            graphics.drawString(font, licText, barX + barW - font.width(licText) - 8, barY + 6, 0xFF5555, false);
        }

        if (mouseX >= barX && mouseX <= barX + barW && mouseY >= barY && mouseY <= barY + barH) {
            this.hoveredTooltip = Component.literal("§6Pocket Funds: §a" + totalEmeralds + " Emeralds §7(Virtual currency in pocket pouch)");
        }

        // Offer 1 Card: Merchant's License
        renderOfferCard(graphics, mouseX, mouseY,
                cardX(), card1Y(), cardW(), cardH(),
                LICENSE_STACK,
                "§6❖ Imperial Merchant License",
                "§7Permanent royal charter to sell goods.",
                "§7Unlocks selling and merchant dispatch.",
                "§ePrice: §a576 Emeralds §7(64 Blocks)",
                1);

        // Offer 2 Card: Trader's Dispatch Book
        renderOfferCard(graphics, mouseX, mouseY,
                cardX(), card2Y(), cardW(), cardH(),
                BOOK_STACK,
                "§6❖ Trader's Dispatch Book",
                "§7Consumable ledger to summon an express",
                "§7Guild Merchant anywhere for 10 min.",
                "§ePrice: §a200 Emeralds §7(or 23 Blocks)",
                2);

        // Offer 3 Card: Vault Expansion
        int upgradePrice = getUpgradePrice(bankRows);
        String priceStr = bankRows >= 6 ? "§a✔ Maximum Vault Capacity (54 Slots)"
                : String.format("§ePrice: §a%d Emeralds §7(or %d Blocks)", upgradePrice, (upgradePrice + 8) / 9);
        String desc2Str = String.format("§8[Vault Capacity: §e%d / 54 Slots§8]", bankRows * 9);

        renderOfferCard(graphics, mouseX, mouseY,
                cardX(), card3Y(), cardW(), cardH(),
                CHEST_STACK,
                "§6❖ Imperial Vault Expansion",
                "§7Adds +1 storage row (9 slots) to Bank Vault.",
                desc2Str,
                priceStr,
                3);

        // Close Button
        int closeW = 96;
        int closeH = 18;
        int closeX = panelX + (panelW - closeW) / 2;
        int closeY = panelY + panelH - 22;

        boolean closeHov = mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= closeY && mouseY <= closeY + closeH;
        graphics.fill(closeX, closeY, closeX + closeW, closeY + closeH, MarketTheme.PANEL_BORDER);
        graphics.fill(closeX + 1, closeY + 1, closeX + closeW - 1, closeY + closeH - 1, closeHov ? MarketTheme.BTN_DEFAULT_HOVER : MarketTheme.BTN_DEFAULT);
        graphics.drawCenteredString(font, "Close", closeX + closeW / 2, closeY + 5, closeHov ? 0xFFFFFF : MarketTheme.TEXT_PRIMARY);

        // Render hovered item tooltip if applicable
        if (!this.hoveredTooltipStack.isEmpty()) {
            graphics.renderTooltip(font, this.hoveredTooltipStack, mouseX, mouseY);
        } else if (this.hoveredTooltip != null) {
            graphics.renderTooltip(font, this.hoveredTooltip, mouseX, mouseY);
        }
    }

    private String trimText(String text, int maxWidth) {
        if (text == null) return "";
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, maxWidth - font.width("…")) + "…";
    }

    private void renderOfferCard(GuiGraphics graphics, int mouseX, int mouseY,
                                 int x, int y, int width, int height,
                                 ItemStack item, String title, String desc1, String desc2, String price, int offerId) {
        // Card background
        MarketTheme.drawMedievalCard(graphics, x, y, width, height, 0x30000000);

        // Icon box on the left
        int iconBoxW = 38;
        int iconBoxH = 38;
        int iconBoxX = x + 6;
        int iconBoxY = y + (height - iconBoxH) / 2;

        MarketTheme.drawMedievalCard(graphics, iconBoxX, iconBoxY, iconBoxW, iconBoxH, MarketTheme.SLOT_BG);
        graphics.renderOutline(iconBoxX, iconBoxY, iconBoxW, iconBoxH, MarketTheme.PANEL_BORDER_GOLD);
        graphics.renderItem(item, iconBoxX + 11, iconBoxY + 11);

        if (mouseX >= iconBoxX && mouseX <= iconBoxX + iconBoxW && mouseY >= iconBoxY && mouseY <= iconBoxY + iconBoxH) {
            this.hoveredTooltipStack = item;
        }

        // Right Action Button Dimensions
        int btnW = getBtnW();
        int btnH = getBtnH();
        int btnX = x + width - btnW - 8;
        int btnY = y + (height - btnH) / 2;

        // Center text column (guaranteed never to overlap button)
        int textX = x + 48;
        int maxTextW = btnX - textX - 6;

        int lineGap = (height >= 52) ? 11 : 10;
        int startY = (height >= 52) ? (y + 5) : (y + 3);

        graphics.drawString(font, title, textX, startY, MarketTheme.TEXT_GOLD, false);
        graphics.drawString(font, trimText(desc1, maxTextW), textX, startY + lineGap, MarketTheme.TEXT_MUTED, false);
        graphics.drawString(font, trimText(desc2, maxTextW), textX, startY + lineGap * 2, MarketTheme.TEXT_MUTED, false);
        graphics.drawString(font, trimText(price, maxTextW), textX, startY + lineGap * 3, 0xFFE0E0E0, false);

        boolean btnHov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        int singleLineTextY = btnY + (btnH - 8) / 2;
        int twoLineTextY1 = btnY + (btnH - 18) / 2;
        int twoLineTextY2 = twoLineTextY1 + 10;

        if (offerId == 1) { // License Button
            if (hasLicense) {
                boolean holdsDeed = false;
                if (Minecraft.getInstance().player != null) {
                    for (int i = 0; i < Minecraft.getInstance().player.getInventory().getContainerSize(); i++) {
                        if (Minecraft.getInstance().player.getInventory().getItem(i).is(ModRegistry.MERCHANTS_LICENSE.get())) {
                            holdsDeed = true;
                            break;
                        }
                    }
                }
                if (!holdsDeed) {
                    int bg = btnHov ? MarketTheme.BTN_ACCENT_HOVER : MarketTheme.BTN_ACCENT;
                    graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, bg);
                    graphics.renderOutline(btnX, btnY, btnW, btnH, MarketTheme.PANEL_BORDER_GOLD);
                    graphics.drawCenteredString(font, "§e✦ Reissue Deed", btnX + btnW / 2, twoLineTextY1, 0xFFFFFF);
                    graphics.drawCenteredString(font, "§a(Free)", btnX + btnW / 2, twoLineTextY2, 0x55FF55);
                } else {
                    graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, 0xFF142418);
                    graphics.renderOutline(btnX, btnY, btnW, btnH, 0xFF2E7D32);
                    graphics.drawCenteredString(font, "§a✔ Acquired", btnX + btnW / 2, twoLineTextY1, 0x55FF55);
                    graphics.drawCenteredString(font, "§8(Permanent)", btnX + btnW / 2, twoLineTextY2, 0x88AAAAAA);
                }
            } else if (totalEmeralds >= 576) {
                int bg = btnHov ? MarketTheme.BTN_PRIMARY_HOVER : MarketTheme.BTN_PRIMARY;
                graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, bg);
                graphics.renderOutline(btnX, btnY, btnW, btnH, MarketTheme.PANEL_BORDER_GOLD);
                graphics.drawCenteredString(font, "§6⚔ Purchase", btnX + btnW / 2, singleLineTextY, MarketTheme.TEXT_GOLD);
            } else {
                graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, MarketTheme.BTN_DISABLED);
                graphics.renderOutline(btnX, btnY, btnW, btnH, 0xFF3E3326);
                graphics.drawCenteredString(font, "§cNeed 576 ❇", btnX + btnW / 2, singleLineTextY, 0xFF6666);
            }
        } else if (offerId == 2) { // Dispatch Book Button
            if (totalEmeralds >= 200) {
                int bg = btnHov ? MarketTheme.BTN_ACCENT_HOVER : MarketTheme.BTN_ACCENT;
                graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, bg);
                graphics.renderOutline(btnX, btnY, btnW, btnH, MarketTheme.PANEL_BORDER_GOLD);
                graphics.drawCenteredString(font, "§e⚔ Purchase", btnX + btnW / 2, singleLineTextY, 0xFFFFFF);
            } else {
                graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, MarketTheme.BTN_DISABLED);
                graphics.renderOutline(btnX, btnY, btnW, btnH, 0xFF3E3326);
                graphics.drawCenteredString(font, "§cNeed 200 ❇", btnX + btnW / 2, singleLineTextY, 0xFF6666);
            }
        } else if (offerId == 3) { // Vault Expansion Button
            int upgradeCost = getUpgradePrice(bankRows);
            if (bankRows >= 6) {
                graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, 0xFF142418);
                graphics.renderOutline(btnX, btnY, btnW, btnH, 0xFF2E7D32);
                graphics.drawCenteredString(font, "§a✔ Max Vault", btnX + btnW / 2, twoLineTextY1, 0x55FF55);
                graphics.drawCenteredString(font, "§8(54 Slots)", btnX + btnW / 2, twoLineTextY2, 0x88AAAAAA);
            } else if (totalEmeralds >= upgradeCost) {
                int bg = btnHov ? MarketTheme.BTN_PRIMARY_HOVER : MarketTheme.BTN_PRIMARY;
                graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, bg);
                graphics.renderOutline(btnX, btnY, btnW, btnH, MarketTheme.PANEL_BORDER_GOLD);
                graphics.drawCenteredString(font, "§6⚔ Expand Vault", btnX + btnW / 2, singleLineTextY, MarketTheme.TEXT_GOLD);
            } else {
                graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, MarketTheme.BTN_DISABLED);
                graphics.renderOutline(btnX, btnY, btnW, btnH, 0xFF3E3326);
                graphics.drawCenteredString(font, String.format("§cNeed %d ❇", upgradeCost), btnX + btnW / 2, singleLineTextY, 0xFF6666);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int btnW = getBtnW();
            int btnH = getBtnH();

            // Check Offer 1 (License) button click
            int b1X = cardX() + cardW() - btnW - 8;
            int b1Y = card1Y() + (cardH() - btnH) / 2;
            if (mouseX >= b1X && mouseX <= b1X + btnW && mouseY >= b1Y && mouseY <= b1Y + btnH) {
                if (hasLicense) {
                    playClickSound();
                    PacketDistributor.sendToServer(new BuyMasterOfferC2S(1));
                    return true;
                } else if (totalEmeralds >= 576) {
                    playClickSound();
                    PacketDistributor.sendToServer(new BuyMasterOfferC2S(1));
                    return true;
                } else {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
                    return true;
                }
            }

            // Check Offer 2 (Book) button click
            int b2X = cardX() + cardW() - btnW - 8;
            int b2Y = card2Y() + (cardH() - btnH) / 2;
            if (mouseX >= b2X && mouseX <= b2X + btnW && mouseY >= b2Y && mouseY <= b2Y + btnH) {
                if (totalEmeralds >= 200) {
                    playClickSound();
                    PacketDistributor.sendToServer(new BuyMasterOfferC2S(2));
                    return true;
                } else {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
                    return true;
                }
            }

            // Check Offer 3 (Vault Expansion) button click
            int b3X = cardX() + cardW() - btnW - 8;
            int b3Y = card3Y() + (cardH() - btnH) / 2;
            if (mouseX >= b3X && mouseX <= b3X + btnW && mouseY >= b3Y && mouseY <= b3Y + btnH) {
                int upgradeCost = getUpgradePrice(bankRows);
                if (bankRows < 6 && totalEmeralds >= upgradeCost) {
                    playClickSound();
                    PacketDistributor.sendToServer(new BuyMasterOfferC2S(3));
                    return true;
                } else if (bankRows < 6) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
                    return true;
                }
            }

            // Check Close button click
            int closeW = 96;
            int closeH = 18;
            int closeX = getPanelX() + (getPanelW() - closeW) / 2;
            int closeY = getPanelY() + getPanelH() - 22;
            if (mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= closeY && mouseY <= closeY + closeH) {
                playClickSound();
                this.onClose();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
