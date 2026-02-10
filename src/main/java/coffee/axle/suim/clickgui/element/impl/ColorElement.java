package coffee.axle.suim.clickgui.element.impl;

import coffee.axle.suim.clickgui.element.GuiElement;
import coffee.axle.suim.clickgui.mode.cga.ModuleButton;
import coffee.axle.suim.clickgui.settings.SettingType;
import coffee.axle.suim.clickgui.settings.impl.ColorModuleSetting;
import coffee.axle.suim.clickgui.util.MiscElementHelper;
import coffee.axle.suim.ui.animations.impl.EaseOutQuadAnimation;
import coffee.axle.suim.clickgui.util.ColorUtil;
import coffee.axle.suim.clickgui.util.FontUtil;
import coffee.axle.suim.clickgui.misc.elements.impl.MiscElementTextField;
import coffee.axle.suim.clickgui.render.HUDRenderUtils;
import coffee.axle.suim.clickgui.render.StencilUtils;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;

import java.awt.Color;

/**
 * Full HSB color picker element with SB box, hue strip,
 * optional alpha strip, hex text input, and favourite swatches.
 */
public class ColorElement extends GuiElement {

    private final ColorModuleSetting colorSetting;
    private Integer dragging;

    private final MiscElementTextField hexTextField;
    private String hexPrev;
    private final EaseOutQuadAnimation extendAnimation = new EaseOutQuadAnimation(300);

    public ColorElement(
            ModuleButton parent, ColorModuleSetting setting) {
        super(parent, setting, SettingType.COLOR);
        this.colorSetting = setting;
        this.width = parent.getWidth();

        Color colorValue = setting.getValue();
        String hex = colorToHex(colorValue);

        double hexFieldX = FontUtil.INSTANCE.getStringWidth("Hex") + 5.0;
        double hexFieldW = width / 2.0 - hexFieldX
                + (setting.isAllowAlpha() ? 26.5 : 13.5);

        this.hexTextField = MiscElementHelper.createTextField(
                hexFieldX, DEFAULT_HEIGHT * 8 + 5.0,
                hexFieldW, DEFAULT_HEIGHT,
                0, "", "\u00A77#\u00A7r");
        hexTextField.setText(hex);
        hexPrev = hex;
    }

    @Override
    public void update() {
        super.update();
        if (colorSetting.isCollapsible() && !extended) {
            height = DEFAULT_HEIGHT;
        } else {
            height = DEFAULT_HEIGHT * 9 + 5.0;
        }
    }

    @Override
    protected double renderElement() {
        Color colorValue = colorSetting.getValue();
        boolean collapsible = colorSetting.isCollapsible();

        height = extendAnimation.get(
                collapsible
                        ? DEFAULT_HEIGHT
                        : DEFAULT_HEIGHT * 9 + 5.0,
                DEFAULT_HEIGHT * 9 + 5.0,
                !extended);

        FontUtil.INSTANCE.drawString(
                displayName, 0.0, 0.0,
                ColorUtil.textcolor);
        double swatchX = FontUtil.INSTANCE.getStringWidth(displayName) + 5.0;
        HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                swatchX, 0.0, 20.0, 9.0,
                3.0, 1.0,
                colorValue, colorValue.darker());

        if (!(extended || !collapsible)
                && !extendAnimation.isAnimating()) {
            return height;
        }

        if (collapsible) {
            StencilUtils.INSTANCE.write(false, 1);
            HUDRenderUtils.INSTANCE.drawRoundedRect(
                    -3.0, 0.0, width, height, 3.0, Color.WHITE);
            StencilUtils.INSTANCE.erase(true, 1);
        }

        Color hsbMax = getHsbMax(colorValue);
        float hue = colorSetting.getHue();
        float sat = colorSetting.getSaturation();
        float brt = colorSetting.getBrightness();
        float alp = colorSetting.getAlpha();

