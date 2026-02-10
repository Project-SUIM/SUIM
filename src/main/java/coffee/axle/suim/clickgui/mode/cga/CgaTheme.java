package coffee.axle.suim.clickgui.mode.cga;

import coffee.axle.suim.util.HudUtils;

import java.awt.Color;

public final class CgaTheme {
    private static final Color DEFAULT_ACCENT = new Color(80, 200, 220);
    private static final Color DEFAULT_BG = new Color(20, 20, 20, 180);
    private static final int TEXT_COLOR = 0xFFEDEDED;

    private CgaTheme() {
    }

    public static Color getAccent() {
        return HudUtils.getInstance().getHudColor(DEFAULT_ACCENT);
    }

    public static Color getBackground() {
        return DEFAULT_BG;
    }

    public static Color getOutline() {
        return getAccent().darker();
    }

    public static int getTextColor() {
        return TEXT_COLOR;
    }
}
