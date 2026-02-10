package coffee.axle.suim.clickgui;

import coffee.axle.suim.feature.clickgui.ClickGui;
import coffee.axle.suim.util.HudUtils;

import java.awt.Color;

/**
 * Centralized accessor for ClickGUI configuration values.
 * Reads from ClickGui's Myau properties, replacing the old
 * Kotlin ClickGui module object. Used by ColorUtil, FontUtil, ChatUtils.
 */
public final class ClickGuiConfig {

    private ClickGuiConfig() {
    }

    private static ClickGui getFeature() {
        return ClickGui.getInstance();
    }

    public static boolean isBlurEnabled() {
        ClickGui f = getFeature();
        return f != null && f.isBlurEnabled();
    }

    public static boolean isCustomFontEnabled() {
        ClickGui f = getFeature();
        return f != null && f.isCustomFontEnabled();
    }

    public static int getGuiModeIndex() {
        ClickGui f = getFeature();
        return f != null ? f.getGuiModeIndex() : 0;
    }

    public static int getDesignIndex() {
        ClickGui f = getFeature();
        return f != null ? f.getDesignIndex() : 1;
    }

    public static boolean isDesign(String name) {
        int idx = getDesignIndex();
        if ("JellyLike".equalsIgnoreCase(name))
            return idx == 0;
        if ("New".equalsIgnoreCase(name))
            return idx == 1;
        return false;
    }

    public static String getClientName() {
        ClickGui f = getFeature();
        return f != null ? f.getClientName() : "Project SUIM";
    }

    public static int getPrefixStyleIndex() {
        ClickGui f = getFeature();
        return f != null ? f.getPrefixStyleIndex() : 0;
    }

    public static String getCustomPrefix() {
        ClickGui f = getFeature();
        return f != null ? f.getCustomPrefix()
                : "\u00A70\u00A7l[\u00A74\u00A7lProject SUIM\u00A70\u00A7l]\u00A7r";
    }

    public static Color getColor() {
        ClickGui f = getFeature();
        if (f != null) {
            return f.getGuiColor();
        }
        return HudUtils.getInstance().getHudColor(new Color(80, 200, 220));
    }

    public static ClickGuiAutosaveMode getAutosaveMode() {
        ClickGui f = getFeature();
        return f != null ? f.getAutosaveMode() : ClickGuiAutosaveMode.NONE;
    }

    public static boolean isShowUsageInfo() {
        ClickGui f = getFeature();
        return f != null && f.isShowUsageInfo();
    }
}