        // SB box
        HUDRenderUtils.INSTANCE.drawSBBox(
                1.0, DEFAULT_HEIGHT + 1.0,
                width / 2.0 - 2.0,
                DEFAULT_HEIGHT * 7 - 2.0,
                hsbMax.getRGB());
        HUDRenderUtils.INSTANCE.drawRoundedOutline(
                0.0, DEFAULT_HEIGHT,
                width / 2.0, DEFAULT_HEIGHT * 7,
                3.0, 1.0, hsbMax);
        HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                sat * width / 2.0 - 3.0,
                (1 - brt) * DEFAULT_HEIGHT * 7
                        + DEFAULT_HEIGHT - 3.0,
                6.0, 6.0, 6.0, 2.0,
                ColorUtil.INSTANCE.withAlpha(colorValue, 255),
                Color.WHITE);

        // Hue strip
        HUDRenderUtils.INSTANCE.drawRoundedHueBox(
                width / 2.0 + 3.5 + 1.0,
                DEFAULT_HEIGHT + 1.0,
                8.0, DEFAULT_HEIGHT * 7 - 2.0, 3.0, true);
        HUDRenderUtils.INSTANCE.drawRoundedOutline(
                width / 2.0 + 3.5, DEFAULT_HEIGHT,
                10.0, DEFAULT_HEIGHT * 7,
                3.0, 1.0, hsbMax);
        HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                width / 2.0 + 3.5 + 2.0,
                hue * DEFAULT_HEIGHT * 7
                        + DEFAULT_HEIGHT - 3.0,
                6.0, 6.0, 6.0, 2.0,
                ColorUtil.INSTANCE.withAlpha(hsbMax, 255)
                        .darker(),
                Color.WHITE);

        // Alpha strip
        if (colorSetting.isAllowAlpha()) {
            Color solid = ColorUtil.INSTANCE.withAlpha(colorValue, 255);
            HUDRenderUtils.INSTANCE.drawSBBox(
                    width / 2.0 + DEFAULT_HEIGHT + 3.5 + 1.0,
                    DEFAULT_HEIGHT + 1.0,
                    8.0, DEFAULT_HEIGHT * 7 - 2.0,
                    solid.getRGB(), solid.getRGB(),
                    Color.BLACK.getRGB(), Color.BLACK.getRGB());
            HUDRenderUtils.INSTANCE.drawRoundedOutline(
                    width / 2.0 + DEFAULT_HEIGHT + 3.5,
                    DEFAULT_HEIGHT,
                    10.0, DEFAULT_HEIGHT * 7,
                    3.0, 1.0, hsbMax);
            HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                    width / 2.0 + DEFAULT_HEIGHT + 3.5 + 2.0,
                    (1.0 - alp) * DEFAULT_HEIGHT * 7
                            + DEFAULT_HEIGHT - 3.0,
                    6.0, 6.0, 6.0, 2.0,
                    ColorUtil.INSTANCE.withAlpha(Color.WHITE, alp),
                    Color.WHITE);
        }

        // Dragging
        int mxRel = getMouseXRel();
        int myRel = getMouseYRel();
        if (dragging != null) {
            switch (dragging) {
                case 0:
                    colorSetting.setSaturation(
                            MathHelper.clamp_float(
                                    (float) (mxRel
                                            / (width / 2.0)),
                                    0.0f, 1.0f));
                    colorSetting.setBrightness(
                            MathHelper.clamp_float(
                                    (float) (-(myRel
                                            - DEFAULT_HEIGHT * 8)
                                            / (DEFAULT_HEIGHT
                                                    * 7)),
                                    0.0f, 1.0f));
                    break;
                case 1:
                    colorSetting.setHue(
                            MathHelper.clamp_float(
                                    (float) ((myRel
                                            - DEFAULT_HEIGHT
                                            - 2.0)
                                            / (DEFAULT_HEIGHT
                                                    * 7.0
                                                    - 2.0)),
                                    0.0f, 1.0f));
                    break;
                case 2:
                    colorSetting.setAlpha(
                            MathHelper.clamp_float(
                                    1f - (float) ((myRel
                                            - DEFAULT_HEIGHT
                                            - 2.0)
                                            / (DEFAULT_HEIGHT
                                                    * 7.0
                                                    - 2.0)),
                                    0.0f, 1.0f));
                    break;
            }
        }

        // Hex text field sync
        if (dragging != null) {
            hexTextField.setText(
                    colorToHex(colorSetting.getValue()));
            Color hsbMaxDark = ColorUtil.INSTANCE.withAlpha(hsbMax, 255)
                    .darker();
            hexTextField.setOutlineColour(hsbMaxDark);
            hexTextField.setOutlineHoverColour(hsbMaxDark);
            hexPrev = hexTextField.getText();
        }

        FontUtil.INSTANCE.drawString(
                "Hex", 0.0, DEFAULT_HEIGHT * 8 + 7.0,
                ColorUtil.textcolor);
        hexTextField.render(mxRel, myRel);

        // Favourite swatch
        double favX = colorSetting.isAllowAlpha()
                ? width / 2.0 + DEFAULT_HEIGHT * 2 + 3.5
                : width / 2.0 + DEFAULT_HEIGHT + 3.5;
        HUDRenderUtils.INSTANCE.drawRoundedOutline(
                favX, DEFAULT_HEIGHT, 15.0, 15.0,
                3.0, 1.0, colorValue);
        HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                favX + 1.0, DEFAULT_HEIGHT + 1.0,
                13.0, 13.0, 3.0, 1.0,
                colorValue, colorValue);

        for (int i = 0; i < 3; i++) {
            HUDRenderUtils.INSTANCE.drawRoundedOutline(
                    favX,
                    DEFAULT_HEIGHT * 2 + 5.0 + i * 18.0,
                    15.0, 15.0, 3.0, 1.0,
                    Color.WHITE.darker());
        }

        if (collapsible)
            StencilUtils.INSTANCE.dispose();

        return height;
    }

    @Override
    public boolean mouseClicked(int mouseButton) {
        int mx = getMouseX();
        int my = getMouseY();

        if (mouseButton == 0) {
            double swX = FontUtil.INSTANCE
                    .getStringWidth(displayName) + 5.0;
            if (isAreaHovered(mx, my, swX, 0.0, 20.0, 9.0)
                    && colorSetting.isCollapsible()) {
                if (extendAnimation.start(false)) {
                    extended = !extended;
                }
                return true;
            }

            if (!extended && colorSetting.isCollapsible()) {
                return false;
            }

            if (isAreaHovered(mx, my,
                    0.0, DEFAULT_HEIGHT,
                    width / 2.0, DEFAULT_HEIGHT * 7)) {
                dragging = 0;
            } else if (isAreaHovered(mx, my,
                    width / 2.0 + 4.5, DEFAULT_HEIGHT,
                    8.0, DEFAULT_HEIGHT * 7 - 2.0)) {
                dragging = 1;
            } else if (colorSetting.isAllowAlpha()
                    && isAreaHovered(mx, my,
                            width / 2.0 + DEFAULT_HEIGHT + 4.5,
                            DEFAULT_HEIGHT,
                            8.0, DEFAULT_HEIGHT * 7)) {
                dragging = 2;
            } else {
                dragging = null;
            }
        } else if (mouseButton == 1) {
            double swX = FontUtil.INSTANCE
                    .getStringWidth(displayName) + 5.0;
            if (isAreaHovered(mx, my, swX, 0.0, 20.0, 9.0)
                    && colorSetting.isCollapsible()) {
                if (extendAnimation.start(false)) {
                    extended = !extended;
                }
                return true;
            }
        }

        return hexTextField.mouseClicked(
                getMouseXRel(), getMouseYRel(), mouseButton);
    }

    @Override
    public void mouseClickMove(
            int mouseButton, long timeSinceLastClick) {
        hexTextField.mouseClickMove(
                getMouseXRel(), getMouseYRel(),
                mouseButton, timeSinceLastClick);
    }

    @Override
    public void mouseReleased(int state) {
        dragging = null;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (hexTextField.keyTyped(typedChar, keyCode)) {
            if (keyCode == Keyboard.KEY_ESCAPE
                    || keyCode == Keyboard.KEY_NUMPADENTER
                    || keyCode == Keyboard.KEY_RETURN) {
                String hex = completeHexString(
                        hexTextField.getText());
                hexTextField.setText(hex.replace("#", ""));
                hexTextField.setFocused(false);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onGuiClosed() {
        // HSB state already persisted per-setter change
    }

    private String completeHexString(String input) {
        if (input == null || input.isEmpty())
            return hexPrev;
        String clean = input.replace("#", "");
        if (clean.length() != 6 && clean.length() != 8) {
            return hexPrev;
        }

        try {
            int r = Integer.parseInt(
                    clean.substring(0, 2), 16);
            int g = Integer.parseInt(
                    clean.substring(2, 4), 16);
            int b = Integer.parseInt(
                    clean.substring(4, 6), 16);
            float a = clean.length() == 8
                    ? Integer.parseInt(
                            clean.substring(6, 8), 16)
                            / 255f
                    : 1f;

            Color newColor = new Color(
                    r / 255f, g / 255f, b / 255f, a);
            float[] hsb = Color.RGBtoHSB(
                    newColor.getRed(),
                    newColor.getGreen(),
                    newColor.getBlue(), null);

            colorSetting.setHue(hsb[0]);
            colorSetting.setSaturation(hsb[1]);
            colorSetting.setBrightness(hsb[2]);
            colorSetting.setAlpha(a);

            hexPrev = clean;
            return clean;
        } catch (Exception e) {
            return hexPrev;
        }
    }

    private boolean isAreaHovered(
            int mx, int my,
            double x, double y, double w, double h) {
        return mx >= getXAbsolute() + x
                && mx <= getXAbsolute() + x + w
                && my >= getYAbsolute() + y
                && my <= getYAbsolute() + y + h;
    }

    private Color getHsbMax(Color color) {
        float[] hsb = Color.RGBtoHSB(
                color.getRed(), color.getGreen(),
                color.getBlue(), null);
        if (hsb[1] == 0.0f || hsb[2] == 0.0f) {
            hsb[0] = colorSetting.getHue();
        }
        return Color.getHSBColor(hsb[0], 1f, 1f);
    }

    private String colorToHex(Color c) {
        return String.format("%02X%02X%02X%02X",
                c.getRed(), c.getGreen(),
                c.getBlue(), c.getAlpha());
    }
}





