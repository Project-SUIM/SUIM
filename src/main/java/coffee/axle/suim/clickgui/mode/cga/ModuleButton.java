package coffee.axle.suim.clickgui.mode.cga;

import coffee.axle.suim.clickgui.element.GuiElement;
import coffee.axle.suim.clickgui.element.impl.*;
import coffee.axle.suim.clickgui.module.GuiModule;
import coffee.axle.suim.clickgui.settings.ModuleSetting;
import coffee.axle.suim.clickgui.settings.SettingType;
import coffee.axle.suim.clickgui.settings.impl.*;
import coffee.axle.suim.clickgui.util.KeybindListenerUtil;
import coffee.axle.suim.ui.animations.impl.ColorAnimation;
import coffee.axle.suim.ui.animations.impl.LinearAnimation;
import coffee.axle.suim.clickgui.util.FontUtil;
import coffee.axle.suim.clickgui.util.MouseUtils;
import coffee.axle.suim.clickgui.render.HUDRenderUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single module row in the ClickGUI.
 * Expands to show setting elements when right-clicked.
 * Port of the Kotlin ModuleButton.kt.
 */
public class ModuleButton {

    private static final ResourceLocation DOTS_ICON = new ResourceLocation("suim", "dots.png");
    private static final double HEIGHT = 20.0;

    private final GuiModule module;
    private final CategoryPanel panel;
    private final List<GuiElement> menuElements = new ArrayList<>();

    private double x;
    private double y;
    private final double width;

    private boolean extended;

    private final ColorAnimation colorAnimation = new ColorAnimation(100);
    private final LinearAnimation scrollAnimation = new LinearAnimation(200);
    private double scrollTarget;
    private double scrollOffset;

    private final KeybindListenerUtil keybindUtil;

    public ModuleButton(GuiModule module, CategoryPanel panel) {
        this.module = module;
        this.panel = panel;
        this.width = panel.getWidth();
        this.keybindUtil = new KeybindListenerUtil(module);
        updateElements();
    }

    public void updateElements() {
        int position = -1;
        for (ModuleSetting<?> setting : module.getSettings()) {
            if (!setting.shouldBeVisible()) {
                menuElements.removeIf(
                        e -> e.getSetting() == setting);
                continue;
            }

            position++;
            boolean exists = menuElements.stream()
                    .anyMatch(e -> e.getSetting() == setting);
            if (exists)
                continue;

            GuiElement element = createElementFor(setting);
            if (element == null)
                continue;

            try {
                menuElements.add(position, element);
            } catch (IndexOutOfBoundsException ignored) {
            }
        }

        // Remove keybind settings from the element list — keybind
        // is displayed directly on the button via KeybindListenerUtil
        menuElements.removeIf(
                e -> e.getSetting().getType() == SettingType.KEY_BIND);
    }

    private GuiElement createElementFor(ModuleSetting<?> setting) {
        switch (setting.getType()) {
            case BOOLEAN:
                return new BooleanElement(this,
                        (BooleanModuleSetting) setting);
            case NUMBER:
                return new SliderElement(this,
                        (NumberModuleSetting) setting);
            case ENUM:
                return new SelectorElement(this,
                        (EnumModuleSetting) setting);
            case STRING:
                return new TextFieldElement(this,
                        (StringModuleSetting) setting);
            case COLOR:
                return new ColorElement(this,
                        (ColorModuleSetting) setting);
            case ACTION:
                return new ActionElement(this,
                        (ActionModuleSetting) setting);
            case KEY_BIND:
                return new KeyBindElement(this,
                        (ModuleSetting<Integer>) setting);
            case DROPDOWN:
                return new DropdownElement(this, setting);
            case HUD:
                return new HudElement(this,
                        (BooleanModuleSetting) setting);
            case ORDER:
                return new OrderElement(this, setting);
            default:
                return null;
        }
    }

    public double draw() {
        GlStateManager.pushMatrix();

        if (!panel.isInModule()) {
            GlStateManager.translate(x, y, 0.0);

            Color outline = CgaTheme.getOutline();
            Color bg = CgaTheme.getBackground();
            Color colour = colorAnimation.get(
                    outline, bg, module.isEnabled());

            HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                    0.0, 0.0, width, HEIGHT,
                    3.0, 1.0, colour, outline);

            double fh = FontUtil.INSTANCE.getFontHeight();
            FontUtil.INSTANCE.drawString(
                    module.getName(), 9.5, 2 + fh / 2.0,
                    CgaTheme.getTextColor());

            if (!menuElements.isEmpty()) {
                HUDRenderUtils.INSTANCE.drawTexturedRect(
                        DOTS_ICON,
                        width - 18.5, 2.5, 16.0, 16.0);
            }

            String keyName = keybindUtil.getKeyDisplayName();
            String keyDisplay = "[" + keyName + "]";
            double kwid = FontUtil.INSTANCE
                    .getStringWidth(keyDisplay);
            FontUtil.INSTANCE.drawString(
                    keyDisplay,
                    width - 25.5 - kwid, 2 + fh / 2.0,
                    CgaTheme.getTextColor());

            GlStateManager.popMatrix();
            return HEIGHT + 5.0;
        }

