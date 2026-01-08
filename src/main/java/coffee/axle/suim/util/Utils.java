package coffee.axle.suim.util;

import net.minecraft.client.Minecraft;

/**
 * Basic utils
 * 
 * @author maybsomeday (meow) - ported to d'Myau
 */
public class Utils {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static boolean nullCheck() {
        return mc.thePlayer != null && mc.theWorld != null;
    }

    public static long time() {
        return System.currentTimeMillis();
    }
}
