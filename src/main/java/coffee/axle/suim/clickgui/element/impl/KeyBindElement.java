package coffee.axle.suim.clickgui.element.impl;

import coffee.axle.suim.clickgui.element.GuiElement;
import coffee.axle.suim.clickgui.mode.cga.ModuleButton;
import coffee.axle.suim.clickgui.settings.SettingType;
import coffee.axle.suim.clickgui.settings.ModuleSetting;
import coffee.axle.suim.ui.animations.impl.ColorAnimation;
import coffee.axle.suim.clickgui.util.ColorUtil;
import coffee.axle.suim.clickgui.util.FontUtil;
import coffee.axle.suim.clickgui.render.HUDRenderUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;

public class KeyBindElement extends GuiElement {

    private final ModuleSetting<Integer> keySetting;
    private final ColorAnimation colorAnimation = new ColorAnimation(100);
    private double stringWidth;
    private double keyWidth;

    public KeyBindElement(
            ModuleButton parent, ModuleSetting<Integer> setting) {
        super(parent, setting, SettingType.KEY_BIND);
        this.keySetting = setting;
        this.stringWidth = FontUtil.INSTANCE.getStringWidth(displayName);
    }

    @Override
    protected double renderElement() {
        int keyCode = keySetting.getValue();
        String keyName;
        if (keyCode > 0) {
            keyName = Keyboard.getKeyName(keyCode);
            if (keyName == null)
                keyName = "Err";
        } else if (keyCode < 0) {
            keyName = Mouse.getButtonName(keyCode + 100);
        } else {
            keyName = "None";
        }
        keyWidth = FontUtil.INSTANCE.getStringWidth(keyName);

        Color accent = ColorUtil.INSTANCE.getClickGUIColor();
        Color outline = ColorUtil.INSTANCE.getOutlineColor();
        Color btnColor = ColorUtil.INSTANCE.getButtonColor();

        FontUtil.INSTANCE.drawString(
                displayName, 0.0, 1.0,
                ColorUtil.textcolor);

        Color boxOutline = colorAnimation.get(
                accent, outline, listening);
        Color boxFill = colorAnimation.get(
                outline.darker().darker(), btnColor, listening);

        HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                stringWidth + 5.0, 0.0,
                keyWidth + 5.0, height,
                3.0, 1.0, boxFill, boxOutline);

        FontUtil.INSTANCE.drawString(
                keyName,
                stringWidth + 5.0 + 3.0, 2.0,
                ColorUtil.textcolor);

        return super.renderElement();
    }

    @Override
    public boolean mouseClicked(int mouseButton) {
        if (mouseButton == 0 && isKeyBoxHovered() && !listening) {
            colorAnimation.start();
            listening = true;
            return true;
        } else if (listening) {
            keySetting.setValue(-100 + mouseButton);
            colorAnimation.start();
            listening = false;
        }
        return false;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (!listening)
            return false;

        switch (keyCode) {
            case Keyboard.KEY_ESCAPE:
                keySetting.setValue(Keyboard.KEY_NONE);
                colorAnimation.start();
                listening = false;
                break;
            case Keyboard.KEY_NUMPADENTER:
            case Keyboard.KEY_RETURN:
                colorAnimation.start();
                listening = false;
                break;
            default:
                keySetting.setValue(keyCode);
                colorAnimation.start();
                listening = false;
                break;
        }
        return true;
    }

    private boolean isKeyBoxHovered() {
        int mx = getMouseX();
        int my = getMouseY();
        double kbX = getXAbsolute() + stringWidth + 5.0;
        return mx >= kbX
                && mx <= kbX + keyWidth + 5.0
                && my >= getYAbsolute()
                && my <= getYAbsolute() + height;
    }
}
