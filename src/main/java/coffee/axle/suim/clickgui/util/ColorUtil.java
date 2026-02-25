package coffee.axle.suim.clickgui.util;

import coffee.axle.suim.clickgui.ClickGuiConfig;

import java.awt.Color;

/**
 * Provides color constants and utilities for the click GUI.
 *
 * @author Aton
 */
public final class ColorUtil {

    /**
     * @deprecated Use static methods directly, e.g.
     *             {@code ColorUtil.getClickGUIColor()}.
     */
    @Deprecated
    public static final ColorUtil INSTANCE = new ColorUtil();

    private ColorUtil() {
    }

    public static Color getClickGUIColor() {
        return ClickGuiConfig.getColor();
    }

    public static Color getElementColor() {
        if (ClickGuiConfig.isDesign("New"))
            return new Color(newColor);
        if (ClickGuiConfig.isDesign("JellyLike"))
            return new Color(jellyColor);
        return Color.black;
    }

    public static Color getBgColor() {
        if (ClickGuiConfig.isDesign("New"))
            return new Color(newColor);
        if (ClickGuiConfig.isDesign("JellyLike"))
            return new Color(255, 255, 255, 50);
        return Color.black;
    }

    public static Color getOutlineColor() {
        return getClickGUIColor().darker();
    }

    public static int getHoverColor() {
        Color temp = getClickGUIColor().darker();
        double scale = 0.5;
        return new Color(
                (int) (temp.getRed() * scale),
                (int) (temp.getGreen() * scale),
                (int) (temp.getBlue() * scale)).getRGB();
    }

    public static int getTabColor() {
        return withAlpha(getClickGUIColor(), 150).getRGB();
    }

    public static int sliderColor(boolean dragging) {
        return withAlpha(getClickGUIColor(), dragging ? 250 : 200).getRGB();
    }

    public static int sliderKnobColor(boolean dragging) {
        return withAlpha(getClickGUIColor(), dragging ? 255 : 230).getRGB();
    }

    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public static Color withAlpha(Color color, float alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (alpha * 255));
    }

    public static Color hsbMax(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return Color.getHSBColor(hsb[0], 1f, 1f);
    }

    /**
     * Mixes two colours together, keeping the first colour's alpha.
     */
    public static Color mix(Color c1, Color c2) {
        int rgb1 = c1.getRGB();
        int rgb2 = c2.getRGB();

        int red1 = (rgb1 >> 16) & 0xFF;
        int green1 = (rgb1 >> 8) & 0xFF;
        int blue1 = rgb1 & 0xFF;

        int red2 = (rgb2 >> 16) & 0xFF;
        int green2 = (rgb2 >> 8) & 0xFF;
        int blue2 = rgb2 & 0xFF;

        int mixedRed = (red1 + red2) / 2;
        int mixedGreen = (green1 + green2) / 2;
        int mixedBlue = (blue1 + blue2) / 2;

        int originalAlpha = (rgb1 >> 24) & 0xFF;

        return new Color((originalAlpha << 24) | (mixedRed << 16) | (mixedGreen << 8) | mixedBlue, true);
    }

    public static String hex(Color color) {
        int rgba = (color.getRed() << 24) | (color.getGreen() << 16) | (color.getBlue() << 8) | color.getAlpha();
        return String.format("%08X", rgba);
    }

    public static int toInt(Color color) {
        return (color.getAlpha() << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
    }

    public static Color invert(Color color) {
        int rgb = color.getRGB();
        int alpha = (rgb >> 24) & 0xFF;
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;

        return new Color((alpha << 24) | ((255 - red) << 16) | ((255 - green) << 8) | (255 - blue), true);
    }

    public static final int jellyColor = -0x44eaeaeb;
    public static final int newColor = -0xdcdcdd;
    public static final int moduleButtonColor = -0xe5e5e6;
    public static final int textcolor = -0x101011;

    public static final int jellyPanelColor = -0x555556;

    public static final int tabColorBg = 0x77000000;
    public static final int dropDownColor = -0x55ededee;
    public static final int boxHoverColor = 0x55111111;
    public static final int sliderBackground = -0xefeff0;

    public static final Color buttonColor = new Color(0xFF000000, true);

    public static Color getButtonColor() {
        return buttonColor;
    }
}
