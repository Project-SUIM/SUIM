package coffee.axle.suim.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

/**
 * NoSlow fix via slot switch
 * 
 * @author maybsomeday (meow) - ported 1:1 to d'Myau
 */
@Pseudo
@Mixin(targets = "myau.Kf", remap = false)
public class MixinKillAura {

    @Shadow
    private int y;

    @Inject(method = "r(J)V", at = @At("HEAD"), remap = false, require = 0)
    private void dMyauNoSlowBypass(long param, CallbackInfo ci) {
        try {
            if (this.y != 1)
                return;
            boolean isPlayerBlocking = (boolean) this.getClass().getMethod("Q", int.class, int.class, char.class)
                    .invoke(this, 0, 0, '\u0000');
            if (!isPlayerBlocking)
                return;

            Class<?> clientClass = Class.forName("myau.X");
            Object moduleManager = clientClass.getField("j").get(null);

            Class<?> moduleManagerClass = Class.forName("myau.mJ");
            java.util.LinkedHashMap<?, ?> modules = (java.util.LinkedHashMap<?, ?>) moduleManagerClass.getField("E")
                    .get(moduleManager);

            Object noSlow = modules.get(Class.forName("myau.Kq"));
            if (noSlow == null)
                return;

            boolean enabled = (boolean) noSlow.getClass().getMethod("P").invoke(noSlow);
            if (!enabled)
                return;
            Minecraft mc = Minecraft.getMinecraft();
            int current = mc.thePlayer.inventory.currentItem;
            int random = new Random().nextInt(9);
            while (random == current)
                random = new Random().nextInt(9);

            Class<?> packetUtil = Class.forName("myau.k");
            java.lang.reflect.Method sendPacket = packetUtil.getMethod("E",
                    Class.forName("net.minecraft.network.Packet"));
            sendPacket.invoke(null, new C09PacketHeldItemChange(random));
            sendPacket.invoke(null, new C09PacketHeldItemChange(current));

        } catch (Exception ignored) {
        }
    }
}
