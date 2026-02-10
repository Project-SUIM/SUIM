package coffee.axle.suim.clickgui.util;

import coffee.axle.suim.clickgui.module.GuiModule;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Shared keybind listening utility for all ClickGUI modes.
 * Manages the state machine for binding keys/mouse buttons
 * to modules via middle-click or shift-click.
 *
 * Usage: create one instance per module button, call
 * {@link #handleMouseClick(int)} and {@link #handleKeyTyped(int)}
 * from the button's input handlers.
 */
public class KeybindListenerUtil {

    private final GuiModule module;
    private boolean listening;

    public KeybindListenerUtil(GuiModule module) {
        this.module = module;
    }

    public boolean isListening() {
        return listening;
    }

    public void startListening() {
        listening = true;
    }

    /**
     * Handles mouse clicks during keybind listening.
     *
     * @param mouseButton the mouse button pressed
     * @return true if the click was consumed
     */
    public boolean handleMouseClick(int mouseButton) {
        if (!listening) {
            return false;
        }
        module.setKeybind(-100 + mouseButton);
        listening = false;
        return true;
    }

    /**
     * Handles key presses during keybind listening.
     *
     * @param keyCode the LWJGL key code
     * @return true if the key was consumed
     */
    public boolean handleKeyTyped(int keyCode) {
        if (!listening) {
            return false;
        }

        switch (keyCode) {
            case Keyboard.KEY_ESCAPE:
                module.setKeybind(Keyboard.KEY_NONE);
                listening = false;
                return true;
            case Keyboard.KEY_NUMPADENTER:
            case Keyboard.KEY_RETURN:
                listening = false;
                return true;
            default:
                module.setKeybind(keyCode);
                listening = false;
                return true;
        }
    }

    /**
     * Checks whether a mouse click on a module button should
     * initiate keybind listening.
     *
     * @param mouseButton 0=left, 1=right, 2=middle
     * @param isShiftDown whether shift is held
     * @return true if listening was started
     */
    public boolean shouldStartListening(int mouseButton, boolean isShiftDown) {
        if (mouseButton == 2) {
            listening = true;
            return true;
        }
        if (mouseButton == 0 && isShiftDown) {
            listening = true;
            return true;
        }
        return false;
    }

    /**
     * Gets the display name for the module's current keybind.
     *
     * @return "..." if listening, key/mouse name if bound, "NONE" otherwise
     */
    public String getKeyDisplayName() {
        if (listening) {
            return "...";
        }
        int key = module.getKeybind();
        if (key > 0) {
            String name = Keyboard.getKeyName(key);
            return name != null ? name : "Err";
        }
        if (key < 0) {
            return Mouse.getButtonName(key + 100);
        }
        return "NONE";
    }
}
