package coffee.axle.suim.clickgui.element.impl;

import coffee.axle.suim.clickgui.element.GuiElement;
import coffee.axle.suim.clickgui.mode.cga.ModuleButton;
import coffee.axle.suim.clickgui.settings.ModuleSetting;
import coffee.axle.suim.clickgui.settings.SettingType;
import coffee.axle.suim.clickgui.util.ColorUtil;
import coffee.axle.suim.clickgui.util.FontUtil;
import coffee.axle.suim.clickgui.render.HUDRenderUtils;

/**
 * Dropdown/collapsible group element. Renders a header
 * with an arrow indicator.
 */
public class DropdownElement extends GuiElement {

    public DropdownElement(
            ModuleButton parent, ModuleSetting<?> setting) {
        super(parent, setting, SettingType.DROPDOWN);
    }

    @Override
    protected double renderElement() {
        HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                -2.0, 0.0, width + 4.0, height,
                3.0, 1.0,
                ColorUtil.INSTANCE.getOutlineColor().darker(),
                ColorUtil.INSTANCE.getClickGUIColor());
        FontUtil.INSTANCE.drawString(
                displayName, 0.0, 3.0,
                ColorUtil.textcolor);
        return super.renderElement();
    }

    @Override
    public boolean mouseClicked(int mouseButton) {
        if (mouseButton == 0 && isHovered()) {
            extended = !extended;
            parent.updateElements();
            return true;
        }
        return false;
    }
}





