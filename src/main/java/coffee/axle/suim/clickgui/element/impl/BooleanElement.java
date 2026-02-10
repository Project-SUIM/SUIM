package coffee.axle.suim.clickgui.element.impl;

import coffee.axle.suim.clickgui.element.GuiElement;
import coffee.axle.suim.clickgui.mode.cga.ModuleButton;
import coffee.axle.suim.clickgui.settings.SettingType;
import coffee.axle.suim.clickgui.settings.impl.BooleanModuleSetting;
import coffee.axle.suim.ui.animations.impl.ColorAnimation;
import coffee.axle.suim.clickgui.util.ColorUtil;
import coffee.axle.suim.clickgui.util.FontUtil;
import coffee.axle.suim.clickgui.render.HUDRenderUtils;

import java.awt.Color;

public class BooleanElement extends GuiElement {

    private final BooleanModuleSetting boolSetting;
    private final ColorAnimation colorAnimation = new ColorAnimation(250);
    private static final double BOX_SIZE = 10.0;

    public BooleanElement(
            ModuleButton parent, BooleanModuleSetting setting) {
        super(parent, setting, SettingType.BOOLEAN);
        this.boolSetting = setting;
    }

    @Override
    protected double renderElement() {
        boolean enabled = boolSetting.getValue();
        Color outlineColor = ColorUtil.INSTANCE.getOutlineColor();
        Color accentColor = ColorUtil.INSTANCE.getClickGUIColor();
        Color bgColor = ColorUtil.INSTANCE.getBgColor().darker();

        Color fillColor = colorAnimation.get(
                accentColor, bgColor, enabled);

        HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                0.0, 0.0,
                BOX_SIZE, BOX_SIZE,
                3.0, 1.0, fillColor, fillColor);

        boolean hovered = isBoxHovered();
        HUDRenderUtils.INSTANCE.drawRoundedOutline(
                0.0, 0.0, BOX_SIZE, BOX_SIZE,
                3.0, 1.0,
                hovered ? accentColor : outlineColor);

        FontUtil.INSTANCE.drawString(
                displayName,
                BOX_SIZE + 5.0,
                BOX_SIZE / 2.0
                        - FontUtil.INSTANCE.getFontHeight() / 2.0,
                ColorUtil.textcolor);

        return super.renderElement();
    }

    @Override
    public boolean mouseClicked(int mouseButton) {
        if (mouseButton == 0 && isBoxHovered()) {
            boolSetting.toggle();
            colorAnimation.start();
            return true;
        }
        return false;
    }

    private boolean isBoxHovered() {
        int mx = getMouseXRel();
        int my = getMouseYRel();
        double totalWidth = BOX_SIZE
                + FontUtil.INSTANCE.getStringWidth(displayName) + 5.0;
        return mx >= 0 && mx <= totalWidth
                && my >= 0 && my <= BOX_SIZE;
    }
}





