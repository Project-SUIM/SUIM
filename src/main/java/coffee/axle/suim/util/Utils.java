package coffee.axle.suim.util;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Basic utils
 * 
 * @author maybsomeday (meow) - ported to SUIM
 */
public class Utils {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static boolean nullCheck() {
        return mc.thePlayer != null && mc.theWorld != null;
    }

    public static long time() {
        return System.currentTimeMillis();
    }

    public static double lerp(double current, double previous, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

    public static float lerp(float current, float previous, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

    // keybinds encode mouse buttons as negative codes, so 0 is "unbound"
    public static boolean isKeyDown(int code) {
        if (code > 0) {
            return code < Keyboard.getKeyCount() && Keyboard.isKeyDown(code);
        }
        int button = 100 + code;
        return code < 0 && button < Mouse.getButtonCount() && Mouse.isButtonDown(button);
    }

    public static Vec3 boxCenter(AxisAlignedBB box) {
        return new Vec3(
                (box.minX + box.maxX) / 2.0,
                (box.minY + box.maxY) / 2.0,
                (box.minZ + box.maxZ) / 2.0);
    }

    // render position, between the last tick and the current one
    public static Vec3 interpolate(Entity entity, float partialTicks) {
        return new Vec3(
                lerp(entity.posX, entity.lastTickPosX, partialTicks),
                lerp(entity.posY, entity.lastTickPosY, partialTicks),
                lerp(entity.posZ, entity.lastTickPosZ, partialTicks));
    }
}





