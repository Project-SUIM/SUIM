package coffee.axle.suim.clickgui.render;

public class Color {
    private float hue;
    private float saturation;
    private float brightness;
    private float alpha;
    private boolean needsUpdate = true;
    private int rgba;

    public Color(float hue, float saturation, float brightness, float alpha) {
        this.hue = hue;
        this.saturation = saturation;
        this.brightness = brightness;
        this.alpha = alpha;
    }

    public Color(float hue, float saturation, float brightness) {
        this(hue, saturation, brightness, 1f);
    }

    public Color(int r, int g, int b, float alpha) {
        float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, new float[3]);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
        this.alpha = alpha;
    }

    public Color(int r, int g, int b) {
        this(r, g, b, 1f);
    }

    public Color(int rgba) {
        this(red(rgba), green(rgba), blue(rgba), alpha(rgba) / 255f);
    }

    public Color(int rgba, float alpha) {
        this(red(rgba), green(rgba), blue(rgba), alpha);
    }

    public Color(String hex) {
        this(Integer.parseInt(hex.substring(0, 2), 16),
                Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16),
                Integer.parseInt(hex.substring(6, 8), 16) / 255f);
    }

    public float getHue() {
        return hue;
    }

    public void setHue(float hue) {
        this.hue = hue;
        needsUpdate = true;
    }

    public float getSaturation() {
        return saturation;
    }

    public void setSaturation(float saturation) {
        this.saturation = saturation;
        needsUpdate = true;
    }

    public float getBrightness() {
        return brightness;
    }

    public void setBrightness(float brightness) {
        this.brightness = brightness;
        needsUpdate = true;
    }

    public float getAlpha() {
        return alpha;
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
        needsUpdate = true;
    }

    public java.awt.Color getJavaColor() {
        return new java.awt.Color(getR(), getG(), getB(), getA());
    }

    public int getRgba() {
        if (needsUpdate) {
            rgba = (java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF) | (((int) (alpha * 255)) << 24);
            needsUpdate = false;
        }
        return rgba;
    }

    public int getR() {
        return red(getRgba());
    }

    public int getG() {
        return green(getRgba());
    }

    public int getB() {
        return blue(getRgba());
    }

    public int getA() {
        return alpha(getRgba());
    }

    public float getRedFloat() {
        return getR() / 255f;
    }

    public float getGreenFloat() {
        return getG() / 255f;
    }

    public float getBlueFloat() {
        return getB() / 255f;
    }

    public float getAlphaFloat() {
        return alpha;
    }

    public boolean isTransparent() {
        return alpha == 0f;
    }

    public Color copy() {
        return new Color(getRgba());
    }

    @Override
    public String toString() {
        return "Color(red=" + getR() + ",green=" + getG() + ",blue=" + getB() + ",alpha=" + getA() + ")";
    }

    @Override
    public int hashCode() {
        int result = Float.floatToIntBits(hue);
        result = 31 * result + Float.floatToIntBits(saturation);
        result = 31 * result + Float.floatToIntBits(brightness);
        result = 31 * result + Float.floatToIntBits(alpha);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (o instanceof Color)
            return getRgba() == ((Color) o).getRgba();
        return false;
    }

    // Static color extraction methods
    public static int red(int c) {
        return (c >> 16) & 0xFF;
    }

    public static int green(int c) {
        return (c >> 8) & 0xFF;
    }

    public static int blue(int c) {
        return c & 0xFF;
    }

    public static int alpha(int c) {
        return (c >> 24) & 0xFF;
    }

    // Static color constants
    public static final Color TRANSPARENT = new Color(0, 0, 0, 0f);
    public static final Color WHITE = new Color(255, 255, 255);
    public static final Color BLACK = new Color(0, 0, 0);
    public static final Color PURPLE = new Color(170, 0, 170);
    public static final Color ORANGE = new Color(255, 170, 0);
    public static final Color GREEN = new Color(0, 255, 0);
    public static final Color DARK_GREEN = new Color(0, 170, 0);
    public static final Color DARK_RED = new Color(170, 0, 0);
    public static final Color RED = new Color(255, 0, 0);
    public static final Color GRAY = new Color(170, 170, 170);
    public static final Color DARK_GRAY = new Color(35, 35, 35);
    public static final Color BLUE = new Color(85, 255, 255);
    public static final Color PINK = new Color(255, 85, 255);
    public static final Color YELLOW = new Color(253, 218, 13);
    public static final Color CYAN = new Color(0, 170, 170);
    public static final Color MAGENTA = new Color(170, 0, 170);
}





