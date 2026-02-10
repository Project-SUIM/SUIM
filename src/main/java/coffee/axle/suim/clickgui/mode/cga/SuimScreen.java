package coffee.axle.suim.clickgui.mode.cga;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Base screen for SUIM GUIs. Forces 2.0x scale factor for consistent rendering.
 * Port of the Kotlin Screen.kt class.
 */
public abstract class SuimScreen extends GuiScreen {

    public static final double CLICK_GUI_SCALE = 2.0;

    protected static final Minecraft MC = Minecraft.getMinecraft();

    protected ScaledResolution sr;
    protected double scale;
    private final boolean forceScale;

    protected SuimScreen() {
        this(true);
    }

    protected SuimScreen(boolean forceScale) {
        this.forceScale = forceScale;
        this.sr = new ScaledResolution(MC);
        this.scale = CLICK_GUI_SCALE / sr.getScaleFactor();
    }

    @Override
    public final void initGui() {
        sr = new ScaledResolution(mc);
        scale = CLICK_GUI_SCALE / sr.getScaleFactor();
        onInit();
    }

    protected void onInit() {
    }

    @Override
    public final void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (forceScale) {
            GlStateManager.pushMatrix();
            int prevScale = mc.gameSettings.guiScale;
            mc.gameSettings.guiScale = 2;
            GlStateManager.scale(scale, scale, scale);
            draw();
            GlStateManager.popMatrix();
            mc.gameSettings.guiScale = prevScale;
        } else {
            draw();
        }
    }

    protected void draw() {
    }

    @Override
    public final void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int scrollEvent = Mouse.getEventDWheel();
        if (scrollEvent != 0) {
            int amount = Math.max(-1, Math.min(1, scrollEvent));
            if (isShiftKeyDown())
                amount *= 7;
            onScroll(amount);
        }
    }

    protected void onScroll(int amount) {
    }

    @Override
    public final void mouseClicked(int mouseX, int mouseY, int mouseButton)
            throws java.io.IOException {
        onMouseClick(mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    protected void onMouseClick(int mouseButton) {
    }

    @Override
    public final void mouseClickMove(int mouseX, int mouseY,
            int clickedMouseButton, long timeSinceLastClick) {
        onMouseClickMove(clickedMouseButton, timeSinceLastClick);
        super.mouseClickMove(
                mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    protected void onMouseClickMove(int mouseButton, long timeSinceLastClick) {
    }

    @Override
    public final void mouseReleased(int mouseX, int mouseY, int state) {
        onMouseRelease(state);
        super.mouseReleased(mouseX, mouseY, state);
    }

    protected void onMouseRelease(int state) {
    }

    @Override
    public void keyTyped(char typedChar, int keyCode)
            throws java.io.IOException {
        handleKeyScroll(keyCode);
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public final boolean doesGuiPauseGame() {
        return false;
    }

    private void handleKeyScroll(int keyCode) {
        if (keyCode == Keyboard.KEY_UP)
            onScroll(1);
        else if (keyCode == Keyboard.KEY_DOWN)
            onScroll(-1);
    }

    protected double getX(double guiWidth) {
        return sr.getScaledWidth() / (2.0 * scale) - guiWidth / 2.0;
    }

    protected double getY(double guiHeight) {
        return sr.getScaledHeight() / (2.0 * scale) - guiHeight / 2.0;
    }

    /**
     * Scaled mouse X coordinate for the forced GUI scale.
     */
    public static int getMouseX() {
        ScaledResolution sr = new ScaledResolution(MC);
        double s = CLICK_GUI_SCALE / sr.getScaleFactor();
        return (int) (Mouse.getX() / (s * sr.getScaleFactor()));
    }

    /**
     * Scaled mouse Y coordinate for the forced GUI scale.
     */
    public static int getMouseY() {
        ScaledResolution sr = new ScaledResolution(MC);
        double s = CLICK_GUI_SCALE / sr.getScaleFactor();
        return (int) (((MC.displayHeight - Mouse.getY())
                / sr.getScaleFactor()) / s);
    }
}






