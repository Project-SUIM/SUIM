package coffee.axle.suim.clickgui.settings.impl;

import coffee.axle.suim.clickgui.settings.ModuleSetting;
import coffee.axle.suim.clickgui.settings.SettingType;
import coffee.axle.suim.clickgui.settings.SettingVisibility;
import coffee.axle.suim.hooks.ModulePropertyManager;
import coffee.axle.suim.hooks.MyauModuleManager;

import java.awt.Color;

/**
 * Color setting backed by a Myau string property (hex RRGGBBAA format).
 * Manages HSB state locally for the color picker.
 */
public class ColorModuleSetting extends ModuleSetting<Color> {

    private final Object myauProp;
    private final MyauModuleManager mgr;
    private final ModulePropertyManager propMgr;
    private final boolean allowAlpha;
    private final boolean collapsible;

    private float hue;
    private float saturation;
    private float brightness;
    private float alpha;

    private final Color defaultValue;

    public ColorModuleSetting(
            String name,
            String description,
            Object myauProp,
            MyauModuleManager mgr,
            ModulePropertyManager propMgr,
            boolean allowAlpha,
            boolean collapsible) {
        this(name, description, myauProp, mgr, propMgr, allowAlpha,
                collapsible, SettingVisibility.VISIBLE);
    }

    public ColorModuleSetting(
            String name,
            String description,
            Object myauProp,
            MyauModuleManager mgr,
            ModulePropertyManager propMgr,
            boolean allowAlpha,
            boolean collapsible,
            SettingVisibility visibility) {
        super(name, description, SettingType.COLOR, visibility);
        this.myauProp = myauProp;
        this.mgr = mgr;
        this.propMgr = propMgr;
        this.allowAlpha = allowAlpha;
        this.collapsible = collapsible;

        Color initial = parseHex(propMgr.getString(myauProp, "FF89D5FF"));
        this.defaultValue = initial;

        float[] hsb = Color.RGBtoHSB(
                initial.getRed(), initial.getGreen(), initial.getBlue(), null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
        this.alpha = initial.getAlpha() / 255f;
    }

    @Override
    public Color getValue() {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        int a = (int) (alpha * 255);
        return new Color(
                (rgb >> 16) & 0xFF,
                (rgb >> 8) & 0xFF,
                rgb & 0xFF,
                a);
    }

    @Override
    public void setValue(Color value) {
        float[] hsb = Color.RGBtoHSB(
                value.getRed(), value.getGreen(), value.getBlue(), null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
        this.alpha = value.getAlpha() / 255f;
        persist();
    }

    @Override
    public Color getDefault() {
        return defaultValue;
    }

    public float getHue() {
        return hue;
    }

    public void setHue(float hue) {
        this.hue = hue;
        persist();
    }

    public float getSaturation() {
        return saturation;
    }

    public void setSaturation(float saturation) {
        this.saturation = saturation;
        persist();
    }

    public float getBrightness() {
        return brightness;
    }

    public void setBrightness(float brightness) {
        this.brightness = brightness;
        persist();
    }

    public float getAlpha() {
        return alpha;
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
        persist();
    }

    public boolean isAllowAlpha() {
        return allowAlpha;
    }

    public boolean isCollapsible() {
        return collapsible;
    }

    private void persist() {
        Color c = getValue();
        String hex = String.format("%02X%02X%02X%02X",
                c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
        try {
            mgr.setPropertyValue(myauProp, hex);
        } catch (Exception ignored) {
        }
    }

    /**
     * Returns the fully saturated/bright version of the current hue.
     */
    public Color getHsbMax() {
        return Color.getHSBColor(hue, 1f, 1f);
    }

    private static Color parseHex(String hex) {
        try {
            if (hex == null || hex.isEmpty()) {
                return new Color(255, 137, 213);
            }
            hex = hex.replace("#", "");
            if (hex.length() == 6) {
                hex = hex + "FF";
            }
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            int a = Integer.parseInt(hex.substring(6, 8), 16);
            return new Color(r, g, b, a);
        } catch (Exception e) {
            return new Color(255, 137, 213);
        }
    }
}





