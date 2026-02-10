package coffee.axle.suim.clickgui.element.impl;

import coffee.axle.suim.clickgui.element.GuiElement;
import coffee.axle.suim.clickgui.mode.cga.ModuleButton;
import coffee.axle.suim.clickgui.settings.SettingType;
import coffee.axle.suim.clickgui.settings.impl.NumberModuleSetting;
import coffee.axle.suim.clickgui.util.ColorUtil;
import coffee.axle.suim.clickgui.util.FontUtil;
import coffee.axle.suim.clickgui.render.HUDRenderUtils;
import net.minecraft.util.MathHelper;

import java.awt.Color;

public class SliderElement extends GuiElement {

    private final NumberModuleSetting numSetting;
    private boolean dragging;

    public SliderElement(
            ModuleButton parent, NumberModuleSetting setting) {
        super(parent, setting, SettingType.NUMBER);
        this.numSetting = setting;
    }

    @Override
    protected double renderElement() {
        double value = numSetting.getValue();
        double min = numSetting.getMin();
        double max = numSetting.getMax();
        double increment = numSetting.getIncrement();

        double rounded = Math.round(value * 100.0) / 100.0;
        String displayValue;
        if (increment % 1 == 0) {
            displayValue = (int) rounded + numSetting.getUnit();
        } else {
            displayValue = rounded + numSetting.getUnit();
        }

        double percentBar = (value - min) / (max - min);
        double fh = FontUtil.INSTANCE.getFontHeight();
        Color btnColor = ColorUtil.INSTANCE.getButtonColor();
        Color accentColor = ColorUtil.INSTANCE.getClickGUIColor();

        FontUtil.INSTANCE.drawString(
                displayName + ": " + displayValue,
                0.0, 0.0, ColorUtil.textcolor);

        HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                0.0, fh + 5.0, width, 2.0,
                2.0, 1.0, btnColor, btnColor);

        HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                0.0, fh + 5.0, percentBar * width, 2.0,
                2.0, 1.0, accentColor, accentColor);

        double knobX = (percentBar * width - 3.0)
                - (percentBar - 0.5) * 4.5;
        HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                knobX, fh + 3.0, 6.0, 6.0,
                3.0, 1.0, btnColor, accentColor);

        if (dragging) {
            double diff = max - min;
            double pct = MathHelper.clamp_double(
                    (getMouseX() - getXAbsolute()) / width, 0.0, 1.0);
            numSetting.setValue(min + pct * diff);
        }

        return super.renderElement();
    }

    @Override
    public boolean mouseClicked(int mouseButton) {
        if (mouseButton == 0 && isSliderHovered()) {
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(int state) {
        dragging = false;
    }

    private boolean isSliderHovered() {
        int mx = getMouseX();
        int my = getMouseY();
        double fh = FontUtil.INSTANCE.getFontHeight();
        return mx >= getXAbsolute()
                && mx <= getXAbsolute() + width
                && my >= getYAbsolute() + fh + 3.0
                && my <= getYAbsolute() + height;
    }
}





