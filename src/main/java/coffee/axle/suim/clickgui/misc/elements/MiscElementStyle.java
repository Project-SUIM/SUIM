package coffee.axle.suim.clickgui.misc.elements;

import coffee.axle.suim.clickgui.util.Alignment;
import coffee.axle.suim.clickgui.util.ColorUtil;
import coffee.axle.suim.clickgui.util.VAlignment;

import java.awt.Color;
import java.util.function.Consumer;

public class MiscElementStyle {
    private String value;
    private double x;
    private double y;
    private double width;
    private double height;
    private double radius;
    private double thickness;
    private Color textColour;
    private Color colour;
    private Color outlineColour;
    private Color outlineHoverColour;
    private Alignment alignment;
    private VAlignment vAlignment;
    private double textPadding;
    private double vTextPadding;

    public MiscElementStyle() {
        this("", 0.0, 0.0, 80.0, 20.0, 3.0, 1.0,
                new Color(ColorUtil.textcolor), ColorUtil.INSTANCE.getBgColor().darker(),
                ColorUtil.INSTANCE.getOutlineColor(), ColorUtil.INSTANCE.getClickGUIColor(),
                Alignment.CENTRE, VAlignment.CENTRE, 5.0, 0.0);
    }

    public MiscElementStyle(String value, double x, double y, double width, double height,
            double radius, double thickness, Color textColour, Color colour,
            Color outlineColour, Color outlineHoverColour, Alignment alignment,
            VAlignment vAlignment, double textPadding, double vTextPadding) {
        this.value = value;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.radius = radius;
        this.thickness = thickness;
        this.textColour = textColour;
        this.colour = colour;
        this.outlineColour = outlineColour;
        this.outlineHoverColour = outlineHoverColour;
        this.alignment = alignment;
        this.vAlignment = vAlignment;
        this.textPadding = textPadding;
        this.vTextPadding = vTextPadding;
    }

    /**
     * Applies mutations via a Consumer and returns this instance for chaining.
     */
    public MiscElementStyle apply(Consumer<MiscElementStyle> block) {
        block.accept(this);
        return this;
    }

    // Getters and setters

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public double getThickness() {
        return thickness;
    }

    public void setThickness(double thickness) {
        this.thickness = thickness;
    }

    public Color getTextColour() {
        return textColour;
    }

    public void setTextColour(Color textColour) {
        this.textColour = textColour;
    }

    public Color getColour() {
        return colour;
    }

    public void setColour(Color colour) {
        this.colour = colour;
    }

    public Color getOutlineColour() {
        return outlineColour;
    }

    public void setOutlineColour(Color outlineColour) {
        this.outlineColour = outlineColour;
    }

    public Color getOutlineHoverColour() {
        return outlineHoverColour;
    }

    public void setOutlineHoverColour(Color outlineHoverColour) {
        this.outlineHoverColour = outlineHoverColour;
    }

    public Alignment getAlignment() {
        return alignment;
    }

    public void setAlignment(Alignment alignment) {
        this.alignment = alignment;
    }

    public VAlignment getVAlignment() {
        return vAlignment;
    }

    public void setVAlignment(VAlignment vAlignment) {
        this.vAlignment = vAlignment;
    }

    public double getTextPadding() {
        return textPadding;
    }

    public void setTextPadding(double textPadding) {
        this.textPadding = textPadding;
    }

    public double getVTextPadding() {
        return vTextPadding;
    }

    public void setVTextPadding(double vTextPadding) {
        this.vTextPadding = vTextPadding;
    }

    @Override
    public String toString() {
        return "MiscElementStyle(value=" + value + ", x=" + x + ", y=" + y +
                ", width=" + width + ", height=" + height + ", radius=" + radius +
                ", thickness=" + thickness + ", textColour=" + textColour +
                ", colour=" + colour + ", outlineColour=" + outlineColour +
                ", outlineHoverColour=" + outlineHoverColour + ", alignment=" + alignment +
                ", vAlignment=" + vAlignment + ", textPadding=" + textPadding +
                ", vTextPadding=" + vTextPadding + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof MiscElementStyle))
            return false;
        MiscElementStyle that = (MiscElementStyle) o;
        return Double.compare(that.x, x) == 0 &&
                Double.compare(that.y, y) == 0 &&
                Double.compare(that.width, width) == 0 &&
                Double.compare(that.height, height) == 0 &&
                Double.compare(that.radius, radius) == 0 &&
                Double.compare(that.thickness, thickness) == 0 &&
                Double.compare(that.textPadding, textPadding) == 0 &&
                Double.compare(that.vTextPadding, vTextPadding) == 0 &&
                java.util.Objects.equals(value, that.value) &&
                java.util.Objects.equals(textColour, that.textColour) &&
                java.util.Objects.equals(colour, that.colour) &&
                java.util.Objects.equals(outlineColour, that.outlineColour) &&
                java.util.Objects.equals(outlineHoverColour, that.outlineHoverColour) &&
                alignment == that.alignment &&
                vAlignment == that.vAlignment;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(value, x, y, width, height, radius, thickness,
                textColour, colour, outlineColour, outlineHoverColour, alignment,
                vAlignment, textPadding, vTextPadding);
    }

    /**
     * Returns a copy of this style with the same field values.
     */
    public MiscElementStyle copy() {
        return new MiscElementStyle(value, x, y, width, height, radius, thickness,
                textColour, colour, outlineColour, outlineHoverColour, alignment,
                vAlignment, textPadding, vTextPadding);
    }
}





