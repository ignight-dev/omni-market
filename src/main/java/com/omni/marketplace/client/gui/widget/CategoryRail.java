package com.omni.marketplace.client.gui.widget;

import com.omni.marketplace.catalog.CategoryDef.MainCategory;
import com.omni.marketplace.client.gui.MarketTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Consumer;

public class CategoryRail {

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final Font font;
    private final Consumer<MainCategory> onSelect;
    private MainCategory activeCategory = MainCategory.ALL;
    private MainCategory hoveredCategory = null;

    public CategoryRail(int x, int y, int width, int height, Font font, Consumer<MainCategory> onSelect) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.font = font;
        this.onSelect = onSelect;
    }

    public void setActiveCategory(MainCategory cat) {
        if (cat != null) {
            this.activeCategory = cat;
        }
    }

    public static ItemStack getCategoryIcon(MainCategory cat) {
        return switch (cat) {
            case ALL -> new ItemStack(Items.CHEST);
            case FAVORITES -> new ItemStack(Items.NETHER_STAR);
            case WEAPONS_ARMOR -> new ItemStack(Items.DIAMOND_SWORD);
            case TOOLS -> new ItemStack(Items.DIAMOND_PICKAXE);
            case MATERIALS -> new ItemStack(Items.IRON_INGOT);
            case CONSUMABLES -> new ItemStack(Items.GOLDEN_APPLE);
            case BLOCKS -> new ItemStack(Items.BRICKS);
            case SPECIAL -> new ItemStack(Items.TOTEM_OF_UNDYING);
        };
    }

    public void render(GuiGraphics gfx, int mouseX, int mouseY) {
        // Medieval wrought-iron container card for category rail
        MarketTheme.drawMedievalCard(gfx, this.x, this.y, this.width, this.height, MarketTheme.SLOT_BG);
        gfx.fill(this.x + this.width - 1, this.y, this.x + this.width, this.y + this.height, MarketTheme.PANEL_BORDER);

        this.hoveredCategory = null;

        MainCategory[] categories = MainCategory.values();
        int count = categories.length; // 7 categories
        int availH = this.height - 8;
        int step = Math.min(27, Math.max(20, availH / count));
        int slotSize = Math.min(22, step - 2);
        int totalSpan = count * step;
        int startY = this.y + 4 + Math.max(0, (availH - totalSpan) / 2);

        for (int i = 0; i < count; i++) {
            MainCategory cat = categories[i];
            int slotX = this.x + (this.width - slotSize) / 2;
            int slotY = startY + (i * step);

            boolean isHovered = mouseX >= slotX && mouseX < slotX + slotSize && mouseY >= slotY && mouseY < slotY + slotSize;
            boolean isActive = (cat == this.activeCategory);

            if (isHovered) {
                this.hoveredCategory = cat;
            }

            // Inset slot backgrounds
            int bg = isActive ? 0x45D4AF37 : (isHovered ? MarketTheme.SLOT_HOVER : 0x40141210);
            int border = isActive ? MarketTheme.PANEL_BORDER_GOLD : (isHovered ? MarketTheme.PANEL_BORDER_GOLD : MarketTheme.SLOT_BORDER);

            gfx.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, border);
            gfx.fill(slotX + 1, slotY + 1, slotX + slotSize - 1, slotY + slotSize - 1, bg);

            if (isActive) {
                // Gold active tab bar on left edge
                gfx.fill(this.x + 1, slotY + 2, this.x + 3, slotY + slotSize - 2, MarketTheme.TAB_ACTIVE_BAR);
            }

            // Center 16x16 icon in slot
            int itemX = slotX + (slotSize - 16) / 2;
            int itemY = slotY + (slotSize - 16) / 2;
            gfx.renderItem(getCategoryIcon(cat), itemX, itemY);
        }
    }

    public void renderTooltip(GuiGraphics gfx, int mouseX, int mouseY) {
        if (this.hoveredCategory != null) {
            gfx.renderTooltip(this.font, Component.literal("§6" + this.hoveredCategory.displayName), mouseX, mouseY);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        MainCategory[] categories = MainCategory.values();
        int count = categories.length;
        int availH = this.height - 8;
        int step = Math.min(27, Math.max(20, availH / count));
        int slotSize = Math.min(22, step - 2);
        int totalSpan = count * step;
        int startY = this.y + 4 + Math.max(0, (availH - totalSpan) / 2);

        for (int i = 0; i < count; i++) {
            MainCategory cat = categories[i];
            int slotX = this.x + (this.width - slotSize) / 2;
            int slotY = startY + (i * step);

            if (mouseX >= slotX && mouseX < slotX + slotSize && mouseY >= slotY && mouseY < slotY + slotSize) {
                this.activeCategory = cat;
                if (this.onSelect != null) {
                    this.onSelect.accept(cat);
                }
                return true;
            }
        }
        return false;
    }
}
