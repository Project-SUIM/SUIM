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

import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.util.MyauLogger;

/**
 * NoSlow fix via slot switch
 * 
 * @author maybsomeday (meow)
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
            boolean isPlayerBlocking = (boolean) this.getClass()
                    .getMethod(MyauMappings.METHOD_KILL_AURA_IS_PLAYER_BLOCKING, int.class, int.class, char.class)
                    .invoke(this, 0, 0, '\u0000');
            if (!isPlayerBlocking)
                return;

            Class<?> clientClass = Class.forName(MyauMappings.CLASS_MAIN);
            Object moduleManager = clientClass.getField(MyauMappings.FIELD_MODULE_MANAGER).get(null);

            Class<?> moduleManagerClass = Class.forName(MyauMappings.CLASS_MODULE_MANAGER);
            java.util.LinkedHashMap<?, ?> modules = (java.util.LinkedHashMap<?, ?>) moduleManagerClass
                    .getField(MyauMappings.FIELD_MODULES_MAP)
                    .get(moduleManager);

            Object noSlow = modules.get(Class.forName(MyauMappings.CLASS_NO_SLOW));
            if (noSlow == null)
                return;

            boolean enabled = (boolean) noSlow.getClass().getMethod(MyauMappings.METHOD_IS_ENABLED).invoke(noSlow);
            if (!enabled)
                return;
            Minecraft mc = Minecraft.getMinecraft();
            int current = mc.thePlayer.inventory.currentItem;
            int random = new Random().nextInt(9);
            while (random == current)
                random = new Random().nextInt(9);

            Class<?> packetUtil = Class.forName(MyauMappings.CLASS_PACKET_UTIL);
            java.lang.reflect.Method sendPacket = packetUtil.getMethod(MyauMappings.METHOD_SEND_PACKET,
                    Class.forName("net.minecraft.network.Packet"));
            sendPacket.invoke(null, new C09PacketHeldItemChange(random));
            sendPacket.invoke(null, new C09PacketHeldItemChange(current));

        } catch (Exception e) {
            MyauLogger.error("MixinKillAura:NoSlowBypass", e);
        }
    }
}