        if (extended) {
            scrollOffset = scrollAnimation.get(scrollOffset, scrollTarget, false).doubleValue();

            double drawY = scrollOffset;
            for (GuiElement element : menuElements) {
                element.setY(drawY);
                element.update();
                drawY += element.draw();
            }
        }

        GlStateManager.popMatrix();
        return 0.0;
    }

    public boolean scroll(int amount) {
        if (!extended || !panel.isHovered())
            return false;
        double h = getElementsHeight() + 15.0;
        if (h < panel.getHeight()) {
            scrollTarget = 0.0;
            return false;
        }
        scrollTarget = Math.max(
                -h + panel.getHeight(),
                Math.min(0.0,
                        scrollTarget
                                + amount
                                        * CategoryPanel.SCROLL_DISTANCE));
        scrollAnimation.start(true);
        return true;
    }

    public boolean mouseClicked(int mouseButton) {
        if (keybindUtil.handleMouseClick(mouseButton)) {
            return true;
        }

        if (isButtonHovered() && !panel.isInModule()) {
            if (keybindUtil.shouldStartListening(mouseButton, GuiScreen.isShiftKeyDown())) {
                return true;
            }

            switch (mouseButton) {
                case 0:
                    module.toggle();
                    colorAnimation.start();
                    return true;

                case 1:
                    if (!menuElements.isEmpty()) {
                        extended = true;
                        for (GuiElement e : menuElements) {
                            e.setListening(false);
                        }
                        return true;
                    }
                    return false;
            }
        }

        if (isMouseUnderButton()) {
            for (int i = menuElements.size() - 1; i >= 0; i--) {
                if (menuElements.get(i)
                        .mouseClicked(mouseButton)) {
                    updateElements();
                    return true;
                }
            }
        }

        return false;
    }

    public void mouseReleased(int state) {
        if (extended) {
            for (int i = menuElements.size() - 1; i >= 0; i--) {
                menuElements.get(i).mouseReleased(state);
            }
        }
    }

    public void mouseClickMove(
            int mouseButton, long timeSinceLastClick) {
        if (extended) {
            for (int i = menuElements.size() - 1; i >= 0; i--) {
                menuElements.get(i).mouseClickMove(
                        mouseButton, timeSinceLastClick);
            }
        }
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        if (extended) {
            for (int i = menuElements.size() - 1; i >= 0; i--) {
                if (menuElements.get(i)
                        .keyTyped(typedChar, keyCode)) {
                    return true;
                }
            }
            if (keyCode == Keyboard.KEY_ESCAPE) {
                extended = false;
                return true;
            }
            boolean anyListening = menuElements.stream()
                    .anyMatch(GuiElement::isListening);
            if (!anyListening) {
                if (keyCode == Keyboard.KEY_UP)
                    scroll(1);
                if (keyCode == Keyboard.KEY_DOWN)
                    scroll(-1);
            }
        }

        return keybindUtil.handleKeyTyped(keyCode);
    }

    public void onGuiClosed() {
        for (int i = menuElements.size() - 1; i >= 0; i--) {
            menuElements.get(i).onGuiClosed();
        }
    }

    private double getElementsHeight() {
        double total = 0;
        for (GuiElement e : menuElements) {
            total += e.getElementHeight() + 5.0;
        }
        return total;
    }

    private boolean isButtonHovered() {
        if (extended && panel.isInModule())
            return false;
        int mx = MouseUtils.INSTANCE.getMouseX();
        int my = MouseUtils.INSTANCE.getMouseY();
        double xa = getXAbsolute();
        double ya = getYAbsolute();
        return mx >= xa && mx <= xa + width
                && my >= ya && my <= ya + HEIGHT;
    }

    private boolean isMouseUnderButton() {
        if (!extended)
            return false;
        int mx = MouseUtils.INSTANCE.getMouseX();
        int my = MouseUtils.INSTANCE.getMouseY();
        return mx >= panel.getX()
                && mx <= panel.getX() + width
                && my > getYAbsolute();
    }

    // Accessors
    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return HEIGHT;
    }

    public boolean isExtended() {
        return extended;
    }

    public void setExtended(boolean extended) {
        this.extended = extended;
    }

    public CategoryPanel getPanel() {
        return panel;
    }

    public GuiModule getModule() {
        return module;
    }

    public KeybindListenerUtil getKeybindUtil() {
        return keybindUtil;
    }

    public double getXAbsolute() {
        return x + panel.getX();
    }

    public double getYAbsolute() {
        return y + panel.getY();
    }
}
