package coffee.axle.suim.clickgui.misc.elements;

import java.awt.Color;

/**
 * Base class for misc UI elements.
 * Java port of the Kotlin MiscElement abstract class.
 */
public abstract class MiscElement {

    public static MiscElement currentlyFocused = null;

    protected MiscElementStyle style;

    public MiscElement(MiscElementStyle style) {
        this.style = style;
    }

    public MiscElementStyle getStyle() {
        return style;
    }

    public MiscElementStyle getUpdates() {
        return style;
    }

    public String getValue() {
        return style.getValue();
    }

    public void setValue(String value) {
        style.setValue(value);
    }

    public double getX() {
        return style.getX();
    }

    public void setX(double x) {
        style.setX(x);
    }

    public double getY() {
        return style.getY();
    }

    public void setY(double y) {
        style.setY(y);
    }

    public double getWidth() {
        return style.getWidth();
    }

    public void setWidth(double width) {
        style.setWidth(width);
    }

    public double getHeight() {
        return style.getHeight();
    }

    public void setHeight(double height) {
        style.setHeight(height);
    }

    public double getThickness() {
        return style.getThickness();
    }

    public void setThickness(double thickness) {
        style.setThickness(thickness);
    }

    public double getRadius() {
        return style.getRadius();
    }

    public void setRadius(double radius) {
        style.setRadius(radius);
    }

    public Color getColour() {
        return style.getColour();
    }

    public void setColour(Color colour) {
        style.setColour(colour);
    }

    public Color getOutlineColour() {
        return style.getOutlineColour();
    }

    public void setOutlineColour(Color outlineColour) {
        style.setOutlineColour(outlineColour);
    }

    public Color getOutlineHoverColour() {
        return style.getOutlineHoverColour();
    }

    public void setOutlineHoverColour(Color outlineHoverColour) {
        style.setOutlineHoverColour(outlineHoverColour);
    }

    public void render(int mouseX, int mouseY) {
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        return false;
    }

    public void mouseClickMove(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }

    public boolean onScroll(int amount) {
        return false;
    }

    public boolean isHovered(int mouseX, int mouseY) {
        return isHovered(mouseX, mouseY, 0.0, 0.0);
    }

    public boolean isHovered(int mouseX, int mouseY, double xOff, double yOff) {
        return mouseX >= getX() + xOff && mouseX <= getX() + getWidth() + xOff
                && mouseY >= getY() + yOff && mouseY <= getY() + getHeight() + yOff;
    }
}
