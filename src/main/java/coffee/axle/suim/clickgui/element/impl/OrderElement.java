package coffee.axle.suim.clickgui.element.impl;

import coffee.axle.suim.clickgui.element.GuiElement;
import coffee.axle.suim.clickgui.mode.cga.ModuleButton;
import coffee.axle.suim.clickgui.settings.ModuleSetting;
import coffee.axle.suim.clickgui.settings.SettingType;
import coffee.axle.suim.clickgui.util.ColorUtil;
import coffee.axle.suim.clickgui.util.FontUtil;
import coffee.axle.suim.clickgui.render.HUDRenderUtils;

/**
 * Order/drag-reorder element. Stub implementation that renders
 * a placeholder with the current value display.
 * Full drag-and-drop grid to be implemented when needed.
 */
public class OrderElement extends GuiElement {

    public OrderElement(
            ModuleButton parent, ModuleSetting<?> setting) {
        super(parent, setting, SettingType.ORDER);
        this.height = DEFAULT_HEIGHT * 3;
    }

    @Override
    protected double renderElement() {
        HUDRenderUtils.INSTANCE.drawRoundedOutline(
                0.0, FontUtil.INSTANCE.getFontHeight() + 3.0,
                width, height
                        - FontUtil.INSTANCE.getFontHeight() - 3.0,
                3.0, 1.0,
                ColorUtil.INSTANCE.getClickGUIColor());
        FontUtil.INSTANCE.drawString(
                displayName, 0.0, 0.0,
                ColorUtil.textcolor);
        FontUtil.INSTANCE.drawString(
                "(Order not yet implemented)",
                5.0,
                FontUtil.INSTANCE.getFontHeight() + 8.0,
                ColorUtil.INSTANCE.getOutlineColor().getRGB());
        return getElementHeight();
    }

    @Override
    public double getElementHeight() {
        return height;
    }
}





