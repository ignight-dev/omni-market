package com.omni.marketplace.client.gui.widget;

import com.omni.marketplace.client.gui.MarketTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class CustomButton extends AbstractWidget {

    public interface OnPress {
        void onPress(CustomButton button);
    }

    private final OnPress onPress;
    private int normalBg = MarketTheme.BTN_DEFAULT;
    private int hoverBg = MarketTheme.BTN_DEFAULT_HOVER;
    private int textColor = MarketTheme.TEXT_PRIMARY;
    private int borderColor = MarketTheme.BTN_DEFAULT_BORDER;

    public CustomButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    public CustomButton withColors(int normalBg, int hoverBg, int textColor) {
        this.normalBg = normalBg;
        this.hoverBg = hoverBg;
        this.textColor = textColor;
        return this;
    }

    public CustomButton withBorder(int borderColor) {
        this.borderColor = borderColor;
        return this;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (this.active && this.visible && this.onPress != null) {
            this.onPress.onPress(this);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x0 = this.getX();
        int y0 = this.getY();
        int x1 = x0 + this.width;
        int y1 = y0 + this.height;

        boolean hovered = this.isHovered && this.active;
        int bg = !this.active ? MarketTheme.BTN_DISABLED : (hovered ? this.hoverBg : this.normalBg);
        int border = !this.active ? MarketTheme.BTN_DISABLED_BORDER : (hovered ? MarketTheme.PANEL_BORDER_GOLD : this.borderColor);
        int txt = !this.active ? MarketTheme.TEXT_MUTED : this.textColor;

        // Outer border
        graphics.fill(x0, y0, x1, y1, border);
        // Inner button plate
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, bg);

        // Medieval bevel: top highlight and bottom shadow
        if (this.active) {
            graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, hovered ? 0x4DF4D06F : 0x22FFFFFF);
            graphics.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, 0x40000000);
            // Corner brass studs
            graphics.fill(x0 + 2, y0 + 2, x0 + 3, y0 + 3, MarketTheme.PANEL_BORDER_GOLD);
            graphics.fill(x1 - 3, y0 + 2, x1 - 2, y0 + 3, MarketTheme.PANEL_BORDER_GOLD);
        }

        Font font = Minecraft.getInstance().font;
        int textX = x0 + (this.width - font.width(this.getMessage())) / 2;
        int textY = y0 + (this.height - 8) / 2;
        graphics.drawString(font, this.getMessage(), textX, textY, txt, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
