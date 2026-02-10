package coffee.axle.suim.clickgui;

import coffee.axle.suim.feature.clickgui.ClickGui;
import net.minecraft.client.Minecraft;

/**
 * Centralized close handler for all ClickGUI screen modes.
 * Encapsulates the common shutdown sequence so new GUI modes
 * only need to call {@link #onClose()} in their onGuiClosed().
 */
public final class ClickGuiCloseHandler {

    private ClickGuiCloseHandler() {
    }

    /**
     * Performs the standard GUI close sequence:
     * disables the ClickGui module and triggers autosave.
     */
    public static void onClose() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.entityRenderer.stopUseShader();

        ClickGui clickGui = ClickGui.getInstance();
        if (clickGui != null) {
            clickGui.onGuiClosed();
        }
    }
}
