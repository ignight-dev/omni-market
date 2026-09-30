package com.omni.marketplace.client.screen;

import com.omni.marketplace.catalog.CategoryDef;
import com.omni.marketplace.catalog.CategoryDef.MainCategory;
import com.omni.marketplace.catalog.CategoryDef.SubCategory;
import com.omni.marketplace.catalog.PriceEngine;
import com.omni.marketplace.client.gui.MarketTheme;
import com.omni.marketplace.client.gui.widget.CategoryRail;
import com.omni.marketplace.client.gui.widget.CustomButton;
import com.omni.marketplace.client.gui.widget.CustomTabButton;
import com.omni.marketplace.network.MarketPackets.*;
import com.omni.marketplace.util.CurrencyUtils;
import com.omni.marketplace.util.CurrencyUtils.CurrencyBreakdown;
import com.omni.marketplace.util.ItemSerializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class MarketplaceScreen extends Screen {

    public enum Tab {
        BROWSE("⚔ Market Catalog", "⚔ Catalog"),
        SELL("✦ Merchant's Bag", "✦ Bag"),
        TRANSACTIONS("❖ Guild Ledgers", "❖ Ledgers"),
        EXCHANGE("⚖ Coin Exchange", "⚖ Coins");

        public final String label;
        public final String shortLabel;
        Tab(String label, String shortLabel) {
            this.label = label;
            this.shortLabel = shortLabel;
        }
    }

    private Tab currentTab = Tab.BROWSE;
    private MainCategory currentMainCategory = MainCategory.ALL;
    private SubCategory currentSubCategory = SubCategory.ALL;

    // Account & Data
    private long playerBalance = 0L;
    private int vaultItemCount = 0;
    private long vaultCoins = 0L;

    private final List<CatalogEntry> catalogEntries = new ArrayList<>();
    private final List<OrderEntry> playerListings = new ArrayList<>();
    private final List<OrderEntry> playerBuyOrders = new ArrayList<>();
    private final List<OrderEntry> currentAsks = new ArrayList<>();
    private final List<OrderEntry> currentBids = new ArrayList<>();

    // Selected item for Focus & Trade Dialog
    private CatalogEntry selectedCatalogEntry = null;
    private ItemStack selectedStack = ItemStack.EMPTY;
    private int selectedInventorySlot = -1;

    // Scrolling & Pagination
    private int catalogScrollOffset = 0;
    private int catalogPage = 0;
    private int transactionsListingScroll = 0;
    private int transactionsOrderScroll = 0;

    // Hovered tooltips
    private ItemStack hoveredTooltipStack = ItemStack.EMPTY;
    private Component hoveredTooltipText = null;

    // Trade Dialog State (Dedicated full modal flow like Swell's marketplace)
    private boolean showTradeDialog = false;
    private boolean tradeIsBuy = true;
    private int tradeQuantity = 1;
    private long tradeUnitPrice = 100L;

    // Price Adjustment Hold-to-Repeat State
    private enum HoldAction {
        NONE,
        PLUS_PRICE,
        MINUS_PRICE
    }
    private HoldAction activeHold = HoldAction.NONE;
    private long holdStartTime = 0L;
    private long lastHoldStepTime = 0L;

    // Widgets
    private final List<CustomTabButton> tabButtons = new ArrayList<>();
    private CustomButton takeAllVaultBtn;
    private EditBox searchBox;
    private EditBox qtyBox;
    private EditBox goldBox;
    private EditBox silverBox;
    private EditBox copperBox;
    private CategoryRail categoryRail;

    // Layout coordinates
    private int panelX, panelY, panelW, panelH;
    private int contentX, contentY, contentW, contentH;

    public MarketplaceScreen() {
        super(Component.literal("Global Trading Post"));
    }

    private int getBrowseLeftX() {
        return this.contentX + 32;
    }

    private int getBrowseLeftW() {
        return this.contentW - 36;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.tabButtons.clear();

        // Responsive floating panel layout (fixed clean bounds on fullscreen, adaptive in windowed)
        this.panelW = Math.min(680, Math.max(340, this.width - 16));
        if (this.panelW > this.width) {
            this.panelW = Math.max(200, this.width - 8);
        }
        this.panelH = Math.min(420, Math.max(220, this.height - 16));
        if (this.panelH > this.height) {
            this.panelH = Math.max(160, this.height - 8);
        }
        this.panelX = Math.max(4, (this.width - this.panelW) / 2);
        this.panelY = Math.max(4, (this.height - this.panelH) / 2);

        this.contentX = this.panelX + 6;
        this.contentY = this.panelY + 52;
        this.contentW = this.panelW - 12;
        this.contentH = this.panelH - 58;

        // Header Custom Claim All Vault Button (Embossed Guild Amber with Gold Border)
        this.takeAllVaultBtn = addRenderableWidget(new CustomButton(
                this.panelX + this.panelW - 84,
                this.panelY + 5,
                78,
                18,
                Component.literal("📥 Claim"),
                btn -> PacketDistributor.sendToServer(new ClaimVaultC2S())
        ).withColors(MarketTheme.BTN_ACCENT, MarketTheme.BTN_ACCENT_HOVER, 0xFFFFFFFF)
         .withBorder(MarketTheme.PANEL_BORDER_GOLD));

        // Tab Buttons (Medieval Guild Tabs)
        int tabY = this.panelY + 28;
        int tabW = (this.panelW - 12) / Tab.values().length;
        int currentTabX = this.panelX + 6;

        for (Tab tab : Tab.values()) {
            int actualW = tabW - 2;
            String label = (actualW < 90) ? tab.shortLabel : tab.label;
            CustomTabButton tabBtn = addRenderableWidget(new CustomTabButton(
                    currentTabX,
                    tabY,
                    actualW,
                    20,
                    Component.literal(label),
                    tab == this.currentTab,
                    btn -> switchTab(tab)
            ));
            this.tabButtons.add(tabBtn);
            currentTabX += tabW;
        }

        // Category Rail for Browse Tab (Wrought-Iron Heraldic Banner)
        this.categoryRail = new CategoryRail(this.contentX, this.contentY + 2, 28, this.contentH - 4, this.font, cat -> {
            this.currentMainCategory = cat;
            this.currentSubCategory = SubCategory.ALL;
            this.catalogPage = 0;
            this.catalogScrollOffset = 0;
            refreshCatalog();
        });
        this.categoryRail.setActiveCategory(this.currentMainCategory);

        // Search Edit Box (Spans full remaining width)
        int leftX = getBrowseLeftX();
        int leftW = getBrowseLeftW();
        this.searchBox = new EditBox(this.font, leftX, this.contentY + 2, leftW, 14, Component.literal("Search..."));
        this.searchBox.setResponder(text -> {
            this.catalogPage = 0;
            this.catalogScrollOffset = 0;
            refreshCatalog();
        });
        addWidget(this.searchBox);

        // Trade Dialog Quantity & Price (Gold, Silver, Copper) Edit Boxes
        this.qtyBox = new EditBox(this.font, 0, 0, 32, 14, Component.literal("Qty"));
        this.qtyBox.setValue(String.valueOf(this.tradeQuantity));
        this.qtyBox.setFilter(s -> s.matches("\\d*"));
        this.qtyBox.setMaxLength(4);
        this.qtyBox.setResponder(val -> {
            try { this.tradeQuantity = Math.max(1, Integer.parseInt(val.trim())); } catch (Exception ignored) {}
        });
        addWidget(this.qtyBox);

        CurrencyBreakdown b = CurrencyUtils.breakdown(this.tradeUnitPrice);

        this.goldBox = new EditBox(this.font, 0, 0, 30, 14, Component.literal("Gold"));
        this.goldBox.setValue(String.valueOf(b.gold()));
        this.goldBox.setFilter(s -> s.matches("\\d*"));
        this.goldBox.setMaxLength(6);
        this.goldBox.setResponder(val -> updatePriceFromBoxes());
        addWidget(this.goldBox);

        this.silverBox = new EditBox(this.font, 0, 0, 26, 14, Component.literal("Silver"));
        this.silverBox.setValue(String.valueOf(b.silver()));
        this.silverBox.setFilter(s -> s.matches("\\d*"));
        this.silverBox.setMaxLength(2);
        this.silverBox.setResponder(val -> updatePriceFromBoxes());
        addWidget(this.silverBox);

        this.copperBox = new EditBox(this.font, 0, 0, 26, 14, Component.literal("Copper"));
        this.copperBox.setValue(String.valueOf(b.copper()));
        this.copperBox.setFilter(s -> s.matches("\\d*"));
        this.copperBox.setMaxLength(2);
        this.copperBox.setResponder(val -> updatePriceFromBoxes());
        addWidget(this.copperBox);

        updateWidgetVisibility();
        refreshCatalog();
    }

    private void setDialogFocus(EditBox target) {
        if (this.qtyBox != null) this.qtyBox.setFocused(this.qtyBox == target);
        if (this.goldBox != null) this.goldBox.setFocused(this.goldBox == target);
        if (this.silverBox != null) this.silverBox.setFocused(this.silverBox == target);
        if (this.copperBox != null) this.copperBox.setFocused(this.copperBox == target);
        if (this.searchBox != null) this.searchBox.setFocused(false);
        this.setFocused(target);
    }

    private void updateWidgetVisibility() {
        boolean inDialog = this.showTradeDialog;
        if (this.searchBox != null) {
            this.searchBox.setVisible(!inDialog && this.currentTab == Tab.BROWSE);
        }
        if (this.qtyBox != null) {
            this.qtyBox.setVisible(inDialog);
        }
        if (this.goldBox != null) {
            this.goldBox.setVisible(inDialog);
        }
        if (this.silverBox != null) {
            this.silverBox.setVisible(inDialog);
        }
        if (this.copperBox != null) {
            this.copperBox.setVisible(inDialog);
        }
        for (CustomTabButton btn : this.tabButtons) {
            btn.visible = !inDialog;
        }
        if (this.takeAllVaultBtn != null) {
            this.takeAllVaultBtn.visible = !inDialog;
        }
    }

    private void updatePriceFromBoxes() {
        try {
            long g = (goldBox != null && !goldBox.getValue().trim().isEmpty()) ? Math.max(0, Long.parseLong(goldBox.getValue().trim())) : 0L;
            long s = (silverBox != null && !silverBox.getValue().trim().isEmpty()) ? Math.max(0, Long.parseLong(silverBox.getValue().trim())) : 0L;
            long c = (copperBox != null && !copperBox.getValue().trim().isEmpty()) ? Math.max(0, Long.parseLong(copperBox.getValue().trim())) : 0L;
            long total = CurrencyUtils.toCopper(g, s, c);
            this.tradeUnitPrice = Math.max(1L, total);
        } catch (Exception ignored) {
        }
    }

    private void adjustPriceByCopper(long delta) {
        updatePriceFromBoxes();
        long newPrice = Math.max(1L, this.tradeUnitPrice + delta);
        setBoxesFromPrice(newPrice);
    }

    private void setBoxesFromPrice(long copperPrice) {
        this.tradeUnitPrice = Math.max(1L, copperPrice);
        CurrencyBreakdown b = CurrencyUtils.breakdown(this.tradeUnitPrice);
        if (this.goldBox != null) this.goldBox.setValue(String.valueOf(b.gold()));
        if (this.silverBox != null) this.silverBox.setValue(String.valueOf(b.silver()));
        if (this.copperBox != null) this.copperBox.setValue(String.valueOf(b.copper()));
    }

    private void switchTab(Tab tab) {
        this.currentTab = tab;
        this.showTradeDialog = false;
        updateWidgetVisibility();
        for (int i = 0; i < Tab.values().length && i < this.tabButtons.size(); i++) {
            this.tabButtons.get(i).setSelected(Tab.values()[i] == tab);
        }
        if (tab == Tab.TRANSACTIONS) {
            PacketDistributor.sendToServer(new RequestTransactionsC2S());
        }
    }

    public void updateAccount(long balance, int vaultCount, long vaultCoins) {
        this.playerBalance = balance;
        this.vaultItemCount = vaultCount;
        this.vaultCoins = vaultCoins;
    }

    private ItemAnalytics currentAnalytics = null;

    public void updateOrderBook(String itemId, List<OrderEntry> asks, List<OrderEntry> bids, ItemAnalytics analytics) {
        this.currentAsks.clear();
        this.currentAsks.addAll(asks);
        this.currentBids.clear();
        this.currentBids.addAll(bids);
        this.currentAnalytics = analytics;
    }

    public void updateOrderBook(String itemId, List<OrderEntry> asks, List<OrderEntry> bids) {
        updateOrderBook(itemId, asks, bids, ItemAnalytics.EMPTY);
    }

    public void updateFavorites(List<String> favs) {
        com.omni.marketplace.client.FavoritesClientState.setFavorites(favs);
        if (this.currentMainCategory == MainCategory.FAVORITES) {
            refreshCatalog();
        }
    }

    public void updateCatalog(List<CatalogEntry> entries) {
        this.catalogEntries.clear();
        this.catalogEntries.addAll(entries);
        if (this.selectedCatalogEntry == null && !this.catalogEntries.isEmpty()) {
            selectCatalogEntry(this.catalogEntries.get(0));
        } else if (this.selectedCatalogEntry != null) {
            for (CatalogEntry e : entries) {
                if (e.itemId().equals(this.selectedCatalogEntry.itemId())) {
                    this.selectedCatalogEntry = e;
                    break;
                }
            }
        }
    }

    public void updateTransactions(List<OrderEntry> listings, List<OrderEntry> buyOrders) {
        this.playerListings.clear();
        this.playerListings.addAll(listings);
        this.playerBuyOrders.clear();
        this.playerBuyOrders.addAll(buyOrders);
    }

    public void refreshCatalog() {
        String query = searchBox != null ? searchBox.getValue() : "";
        PacketDistributor.sendToServer(new RequestCatalogC2S(query, this.currentMainCategory.name(), this.currentSubCategory.name(), this.catalogPage));
    }

    private void selectCatalogEntry(CatalogEntry entry) {
        this.selectedCatalogEntry = entry;
        this.selectedInventorySlot = -1;
        this.selectedStack = ItemSerializer.deserialize(entry.sampleNbt(), entry.itemId(), 1, Minecraft.getInstance().level.registryAccess());
        this.tradeQuantity = 1;
        long targetPrice = entry.lowestSell() > 0 ? entry.lowestSell() : (entry.highestBuy() > 0 ? entry.highestBuy() : entry.suggestedPrice());
        if (targetPrice <= 0) targetPrice = 100L;
        setBoxesFromPrice(targetPrice);
        if (this.qtyBox != null) this.qtyBox.setValue(String.valueOf(this.tradeQuantity));
        PacketDistributor.sendToServer(new RequestItemDetailsC2S(entry.itemId()));
    }

    private void openTradeDialogForCatalog(CatalogEntry entry) {
        this.activeHold = HoldAction.NONE;
        selectCatalogEntry(entry);
        this.tradeIsBuy = true;
        this.showTradeDialog = true;
        setDialogFocus(null);
        updateWidgetVisibility();
    }

    private void openTradeDialogForInventory(ItemStack stack, int slotIndex) {
        this.activeHold = HoldAction.NONE;
        selectInventoryItem(stack, slotIndex);
        this.tradeIsBuy = false;
        this.showTradeDialog = true;
        setDialogFocus(null);
        updateWidgetVisibility();
    }

    private void selectInventoryItem(ItemStack stack, int slotIndex) {
        this.selectedStack = stack.copy();
        this.selectedInventorySlot = slotIndex;
        ResourceLocation loc = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String itemId = loc.toString();

        CatalogEntry matched = null;
        for (CatalogEntry e : this.catalogEntries) {
            if (e.itemId().equals(itemId)) {
                matched = e;
                break;
            }
        }

        if (matched != null) {
            this.selectedCatalogEntry = matched;
        } else {
            long baseline = PriceEngine.getBaselinePrice(stack.getItem());
            this.selectedCatalogEntry = new CatalogEntry(itemId, ItemSerializer.serialize(stack, Minecraft.getInstance().level.registryAccess()), 0, 0, baseline, 0, 0);
        }

        this.tradeIsBuy = false;
        this.tradeQuantity = stack.getCount();

        long targetPrice = this.selectedCatalogEntry.highestBuy() > 0 ? this.selectedCatalogEntry.highestBuy() : (this.selectedCatalogEntry.lowestSell() > 0 ? this.selectedCatalogEntry.lowestSell() : this.selectedCatalogEntry.suggestedPrice());
        if (targetPrice <= 0) targetPrice = 100L;
        setBoxesFromPrice(targetPrice);
        if (this.qtyBox != null) this.qtyBox.setValue(String.valueOf(this.tradeQuantity));
        PacketDistributor.sendToServer(new RequestItemDetailsC2S(itemId));
    }

    /* ========================================================================= */
    /* Render Pipeline                                                           */
    /* ========================================================================= */

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Atmospheric deep medieval dusk without blur shader
        graphics.fillGradient(0, 0, this.width, this.height, MarketTheme.BACKDROP, 0xDC100B12);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics, mouseX, mouseY, partialTicks);
        this.hoveredTooltipStack = ItemStack.EMPTY;
        this.hoveredTooltipText = null;

        if (this.showTradeDialog) {
            updateHoldRepeat(mouseX, mouseY);
            // Dedicated Trade Dialog Screen View:
            // Skip ALL background tab rendering, search box, pills, and tab buttons to prevent overlap
            renderTradeDialog(graphics, mouseX, mouseY, partialTicks);
        } else {
            this.renderPanelFrame(graphics);
            this.renderHeader(graphics);

            switch (this.currentTab) {
                case BROWSE -> renderBrowseTab(graphics, mouseX, mouseY);
                case SELL -> renderSellTab(graphics, mouseX, mouseY);
                case TRANSACTIONS -> renderTransactionsTab(graphics, mouseX, mouseY);
                case EXCHANGE -> renderExchangeTab(graphics, mouseX, mouseY);
            }

            super.render(graphics, mouseX, mouseY, partialTicks);

            if (this.currentTab == Tab.BROWSE && this.categoryRail != null) {
                this.categoryRail.renderTooltip(graphics, mouseX, mouseY);
            }
        }

        if (this.hoveredTooltipStack != null && !this.hoveredTooltipStack.isEmpty()) {
            graphics.renderTooltip(this.font, this.hoveredTooltipStack, mouseX, mouseY);
            this.hoveredTooltipStack = ItemStack.EMPTY;
        } else if (this.hoveredTooltipText != null) {
            graphics.renderTooltip(this.font, this.hoveredTooltipText, mouseX, mouseY);
            this.hoveredTooltipText = null;
        }
    }

    private void updateHoldRepeat(double mouseX, double mouseY) {
        if (!this.showTradeDialog || this.activeHold == HoldAction.NONE) {
            return;
        }

        // Verify mouse button is still physically held down
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            this.activeHold = HoldAction.NONE;
            return;
        }

        // Verify mouse cursor is still over the button
        int dialogW = Math.min(320, this.width - 24);
        int dialogH = Math.min(218, this.height - 16);
        int dialogX = (this.width - dialogW) / 2;
        int dialogY = (this.height - dialogH) / 2;
        int pY = dialogY + 116;

        int btnMinusX = dialogX + 190;
        int btnMinusW = 32;
        int btnMinusY = pY + 1;
        int btnMinusH = 14;

        int btnPlusX = dialogX + 226;
        int btnPlusW = 32;
        int btnPlusY = pY + 1;
        int btnPlusH = 14;

        if (this.activeHold == HoldAction.MINUS_PRICE) {
            if (!(mouseX >= btnMinusX && mouseX <= btnMinusX + btnMinusW && mouseY >= btnMinusY && mouseY <= btnMinusY + btnMinusH)) {
                this.activeHold = HoldAction.NONE;
                return;
            }
        } else if (this.activeHold == HoldAction.PLUS_PRICE) {
            if (!(mouseX >= btnPlusX && mouseX <= btnPlusX + btnPlusW && mouseY >= btnPlusY && mouseY <= btnPlusY + btnPlusH)) {
                this.activeHold = HoldAction.NONE;
                return;
            }
        }

        long now = System.currentTimeMillis();
        long heldDuration = now - this.holdStartTime;

        // Wait 3 seconds to detect hold press before activating rapid adjustment
        if (heldDuration >= 3000L) {
            // Rapid mode: 50 copper per second (5 copper every 100 ms)
            long stepInterval = 100L;
            if (this.lastHoldStepTime < this.holdStartTime + 3000L) {
                this.lastHoldStepTime = this.holdStartTime + 3000L;
            }

            long elapsedSinceStep = now - this.lastHoldStepTime;
            if (elapsedSinceStep >= stepInterval) {
                long steps = elapsedSinceStep / stepInterval;
                long delta = steps * 5L;
                this.lastHoldStepTime += steps * stepInterval;

                if (this.activeHold == HoldAction.PLUS_PRICE) {
                    adjustPriceByCopper(delta);
                } else if (this.activeHold == HoldAction.MINUS_PRICE) {
                    adjustPriceByCopper(-delta);
                }
            }
        }
    }

    private void renderPanelFrame(GuiGraphics graphics) {
        MarketTheme.drawMedievalFrame(graphics, this.panelX, this.panelY, this.panelW, this.panelH);
    }

    private void renderHeader(GuiGraphics graphics) {
        int claimBtnX = this.panelX + this.panelW - 84;
        int rightLimit = claimBtnX - 8;

        if (this.takeAllVaultBtn != null) {
            this.takeAllVaultBtn.active = (this.vaultItemCount > 0 || this.vaultCoins > 0);
        }

        if (this.panelW >= 580) {
            graphics.drawString(this.font, Component.literal("❖ §6GLOBAL TRADING POST §f❖"), this.panelX + 12, this.panelY + 10, MarketTheme.TEXT_TITLE, false);
            String walletText = "§7Royal Purse: " + CurrencyUtils.format(this.playerBalance);
            graphics.drawString(this.font, Component.literal(walletText), this.panelX + 165, this.panelY + 10, MarketTheme.TEXT_GOLD, false);
            String vaultText = String.format("§7Guild Vault: §e%d goods §7| %s", this.vaultItemCount, CurrencyUtils.format(this.vaultCoins));
            int vaultTextW = this.font.width(vaultText);
            int vaultTextX = rightLimit - vaultTextW;
            graphics.drawString(this.font, Component.literal(vaultText), vaultTextX, this.panelY + 10, MarketTheme.TEXT_PRIMARY, false);
        } else {
            graphics.drawString(this.font, Component.literal("§6TRADING POST"), this.panelX + 10, this.panelY + 6, MarketTheme.TEXT_TITLE, false);
            String walletText = "§7Purse: " + CurrencyUtils.format(this.playerBalance);
            graphics.drawString(this.font, Component.literal(walletText), this.panelX + 10, this.panelY + 16, MarketTheme.TEXT_GOLD, false);
            String vaultText = String.format("§7Vault: §e%d §7| %s", this.vaultItemCount, CurrencyUtils.format(this.vaultCoins));
            int vaultTextW = this.font.width(vaultText);
            int vaultTextX = Math.max(this.panelX + 115, rightLimit - vaultTextW);
            graphics.drawString(this.font, Component.literal(trimText(vaultText, rightLimit - vaultTextX)), vaultTextX, this.panelY + 16, MarketTheme.TEXT_PRIMARY, false);
        }
    }

    /* ========================================================================= */
    /* Tab 1: Market Catalog (Full Table Width, No Cramped Side Panel)           */
    /* ========================================================================= */

    private void renderBrowseTab(GuiGraphics graphics, int mouseX, int mouseY) {
        this.categoryRail.render(graphics, mouseX, mouseY);

        int searchX = getBrowseLeftX();
        int searchY = this.contentY + 2;
        int searchW = getBrowseLeftW();

        graphics.fill(searchX - 1, searchY - 1, searchX + searchW + 1, searchY + 15, MarketTheme.PANEL_BORDER);
        graphics.fill(searchX, searchY, searchX + searchW, searchY + 14, MarketTheme.SLOT_BG);
        this.searchBox.setX(searchX);
        this.searchBox.setY(searchY);
        this.searchBox.setWidth(searchW);
        this.searchBox.render(graphics, mouseX, mouseY, 0);

        int tableX = searchX;
        int tableW = searchW;
        int pillX = tableX;
        int pillY = this.contentY + 18;
        int pillRowH = 15;

        List<SubCategory> subs = CategoryDef.getSubCategories(this.currentMainCategory);
        for (SubCategory sub : subs) {
            int btnW = this.font.width(sub.displayName) + 8;
            if (pillX + btnW > tableX + tableW && pillX > tableX) {
                pillX = tableX;
                pillY += pillRowH;
            }
            boolean isSubActive = (sub == this.currentSubCategory);
            boolean isSubHov = mouseX >= pillX && mouseX <= pillX + btnW && mouseY >= pillY && mouseY <= pillY + 13;

            int bg = isSubActive ? MarketTheme.BTN_ACCENT : (isSubHov ? MarketTheme.BTN_ACCENT_HOVER : MarketTheme.SLOT_BG);
            int border = isSubActive ? MarketTheme.PANEL_BORDER_GOLD : (isSubHov ? MarketTheme.PANEL_BORDER_GOLD : MarketTheme.SLOT_BORDER);

            graphics.fill(pillX, pillY, pillX + btnW, pillY + 13, border);
            graphics.fill(pillX + 1, pillY + 1, pillX + btnW - 1, pillY + 12, bg);

            if (isSubActive) {
                graphics.fill(pillX + 2, pillY + 11, pillX + btnW - 2, pillY + 12, MarketTheme.TAB_ACTIVE_BAR);
            }
            int textColor = isSubActive ? MarketTheme.TEXT_GOLD : (isSubHov ? 0xFFFFFFFF : MarketTheme.TEXT_SECONDARY);
            graphics.drawCenteredString(this.font, Component.literal(sub.displayName), pillX + (btnW / 2), pillY + 2, textColor);
            pillX += btnW + 2;
        }

        int tableY = pillY + pillRowH + 2;
        int tableH = this.contentH - (tableY - this.contentY) - 16;

        MarketTheme.drawMedievalCard(graphics, tableX, tableY, tableW, tableH, MarketTheme.SLOT_BG);

        // Table Header
        graphics.fill(tableX + 1, tableY + 1, tableX + tableW - 1, tableY + 16, MarketTheme.PANEL_HEADER_BG);
        graphics.fill(tableX + 1, tableY + 16, tableX + tableW - 1, tableY + 17, MarketTheme.PANEL_BORDER_GOLD);
        graphics.drawString(this.font, Component.literal("★"), tableX + 24, tableY + 4, MarketTheme.TEXT_GOLD, false);
        graphics.drawString(this.font, Component.literal("Good / Item"), tableX + 38, tableY + 4, MarketTheme.TEXT_GOLD, false);

        int tradeBtnW = 48;
        int tradeBtnX = tableX + tableW - 54;
        int bidX = tableX + tableW - 128;
        int askX = tableX + tableW - 202;
        graphics.drawString(this.font, Component.literal("Ask"), askX, tableY + 4, MarketTheme.TEXT_GOLD, false);
        graphics.drawString(this.font, Component.literal("Bid"), bidX, tableY + 4, MarketTheme.TEXT_GOLD, false);
        graphics.drawString(this.font, Component.literal("Action"), tradeBtnX + 6, tableY + 4, MarketTheme.TEXT_GOLD, false);

        int rowY = tableY + 18;
        int visibleRows = Math.max(1, (tableH - 20) / 22);
        int maxOffset = Math.max(0, this.catalogEntries.size() - visibleRows);
        this.catalogScrollOffset = Math.min(this.catalogScrollOffset, maxOffset);

        for (int i = 0; i < visibleRows; i++) {
            int index = this.catalogScrollOffset + i;
            if (index >= this.catalogEntries.size()) break;
            CatalogEntry entry = this.catalogEntries.get(index);
            int currentY = rowY + (i * 22);

            boolean isHovered = mouseX >= tableX && mouseX <= tableX + tableW - 6 && mouseY >= currentY && mouseY < currentY + 21;
            boolean isSelected = (this.selectedCatalogEntry != null && this.selectedCatalogEntry.itemId().equals(entry.itemId()));

            int rowBg = isSelected ? 0x35D4AF37 : (isHovered ? MarketTheme.ROW_HOVER : (i % 2 == 0 ? MarketTheme.ROW_EVEN : MarketTheme.ROW_ODD));
            graphics.fill(tableX + 1, currentY, tableX + tableW - 5, currentY + 21, rowBg);

            if (isSelected) {
                graphics.fill(tableX + 1, currentY, tableX + 3, currentY + 21, MarketTheme.PANEL_CORNER_GOLD);
            } else if (isHovered) {
                graphics.fill(tableX + 1, currentY, tableX + 2, currentY + 21, MarketTheme.PANEL_BORDER_GOLD);
            }

            // Inset Item Slot
            int slotX = tableX + 3;
            int slotY = currentY + 2;
            graphics.fill(slotX, slotY, slotX + 18, slotY + 18, MarketTheme.SLOT_BORDER);
            graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF0A090D);

            ItemStack stack = ItemSerializer.deserialize(entry.sampleNbt(), entry.itemId(), 1, Minecraft.getInstance().level.registryAccess());
            graphics.renderItem(stack, slotX + 1, slotY + 1);

            // Item tooltip only triggers when hovering specifically over the item icon
            boolean isIconHovered = mouseX >= slotX && mouseX <= slotX + 18 && mouseY >= slotY && mouseY <= slotY + 18;
            if (isIconHovered && !stack.isEmpty()) {
                this.hoveredTooltipStack = stack;
            }

            // Watchlist Star (Clickable)
            int starX = tableX + 23;
            int starY = currentY + 3;
            int starW = 12;
            int starH = 16;
            boolean isFav = com.omni.marketplace.client.FavoritesClientState.isFavorite(entry.itemId());
            boolean isStarHovered = mouseX >= starX && mouseX <= starX + starW && mouseY >= starY && mouseY <= starY + starH;

            String starIcon = isFav ? "§6★" : (isStarHovered ? "§e☆" : "§8☆");
            graphics.drawString(this.font, Component.literal(starIcon), starX + 1, currentY + 7, isFav ? MarketTheme.TEXT_GOLD : MarketTheme.TEXT_MUTED, false);

            if (isStarHovered) {
                this.hoveredTooltipText = Component.literal(isFav ? "§cRemove from Watchlist (★)" : "§6★ Add to Watchlist");
            }

            String displayName = stack.isEmpty() ? entry.itemId() : stack.getHoverName().getString();
            int maxNameW = Math.max(20, askX - (tableX + 38) - 8);
            graphics.drawString(this.font, Component.literal(trimText(displayName, maxNameW)), tableX + 38, currentY + 7, isSelected ? MarketTheme.TEXT_GOLD : MarketTheme.TEXT_PRIMARY, false);

            String sellText = entry.lowestSell() > 0 ? CurrencyUtils.format(entry.lowestSell()) : "§8--";
            String buyText = entry.highestBuy() > 0 ? CurrencyUtils.format(entry.highestBuy()) : "§8--";
            graphics.drawString(this.font, Component.literal(trimText(sellText, 70)), askX, currentY + 7, MarketTheme.TEXT_PRIMARY, false);
            graphics.drawString(this.font, Component.literal(trimText(buyText, 70)), bidX, currentY + 7, MarketTheme.TEXT_PRIMARY, false);

            // Trade Button on the row
            int tradeBtnY = currentY + 2;
            int tradeBtnH = 17;
            boolean btnHov = mouseX >= tradeBtnX && mouseX <= tradeBtnX + tradeBtnW && mouseY >= tradeBtnY && mouseY <= tradeBtnY + tradeBtnH;
            graphics.fill(tradeBtnX, tradeBtnY, tradeBtnX + tradeBtnW, tradeBtnY + tradeBtnH, MarketTheme.PANEL_BORDER_GOLD);
            graphics.fill(tradeBtnX + 1, tradeBtnY + 1, tradeBtnX + tradeBtnW - 1, tradeBtnY + tradeBtnH - 1, btnHov ? MarketTheme.BTN_PRIMARY_HOVER : MarketTheme.BTN_PRIMARY);
            graphics.drawCenteredString(this.font, Component.literal("⚔ Trade"), tradeBtnX + (tradeBtnW / 2), tradeBtnY + 4, 0xFFFFFF);
        }

        // Render Medieval Scrollbar
        if (this.catalogEntries.size() > visibleRows) {
            int scrollbarX = tableX + tableW - 4;
            int trackY = tableY + 18;
            int trackH = tableH - 20;
            int thumbH = Math.max(12, (trackH * visibleRows) / this.catalogEntries.size());
            int thumbY = trackY + ((trackH - thumbH) * this.catalogScrollOffset) / Math.max(1, maxOffset);

            graphics.fill(scrollbarX, trackY, scrollbarX + 3, trackY + trackH, 0x502A231C);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 3, thumbY + thumbH, MarketTheme.PANEL_CORNER_GOLD);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 3, thumbY + 1, 0x80FFFFFF);
        }

        if (this.catalogEntries.isEmpty()) {
            if (this.currentMainCategory == MainCategory.FAVORITES) {
                int cardH = 50;
                int cardW = Math.min(300, tableW - 40);
                int cardX = tableX + (tableW - cardW) / 2;
                int cardY = tableY + (tableH - cardH) / 2;
                MarketTheme.drawMedievalCard(graphics, cardX, cardY, cardW, cardH, 0xD017151B);
                graphics.drawCenteredString(this.font, Component.literal("§6★ WATCHLIST IS EMPTY ★"), cardX + (cardW / 2), cardY + 12, MarketTheme.TEXT_GOLD);
                graphics.drawCenteredString(this.font, Component.literal("§7Click §6☆§7 next to any item to track it here!"), cardX + (cardW / 2), cardY + 28, MarketTheme.TEXT_MUTED);
            } else {
                graphics.drawCenteredString(this.font, Component.literal("§7No goods found in records."), tableX + (tableW / 2), tableY + 60, MarketTheme.TEXT_MUTED);
            }
        }

        // Pagination Bar below table
        int pagY = tableY + tableH + 2;
        graphics.fill(tableX, pagY, tableX + tableW, pagY + 14, MarketTheme.PANEL_HEADER_BG);
        graphics.fill(tableX, pagY, tableX + tableW, pagY + 1, MarketTheme.PANEL_BORDER);

        boolean canPrev = this.catalogPage > 0;
        boolean prevHov = canPrev && mouseX >= tableX + 2 && mouseX <= tableX + 34 && mouseY >= pagY && mouseY <= pagY + 13;
        graphics.fill(tableX + 2, pagY + 1, tableX + 34, pagY + 13, prevHov ? MarketTheme.BTN_DEFAULT_HOVER : (canPrev ? MarketTheme.BTN_DEFAULT : MarketTheme.BTN_DISABLED));
        graphics.drawCenteredString(this.font, Component.literal("◀ Prev"), tableX + 18, pagY + 3, canPrev ? MarketTheme.TEXT_GOLD : 0x555555);

        String pageStr = "§6" + (this.catalogPage + 1);
        graphics.drawCenteredString(this.font, Component.literal(pageStr), tableX + (tableW / 2), pagY + 3, MarketTheme.TEXT_PRIMARY);

        boolean canNext = this.catalogEntries.size() >= 50;
        boolean nextHov = canNext && mouseX >= tableX + tableW - 34 && mouseX <= tableX + tableW - 2 && mouseY >= pagY && mouseY <= pagY + 13;
        graphics.fill(tableX + tableW - 34, pagY + 1, tableX + tableW - 2, pagY + 13, nextHov ? MarketTheme.BTN_DEFAULT_HOVER : (canNext ? MarketTheme.BTN_DEFAULT : MarketTheme.BTN_DISABLED));
        graphics.drawCenteredString(this.font, Component.literal("Next ▶"), tableX + tableW - 18, pagY + 3, canNext ? MarketTheme.TEXT_GOLD : 0x555555);
    }

    /* ========================================================================= */
    /* Tab 2: Merchant's Bag (Spacious Centered View, No Overlapping Side Panel)  */
    /* ========================================================================= */

    private void renderSellTab(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, Component.literal(trimText("❖ §6MERCHANT'S INVENTORY BAG — SELECT GOOD TO LIST §f❖", this.contentW - 24)), this.contentX + 12, this.contentY + 6, MarketTheme.TEXT_TITLE, false);
        if (!com.omni.marketplace.util.LicenseHelper.hasLicense(Minecraft.getInstance().player)) {
            graphics.drawString(this.font, Component.literal(trimText("§c🔒 SELLER PRIVILEGES RESTRICTED: Merchant License required to sell goods. Visit Grand Merchant Master.", this.contentW - 24)), this.contentX + 12, this.contentY + 18, 0xFFFF6666, false);
        } else {
            graphics.drawString(this.font, Component.literal(trimText("§7Click any item to configure its price and place it on the Trading Post.", this.contentW - 24)), this.contentX + 12, this.contentY + 18, MarketTheme.TEXT_MUTED, false);
        }

        Inventory inv = Minecraft.getInstance().player.getInventory();
        int slotSize = 22;
        int bagW = 9 * slotSize;
        int bagH = (4 * slotSize) + 16;

        int startX = this.contentX + (this.contentW - bagW) / 2;
        int startY = this.contentY + 34;

        // Medieval Bag Card Frame
        MarketTheme.drawMedievalCard(graphics, startX - 6, startY - 6, bagW + 12, bagH + 12, MarketTheme.SLOT_BG);

        // Main inventory slots 9-35
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = 9 + (row * 9) + col;
                int x = startX + (col * slotSize);
                int y = startY + (row * slotSize);

                boolean hovered = mouseX >= x && mouseX < x + slotSize && mouseY >= y && mouseY < y + slotSize;
                boolean selected = (this.selectedInventorySlot == slotIndex);

                int bg = selected ? 0x40D4AF37 : (hovered ? MarketTheme.SLOT_HOVER : MarketTheme.SLOT_BG);
                int border = selected ? MarketTheme.SLOT_SELECTED : MarketTheme.SLOT_BORDER;

                graphics.fill(x, y, x + slotSize - 2, y + slotSize - 2, bg);
                graphics.fill(x, y, x + slotSize - 2, y + 1, border);
                graphics.fill(x, y + slotSize - 3, x + slotSize - 2, y + slotSize - 2, border);
                graphics.fill(x, y, x + 1, y + slotSize - 2, border);
                graphics.fill(x + slotSize - 3, y, x + slotSize - 2, y + slotSize - 2, border);

                ItemStack stack = inv.getItem(slotIndex);
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, x + 2, y + 2);
                    graphics.renderItemDecorations(this.font, stack, x + 2, y + 2);
                    if (stack.is(com.omni.marketplace.registry.ModRegistry.MERCHANTS_LICENSE.get())) {
                        graphics.renderOutline(x, y, slotSize - 2, slotSize - 2, 0xFFFF4444);
                    }
                    if (hovered) {
                        this.hoveredTooltipStack = stack;
                    }
                }
            }
        }

        // Hotbar separator and slots 0-8
        int hotbarY = startY + (3 * slotSize) + 6;
        graphics.fill(startX - 2, hotbarY - 3, startX + bagW + 2, hotbarY - 2, MarketTheme.PANEL_BORDER_GOLD);

        for (int col = 0; col < 9; col++) {
            int slotIndex = col;
            int x = startX + (col * slotSize);
            boolean hovered = mouseX >= x && mouseX < x + slotSize && mouseY >= hotbarY && mouseY < hotbarY + slotSize;
            boolean selected = (this.selectedInventorySlot == slotIndex);

            int bg = selected ? 0x40D4AF37 : (hovered ? MarketTheme.SLOT_HOVER : MarketTheme.SLOT_BG);
            int border = selected ? MarketTheme.SLOT_SELECTED : MarketTheme.SLOT_BORDER;

            graphics.fill(x, hotbarY, x + slotSize - 2, hotbarY + slotSize - 2, bg);
            graphics.fill(x, hotbarY, x + slotSize - 2, hotbarY + 1, border);
            graphics.fill(x, hotbarY + slotSize - 3, x + slotSize - 2, hotbarY + slotSize - 2, border);
            graphics.fill(x, hotbarY, x + 1, hotbarY + slotSize - 2, border);
            graphics.fill(x + slotSize - 3, hotbarY, x + slotSize - 2, hotbarY + slotSize - 2, border);

            ItemStack stack = inv.getItem(slotIndex);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 2, hotbarY + 2);
                graphics.renderItemDecorations(this.font, stack, x + 2, hotbarY + 2);
                if (stack.is(com.omni.marketplace.registry.ModRegistry.MERCHANTS_LICENSE.get())) {
                    graphics.renderOutline(x, hotbarY, slotSize - 2, slotSize - 2, 0xFFFF4444);
                }
                if (hovered) {
                    this.hoveredTooltipStack = stack;
                }
            }
        }
    }

    /* ========================================================================= */
    /* Dedicated Trade Dialog (Clean Swell-style Separate Screen Architecture)   */
    /* ========================================================================= */

    private void renderTradeDialog(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int dialogW = Math.min(340, this.width - 16);
        int dialogH = Math.min(242, this.height - 16);
        int dialogX = (this.width - dialogW) / 2;
        int dialogY = (this.height - dialogH) / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F);

        // 1. Solid opaque medieval dialog surface
        graphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + dialogH, MarketTheme.PANEL_BG);
        MarketTheme.drawMedievalFrame(graphics, dialogX, dialogY, dialogW, dialogH);

        // 2. Header Title & Close Button
        String titleText = this.tradeIsBuy ? "❖ §6PLACE BUY ORDER (BID) §f❖" : "❖ §6CREATE SELL LISTING (ASK) §f❖";
        graphics.drawString(this.font, Component.literal(trimText(titleText, dialogW - 32)), dialogX + 10, dialogY + 7, MarketTheme.TEXT_TITLE, false);

        int closeBtnX = dialogX + dialogW - 18;
        int closeBtnY = dialogY + 5;
        boolean closeHov = mouseX >= closeBtnX && mouseX <= closeBtnX + 12 && mouseY >= closeBtnY && mouseY <= closeBtnY + 12;
        graphics.fill(closeBtnX, closeBtnY, closeBtnX + 12, closeBtnY + 12, closeHov ? MarketTheme.BTN_DANGER_HOVER : MarketTheme.SLOT_BG);
        graphics.drawCenteredString(this.font, Component.literal("✕"), closeBtnX + 6, closeBtnY + 2, closeHov ? 0xFFFFFF : MarketTheme.TEXT_MUTED);

        // 3. Selected Item Info (Icon + Name + Market status)
        int itemIconX = dialogX + 10;
        int itemIconY = dialogY + 20;
        graphics.fill(itemIconX - 1, itemIconY - 1, itemIconX + 19, itemIconY + 19, MarketTheme.SLOT_BORDER);
        graphics.fill(itemIconX, itemIconY, itemIconX + 18, itemIconY + 18, 0xFF0A090D);
        if (!this.selectedStack.isEmpty()) {
            graphics.renderItem(this.selectedStack, itemIconX + 1, itemIconY + 1);
            if (mouseX >= itemIconX && mouseX <= itemIconX + 18 && mouseY >= itemIconY && mouseY <= itemIconY + 18) {
                this.hoveredTooltipStack = this.selectedStack;
            }
        }

        String itemName = !this.selectedStack.isEmpty() ? this.selectedStack.getHoverName().getString() : (this.selectedCatalogEntry != null ? this.selectedCatalogEntry.itemId() : "Item");
        graphics.drawString(this.font, Component.literal("§6" + trimText(itemName, dialogW - 46)), dialogX + 34, dialogY + 20, MarketTheme.TEXT_GOLD, false);

        long lowestAsk = this.selectedCatalogEntry != null ? this.selectedCatalogEntry.lowestSell() : 0L;
        long highestBid = this.selectedCatalogEntry != null ? this.selectedCatalogEntry.highestBuy() : 0L;
        long suggested = this.selectedCatalogEntry != null ? this.selectedCatalogEntry.suggestedPrice() : 0L;
        if (suggested <= 0 && this.selectedCatalogEntry != null) {
            Item it = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(this.selectedCatalogEntry.itemId()));
            if (it != null) suggested = PriceEngine.computeSuggestedPrice(lowestAsk, highestBid, it);
        }
        String askStr = lowestAsk > 0 ? CurrencyUtils.format(lowestAsk) : "§8None";
        String bidStr = highestBid > 0 ? CurrencyUtils.format(highestBid) : "§8None";
        String sugStr = suggested > 0 ? CurrencyUtils.format(suggested) : "§8None";
        String statusStr = "§7Ask: " + askStr + " §7| Bid: " + bidStr + " §7| Sugg: §6" + sugStr;
        graphics.drawString(this.font, Component.literal(trimText(statusStr, dialogW - 46)), dialogX + 34, dialogY + 31, MarketTheme.TEXT_PRIMARY, false);

        // 3b. Market Intelligence / 24h Trends Ribbon
        int statsY = dialogY + 43;
        int statsW = dialogW - 16;
        graphics.fill(dialogX + 8, statsY, dialogX + 8 + statsW, statsY + 14, 0xD017151B);
        graphics.fill(dialogX + 8, statsY, dialogX + 8 + statsW, statsY + 1, 0x40D4AF37);
        graphics.fill(dialogX + 8, statsY + 13, dialogX + 8 + statsW, statsY + 14, 0x40D4AF37);

        String selItemId = this.selectedCatalogEntry != null ? this.selectedCatalogEntry.itemId() : (!this.selectedStack.isEmpty() ? BuiltInRegistries.ITEM.getKey(this.selectedStack.getItem()).toString() : "");
        String volStr = "0 units";
        String rangeStr = "§8--";
        String lastStr = "§8--";

        if (this.currentAnalytics != null && this.currentAnalytics.itemId().equals(selItemId)) {
            if (this.currentAnalytics.volume24h() > 0) {
                volStr = String.format("%,d units", this.currentAnalytics.volume24h());
                rangeStr = CurrencyUtils.format(this.currentAnalytics.minPrice24h()) + "§7-§f" + CurrencyUtils.format(this.currentAnalytics.maxPrice24h());
            }
            if (this.currentAnalytics.lastTradedPrice() > 0) {
                lastStr = CurrencyUtils.format(this.currentAnalytics.lastTradedPrice());
            }
        }
        String analyticsLine = String.format("§724h Vol: §e%s §7| Range: %s §7| Last Sale: %s", volStr, rangeStr, lastStr);
        graphics.drawCenteredString(this.font, Component.literal(trimText(analyticsLine, statsW - 6)), dialogX + (dialogW / 2), statsY + 3, MarketTheme.TEXT_PRIMARY);

        // Divider
        MarketTheme.drawMedievalDivider(graphics, dialogX + 8, dialogY + 59, dialogW - 16);

        // 4. Buy vs Sell Mode Tabs
        int modeY = dialogY + 63;
        int modeBtnW = (dialogW - 24) / 2;
        int buyBg = this.tradeIsBuy ? MarketTheme.BTN_PRIMARY : MarketTheme.BTN_DEFAULT;
        int buyBorder = this.tradeIsBuy ? MarketTheme.PANEL_BORDER_GOLD : MarketTheme.BTN_DEFAULT_BORDER;
        int sellBg = !this.tradeIsBuy ? MarketTheme.BTN_ACCENT : MarketTheme.BTN_DEFAULT;
        int sellBorder = !this.tradeIsBuy ? MarketTheme.PANEL_BORDER_GOLD : MarketTheme.BTN_DEFAULT_BORDER;

        boolean buyHov = mouseX >= dialogX + 8 && mouseX <= dialogX + 8 + modeBtnW && mouseY >= modeY && mouseY <= modeY + 16;
        graphics.fill(dialogX + 8, modeY, dialogX + 8 + modeBtnW, modeY + 16, buyBorder);
        graphics.fill(dialogX + 9, modeY + 1, dialogX + 7 + modeBtnW, modeY + 15, buyHov ? MarketTheme.BTN_PRIMARY_HOVER : buyBg);
        graphics.drawCenteredString(this.font, Component.literal("⚔ Buy (Bid)"), dialogX + 8 + (modeBtnW / 2), modeY + 4, this.tradeIsBuy ? MarketTheme.TEXT_GOLD : MarketTheme.TEXT_SECONDARY);

        boolean sellHov = mouseX >= dialogX + 16 + modeBtnW && mouseX <= dialogX + 16 + (modeBtnW * 2) && mouseY >= modeY && mouseY <= modeY + 16;
        graphics.fill(dialogX + 16 + modeBtnW, modeY, dialogX + 16 + (modeBtnW * 2), modeY + 16, sellBorder);
        graphics.fill(dialogX + 17 + modeBtnW, modeY + 1, dialogX + 15 + (modeBtnW * 2), modeY + 15, sellHov ? MarketTheme.BTN_ACCENT_HOVER : sellBg);
        graphics.drawCenteredString(this.font, Component.literal("💰 Sell (Ask)"), dialogX + 16 + modeBtnW + (modeBtnW / 2), modeY + 4, !this.tradeIsBuy ? MarketTheme.TEXT_GOLD : MarketTheme.TEXT_SECONDARY);

        // 5. Instant Action Bar
        int instantY = dialogY + 82;
        int instantH = 16;
        int instantBtnW = dialogW - 16;
        if (this.tradeIsBuy) {
            if (lowestAsk > 0) {
                long totalCost = lowestAsk * (long) this.tradeQuantity;
                boolean isInstHov = mouseX >= dialogX + 8 && mouseX <= dialogX + 8 + instantBtnW && mouseY >= instantY && mouseY <= instantY + instantH;
                graphics.fill(dialogX + 8, instantY, dialogX + 8 + instantBtnW, instantY + instantH, MarketTheme.PANEL_BORDER_GOLD);
                graphics.fill(dialogX + 9, instantY + 1, dialogX + 7 + instantBtnW, instantY + instantH - 1, isInstHov ? MarketTheme.BTN_PRIMARY_HOVER : MarketTheme.BTN_PRIMARY);
                graphics.drawCenteredString(this.font, Component.literal("⚡ Instant Buy for " + CurrencyUtils.format(totalCost)), dialogX + (dialogW / 2), instantY + 4, 0xFFFFFF);
            } else {
                graphics.fill(dialogX + 8, instantY, dialogX + 8 + instantBtnW, instantY + instantH, MarketTheme.BTN_DISABLED);
                graphics.drawCenteredString(this.font, Component.literal("§8Instant Buy Unavailable (No Sellers)"), dialogX + (dialogW / 2), instantY + 4, 0x888888);
            }
        } else {
            if (highestBid > 0) {
                long netPayout = PriceEngine.calculateRealProfit(highestBid, this.tradeQuantity, true);
                boolean isInstHov = mouseX >= dialogX + 8 && mouseX <= dialogX + 8 + instantBtnW && mouseY >= instantY && mouseY <= instantY + instantH;
                graphics.fill(dialogX + 8, instantY, dialogX + 8 + instantBtnW, instantY + instantH, MarketTheme.PANEL_BORDER_GOLD);
                graphics.fill(dialogX + 9, instantY + 1, dialogX + 7 + instantBtnW, instantY + instantH - 1, isInstHov ? MarketTheme.BTN_PRIMARY_HOVER : MarketTheme.BTN_PRIMARY);
                graphics.drawCenteredString(this.font, Component.literal("⚡ Instant Sell for Net " + CurrencyUtils.format(netPayout)), dialogX + (dialogW / 2), instantY + 4, 0xFFFFFF);
            } else {
                graphics.fill(dialogX + 8, instantY, dialogX + 8 + instantBtnW, instantY + instantH, MarketTheme.BTN_DISABLED);
                graphics.drawCenteredString(this.font, Component.literal("§8Instant Sell Unavailable (No Buyers)"), dialogX + (dialogW / 2), instantY + 4, 0x888888);
            }
        }

        // 6. Custom Order Config: Quantity Row
        int qY = dialogY + 101;
        graphics.drawString(this.font, Component.literal("Qty:"), dialogX + 10, qY + 3, MarketTheme.TEXT_SECONDARY, false);

        boolean minusHov = mouseX >= dialogX + 38 && mouseX <= dialogX + 51 && mouseY >= qY + 1 && mouseY <= qY + 15;
        graphics.fill(dialogX + 38, qY + 1, dialogX + 51, qY + 15, minusHov ? MarketTheme.BTN_DEFAULT_HOVER : MarketTheme.BTN_DEFAULT);
        graphics.drawCenteredString(this.font, Component.literal("-"), dialogX + 44, qY + 3, 0xFFFFFF);

        this.qtyBox.setX(dialogX + 53);
        this.qtyBox.setY(qY + 1);
        this.qtyBox.setWidth(30);
        this.qtyBox.setHeight(14);
        this.qtyBox.render(graphics, mouseX, mouseY, partialTicks);

        boolean plusHov = mouseX >= dialogX + 85 && mouseX <= dialogX + 98 && mouseY >= qY + 1 && mouseY <= qY + 15;
        graphics.fill(dialogX + 85, qY + 1, dialogX + 98, qY + 15, plusHov ? MarketTheme.BTN_DEFAULT_HOVER : MarketTheme.BTN_DEFAULT);
        graphics.drawCenteredString(this.font, Component.literal("+"), dialogX + 91, qY + 3, 0xFFFFFF);

        // Qty Presets [1] [16] [64] [Max]
        int b1 = dialogX + 104;
        boolean h1 = mouseX >= b1 && mouseX <= b1 + 16 && mouseY >= qY + 1 && mouseY <= qY + 15;
        graphics.fill(b1, qY + 1, b1 + 16, qY + 15, h1 ? MarketTheme.BTN_ACCENT_HOVER : MarketTheme.SLOT_BG);
        graphics.drawCenteredString(this.font, Component.literal("1"), b1 + 8, qY + 3, h1 ? 0xFFFFFF : MarketTheme.TEXT_MUTED);

        int b16 = b1 + 18;
        boolean h16 = mouseX >= b16 && mouseX <= b16 + 20 && mouseY >= qY + 1 && mouseY <= qY + 15;
        graphics.fill(b16, qY + 1, b16 + 20, qY + 15, h16 ? MarketTheme.BTN_ACCENT_HOVER : MarketTheme.SLOT_BG);
        graphics.drawCenteredString(this.font, Component.literal("16"), b16 + 10, qY + 3, h16 ? 0xFFFFFF : MarketTheme.TEXT_MUTED);

        int b64 = b16 + 22;
        boolean h64 = mouseX >= b64 && mouseX <= b64 + 20 && mouseY >= qY + 1 && mouseY <= qY + 15;
        graphics.fill(b64, qY + 1, b64 + 20, qY + 15, h64 ? MarketTheme.BTN_ACCENT_HOVER : MarketTheme.SLOT_BG);
        graphics.drawCenteredString(this.font, Component.literal("64"), b64 + 10, qY + 3, h64 ? 0xFFFFFF : MarketTheme.TEXT_MUTED);

        int bMax = b64 + 22;
        boolean hMax = mouseX >= bMax && mouseX <= bMax + 26 && mouseY >= qY + 1 && mouseY <= qY + 15;
        graphics.fill(bMax, qY + 1, bMax + 26, qY + 15, MarketTheme.PANEL_BORDER_GOLD);
        graphics.fill(bMax + 1, qY + 2, bMax + 25, qY + 14, hMax ? MarketTheme.BTN_ACCENT_HOVER : MarketTheme.BTN_ACCENT);
        graphics.drawCenteredString(this.font, Component.literal("Max"), bMax + 13, qY + 3, 0xFFFFFF);

        // 7. Price Presets Row: [Suggested] [Match Bid/Ask] [+/-1c]
        int quickY = dialogY + 119;
        graphics.drawString(this.font, Component.literal("Presets:"), dialogX + 10, quickY + 2, MarketTheme.TEXT_MUTED, false);

        boolean hovSug = mouseX >= dialogX + 54 && mouseX <= dialogX + 110 && mouseY >= quickY && mouseY <= quickY + 13;
        graphics.fill(dialogX + 54, quickY, dialogX + 110, quickY + 13, hovSug ? MarketTheme.BTN_ACCENT_HOVER : MarketTheme.SLOT_BG);
        graphics.drawCenteredString(this.font, Component.literal("Suggested"), dialogX + 82, quickY + 3, hovSug ? 0xFFFFFF : MarketTheme.TEXT_GOLD);

        boolean hovMatch = mouseX >= dialogX + 114 && mouseX <= dialogX + 170 && mouseY >= quickY && mouseY <= quickY + 13;
        graphics.fill(dialogX + 114, quickY, dialogX + 170, quickY + 13, hovMatch ? MarketTheme.BTN_ACCENT_HOVER : MarketTheme.SLOT_BG);
        graphics.drawCenteredString(this.font, Component.literal(this.tradeIsBuy ? "Match Bid" : "Match Ask"), dialogX + 142, quickY + 3, hovMatch ? 0xFFFFFF : MarketTheme.TEXT_MUTED);

        boolean hovOffset = mouseX >= dialogX + 174 && mouseX <= dialogX + 230 && mouseY >= quickY && mouseY <= quickY + 13;
        graphics.fill(dialogX + 174, quickY, dialogX + 230, quickY + 13, hovOffset ? MarketTheme.BTN_PRIMARY_HOVER : MarketTheme.SLOT_BG);
        graphics.drawCenteredString(this.font, Component.literal(this.tradeIsBuy ? "+1c Over" : "-1c Under"), dialogX + 202, quickY + 3, hovOffset ? 0xFFFFFF : 0x55FF55);

        // 8. Unit Price Row: Gold, Silver, Copper boxes + Adjust Buttons [-5c] [+5c]
        int pY = dialogY + 135;
        graphics.drawString(this.font, Component.literal("Price:"), dialogX + 10, pY + 3, MarketTheme.TEXT_SECONDARY, false);

        this.goldBox.setX(dialogX + 44);
        this.goldBox.setY(pY + 1);
        this.goldBox.setWidth(30);
        this.goldBox.setHeight(14);
        this.goldBox.render(graphics, mouseX, mouseY, partialTicks);
        graphics.drawString(this.font, Component.literal("§6●g"), dialogX + 76, pY + 4, MarketTheme.TEXT_GOLD, false);

        this.silverBox.setX(dialogX + 94);
        this.silverBox.setY(pY + 1);
        this.silverBox.setWidth(26);
        this.silverBox.setHeight(14);
        this.silverBox.render(graphics, mouseX, mouseY, partialTicks);
        graphics.drawString(this.font, Component.literal("§f●s"), dialogX + 122, pY + 4, MarketTheme.TEXT_SILVER, false);

        this.copperBox.setX(dialogX + 140);
        this.copperBox.setY(pY + 1);
        this.copperBox.setWidth(26);
        this.copperBox.setHeight(14);
        this.copperBox.render(graphics, mouseX, mouseY, partialTicks);
        graphics.drawString(this.font, Component.literal("§c●c"), dialogX + 168, pY + 4, MarketTheme.TEXT_COPPER, false);

        // Price Adjustment Buttons: [-5c] and [+5c] (Click: +/-5c, Hold 3s: +/-50c/sec)
        int btnMinusX = dialogX + 190;
        int btnMinusW = 32;
        int btnMinusY = pY + 1;
        int btnMinusH = 14;

        int btnPlusX = dialogX + 226;
        int btnPlusW = 32;
        int btnPlusY = pY + 1;
        int btnPlusH = 14;

        boolean priceMinusHov = mouseX >= btnMinusX && mouseX <= btnMinusX + btnMinusW && mouseY >= btnMinusY && mouseY <= btnMinusY + btnMinusH;
        boolean pricePlusHov = mouseX >= btnPlusX && mouseX <= btnPlusX + btnPlusW && mouseY >= btnPlusY && mouseY <= btnPlusY + btnPlusH;

        // Minus Button Render
        boolean isHoldingMinus = this.activeHold == HoldAction.MINUS_PRICE;
        long minusHeld = isHoldingMinus ? (System.currentTimeMillis() - this.holdStartTime) : 0L;
        String minusText = (isHoldingMinus && minusHeld >= 3000L) ? "§c-50/s" : "-5c";

        graphics.fill(btnMinusX, btnMinusY, btnMinusX + btnMinusW, btnMinusY + btnMinusH, MarketTheme.PANEL_BORDER_GOLD);
        graphics.fill(btnMinusX + 1, btnMinusY + 1, btnMinusX + btnMinusW - 1, btnMinusY + btnMinusH - 1, isHoldingMinus ? MarketTheme.BTN_DANGER : (priceMinusHov ? MarketTheme.BTN_DANGER_HOVER : MarketTheme.SLOT_BG));
        graphics.drawCenteredString(this.font, Component.literal(minusText), btnMinusX + (btnMinusW / 2), btnMinusY + 3, 0xFFFFFF);

        if (isHoldingMinus && minusHeld < 3000L) {
            int progW = (int) ((btnMinusW - 2) * (minusHeld / 3000.0));
            graphics.fill(btnMinusX + 1, btnMinusY + btnMinusH - 2, btnMinusX + 1 + progW, btnMinusY + btnMinusH - 1, 0xFFFF5555);
        }

        if (priceMinusHov) {
            this.hoveredTooltipText = Component.literal("§c-5 Copper §7(Hold 3s: -50c/sec)");
        }

        // Plus Button Render
        boolean isHoldingPlus = this.activeHold == HoldAction.PLUS_PRICE;
        long plusHeld = isHoldingPlus ? (System.currentTimeMillis() - this.holdStartTime) : 0L;
        String plusText = (isHoldingPlus && plusHeld >= 3000L) ? "§a+50/s" : "+5c";

        graphics.fill(btnPlusX, btnPlusY, btnPlusX + btnPlusW, btnPlusY + btnPlusH, MarketTheme.PANEL_BORDER_GOLD);
        graphics.fill(btnPlusX + 1, btnPlusY + 1, btnPlusX + btnPlusW - 1, btnPlusY + btnPlusH - 1, isHoldingPlus ? MarketTheme.BTN_ACCENT : (pricePlusHov ? MarketTheme.BTN_ACCENT_HOVER : MarketTheme.SLOT_BG));
        graphics.drawCenteredString(this.font, Component.literal(plusText), btnPlusX + (btnPlusW / 2), btnPlusY + 3, 0xFFFFFF);

        if (isHoldingPlus && plusHeld < 3000L) {
            int progW = (int) ((btnPlusW - 2) * (plusHeld / 3000.0));
            graphics.fill(btnPlusX + 1, btnPlusY + btnPlusH - 2, btnPlusX + 1 + progW, btnPlusY + btnPlusH - 1, 0xFF55FF55);
        }

        if (pricePlusHov) {
            this.hoveredTooltipText = Component.literal("§a+5 Copper §7(Hold 3s: +50c/sec)");
        }

        // 9. Financial / Fee Breakdown Card
        int feeY = dialogY + 152;
        int feeCardW = dialogW - 16;
        if (!this.tradeIsBuy) {
            long gross = this.tradeUnitPrice * (long) this.tradeQuantity;
            long listingFee = CurrencyUtils.calculateListingFee(this.tradeUnitPrice, this.tradeQuantity);
            long exchangeFee = CurrencyUtils.calculateExchangeFee(this.tradeUnitPrice, this.tradeQuantity);
            long realNet = PriceEngine.calculateRealProfit(this.tradeUnitPrice, this.tradeQuantity, false);

            graphics.fill(dialogX + 8, feeY, dialogX + 8 + feeCardW, feeY + 38, 0xFF17151B);
            graphics.fill(dialogX + 8, feeY, dialogX + 8 + feeCardW, feeY + 1, MarketTheme.PANEL_BORDER);
            graphics.fill(dialogX + 8, feeY + 37, dialogX + 8 + feeCardW, feeY + 38, MarketTheme.PANEL_BORDER);

            graphics.drawString(this.font, Component.literal("Gross: " + CurrencyUtils.format(gross) + " §7| Fees: §c-" + CurrencyUtils.format(listingFee + exchangeFee)), dialogX + 12, feeY + 4, MarketTheme.TEXT_PRIMARY, false);
            graphics.drawString(this.font, Component.literal("✦ Real Net Profit (after 15%): §a" + CurrencyUtils.format(realNet)), dialogX + 12, feeY + 15, 0x55FF55, false);
            graphics.drawString(this.font, Component.literal("§7(5% listing fee upfront, 10% fee on sale)"), dialogX + 12, feeY + 26, MarketTheme.TEXT_MUTED, false);
        } else {
            long totalEscrow = this.tradeUnitPrice * (long) this.tradeQuantity;
            graphics.fill(dialogX + 8, feeY, dialogX + 8 + feeCardW, feeY + 34, 0xFF17151B);
            graphics.fill(dialogX + 8, feeY, dialogX + 8 + feeCardW, feeY + 1, MarketTheme.PANEL_BORDER);
            graphics.fill(dialogX + 8, feeY + 33, dialogX + 8 + feeCardW, feeY + 34, MarketTheme.PANEL_BORDER);

            graphics.drawString(this.font, Component.literal("Total Escrow Cost: §e" + CurrencyUtils.format(totalEscrow)), dialogX + 12, feeY + 4, MarketTheme.TEXT_GOLD, false);
            graphics.drawString(this.font, Component.literal("§7(Unspent escrow is refunded if matched cheaper)"), dialogX + 12, feeY + 16, MarketTheme.TEXT_MUTED, false);
        }

        // 10. Bottom Action Buttons: [ ◀ Back ] and [ Place Order / Listing ]
        int btnY = dialogY + dialogH - 22;
        int backBtnW = 72;
        boolean backHov = mouseX >= dialogX + 8 && mouseX <= dialogX + 8 + backBtnW && mouseY >= btnY && mouseY <= btnY + 18;
        graphics.fill(dialogX + 8, btnY, dialogX + 8 + backBtnW, btnY + 18, MarketTheme.PANEL_BORDER);
        graphics.fill(dialogX + 9, btnY + 1, dialogX + 7 + backBtnW, btnY + 17, backHov ? MarketTheme.BTN_DEFAULT_HOVER : MarketTheme.BTN_DEFAULT);
        graphics.drawCenteredString(this.font, Component.literal("◀ Back"), dialogX + 8 + (backBtnW / 2), btnY + 5, backHov ? 0xFFFFFF : MarketTheme.TEXT_PRIMARY);

        int submitX = dialogX + 12 + backBtnW;
        int submitW = dialogW - 20 - backBtnW;
        boolean submitHov = mouseX >= submitX && mouseX <= submitX + submitW && mouseY >= btnY && mouseY <= btnY + 18;
        int submitBg = this.tradeIsBuy ? (submitHov ? MarketTheme.BTN_PRIMARY_HOVER : MarketTheme.BTN_PRIMARY) : (submitHov ? MarketTheme.BTN_ACCENT_HOVER : MarketTheme.BTN_ACCENT);
        graphics.fill(submitX, btnY, submitX + submitW, btnY + 18, MarketTheme.PANEL_BORDER_GOLD);
        graphics.fill(submitX + 1, btnY + 1, submitX + submitW - 1, btnY + 17, submitBg);
        String submitText = this.tradeIsBuy ? "⚔ Place Buy Order" : "📜 Place Sell Listing";
        graphics.drawCenteredString(this.font, Component.literal(submitText), submitX + (submitW / 2), btnY + 5, 0xFFFFFF);

        graphics.pose().popPose();
    }

    /* ========================================================================= */
    /* Tab 3: Guild Ledgers / Transactions                                       */
    /* ========================================================================= */

    private void renderTransactionsTab(GuiGraphics graphics, int mouseX, int mouseY) {
        int halfW = (this.contentW - 12) / 2;

        int refBtnW = 68;
        int refBtnX = this.contentX + this.contentW - refBtnW;
        int refBtnY = this.contentY + 4;
        boolean refHov = mouseX >= refBtnX && mouseX <= refBtnX + refBtnW && mouseY >= refBtnY && mouseY <= refBtnY + 14;
        graphics.fill(refBtnX, refBtnY, refBtnX + refBtnW, refBtnY + 14, MarketTheme.PANEL_BORDER_GOLD);
        graphics.fill(refBtnX + 1, refBtnY + 1, refBtnX + refBtnW - 1, refBtnY + 13, refHov ? MarketTheme.BTN_ACCENT_HOVER : MarketTheme.BTN_DEFAULT);
        graphics.drawCenteredString(this.font, Component.literal("⟳ Refresh"), refBtnX + (refBtnW / 2), refBtnY + 3, MarketTheme.TEXT_GOLD);

        // Column 1: Active Listings Card
        graphics.drawString(this.font, Component.literal("❖ ACTIVE SELL CONTRACTS (" + this.playerListings.size() + ")"), this.contentX + 4, this.contentY + 6, MarketTheme.TEXT_GOLD, false);
        MarketTheme.drawMedievalCard(graphics, this.contentX, this.contentY + 20, halfW, this.contentH - 20, MarketTheme.SLOT_BG);

        int rowY1 = this.contentY + 24;
        int maxRows = Math.max(1, (this.contentH - 28) / 24);
        int maxListScroll = Math.max(0, this.playerListings.size() - maxRows);
        this.transactionsListingScroll = Math.min(this.transactionsListingScroll, maxListScroll);

        for (int i = 0; i < maxRows; i++) {
            int idx = this.transactionsListingScroll + i;
            if (idx >= this.playerListings.size()) break;
            OrderEntry entry = this.playerListings.get(idx);
            int currentY = rowY1 + (i * 24);
            ItemStack stack = ItemSerializer.deserialize(entry.itemNbt(), entry.itemId(), entry.quantity(), Minecraft.getInstance().level.registryAccess());
            graphics.renderItem(stack, this.contentX + 4, currentY);

            if (mouseX >= this.contentX + 4 && mouseX <= this.contentX + 20 && mouseY >= currentY && mouseY <= currentY + 16 && !stack.isEmpty()) {
                this.hoveredTooltipStack = stack;
            }

            int btnX = this.contentX + halfW - 48;
            String priceStr = CurrencyUtils.format(entry.unitPrice());
            int priceW = this.font.width(priceStr);
            int priceX = btnX - 6 - priceW;

            int maxNameW = Math.max(20, priceX - (this.contentX + 24) - 6);
            String name = stack.isEmpty() ? entry.itemId() : stack.getHoverName().getString();
            graphics.drawString(this.font, Component.literal(trimText(name + " x" + entry.quantity(), maxNameW)), this.contentX + 24, currentY + 4, MarketTheme.TEXT_PRIMARY, false);
            graphics.drawString(this.font, Component.literal(priceStr), priceX, currentY + 4, MarketTheme.TEXT_GOLD, false);

            boolean hovered = mouseX >= btnX && mouseX <= btnX + 44 && mouseY >= currentY + 2 && mouseY <= currentY + 18;
            graphics.fill(btnX, currentY + 2, btnX + 44, currentY + 18, MarketTheme.PANEL_BORDER_GOLD);
            graphics.fill(btnX + 1, currentY + 3, btnX + 43, currentY + 17, hovered ? MarketTheme.BTN_DANGER_HOVER : MarketTheme.BTN_DANGER);
            graphics.drawCenteredString(this.font, Component.literal("✕ Cancel"), btnX + 22, currentY + 5, 0xFFFFFF);
        }

        // Column 2: Active Buy Orders Card
        int col2X = this.contentX + halfW + 8;
        int maxTitleW = Math.max(20, refBtnX - col2X - 8);
        graphics.drawString(this.font, Component.literal(trimText("❖ ACTIVE BUY ORDERS (" + this.playerBuyOrders.size() + ")", maxTitleW)), col2X + 4, this.contentY + 6, MarketTheme.TEXT_GOLD, false);
        MarketTheme.drawMedievalCard(graphics, col2X, this.contentY + 20, halfW, this.contentH - 20, MarketTheme.SLOT_BG);

        int rowY2 = this.contentY + 24;
        int maxOrderScroll = Math.max(0, this.playerBuyOrders.size() - maxRows);
        this.transactionsOrderScroll = Math.min(this.transactionsOrderScroll, maxOrderScroll);

        for (int i = 0; i < maxRows; i++) {
            int idx = this.transactionsOrderScroll + i;
            if (idx >= this.playerBuyOrders.size()) break;
            OrderEntry entry = this.playerBuyOrders.get(idx);
            int currentY = rowY2 + (i * 24);
            ItemStack stack = ItemSerializer.deserialize(entry.itemNbt(), entry.itemId(), entry.quantity(), Minecraft.getInstance().level.registryAccess());
            graphics.renderItem(stack, col2X + 4, currentY);

            if (mouseX >= col2X + 4 && mouseX <= col2X + 20 && mouseY >= currentY && mouseY <= currentY + 16 && !stack.isEmpty()) {
                this.hoveredTooltipStack = stack;
            }

            int btnX = col2X + halfW - 48;
            String priceStr = CurrencyUtils.format(entry.unitPrice());
            int priceW = this.font.width(priceStr);
            int priceX = btnX - 6 - priceW;

            int maxNameW = Math.max(20, priceX - (col2X + 24) - 6);
            String name = stack.isEmpty() ? entry.itemId() : stack.getHoverName().getString();
            graphics.drawString(this.font, Component.literal(trimText(name + " x" + entry.quantity(), maxNameW)), col2X + 24, currentY + 4, MarketTheme.TEXT_PRIMARY, false);
            graphics.drawString(this.font, Component.literal(priceStr), priceX, currentY + 4, MarketTheme.TEXT_GOLD, false);

            boolean hovered = mouseX >= btnX && mouseX <= btnX + 44 && mouseY >= currentY + 2 && mouseY <= currentY + 18;
            graphics.fill(btnX, currentY + 2, btnX + 44, currentY + 18, MarketTheme.PANEL_BORDER_GOLD);
            graphics.fill(btnX + 1, currentY + 3, btnX + 43, currentY + 17, hovered ? MarketTheme.BTN_DANGER_HOVER : MarketTheme.BTN_DANGER);
            graphics.drawCenteredString(this.font, Component.literal("✕ Cancel"), btnX + 22, currentY + 5, 0xFFFFFF);
        }
    }

    /* ========================================================================= */
    /* Tab 4: Coin Exchange                                                      */
    /* ========================================================================= */

    private void renderExchangeTab(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, Component.literal("❖ §6ROYAL COIN & EMERALD EXCHANGE MINT §f❖"), this.contentX + 12, this.contentY + 8, MarketTheme.TEXT_TITLE, false);
        graphics.drawString(this.font, Component.literal("§7Convert physical emerald gems into guild currency or withdraw your wealth."), this.contentX + 12, this.contentY + 20, MarketTheme.TEXT_MUTED, false);

        int availableW = this.contentW - 16;
        int boxW = Math.min(210, (availableW - 8) / 2);
        int boxH = Math.max(126, Math.min(134, this.contentH - 46));
        int leftX = this.contentX + (this.contentW / 2) - boxW - 4;
        int rightX = this.contentX + (this.contentW / 2) + 4;
        int boxY = this.contentY + 36;

        // Deposit Card
        MarketTheme.drawMedievalCard(graphics, leftX, boxY, boxW, boxH, MarketTheme.SLOT_BG);
        graphics.drawCenteredString(this.font, Component.literal("📥 DEPOSIT EMERALDS"), leftX + (boxW / 2), boxY + 8, 0x55FF55);

        boolean d1Hov = mouseX >= leftX + 10 && mouseX <= leftX + boxW - 10 && mouseY >= boxY + 24 && mouseY <= boxY + 42;
        graphics.fill(leftX + 10, boxY + 24, leftX + boxW - 10, boxY + 42, MarketTheme.PANEL_BORDER_GOLD);
        graphics.fill(leftX + 11, boxY + 25, leftX + boxW - 11, boxY + 41, d1Hov ? MarketTheme.BTN_PRIMARY_HOVER : MarketTheme.BTN_PRIMARY);
        graphics.drawCenteredString(this.font, Component.literal("Deposit 1 Emerald (+1s)"), leftX + (boxW / 2), boxY + 29, 0xFFFFFF);

        boolean d2Hov = mouseX >= leftX + 10 && mouseX <= leftX + boxW - 10 && mouseY >= boxY + 48 && mouseY <= boxY + 66;
        graphics.fill(leftX + 10, boxY + 48, leftX + boxW - 10, boxY + 66, MarketTheme.PANEL_BORDER_GOLD);
        graphics.fill(leftX + 11, boxY + 49, leftX + boxW - 11, boxY + 65, d2Hov ? MarketTheme.BTN_PRIMARY_HOVER : MarketTheme.BTN_PRIMARY);
        graphics.drawCenteredString(this.font, Component.literal("Deposit 1 Block (+9s)"), leftX + (boxW / 2), boxY + 53, 0xFFFFFF);

        boolean d3Hov = mouseX >= leftX + 10 && mouseX <= leftX + boxW - 10 && mouseY >= boxY + 72 && mouseY <= boxY + 90;
        graphics.fill(leftX + 10, boxY + 72, leftX + boxW - 10, boxY + 90, MarketTheme.PANEL_BORDER_GOLD);
        graphics.fill(leftX + 11, boxY + 73, leftX + boxW - 11, boxY + 89, d3Hov ? MarketTheme.BTN_ACCENT_HOVER : MarketTheme.BTN_ACCENT);
        graphics.drawCenteredString(this.font, Component.literal("Deposit All Emeralds"), leftX + (boxW / 2), boxY + 77, 0xFFFFFF);

        boolean d4Hov = mouseX >= leftX + 10 && mouseX <= leftX + boxW - 10 && mouseY >= boxY + 96 && mouseY <= boxY + 114;
        graphics.fill(leftX + 10, boxY + 96, leftX + boxW - 10, boxY + 114, MarketTheme.PANEL_BORDER_GOLD);
        graphics.fill(leftX + 11, boxY + 97, leftX + boxW - 11, boxY + 113, d4Hov ? MarketTheme.BTN_ACCENT_HOVER : MarketTheme.BTN_ACCENT);
        graphics.drawCenteredString(this.font, Component.literal("Deposit All Blocks"), leftX + (boxW / 2), boxY + 101, 0xFFFFFF);

        // Withdraw Card
        MarketTheme.drawMedievalCard(graphics, rightX, boxY, boxW, boxH, MarketTheme.SLOT_BG);
        graphics.drawCenteredString(this.font, Component.literal("📤 WITHDRAW EMERALDS"), rightX + (boxW / 2), boxY + 8, 0xFF5555);

        boolean w1Hov = mouseX >= rightX + 10 && mouseX <= rightX + boxW - 10 && mouseY >= boxY + 24 && mouseY <= boxY + 42;
        graphics.fill(rightX + 10, boxY + 24, rightX + boxW - 10, boxY + 42, MarketTheme.PANEL_BORDER_GOLD);
        graphics.fill(rightX + 11, boxY + 25, rightX + boxW - 11, boxY + 41, w1Hov ? MarketTheme.BTN_DANGER_HOVER : MarketTheme.BTN_DANGER);
        graphics.drawCenteredString(this.font, Component.literal("Withdraw 1 Emerald (-1s)"), rightX + (boxW / 2), boxY + 29, 0xFFFFFF);

        boolean w2Hov = mouseX >= rightX + 10 && mouseX <= rightX + boxW - 10 && mouseY >= boxY + 48 && mouseY <= boxY + 66;
        graphics.fill(rightX + 10, boxY + 48, rightX + boxW - 10, boxY + 66, MarketTheme.PANEL_BORDER_GOLD);
        graphics.fill(rightX + 11, boxY + 49, rightX + boxW - 11, boxY + 65, w2Hov ? MarketTheme.BTN_DANGER_HOVER : MarketTheme.BTN_DANGER);
        graphics.drawCenteredString(this.font, Component.literal("Withdraw 1 Block (-9s)"), rightX + (boxW / 2), boxY + 53, 0xFFFFFF);

        boolean w3Hov = mouseX >= rightX + 10 && mouseX <= rightX + boxW - 10 && mouseY >= boxY + 72 && mouseY <= boxY + 90;
        graphics.fill(rightX + 10, boxY + 72, rightX + boxW - 10, boxY + 90, MarketTheme.PANEL_BORDER_GOLD);
        graphics.fill(rightX + 11, boxY + 73, rightX + boxW - 11, boxY + 89, w3Hov ? MarketTheme.BTN_DANGER_HOVER : MarketTheme.BTN_DANGER);
        graphics.drawCenteredString(this.font, Component.literal("Withdraw 64 Emeralds (-64s)"), rightX + (boxW / 2), boxY + 77, 0xFFFFFF);

        boolean w4Hov = mouseX >= rightX + 10 && mouseX <= rightX + boxW - 10 && mouseY >= boxY + 96 && mouseY <= boxY + 114;
        graphics.fill(rightX + 10, boxY + 96, rightX + boxW - 10, boxY + 114, MarketTheme.PANEL_BORDER_GOLD);
        graphics.fill(rightX + 11, boxY + 97, rightX + boxW - 11, boxY + 113, w4Hov ? MarketTheme.BTN_DANGER_HOVER : MarketTheme.BTN_DANGER);
        graphics.drawCenteredString(this.font, Component.literal("Withdraw 64 Blocks (-576s)"), rightX + (boxW / 2), boxY + 101, 0xFFFFFF);
    }

    /* ========================================================================= */
    /* Input & Mouse Handlers                                                    */
    /* ========================================================================= */

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.showTradeDialog) {
            return true; // Modal consumes scroll
        }
        if (this.currentTab == Tab.BROWSE) {
            int tableX = getBrowseLeftX();
            int tableW = getBrowseLeftW();
            int pillX = tableX;
            int pillY = this.contentY + 18;
            int pillRowH = 15;

            List<SubCategory> subs = CategoryDef.getSubCategories(this.currentMainCategory);
            for (SubCategory sub : subs) {
                int btnW = this.font.width(sub.displayName) + 8;
                if (pillX + btnW > tableX + tableW && pillX > tableX) {
                    pillX = tableX;
                    pillY += pillRowH;
                }
                pillX += btnW + 2;
            }

            int tableY = pillY + pillRowH + 2;
            int tableH = this.contentH - (tableY - this.contentY) - 16;

            if (mouseX >= tableX && mouseX <= tableX + tableW && mouseY >= tableY && mouseY <= tableY + tableH) {
                int visibleRows = Math.max(1, (tableH - 20) / 22);
                int maxOffset = Math.max(0, this.catalogEntries.size() - visibleRows);
                if (scrollY > 0) {
                    this.catalogScrollOffset = Math.max(0, this.catalogScrollOffset - 1);
                } else if (scrollY < 0) {
                    this.catalogScrollOffset = Math.min(maxOffset, this.catalogScrollOffset + 1);
                }
                return true;
            }
        } else if (this.currentTab == Tab.TRANSACTIONS) {
            int halfW = (this.contentW - 12) / 2;
            int maxRows = Math.max(1, (this.contentH - 28) / 24);
            if (mouseX >= this.contentX && mouseX <= this.contentX + halfW) {
                int maxOffset = Math.max(0, this.playerListings.size() - maxRows);
                if (scrollY > 0) this.transactionsListingScroll = Math.max(0, this.transactionsListingScroll - 1);
                else if (scrollY < 0) this.transactionsListingScroll = Math.min(maxOffset, this.transactionsListingScroll + 1);
                return true;
            } else if (mouseX > this.contentX + halfW && mouseX <= this.contentX + (halfW * 2) + 8) {
                int maxOffset = Math.max(0, this.playerBuyOrders.size() - maxRows);
                if (scrollY > 0) this.transactionsOrderScroll = Math.max(0, this.transactionsOrderScroll - 1);
                else if (scrollY < 0) this.transactionsOrderScroll = Math.min(maxOffset, this.transactionsOrderScroll + 1);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.showTradeDialog) {
            return handleTradeDialogClick(mouseX, mouseY, button);
        }

        // Browse Tab Clicks
        if (this.currentTab == Tab.BROWSE) {
            if (this.categoryRail != null && this.categoryRail.mouseClicked(mouseX, mouseY, button)) return true;
            if (this.searchBox != null && this.searchBox.mouseClicked(mouseX, mouseY, button)) return true;

            int tableX = getBrowseLeftX();
            int tableW = getBrowseLeftW();
            int pillX = tableX;
            int pillY = this.contentY + 18;
            int pillRowH = 15;

            List<SubCategory> subs = CategoryDef.getSubCategories(this.currentMainCategory);
            for (SubCategory sub : subs) {
                int btnW = this.font.width(sub.displayName) + 8;
                if (pillX + btnW > tableX + tableW && pillX > tableX) {
                    pillX = tableX;
                    pillY += pillRowH;
                }
                if (mouseX >= pillX && mouseX <= pillX + btnW && mouseY >= pillY && mouseY <= pillY + 13) {
                    this.currentSubCategory = sub;
                    this.catalogPage = 0;
                    this.catalogScrollOffset = 0;
                    refreshCatalog();
                    return true;
                }
                pillX += btnW + 2;
            }

            int tableY = pillY + pillRowH + 2;
            int tableH = this.contentH - (tableY - this.contentY) - 16;
            int pagY = tableY + tableH + 2;

            // Prev page button
            if (this.catalogPage > 0 && mouseX >= tableX + 2 && mouseX <= tableX + 38 && mouseY >= pagY && mouseY <= pagY + 13) {
                this.catalogPage--;
                this.catalogScrollOffset = 0;
                refreshCatalog();
                return true;
            }

            // Next page button
            if (this.catalogEntries.size() >= 50 && mouseX >= tableX + tableW - 38 && mouseX <= tableX + tableW - 2 && mouseY >= pagY && mouseY <= pagY + 13) {
                this.catalogPage++;
                this.catalogScrollOffset = 0;
                refreshCatalog();
                return true;
            }

            // Catalog Row clicks (Open dedicated Trade Dialog or toggle Favorite)
            int rowY = tableY + 18;
            int visibleRows = Math.max(1, (tableH - 20) / 22);
            for (int i = 0; i < visibleRows; i++) {
                int index = this.catalogScrollOffset + i;
                if (index >= this.catalogEntries.size()) break;
                CatalogEntry entry = this.catalogEntries.get(index);
                int currentY = rowY + (i * 22);

                // Check Watchlist star click
                int starX = tableX + 23;
                int starY = currentY + 3;
                if (mouseX >= starX && mouseX <= starX + 13 && mouseY >= starY && mouseY <= starY + 16) {
                    PacketDistributor.sendToServer(new ToggleFavoriteC2S(entry.itemId()));
                    com.omni.marketplace.client.FavoritesClientState.toggleFavorite(entry.itemId());
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
                    if (this.currentMainCategory == MainCategory.FAVORITES) {
                        refreshCatalog();
                    }
                    return true;
                }

                if (mouseX >= tableX && mouseX <= tableX + tableW - 6 && mouseY >= currentY && mouseY < currentY + 21) {
                    openTradeDialogForCatalog(entry);
                    return true;
                }
            }
        }

        // Sell Tab Clicks (Open dedicated Trade Dialog for clicked inventory item)
        if (this.currentTab == Tab.SELL) {
            Inventory inv = Minecraft.getInstance().player.getInventory();
            int slotSize = 22;
            int bagW = 9 * slotSize;
            int startX = this.contentX + (this.contentW - bagW) / 2;
            int startY = this.contentY + 34;

            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int slotIndex = 9 + (row * 9) + col;
                    int x = startX + (col * slotSize);
                    int y = startY + (row * slotSize);
                    if (mouseX >= x && mouseX < x + slotSize && mouseY >= y && mouseY < y + slotSize) {
                        ItemStack stack = inv.getItem(slotIndex);
                        if (!stack.isEmpty()) {
                            if (stack.is(com.omni.marketplace.registry.ModRegistry.MERCHANTS_LICENSE.get())) {
                                if (Minecraft.getInstance().player != null) {
                                    Minecraft.getInstance().player.displayClientMessage(Component.literal("§c[Trading Post] The Imperial Merchant License is soulbound and cannot be sold!"), true);
                                }
                                return true;
                            }
                            openTradeDialogForInventory(stack, slotIndex);
                            return true;
                        }
                    }
                }
            }

            int hotbarY = startY + (3 * slotSize) + 6;
            for (int col = 0; col < 9; col++) {
                int slotIndex = col;
                int x = startX + (col * slotSize);
                if (mouseX >= x && mouseX < x + slotSize && mouseY >= hotbarY && mouseY < hotbarY + slotSize) {
                    ItemStack stack = inv.getItem(slotIndex);
                    if (!stack.isEmpty()) {
                        if (stack.is(com.omni.marketplace.registry.ModRegistry.MERCHANTS_LICENSE.get())) {
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().player.displayClientMessage(Component.literal("§c[Trading Post] The Imperial Merchant License is soulbound and cannot be sold!"), true);
                            }
                            return true;
                        }
                        openTradeDialogForInventory(stack, slotIndex);
                        return true;
                    }
                }
            }
        }

        // Transactions Tab Clicks
        if (this.currentTab == Tab.TRANSACTIONS) {
            int halfW = (this.contentW - 12) / 2;

            int refBtnW = 68;
            int refBtnX = this.contentX + this.contentW - refBtnW;
            int refBtnY = this.contentY + 4;
            if (mouseX >= refBtnX && mouseX <= refBtnX + refBtnW && mouseY >= refBtnY && mouseY <= refBtnY + 14) {
                PacketDistributor.sendToServer(new RequestTransactionsC2S());
                return true;
            }

            // Cancel listings
            int rowY1 = this.contentY + 24;
            int maxRows = Math.max(1, (this.contentH - 28) / 24);
            for (int i = 0; i < maxRows; i++) {
                int idx = this.transactionsListingScroll + i;
                if (idx >= this.playerListings.size()) break;
                int currentY = rowY1 + (i * 24);
                int btnX = this.contentX + halfW - 48;
                if (mouseX >= btnX && mouseX <= btnX + 44 && mouseY >= currentY + 2 && mouseY <= currentY + 18) {
                    PacketDistributor.sendToServer(new CancelOrderC2S(false, this.playerListings.get(idx).id()));
                    return true;
                }
            }

            // Cancel buy orders
            int col2X = this.contentX + halfW + 8;
            int rowY2 = this.contentY + 24;
            for (int i = 0; i < maxRows; i++) {
                int idx = this.transactionsOrderScroll + i;
                if (idx >= this.playerBuyOrders.size()) break;
                int currentY = rowY2 + (i * 24);
                int btnX = col2X + halfW - 48;
                if (mouseX >= btnX && mouseX <= btnX + 44 && mouseY >= currentY + 2 && mouseY <= currentY + 18) {
                    PacketDistributor.sendToServer(new CancelOrderC2S(true, this.playerBuyOrders.get(idx).id()));
                    return true;
                }
            }
        }

        // Exchange Tab Clicks
        if (this.currentTab == Tab.EXCHANGE) {
            int availableW = this.contentW - 16;
            int boxW = Math.min(210, (availableW - 8) / 2);
            int leftX = this.contentX + (this.contentW / 2) - boxW - 4;
            int rightX = this.contentX + (this.contentW / 2) + 4;
            int boxY = this.contentY + 36;

            if (mouseX >= leftX + 10 && mouseX <= leftX + boxW - 10 && mouseY >= boxY + 24 && mouseY <= boxY + 42) {
                PacketDistributor.sendToServer(new CurrencyExchangeC2S(0));
                return true;
            }
            if (mouseX >= leftX + 10 && mouseX <= leftX + boxW - 10 && mouseY >= boxY + 48 && mouseY <= boxY + 66) {
                PacketDistributor.sendToServer(new CurrencyExchangeC2S(1));
                return true;
            }
            if (mouseX >= leftX + 10 && mouseX <= leftX + boxW - 10 && mouseY >= boxY + 72 && mouseY <= boxY + 90) {
                PacketDistributor.sendToServer(new CurrencyExchangeC2S(4));
                return true;
            }
            if (mouseX >= leftX + 10 && mouseX <= leftX + boxW - 10 && mouseY >= boxY + 96 && mouseY <= boxY + 114) {
                PacketDistributor.sendToServer(new CurrencyExchangeC2S(5));
                return true;
            }

            if (mouseX >= rightX + 10 && mouseX <= rightX + boxW - 10 && mouseY >= boxY + 24 && mouseY <= boxY + 42) {
                PacketDistributor.sendToServer(new CurrencyExchangeC2S(2));
                return true;
            }
            if (mouseX >= rightX + 10 && mouseX <= rightX + boxW - 10 && mouseY >= boxY + 48 && mouseY <= boxY + 66) {
                PacketDistributor.sendToServer(new CurrencyExchangeC2S(3));
                return true;
            }
            if (mouseX >= rightX + 10 && mouseX <= rightX + boxW - 10 && mouseY >= boxY + 72 && mouseY <= boxY + 90) {
                PacketDistributor.sendToServer(new CurrencyExchangeC2S(6));
                return true;
            }
            if (mouseX >= rightX + 10 && mouseX <= rightX + boxW - 10 && mouseY >= boxY + 96 && mouseY <= boxY + 114) {
                PacketDistributor.sendToServer(new CurrencyExchangeC2S(7));
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleTradeDialogClick(double mouseX, double mouseY, int button) {
        int dialogW = Math.min(340, this.width - 16);
        int dialogH = Math.min(242, this.height - 16);
        int dialogX = (this.width - dialogW) / 2;
        int dialogY = (this.height - dialogH) / 2;

        // Close button [ ✕ ]
        int closeBtnX = dialogX + dialogW - 18;
        int closeBtnY = dialogY + 5;
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 12 && mouseY >= closeBtnY && mouseY <= closeBtnY + 12) {
            this.showTradeDialog = false;
            updateWidgetVisibility();
            return true;
        }

        // Back button [ ◀ Back ]
        int btnY = dialogY + dialogH - 22;
        int backBtnW = 72;
        if (mouseX >= dialogX + 8 && mouseX <= dialogX + 8 + backBtnW && mouseY >= btnY && mouseY <= btnY + 18) {
            this.showTradeDialog = false;
            updateWidgetVisibility();
            return true;
        }

        // Buy / Sell Mode Tabs
        int modeY = dialogY + 63;
        int modeBtnW = (dialogW - 24) / 2;
        if (mouseX >= dialogX + 8 && mouseX <= dialogX + 8 + modeBtnW && mouseY >= modeY && mouseY <= modeY + 16) {
            this.tradeIsBuy = true;
            return true;
        }
        if (mouseX >= dialogX + 16 + modeBtnW && mouseX <= dialogX + 16 + (modeBtnW * 2) && mouseY >= modeY && mouseY <= modeY + 16) {
            this.tradeIsBuy = false;
            return true;
        }

        // Instant Action Button
        int instantY = dialogY + 82;
        int instantH = 16;
        int instantBtnW = dialogW - 16;
        if (mouseX >= dialogX + 8 && mouseX <= dialogX + 8 + instantBtnW && mouseY >= instantY && mouseY <= instantY + instantH) {
            if (this.tradeIsBuy) {
                if (this.selectedCatalogEntry != null && this.selectedCatalogEntry.lowestSell() > 0) {
                    PacketDistributor.sendToServer(new InstantBuyC2S(this.selectedCatalogEntry.itemId(), 0L, this.tradeQuantity));
                    this.showTradeDialog = false;
                    updateWidgetVisibility();
                }
            } else {
                if (!com.omni.marketplace.util.LicenseHelper.hasLicense(Minecraft.getInstance().player)) {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(Component.literal("§c[Trading Post] Access Denied: Merchant's License required to sell goods! Visit the Grand Merchant Master."), false);
                    }
                    return true;
                }
                if (this.selectedCatalogEntry != null && this.selectedCatalogEntry.highestBuy() > 0) {
                    int slot = this.selectedInventorySlot >= 0 ? this.selectedInventorySlot : findSlotForSelected();
                    if (slot >= 0) {
                        PacketDistributor.sendToServer(new InstantSellC2S(0L, this.tradeQuantity, slot));
                        this.showTradeDialog = false;
                        updateWidgetVisibility();
                    }
                }
            }
            return true;
        }

        // Quantity - / +
        int qY = dialogY + 101;
        if (mouseX >= dialogX + 38 && mouseX <= dialogX + 51 && mouseY >= qY + 1 && mouseY <= qY + 15) {
            this.tradeQuantity = Math.max(1, this.tradeQuantity - 1);
            this.qtyBox.setValue(String.valueOf(this.tradeQuantity));
            return true;
        }
        if (mouseX >= dialogX + 85 && mouseX <= dialogX + 98 && mouseY >= qY + 1 && mouseY <= qY + 15) {
            this.tradeQuantity++;
            this.qtyBox.setValue(String.valueOf(this.tradeQuantity));
            return true;
        }

        // Quantity presets [1] [16] [64] [Max]
        int b1 = dialogX + 104;
        if (mouseX >= b1 && mouseX <= b1 + 16 && mouseY >= qY + 1 && mouseY <= qY + 15) {
            this.tradeQuantity = 1;
            this.qtyBox.setValue(String.valueOf(this.tradeQuantity));
            return true;
        }
        int b16 = b1 + 18;
        if (mouseX >= b16 && mouseX <= b16 + 20 && mouseY >= qY + 1 && mouseY <= qY + 15) {
            this.tradeQuantity = 16;
            this.qtyBox.setValue(String.valueOf(this.tradeQuantity));
            return true;
        }
        int b64 = b16 + 22;
        if (mouseX >= b64 && mouseX <= b64 + 20 && mouseY >= qY + 1 && mouseY <= qY + 15) {
            this.tradeQuantity = 64;
            this.qtyBox.setValue(String.valueOf(this.tradeQuantity));
            return true;
        }
        int bMax = b64 + 22;
        if (mouseX >= bMax && mouseX <= bMax + 26 && mouseY >= qY + 1 && mouseY <= qY + 15) {
            if (!this.tradeIsBuy) {
                int slot = this.selectedInventorySlot >= 0 ? this.selectedInventorySlot : findSlotForSelected();
                if (slot >= 0 && Minecraft.getInstance().player != null) {
                    this.tradeQuantity = Math.max(1, Minecraft.getInstance().player.getInventory().getItem(slot).getCount());
                } else {
                    this.tradeQuantity = 64;
                }
            } else {
                this.tradeQuantity = 64;
            }
            this.qtyBox.setValue(String.valueOf(this.tradeQuantity));
            return true;
        }

        // Price presets: [Suggested] [Match] [+/-1c]
        int quickY = dialogY + 119;
        long lowestAsk = this.selectedCatalogEntry != null ? this.selectedCatalogEntry.lowestSell() : 0L;
        long highestBid = this.selectedCatalogEntry != null ? this.selectedCatalogEntry.highestBuy() : 0L;
        long suggested = this.selectedCatalogEntry != null ? this.selectedCatalogEntry.suggestedPrice() : 0L;
        if (suggested <= 0 && this.selectedCatalogEntry != null) {
            Item it = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(this.selectedCatalogEntry.itemId()));
            if (it != null) suggested = PriceEngine.computeSuggestedPrice(lowestAsk, highestBid, it);
        }

        if (mouseX >= dialogX + 54 && mouseX <= dialogX + 110 && mouseY >= quickY && mouseY <= quickY + 13) {
            if (suggested > 0) setBoxesFromPrice(suggested);
            return true;
        }
        if (mouseX >= dialogX + 114 && mouseX <= dialogX + 170 && mouseY >= quickY && mouseY <= quickY + 13) {
            if (this.tradeIsBuy) {
                if (highestBid > 0) setBoxesFromPrice(highestBid);
            } else {
                if (lowestAsk > 0) setBoxesFromPrice(lowestAsk);
            }
            return true;
        }
        if (mouseX >= dialogX + 174 && mouseX <= dialogX + 230 && mouseY >= quickY && mouseY <= quickY + 13) {
            if (this.tradeIsBuy) {
                if (highestBid > 0) setBoxesFromPrice(highestBid + 1);
                else setBoxesFromPrice(1);
            } else {
                if (lowestAsk > 1) setBoxesFromPrice(lowestAsk - 1);
                else setBoxesFromPrice(1);
            }
            return true;
        }

        // Price -5c / +5c Buttons
        int pY = dialogY + 135;
        int btnMinusX = dialogX + 190;
        int btnMinusW = 32;
        int btnMinusY = pY + 1;
        int btnMinusH = 14;

        int btnPlusX = dialogX + 226;
        int btnPlusW = 32;
        int btnPlusY = pY + 1;
        int btnPlusH = 14;

        if (button == 0 && mouseX >= btnMinusX && mouseX <= btnMinusX + btnMinusW && mouseY >= btnMinusY && mouseY <= btnMinusY + btnMinusH) {
            adjustPriceByCopper(-5L);
            this.activeHold = HoldAction.MINUS_PRICE;
            this.holdStartTime = System.currentTimeMillis();
            this.lastHoldStepTime = this.holdStartTime;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            setDialogFocus(null);
            return true;
        }

        if (button == 0 && mouseX >= btnPlusX && mouseX <= btnPlusX + btnPlusW && mouseY >= btnPlusY && mouseY <= btnPlusY + btnPlusH) {
            adjustPriceByCopper(5L);
            this.activeHold = HoldAction.PLUS_PRICE;
            this.holdStartTime = System.currentTimeMillis();
            this.lastHoldStepTime = this.holdStartTime;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            setDialogFocus(null);
            return true;
        }

        // Edit boxes focus
        if (this.qtyBox != null && this.qtyBox.mouseClicked(mouseX, mouseY, button)) {
            setDialogFocus(this.qtyBox);
            return true;
        }
        if (this.goldBox != null && this.goldBox.mouseClicked(mouseX, mouseY, button)) {
            setDialogFocus(this.goldBox);
            return true;
        }
        if (this.silverBox != null && this.silverBox.mouseClicked(mouseX, mouseY, button)) {
            setDialogFocus(this.silverBox);
            return true;
        }
        if (this.copperBox != null && this.copperBox.mouseClicked(mouseX, mouseY, button)) {
            setDialogFocus(this.copperBox);
            return true;
        }

        // Submit Order button
        int submitX = dialogX + 12 + backBtnW;
        int submitW = dialogW - 20 - backBtnW;
        if (mouseX >= submitX && mouseX <= submitX + submitW && mouseY >= btnY && mouseY <= btnY + 18) {
            submitCustomTrade();
            this.showTradeDialog = false;
            setDialogFocus(null);
            updateWidgetVisibility();
            return true;
        }

        // Click outside dialog closes the modal
        if (mouseX < dialogX || mouseX > dialogX + dialogW || mouseY < dialogY || mouseY > dialogY + dialogH) {
            this.showTradeDialog = false;
            setDialogFocus(null);
            updateWidgetVisibility();
            return true;
        }

        // Click inside dialog outside any box clears focus
        setDialogFocus(null);
        return true;
    }

    private int findSlotForSelected() {
        if (this.selectedCatalogEntry == null && this.selectedStack.isEmpty()) return -1;
        String itemId = this.selectedCatalogEntry != null ? this.selectedCatalogEntry.itemId() : BuiltInRegistries.ITEM.getKey(this.selectedStack.getItem()).toString();
        Inventory inv = Minecraft.getInstance().player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                return i;
            }
        }
        return -1;
    }

    private void submitCustomTrade() {
        updatePriceFromBoxes();
        if (this.selectedCatalogEntry == null && this.selectedStack.isEmpty()) return;

        String itemId = this.selectedCatalogEntry != null ? this.selectedCatalogEntry.itemId() : BuiltInRegistries.ITEM.getKey(this.selectedStack.getItem()).toString();
        
        // Disallow emerald currency from open marketplace
        if (CategoryDef.isEmeraldCurrency(itemId)) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.literal("§cEmeralds are the official currency and cannot be traded on the open market! Use the Coin Exchange tab."), false);
            }
            return;
        }

        String itemNbt = this.selectedCatalogEntry != null ? this.selectedCatalogEntry.sampleNbt() : ItemSerializer.serialize(this.selectedStack, Minecraft.getInstance().level.registryAccess());

        if (this.tradeIsBuy) {
            PacketDistributor.sendToServer(new CreateBuyOrderC2S(itemId, itemNbt, this.tradeQuantity, this.tradeUnitPrice));
        } else {
            if (!com.omni.marketplace.util.LicenseHelper.hasLicense(Minecraft.getInstance().player)) {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(Component.literal("§c[Trading Post] Access Denied: Merchant's License required to list goods for sale! Visit the Grand Merchant Master."), false);
                }
                return;
            }
            int slot = this.selectedInventorySlot >= 0 ? this.selectedInventorySlot : findSlotForSelected();
            if (slot >= 0) {
                PacketDistributor.sendToServer(new CreateListingC2S(slot, this.tradeQuantity, this.tradeUnitPrice));
            } else {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(Component.literal("§cItem not found in inventory to list!"), false);
                }
            }
        }
        refreshCatalog();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.activeHold = HoldAction.NONE;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        this.activeHold = HoldAction.NONE;
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.showTradeDialog) {
            if (keyCode == 256) { // ESCAPE closes modal
                this.showTradeDialog = false;
                setDialogFocus(null);
                updateWidgetVisibility();
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // ENTER submits order
                submitCustomTrade();
                this.showTradeDialog = false;
                setDialogFocus(null);
                updateWidgetVisibility();
                return true;
            }
            if (this.qtyBox != null && this.qtyBox.isFocused()) {
                if (this.qtyBox.keyPressed(keyCode, scanCode, modifiers)) return true;
            }
            if (this.goldBox != null && this.goldBox.isFocused()) {
                if (this.goldBox.keyPressed(keyCode, scanCode, modifiers)) return true;
            }
            if (this.silverBox != null && this.silverBox.isFocused()) {
                if (this.silverBox.keyPressed(keyCode, scanCode, modifiers)) return true;
            }
            if (this.copperBox != null && this.copperBox.isFocused()) {
                if (this.copperBox.keyPressed(keyCode, scanCode, modifiers)) return true;
            }
            // Quick keyboard shortcuts for price adjustments:
            if (keyCode == 258) { // TAB cycles input boxes
                if (this.qtyBox != null && this.qtyBox.isFocused()) {
                    setDialogFocus(this.goldBox);
                } else if (this.goldBox != null && this.goldBox.isFocused()) {
                    setDialogFocus(this.silverBox);
                } else if (this.silverBox != null && this.silverBox.isFocused()) {
                    setDialogFocus(this.copperBox);
                } else if (this.copperBox != null && this.copperBox.isFocused()) {
                    setDialogFocus(this.qtyBox);
                } else {
                    setDialogFocus(this.goldBox);
                }
                return true;
            }
            if (keyCode == 265 || keyCode == 334) { // UP Arrow or Keypad +
                adjustPriceByCopper(5L);
                return true;
            }
            if (keyCode == 264 || keyCode == 333) { // DOWN Arrow or Keypad -
                adjustPriceByCopper(-5L);
                return true;
            }
            return true; // Block 'E' or inventory hotkeys from closing the screen
        }

        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (this.searchBox.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.showTradeDialog) {
            if (this.qtyBox != null && this.qtyBox.isFocused()) {
                if (this.qtyBox.charTyped(codePoint, modifiers)) return true;
            }
            if (this.goldBox != null && this.goldBox.isFocused()) {
                if (this.goldBox.charTyped(codePoint, modifiers)) return true;
            }
            if (this.silverBox != null && this.silverBox.isFocused()) {
                if (this.silverBox.charTyped(codePoint, modifiers)) return true;
            }
            if (this.copperBox != null && this.copperBox.isFocused()) {
                if (this.copperBox.charTyped(codePoint, modifiers)) return true;
            }
            return true;
        }

        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (this.searchBox.charTyped(codePoint, modifiers)) return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private String trimName(String name, int maxLen) {
        if (name == null) return "";
        if (name.length() <= maxLen) return name;
        return name.substring(0, maxLen - 1) + "…";
    }

    private String trimText(String text, int maxWidth) {
        if (text == null) return "";
        if (this.font.width(text) <= maxWidth) return text;
        return this.font.plainSubstrByWidth(text, maxWidth - this.font.width("…")) + "…";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
