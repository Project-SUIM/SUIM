package coffee.axle.suim.hooks;

import coffee.axle.suim.feature.render.Freelook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/**
 * ASM's hook manager for Freelook
 * Because couldn't do all this inside clienthook for some reason
 */
public class FreelookHooks {

    public static Freelook instance;

    public static boolean overrideMouse(Minecraft minecraft) {
        if (minecraft.inGameHasFocus) {
            if (!Freelook.perspectiveToggled) {
                return true;
            }

            minecraft.mouseHelper.mouseXYChange();
            float sens = minecraft.gameSettings.mouseSensitivity * 0.6F + 0.2F;
            float factor = sens * sens * sens * 8.0F;

            try {
                if (instance == null || instance.getYaw()) {
                    float dx = minecraft.mouseHelper.deltaX * factor;
                    Freelook.cameraYaw += dx * 0.15F;
                }

                if (instance == null || instance.getPitch()) {
                    float dy = minecraft.mouseHelper.deltaY * factor;
                    if (instance != null && instance.getInvertPitch())
                        dy = -dy;

                    Freelook.cameraPitch += dy * 0.15F;

                    if (instance == null || instance.getLockPitch()) {
                        if (Freelook.cameraPitch > 90f)
                            Freelook.cameraPitch = 90f;
                        else if (Freelook.cameraPitch < -90f)
                            Freelook.cameraPitch = -90f;
                    }
                }

                if (instance != null && instance.getCustomFov()) {
                    minecraft.gameSettings.fovSetting = instance.getFov();
                }
            } catch (Throwable ignored) {
            }

            minecraft.renderGlobal.setDisplayListEntitiesDirty();
        }

        return false;
    }

    public static float modifyYaw(EntityPlayer e) {
        return Freelook.perspectiveToggled ? Freelook.cameraYaw : e.rotationYaw;
    }

    public static float modifyPitch(EntityPlayer e) {
        return Freelook.perspectiveToggled ? Freelook.cameraPitch : e.rotationPitch;
    }

    public static float rotationYawModifier(Entity e) {
        return Freelook.perspectiveToggled ? Freelook.cameraYaw : e.rotationYaw;
    }

    public static float prevRotationYawModifier(Entity e) {
        return Freelook.perspectiveToggled ? Freelook.cameraYaw : e.prevRotationYaw;
    }

    public static float rotationPitchModifier(Entity e) {
        return Freelook.perspectiveToggled ? Freelook.cameraPitch : e.rotationPitch;
    }

    public static float prevRotationPitchModifier(Entity e) {
        return Freelook.perspectiveToggled ? Freelook.cameraPitch : e.prevRotationPitch;
    }

    public static void modifyThirdPerson(GameSettings gs, int value) {
        if (Freelook.perspectiveToggled) {
            // Do nothing - prevent F5 changes during freelook
        } else {
            gs.thirdPersonView = value;
        }
    }

    public static void playerViewXModifier(RenderManager rm, float val) {
        rm.playerViewX = Freelook.perspectiveToggled ? Freelook.cameraPitch : val;
    }

    public static void playerViewYModifier(RenderManager rm, float val) {
        rm.playerViewY = Freelook.perspectiveToggled ? Freelook.cameraYaw : val;
    }
}





