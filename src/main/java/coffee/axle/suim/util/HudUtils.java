package coffee.axle.suim.util;

import coffee.axle.suim.hooks.MyauModuleManager;

import java.awt.Color;

/**
 * Shared HUD color utility.
 * Caches the Myau HUD module's color properties and provides
 * gradient / interpolation helpers used by multiple features.
 */
public class HudUtils {

    private static HudUtils instance;

    private MyauModuleManager manager;

    private Object hudModule;
    private boolean initialized = false;

    private Object colorModeProperty;
    private Object custom1Property;
    private Object custom2Property;
    private Object custom3Property;
    private Object colorSpeedProperty;
    private Object colorSaturationProperty;
    private Object colorBrightnessProperty;

    private Color cachedColor;
    private int colorUpdateCounter = 0;
    private static final int COLOR_UPDATE_INTERVAL = 3;

    private static final Color DEFAULT_COLOR = new Color(80, 200, 220);

    private HudUtils() {
    }

    public static HudUtils getInstance() {
        if (instance == null) {
            synchronized (HudUtils.class) {
                if (instance == null) {
                    instance = new HudUtils();
                }
            }
        }
        return instance;
    }

    public void setManager(MyauModuleManager manager) {
        this.manager = manager;
    }

    /**
     * Caches HUD module and its color-related properties.
     * Safe to call multiple times; subsequent calls are no-ops.
     *
     * @return true if HUD properties were successfully cached
     */
    public boolean initialize() {
        if (initialized) {
            return true;
        }

        if (manager == null) {
            return false;
        }

        try {
            hudModule = manager.findModule("HUD");
            if (hudModule == null) {
                return false;
            }

            colorModeProperty = manager.findProperty(hudModule, "color");
            custom1Property = manager.findProperty(hudModule, "custom-color-1");
            custom2Property = manager.findProperty(hudModule, "custom-color-2");
            custom3Property = manager.findProperty(hudModule, "custom-color-3");
            colorSpeedProperty = manager.findProperty(hudModule, "color-speed");
            colorSaturationProperty = manager.findProperty(hudModule, "color-saturation");
            colorBrightnessProperty = manager.findProperty(hudModule, "color-brightness");

            initialized = true;
            return true;
        } catch (Exception e) {
            MyauLogger.error("HudUtils: init fail", e);
            return false;
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Returns the current HUD color, using the HUD module's
     * color-mode, custom colors, speed, saturation, and brightness.
     * Result is cached for {@link #COLOR_UPDATE_INTERVAL} ticks.
     *
     * @param fallback color returned when HUD isn't available
     */
    public Color getHudColor(Color fallback) {
        if (!initialized || colorModeProperty == null) {
            return fallback;
        }

        colorUpdateCounter++;
        if (colorUpdateCounter < COLOR_UPDATE_INTERVAL
                && cachedColor != null) {
            return cachedColor;
        }
        colorUpdateCounter = 0;

        try {
            Integer colorMode = (Integer) manager.getPropertyValue(
                    colorModeProperty);
            if (colorMode == null) {
                return fallback;
            }

            long time = System.currentTimeMillis();
            Color color;

            switch (colorMode) {
                case 3:
                    color = new Color(
                            (Integer) manager.getPropertyValue(
                                    custom1Property));
                    break;
                case 4:
                    color = gradient2Color(time);
                    break;
                case 5:
                    color = gradient3Color(time);
                    break;
                default:
                    return fallback;
            }

            cachedColor = applySaturationBrightness(color);
            return cachedColor;
        } catch (Exception e) {
            return fallback;
        }
    }

    public Color getHudColor() {
        return getHudColor(DEFAULT_COLOR);
    }

    private Color gradient2Color(long time) throws Exception {
        Integer c1 = (Integer) manager.getPropertyValue(
                custom1Property);
        Integer c2 = (Integer) manager.getPropertyValue(
                custom2Property);
        Float speed = (Float) manager.getPropertyValue(
                colorSpeedProperty);

        double cycle = getColorCycle(time, 0L, speed);
        return interpolate(
                (float) (2.0
                        * Math.abs(cycle - Math.floor(cycle + 0.5))),
                new Color(c1), new Color(c2));
    }

    private Color gradient3Color(long time) throws Exception {
        Integer c1 = (Integer) manager.getPropertyValue(
                custom1Property);
        Integer c2 = (Integer) manager.getPropertyValue(
                custom2Property);
        Integer c3 = (Integer) manager.getPropertyValue(
                custom3Property);
        Float speed = (Float) manager.getPropertyValue(
                colorSpeedProperty);

        double cycle = getColorCycle(time, 0L, speed);
        float floor = (float) (2.0
                * Math.abs(cycle - Math.floor(cycle + 0.5)));

        return floor <= 0.5F
                ? interpolate(floor * 2.0F,
                        new Color(c1), new Color(c2))
                : interpolate((floor - 0.5F) * 2.0F,
                        new Color(c2), new Color(c3));
    }

    private Color applySaturationBrightness(Color color)
            throws Exception {
        Integer sat = (Integer) manager.getPropertyValue(
                colorSaturationProperty);
        Integer bri = (Integer) manager.getPropertyValue(
                colorBrightnessProperty);
        float[] hsb = Color.RGBtoHSB(
                color.getRed(), color.getGreen(),
                color.getBlue(), null);
        return Color.getHSBColor(
                hsb[0],
                hsb[1] * (sat / 100.0F),
                hsb[2] * (bri / 100.0F));
    }

    public static float getColorCycle(
            long time, long offset, float speed) {
        long calc = (long) (3000.0
                / Math.pow(
                        Math.min(Math.max(0.5F, speed), 1.5F),
                        3.0));
        return 1.0F
                - (float) (Math.abs(time - offset * 300L) % calc)
                        / (float) calc;
    }

    public static Color interpolate(float f, Color c1, Color c2) {
        f = Math.max(0.0F, Math.min(1.0F, f));
        return new Color(
                (int) (c1.getRed()
                        + (c2.getRed() - c1.getRed()) * f),
                (int) (c1.getGreen()
                        + (c2.getGreen() - c1.getGreen()) * f),
                (int) (c1.getBlue()
                        + (c2.getBlue() - c1.getBlue()) * f));
    }
}





