package coffee.axle.suim.clickgui.element.impl;

import coffee.axle.suim.clickgui.element.GuiElement;
import coffee.axle.suim.clickgui.mode.cga.ModuleButton;
import coffee.axle.suim.clickgui.settings.SettingType;
import coffee.axle.suim.clickgui.settings.impl.StringModuleSetting;
import coffee.axle.suim.clickgui.util.MiscElementHelper;
import coffee.axle.suim.clickgui.util.ColorUtil;
import coffee.axle.suim.clickgui.util.FontUtil;
import coffee.axle.suim.clickgui.misc.elements.impl.MiscElementTextField;

public class TextFieldElement extends GuiElement {

    private final StringModuleSetting stringSetting;
    private final MiscElementTextField textField;
    private String lastKnownText;

    public TextFieldElement(
            ModuleButton parent, StringModuleSetting setting) {
        super(parent, setting, SettingType.STRING);
        this.stringSetting = setting;

        double fh = FontUtil.INSTANCE.getFontHeight();
        this.textField = MiscElementHelper.createTextField(
                0.0, fh + 3.0,
                this.width, 13.0,
                setting.getMaxLength(),
                setting.getPlaceholder(), "");
        this.textField.setText(setting.getValue());
        this.lastKnownText = setting.getValue();
    }

    @Override
    protected double renderElement() {
        if (!stringSetting.getValue().equals(lastKnownText)) {
            textField.setText(stringSetting.getValue());
            lastKnownText = stringSetting.getValue();
        }

        FontUtil.INSTANCE.drawString(
                displayName, 0.0, 0.0,
                ColorUtil.textcolor);

        if (!textField.getText().equals(stringSetting.getValue())) {
            stringSetting.setValue(textField.getText());
            lastKnownText = textField.getText();
        }

        textField.setOutlineColour(
                ColorUtil.INSTANCE.getOutlineColor());
        textField.setOutlineHoverColour(
                ColorUtil.INSTANCE.getClickGUIColor());
        textField.render(getMouseXRel(), getMouseYRel());

        return super.renderElement();
    }

    @Override
    public boolean mouseClicked(int mouseButton) {
        return textField.mouseClicked(
                getMouseXRel(), getMouseYRel(), mouseButton);
    }

    @Override
    public void mouseClickMove(
            int mouseButton, long timeSinceLastClick) {
        textField.mouseClickMove(
                getMouseXRel(), getMouseYRel(),
                mouseButton, timeSinceLastClick);
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        return textField.keyTyped(typedChar, keyCode);
    }
}





