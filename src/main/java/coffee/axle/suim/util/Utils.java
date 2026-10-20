package coffee.axle.suim.util;

import net.minecraft.client.Minecraft;

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
}





