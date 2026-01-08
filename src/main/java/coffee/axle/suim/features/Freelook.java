package coffee.axle.suim.features;

import coffee.axle.suim.hooks.FreelookHooks;
import coffee.axle.suim.hooks.MyauHook;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * Freelook
 * Partly referenced off Blowsy's mod
 */
public class Freelook implements Feature {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private final MyauHook hook = MyauHook.getInstance();

    private Object moduleInstance;

    private Object holdProperty;
    private Object invertPitchProperty;
    private Object lockPitchProperty;
    private Object yawProperty;
    private Object pitchProperty;
    private Object customFovProperty;
    private Object fovProperty;
    private Object keybindProperty;

    public static boolean perspectiveToggled = false;
    public static boolean prevKeyState = false;
    public static float cameraYaw = 0f;
    public static float cameraPitch = 0f;

    private int previousPerspective;
    private float lastFov;

    @Override
    public String getName() {
        return "Freelook";
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");

            FreelookHooks.instance = this;

            moduleInstance = hook.createModule(getName());
            hook.injectModule(moduleInstance, getClass());

            holdProperty = hook.createBooleanProperty("require-hold", false);
            keybindProperty = hook.createStringProperty("keybind", "LMENU");
            invertPitchProperty = hook.createBooleanProperty("invert-pitch", false);
            lockPitchProperty = hook.createBooleanProperty("lock-pitch", true);
            yawProperty = hook.createBooleanProperty("yaw", true);
            pitchProperty = hook.createBooleanProperty("pitch", true);
            customFovProperty = hook.createBooleanProperty("custom-fov", false);
            fovProperty = hook.createFloatProperty("fov", 70f, 10f, 150f);

            hook.registerProperties(
                    moduleInstance,
                    holdProperty,
                    keybindProperty,
                    invertPitchProperty,
                    lockPitchProperty,
                    yawProperty,
                    pitchProperty,
                    customFovProperty,
                    fovProperty);

            hook.reloadModuleCommand();

            lastFov = mc.gameSettings.fovSetting;
            MinecraftForge.EVENT_BUS.register(this);
            hook.registerModuleCallbacks(
                    moduleInstance,
                    () -> onModuleEnabled(),
                    () -> onModuleDisabled());
            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;

        } catch (Exception t) {
            MyauLogger.error("FEATURE_FAIL", t);
            return false;
        }
    }

    private void onModuleDisabled() {
        safeReset();
    }

    private void onModuleEnabled() {
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START)
            return;
        if (mc.thePlayer == null)
            return;
        if (mc.currentScreen != null)
            return;

        if (!hook.isModuleEnabled(moduleInstance)) {
            if (perspectiveToggled)
                safeReset();
            return;
        }

        int key = getKeybind();

        if (key == 0) {
            if (perspectiveToggled)
                safeReset();
            return;
        }

        boolean down = Keyboard.isKeyDown(key);

        if (down != prevKeyState) {
            handleToggle(down);
            prevKeyState = down;
        }
    }

    private void handleToggle(boolean pressed) {
        if (pressed) {
            cameraYaw = mc.thePlayer.rotationYaw;
            cameraPitch = mc.thePlayer.rotationPitch;

            if (!perspectiveToggled)
                enter();
            else
                safeReset();

        } else if (getHoldSafe()) {
            safeReset();
        }
    }

    private void enter() {
        perspectiveToggled = true;

        previousPerspective = mc.gameSettings.thirdPersonView;
        mc.gameSettings.thirdPersonView = 1;

        lastFov = mc.gameSettings.fovSetting;
    }

    private void safeReset() {
        try {
            perspectiveToggled = false;
            mc.gameSettings.thirdPersonView = previousPerspective;

            if (getHoldSafe() || getCustomFovSafe()) {
                mc.gameSettings.fovSetting = lastFov;
            }

        } catch (Throwable ignored) {
        }
    }

    private boolean getHoldSafe() {
        try {
            return (Boolean) hook.getPropertyValue(holdProperty);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean getCustomFovSafe() {
        try {
            return (Boolean) hook.getPropertyValue(customFovProperty);
        } catch (Throwable t) {
            return false;
        }
    }

    private int getKeybind() {
        try {
            String keyName = (String) hook.getPropertyValue(keybindProperty);
            return Keyboard.getKeyIndex(keyName.toUpperCase());
        } catch (Throwable t) {
            return Keyboard.KEY_LMENU;
        }
    }

    public boolean getHold() throws Exception {
        return (Boolean) hook.getPropertyValue(holdProperty);
    }

    public boolean getYaw() throws Exception {
        return (Boolean) hook.getPropertyValue(yawProperty);
    }

    public boolean getPitch() throws Exception {
        return (Boolean) hook.getPropertyValue(pitchProperty);
    }

    public boolean getInvertPitch() throws Exception {
        return (Boolean) hook.getPropertyValue(invertPitchProperty);
    }

    public boolean getLockPitch() throws Exception {
        return (Boolean) hook.getPropertyValue(lockPitchProperty);
    }

    public boolean getCustomFov() throws Exception {
        return (Boolean) hook.getPropertyValue(customFovProperty);
    }

    public float getFov() throws Exception {
        return (Float) hook.getPropertyValue(fovProperty);
    }

    @Override
    public void disable() {
        safeReset();
    }
}
