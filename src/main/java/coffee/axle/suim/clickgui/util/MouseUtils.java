package coffee.axle.suim.clickgui.util;

import coffee.axle.suim.clickgui.render.HUDRenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

/**
 * Provides mouse-position helpers for the click GUI.
 *
 * @author Aton
 */
public final class MouseUtils {

    /**
     * @deprecated Use static methods directly, e.g. {@code MouseUtils.getMouseX()}.
     */
    @Deprecated
    public static final MouseUtils INSTANCE = new MouseUtils();

    private MouseUtils() {
    }

    public static int getMouseButton() {
        return Mouse.getEventButton();
    }

    public static int getMx() {
        ScaledResolution sr = HUDRenderUtils.INSTANCE.getSr();
        return Mouse.getX() / sr.getScaleFactor();
    }

    public static int getMy() {
        ScaledResolution sr = HUDRenderUtils.INSTANCE.getSr();
        return (Minecraft.getMinecraft().displayHeight - Mouse.getY()) / sr.getScaleFactor();
    }

    public static int getMouseX() {
        double scale = HUDRenderUtils.INSTANCE.getScale();
        ScaledResolution sr = HUDRenderUtils.INSTANCE.getSr();
        return (int) (Mouse.getX() / (scale * sr.getScaleFactor()));
    }

    public static int getMouseY() {
        double scale = HUDRenderUtils.INSTANCE.getScale();
        ScaledResolution sr = HUDRenderUtils.INSTANCE.getSr();
        return (int) (((Minecraft.getMinecraft().displayHeight - Mouse.getY()) / sr.getScaleFactor()) / scale);
    }
}





