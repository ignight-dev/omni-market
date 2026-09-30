package com.omni.marketplace.client.gui;

import net.minecraft.client.gui.GuiGraphics;

public class MarketTheme {
    // Backdrop
    public static final int BACKDROP = 0xC80B0A0E; // Atmospheric deep medieval dusk

    // Main Panel (Weathered Dark Wrought Iron & Slate)
    public static final int PANEL_BG = 0xFF141318; // Slate / hammered ironplate
    public static final int PANEL_BORDER = 0xFF2A231C; // Wrought iron outer border
    public static final int PANEL_BORDER_GOLD = 0xFF8A6C28; // Burnished antique gold rim
    public static final int PANEL_CORNER_GOLD = 0xFFD4AF37; // Bright medieval gold corner brackets/rivets
    public static final int PANEL_SHADOW = 0x80000000;
    public static final int PANEL_HEADER_BG = 0xFF1D1B22; // Slightly raised header plate
    public static final int PANEL_HIGHLIGHT = 0x22F4D06F; // Warm candlelight top specular glow

    // Tabs
    public static final int TAB_SELECTED = 0xFF25201A; // Dark bronze plate
    public static final int TAB_SELECTED_BORDER = 0xFFD4AF37; // Bright gold border
    public static final int TAB_INACTIVE = 0xFF161519; // Cold forged iron
    public static final int TAB_INACTIVE_BORDER = 0xFF322B22; // Weathered iron border
    public static final int TAB_HOVER = 0xFF352B1E; // Torchlight warm amber hover
    public static final int TAB_ACTIVE_BAR = 0xFFE5C158; // Antique gold active underline

    // Custom Buttons (Guild Seals & Ironplates)
    public static final int BTN_DEFAULT = 0xFF221E19; // Wrought iron
    public static final int BTN_DEFAULT_BORDER = 0xFF453B2D; // Tarnished brass
    public static final int BTN_DEFAULT_HOVER = 0xFF332B22;

    public static final int BTN_PRIMARY = 0xFF184824; // Medieval Emerald Seal
    public static final int BTN_PRIMARY_BORDER = 0xFF328A47;
    public static final int BTN_PRIMARY_HOVER = 0xFF226032;

    public static final int BTN_ACCENT = 0xFF704210; // Burnished Dragon Amber
    public static final int BTN_ACCENT_BORDER = 0xFFB57224;
    public static final int BTN_ACCENT_HOVER = 0xFF915717;

    public static final int BTN_DANGER = 0xFF5E1717; // Royal Wax Seal Crimson
    public static final int BTN_DANGER_BORDER = 0xFF992727;
    public static final int BTN_DANGER_HOVER = 0xFF7A2020;

    public static final int BTN_DISABLED = 0xFF141316;
    public static final int BTN_DISABLED_BORDER = 0xFF242228;

    // Slots, Tables & Insets (Recessed Dark Leather & Carved Stone)
    public static final int SLOT_BG = 0xFF0E0D11; // Deep recessed bed
    public static final int SLOT_BORDER = 0xFF28231C; // Iron slot border
    public static final int SLOT_HOVER = 0x2AD4AF37; // Warm golden candlelight glow
    public static final int SLOT_SELECTED = 0xFFD4AF37; // Bright gold rim

    public static final int ROW_EVEN = 0x07FFFFFF;
    public static final int ROW_ODD = 0x0EFFFFFF;
    public static final int ROW_HOVER = 0x22D4AF37; // Torchlight amber row hover

    // Typography & Color Codes
    public static final int TEXT_TITLE = 0xFFF5E6C8; // Warm Antique Ivory
    public static final int TEXT_PRIMARY = 0xFFDDD5C7; // Light Vellum / Parchment
    public static final int TEXT_SECONDARY = 0xFF9E9484; // Weathered Bronze
    public static final int TEXT_MUTED = 0xFF686054; // Dark Charcoal Brown
    public static final int TEXT_GOLD = 0xFFFFD700; // Bright Gold
    public static final int TEXT_SILVER = 0xFFD8DEE9; // Polished Silver
    public static final int TEXT_COPPER = 0xFFD47D42; // Burnished Copper

    // =========================================================================
    // Medieval Procedural Graphic Helpers
    // =========================================================================

