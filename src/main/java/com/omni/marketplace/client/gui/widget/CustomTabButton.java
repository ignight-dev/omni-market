package com.omni.marketplace.client.gui.widget;

import com.omni.marketplace.client.gui.MarketTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class CustomTabButton extends AbstractWidget {

    private final Consumer<CustomTabButton> onPress;
    private boolean selected;

    public CustomTabButton(int x, int y, int width, int height, Component message, boolean selected, Consumer<CustomTabButton> onPress) {
        super(x, y, width, height, message);
        this.selected = selected;
        this.onPress = onPress;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isSelected() {
        return selected;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (this.onPress != null) {
            this.onPress.accept(this);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x0 = this.getX();
        int y0 = this.getY();
        int x1 = x0 + this.width;
        int y1 = y0 + this.height;

        boolean hovered = this.isHovered;
        int bg = this.selected ? MarketTheme.TAB_SELECTED : (hovered ? MarketTheme.TAB_HOVER : MarketTheme.TAB_INACTIVE);
        int border = this.selected ? MarketTheme.TAB_SELECTED_BORDER : MarketTheme.TAB_INACTIVE_BORDER;
        int text = this.selected ? MarketTheme.TEXT_GOLD : (hovered ? MarketTheme.TEXT_TITLE : MarketTheme.TEXT_SECONDARY);

        // Tab background & border
        graphics.fill(x0, y0, x1, y1, border);
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, bg);

        if (this.selected) {
            // Gold active indicator at bottom of tab
            graphics.fill(x0 + 2, y1 - 2, x1 - 2, y1, MarketTheme.TAB_ACTIVE_BAR);
            // Top rim highlight
            graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, 0x40F4D06F);
            // Corner brass accents
            graphics.fill(x0 + 2, y0 + 2, x0 + 4, y0 + 4, MarketTheme.PANEL_CORNER_GOLD);
            graphics.fill(x1 - 4, y0 + 2, x1 - 2, y0 + 4, MarketTheme.PANEL_CORNER_GOLD);
        } else if (hovered) {
            graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, 0x20FFFFFF);
        }

        Font font = Minecraft.getInstance().font;
        Component msg = this.getMessage();
        int maxW = this.width - 6;
        if (font.width(msg) > maxW) {
            String str = font.plainSubstrByWidth(msg.getString(), maxW - font.width("…")) + "…";
            msg = Component.literal(str);
        }
        int labelX = x0 + Math.max(3, (this.width - font.width(msg)) / 2);
        int labelY = y0 + (this.height - 8) / 2;
        graphics.drawString(font, msg, labelX, labelY, text, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
