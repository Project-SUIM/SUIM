package coffee.axle.suim.clickgui.misc.elements.impl;

import coffee.axle.suim.ui.animations.impl.ColorAnimation;
import coffee.axle.suim.clickgui.util.FontUtil;
import coffee.axle.suim.clickgui.misc.elements.MiscElement;
import coffee.axle.suim.clickgui.misc.elements.MiscElementStyle;
import coffee.axle.suim.clickgui.render.HUDRenderUtils;

import java.awt.Color;
import java.util.function.Consumer;

/**
 * A toggleable boolean element.
 */
public class MiscElementBoolean extends MiscElement {

    private boolean enabled;
    private double gap;
    private Consumer<Boolean> onChange;

    private final ColorAnimation colourAnimation = new ColorAnimation(250);

    public MiscElementBoolean(MiscElementStyle style, boolean enabled, double gap) {
        super(style);
        this.enabled = enabled;
        this.gap = gap;
    }

    public MiscElementBoolean(MiscElementStyle style) {
        this(style, false, 1.0);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getGap() {
        return gap;
    }

    public void setGap(double gap) {
        this.gap = gap;
    }

    public void setOnChange(Consumer<Boolean> onChange) {
        this.onChange = onChange;
    }

    @Override
    public void render(int mouseX, int mouseY) {
        Color colour = colourAnimation.get(getOutlineHoverColour(), getColour(), this.enabled);
        HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                getX() + gap, getY() + gap,
                getWidth() - gap * 2, getHeight() - gap * 2,
                getRadius(), getThickness(), colour, colour);
        HUDRenderUtils.INSTANCE.drawRoundedOutline(
                getX(), getY(), getWidth(), getHeight(),
                getRadius(), getThickness(),
                isHovered(mouseX, mouseY) ? getOutlineHoverColour() : getOutlineColour());

        FontUtil.INSTANCE.drawString(
                getValue(),
                getX() + getWidth() + 5.0,
                getY() + getHeight() / 2 - FontUtil.INSTANCE.getFontHeight() / 2);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (isHovered(mouseX, mouseY) && mouseButton == 0 && colourAnimation.start()) {
            this.enabled = !this.enabled;
            if (onChange != null) {
                onChange.accept(this.enabled);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }
}





