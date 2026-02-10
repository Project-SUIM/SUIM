package coffee.axle.suim.ui.animations.impl;

import java.awt.Color;

/**
 * Animates between two {@link Color} values using a {@link LinearAnimation} for
 * each channel (r, g, b, a).
 */
public class ColorAnimation {

    private final LinearAnimation anim;

    public ColorAnimation(long duration) {
        this.anim = new LinearAnimation(duration);
    }

    public boolean start() {
        return anim.start(false);
    }

    public boolean start(boolean bypass) {
        return anim.start(bypass);
    }

    public boolean isAnimating() {
        return anim.isAnimating();
    }

    public int percent() {
        return anim.getPercent();
    }

    public Color get(Color start, Color end, boolean reverse) {
        int r = anim.get(start.getRed(), end.getRed(), reverse).intValue();
        int g = anim.get(start.getGreen(), end.getGreen(), reverse).intValue();
        int b = anim.get(start.getBlue(), end.getBlue(), reverse).intValue();
        int a = anim.get(start.getAlpha(), end.getAlpha(), reverse).intValue();

        r = clamp(r, 0, 255);
        g = clamp(g, 0, 255);
        b = clamp(b, 0, 255);
        a = clamp(a, 0, 255);

        return new Color(r, g, b, a);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}





