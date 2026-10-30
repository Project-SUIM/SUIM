package coffee.axle.suim.feature.render;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;

import coffee.axle.suim.hooks.FreelookHooks;
import coffee.axle.suim.property.Setting;
import coffee.axle.suim.util.Utils;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import static coffee.axle.suim.ProjectSUIM.mc;

/**
 * Freelook
 * Partly referenced off Blowsy's mod
 */
public class Freelook extends Feature {


    @Setting(min = 10, max = 150) private float fov = 70f;
    @Setting private String keybind = "LMENU";
    @Setting(name = "require-hold") private boolean hold = false;
    @Setting private boolean invertPitch = false;
    @Setting private boolean lockPitch = true;
    @Setting private boolean yaw = true;
    @Setting private boolean pitch = true;
    @Setting private boolean customFov = false;

    public static boolean perspectiveToggled = false;
    public static boolean prevKeyState = false;
    public static float cameraYaw = 0f;
    public static float cameraPitch = 0f;

    private int previousPerspective;
    private float lastFov;

    public Freelook() {
        super("Freelook", GuiCategory.RENDER);
    }

    @Override
    protected void init() throws Exception {
        FreelookHooks.instance = this;
        lastFov = mc.gameSettings.fovSetting;
    }

    @Override
    protected void onDisable() {
        safeReset();
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START)
            return;
        if (mc.thePlayer == null)
            return;
        if (mc.currentScreen != null)
            return;

        if (!isEnabled()) {
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

        boolean down = Utils.isKeyDown(key);

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
        return hold;
    }

    private boolean getCustomFovSafe() {
        return customFov;
    }

    private int getKeybind() {
        return Keyboard.getKeyIndex(keybind.toUpperCase());
    }

    public boolean getHold() {
        return hold;
    }

    public boolean getYaw() {
        return yaw;
    }

    public boolean getPitch() {
        return pitch;
    }

    public boolean getInvertPitch() {
        return invertPitch;
    }

    public boolean getLockPitch() {
        return lockPitch;
    }

    public boolean getCustomFov() {
        return customFov;
    }

    public float getFov() {
        return fov;
    }

    @Override
    public void disable() {
        safeReset();
    }
}