    public static void drawMedievalFrame(GuiGraphics gfx, int x, int y, int w, int h) {
        // Drop shadow
        gfx.fill(x + 4, y + 4, x + w + 4, y + h + 4, PANEL_SHADOW);

        // Slate background
        gfx.fill(x, y, x + w, y + h, PANEL_BG);

        // Outer dark iron border (1px)
        gfx.fill(x, y, x + w, y + 1, PANEL_BORDER);
        gfx.fill(x, y + h - 1, x + w, y + h, PANEL_BORDER);
        gfx.fill(x, y, x + 1, y + h, PANEL_BORDER);
        gfx.fill(x + w - 1, y, x + w, y + h, PANEL_BORDER);

        // Inner antique gold rim (1px inset)
        gfx.fill(x + 1, y + 1, x + w - 1, y + 2, PANEL_BORDER_GOLD);
        gfx.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, PANEL_BORDER_GOLD);
        gfx.fill(x + 1, y + 1, x + 2, y + h - 1, PANEL_BORDER_GOLD);
        gfx.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, PANEL_BORDER_GOLD);

        // Top warm candlelight highlight
        gfx.fill(x + 2, y + 2, x + w - 2, y + 3, PANEL_HIGHLIGHT);

        // 4 Ornate Medieval Corner Brackets & Rivets
        drawCornerAccents(gfx, x, y, w, h, PANEL_CORNER_GOLD);
    }

    public static void drawMedievalCard(GuiGraphics gfx, int x, int y, int w, int h, int bg) {
        // Recessed card surface
        gfx.fill(x, y, x + w, y + h, bg);

        // Outer border
        gfx.fill(x, y, x + w, y + 1, PANEL_BORDER);
        gfx.fill(x, y + h - 1, x + w, y + h, PANEL_BORDER);
        gfx.fill(x, y, x + 1, y + h, PANEL_BORDER);
        gfx.fill(x + w - 1, y, x + w, y + h, PANEL_BORDER);

        // Subtle top bevel highlight
        gfx.fill(x + 1, y + 1, x + w - 1, y + 2, 0x18FFFFFF);

        // Mini corner rivets
        drawCornerRivets(gfx, x, y, w, h, PANEL_BORDER_GOLD);
    }

    public static void drawCornerAccents(GuiGraphics gfx, int x, int y, int w, int h, int color) {
        // Top-Left L-bracket
        gfx.fill(x, y, x + 5, y + 2, color);
        gfx.fill(x, y, x + 2, y + 5, color);
        // Top-Right L-bracket
        gfx.fill(x + w - 5, y, x + w, y + 2, color);
        gfx.fill(x + w - 2, y, x + w, y + 5, color);
        // Bottom-Left L-bracket
        gfx.fill(x, y + h - 2, x + 5, y + h, color);
        gfx.fill(x, y + h - 5, x + 2, y + h, color);
        // Bottom-Right L-bracket
        gfx.fill(x + w - 5, y + h - 2, x + w, y + h, color);
        gfx.fill(x + w - 2, y + h - 5, x + w, y + h, color);

        // 4 Brass Rivets (inside corners)
        drawCornerRivets(gfx, x + 2, y + 2, w - 4, h - 4, 0xFFE5C158);
    }

    public static void drawCornerRivets(GuiGraphics gfx, int x, int y, int w, int h, int color) {
        // Top-Left rivet
        gfx.fill(x + 2, y + 2, x + 4, y + 4, color);
        // Top-Right rivet
        gfx.fill(x + w - 4, y + 2, x + w - 2, y + 4, color);
        // Bottom-Left rivet
        gfx.fill(x + 2, y + h - 4, x + 4, y + h - 2, color);
        // Bottom-Right rivet
        gfx.fill(x + w - 4, y + h - 4, x + w - 2, y + h - 2, color);
    }

    public static void drawMedievalDivider(GuiGraphics gfx, int x, int y, int w) {
        int half = w / 2;
        // Fading left line
        gfx.fill(x, y, x + half - 6, y + 1, 0x30D4AF37);
        // Fading right line
        gfx.fill(x + half + 6, y, x + w, y + 1, 0x30D4AF37);
        // Center brass diamond
        gfx.fill(x + half - 2, y - 1, x + half + 2, y + 2, PANEL_CORNER_GOLD);
    }
}
