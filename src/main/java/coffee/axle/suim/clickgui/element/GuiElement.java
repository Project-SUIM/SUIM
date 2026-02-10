package coffee.axle.suim.clickgui.element;

import coffee.axle.suim.clickgui.mode.cga.ModuleButton;
import coffee.axle.suim.clickgui.settings.ModuleSetting;
import coffee.axle.suim.clickgui.settings.SettingType;
import coffee.axle.suim.clickgui.util.ColorUtil;
import coffee.axle.suim.clickgui.util.FontUtil;
import coffee.axle.suim.clickgui.util.MouseUtils;
import coffee.axle.suim.clickgui.render.HUDRenderUtils;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Base class for all ClickGUI setting elements.
 * Port of the Kotlin Element.kt class, backed by ModuleSetting.
 */
public abstract class GuiElement {

    public static final double DEFAULT_HEIGHT = 13.0;

    protected final ModuleButton parent;
    protected final ModuleSetting<?> setting;
    protected final SettingType type;

    protected double x;
    protected double y;
    protected double width;
    protected double height;
    protected String displayName;
    protected boolean extended;
    protected boolean listening;

    private Long hoverStartTime;

    protected GuiElement(
            ModuleButton parent,
            ModuleSetting<?> setting,
            SettingType type) {
        this.parent = parent;
        this.setting = setting;
        this.type = type;
        this.displayName = setting.getName();
        this.width = parent.getWidth() * 0.836;

        switch (type) {
            case STRING:
                this.height = 25.0;
                break;
            case NUMBER:
                this.height = 18.0;
                break;
            case BOOLEAN:
                this.height = 11.0;
                break;
            case KEY_BIND:
                this.height = 11.0;
                break;
            case HUD:
                this.height = setting.getName().isEmpty() ? -5.0 : 11.0;
                break;
            default:
                this.height = DEFAULT_HEIGHT;
                break;
        }
    }

    protected double getXAbsolute() {
        return this.x + this.parent.getPanel().getX();
    }

    protected double getYAbsolute() {
        return this.y + this.parent.getPanel().getY();
    }

    protected int getMouseX() {
        return MouseUtils.INSTANCE.getMouseX();
    }

    protected int getMouseY() {
        return MouseUtils.INSTANCE.getMouseY();
    }

    protected int getMouseXRel() {
        return getMouseX() - (int) getXAbsolute();
    }

    protected int getMouseYRel() {
        return getMouseY() - (int) getYAbsolute();
    }

    public void update() {
        this.displayName = setting.getName();
    }

    public double draw() {
        GlStateManager.pushMatrix();
        GlStateManager.translate(this.x, this.y, 0.0);

        double elementLength = renderElement();

        int mx = getMouseX();
        int my = getMouseY();

        if (isHoveredForTooltip(mx, my)
                && setting.getDescription() != null
                && parent.isExtended()) {
            long now = System.currentTimeMillis();
            if (hoverStartTime == null)
                hoverStartTime = now;

            if (now - hoverStartTime >= 1000) {
                GlStateManager.pushAttrib();
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                List<String> lines = FontUtil.INSTANCE.wrapText(
                        setting.getDescription(), 150.0);
                HUDRenderUtils.INSTANCE.drawHoveringText(
                        lines,
                        mx - (int) getXAbsolute(),
                        my - (int) getYAbsolute());
                GlStateManager.popAttrib();
            }
        } else {
            hoverStartTime = null;
        }

        GlStateManager.popMatrix();
        return elementLength + 5.0;
    }

    protected double renderElement() {
        return this.height;
    }

    public boolean mouseClicked(int mouseButton) {
        return isHovered();
    }

    public void mouseReleased(int state) {
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }

    public void mouseClickMove(int mouseButton, long timeSinceLastClick) {
    }

    public void onGuiClosed() {
    }

    public double getElementHeight() {
        return height;
    }

    protected boolean isHovered() {
        int mx = getMouseX();
        int my = getMouseY();
        return mx >= getXAbsolute() && mx <= getXAbsolute() + width
                && my >= getYAbsolute() && my <= getYAbsolute() + height;
    }

    private boolean isHoveredForTooltip(int mx, int my) {
        return mx >= getXAbsolute() && mx <= getXAbsolute() + width
                && my >= getYAbsolute()
                && my <= getYAbsolute() + DEFAULT_HEIGHT;
    }

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

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isExtended() {
        return extended;
    }

    public void setExtended(boolean extended) {
        this.extended = extended;
    }

    public boolean isListening() {
        return listening;
    }

    public void setListening(boolean listening) {
        this.listening = listening;
    }

    public ModuleSetting<?> getSetting() {
        return setting;
    }

    public SettingType getType() {
        return type;
    }
}





