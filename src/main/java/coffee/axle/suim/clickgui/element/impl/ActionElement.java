package coffee.axle.suim.clickgui.element.impl;

import coffee.axle.suim.clickgui.element.GuiElement;
import coffee.axle.suim.clickgui.mode.cga.ModuleButton;
import coffee.axle.suim.clickgui.settings.SettingType;
import coffee.axle.suim.clickgui.settings.impl.ActionModuleSetting;
import coffee.axle.suim.clickgui.util.ColorUtil;
import coffee.axle.suim.clickgui.util.FontUtil;
import coffee.axle.suim.clickgui.render.HUDRenderUtils;

public class ActionElement extends GuiElement {

    private final ActionModuleSetting actionSetting;

    public ActionElement(
            ModuleButton parent, ActionModuleSetting setting) {
        super(parent, setting, SettingType.ACTION);
        this.actionSetting = setting;
    }

    @Override
    protected double renderElement() {
        boolean hovered = isHovered();
        HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                0.0, 0.0, width, height,
                3.0, 1.0,
                ColorUtil.INSTANCE.getBgColor().darker(),
                hovered
                        ? ColorUtil.INSTANCE.getClickGUIColor()
                        : ColorUtil.INSTANCE.getOutlineColor());
        FontUtil.INSTANCE.drawTotalCenteredString(
                displayName, width / 2.0, height / 2.0);
        return super.renderElement();
    }

    @Override
    public boolean mouseClicked(int mouseButton) {
        if (mouseButton == 0 && isHovered()) {
            actionSetting.doAction();
            return true;
        }
        return false;
    }
}





