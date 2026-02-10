package coffee.axle.suim.clickgui.element.impl;

import coffee.axle.suim.clickgui.element.GuiElement;
import coffee.axle.suim.clickgui.mode.cga.ModuleButton;
import coffee.axle.suim.clickgui.settings.SettingType;
import coffee.axle.suim.clickgui.settings.impl.EnumModuleSetting;
import coffee.axle.suim.ui.animations.impl.EaseOutQuadAnimation;
import coffee.axle.suim.clickgui.util.ColorUtil;
import coffee.axle.suim.clickgui.util.FontUtil;
import coffee.axle.suim.clickgui.render.HUDRenderUtils;
import coffee.axle.suim.clickgui.render.StencilUtils;

import java.awt.Color;
import java.util.List;

public class SelectorElement extends GuiElement {

    private final EnumModuleSetting enumSetting;
    private final EaseOutQuadAnimation extendAnimation = new EaseOutQuadAnimation(300);

    public SelectorElement(
            ModuleButton parent, EnumModuleSetting setting) {
        super(parent, setting, SettingType.ENUM);
        this.enumSetting = setting;
    }

    @Override
    public void update() {
        super.update();
        if (extended) {
            height = enumSetting.getOptions().size()
                    * DEFAULT_HEIGHT + DEFAULT_HEIGHT;
        } else {
            height = DEFAULT_HEIGHT;
        }
    }

    @Override
    protected double renderElement() {
        List<String> options = enumSetting.getOptions();
        height = extendAnimation.get(
                13.0,
                options.size() * 13.0 + 13.0,
                !extended);

        String displayValue = displayName + ": "
                + enumSetting.getValue();
        Color bgDark = ColorUtil.INSTANCE.getBgColor().darker();
        Color accent = ColorUtil.INSTANCE.getClickGUIColor();
        Color outline = ColorUtil.INSTANCE.getOutlineColor();

        HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                0.0, 0.0, width, height,
                3.0, 1.0, bgDark, accent);
        HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                0.0, 0.0, width, 13.0,
                3.0, 1.0, bgDark, accent);
        FontUtil.INSTANCE.drawTotalCenteredString(
                displayValue, width / 2.0, 13.0 / 2.0);

        if (!extended && !extendAnimation.isAnimating()) {
            return height;
        }

        StencilUtils.INSTANCE.write(false, 3);
        HUDRenderUtils.INSTANCE.drawRoundedRect(
                0.0, 0.0, width, height, 3.0, Color.WHITE);
        StencilUtils.INSTANCE.erase(true, 3);

        int mx = getMouseXRel();
        int my = getMouseYRel();

        for (int i = 0; i < options.size(); i++) {
            double yOff = (i + 1) * 13.0;
            if (isOptionHovered(mx, my, (int) yOff)) {
                HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                        0.0, yOff, width, 13.0,
                        3.0, 1.0,
                        outline.darker(), outline);
            }
            FontUtil.INSTANCE.drawTotalCenteredString(
                    options.get(i),
                    width / 2.0,
                    yOff + 13.0 / 2.0);
        }

        StencilUtils.INSTANCE.dispose();
        return height;
    }

    @Override
    public boolean mouseClicked(int mouseButton) {
        if (mouseButton == 0) {
            if (isHeaderHovered()) {
                enumSetting.cycle();
                return true;
            }
            if (!extended)
                return false;

            List<String> options = enumSetting.getOptions();
            int mx = getMouseXRel();
            int my = getMouseYRel();
            for (int i = 0; i < options.size(); i++) {
                if (isOptionHovered(mx, my, (i + 1) * 13)) {
                    enumSetting.setIndex(i);
                    return true;
                }
            }
        } else if (mouseButton == 1 && isHeaderHovered()) {
            if (extendAnimation.start(false)) {
                extended = !extended;
            }
            return true;
        }
        return false;
    }

    private boolean isHeaderHovered() {
        int mx = getMouseXRel();
        int my = getMouseYRel();
        return mx >= 0 && mx <= width && my >= 0 && my <= 13;
    }

    private boolean isOptionHovered(int mx, int my, int yOff) {
        return mx >= 0 && mx <= width
                && my >= yOff && my <= yOff + 13;
    }
}





