package coffee.axle.suim.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.util.MyauLogger;

/**
 * Mixin to prevent sword NoSlow from working when player manually blocks.
 * Only allows NoSlow when KillAura is autoblocking.
 * 
 * @author maybsomeday (meow) - ported 1:1 to d'Myau
 */
@SuppressWarnings("unused")

@Pseudo
@Mixin(targets = "myau.Kq", remap = false)
public class MixinNoSlow {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long RANDOM_LONG = 0L;

    @Inject(method = "r(J)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void onIsSwordActive(long param, CallbackInfoReturnable<Boolean> cir) {
        try {
            Class<?> myauXClass = Class.forName(MyauMappings.CLASS_MAIN);
            Field moduleManagerField = myauXClass.getDeclaredField(MyauMappings.FIELD_MODULE_MANAGER);
            moduleManagerField.setAccessible(true);
            Object moduleManager = moduleManagerField.get(null);

            Method getModuleMethod = moduleManager.getClass().getMethod(MyauMappings.METHOD_GET_MODULE, String.class);
            Object killAura = getModuleMethod.invoke(moduleManager, "KillAura");

            if (killAura == null) {
                if (mc.thePlayer.isBlocking()) {
                    cir.setReturnValue(false);
                    cir.cancel();
                }
                return;
            }

            Method isEnabledMethod = killAura.getClass().getMethod(MyauMappings.METHOD_IS_ENABLED);
            boolean isEnabled = (Boolean) isEnabledMethod.invoke(killAura);

            if (!isEnabled) {
                if (mc.thePlayer.isBlocking()) {
                    cir.setReturnValue(false);
                    cir.cancel();
                }
                return;
            }

            Field autoBlockField = killAura.getClass().getDeclaredField(MyauMappings.FIELD_KILL_AURA_AUTOBLOCK);
            autoBlockField.setAccessible(true);
            Object autoBlockMode = autoBlockField.get(killAura);

            Method getValueMethod = autoBlockMode.getClass().getMethod(MyauMappings.METHOD_PROPERTY_GET_VALUE);
            Integer autoBlockValue = (Integer) getValueMethod.invoke(autoBlockMode);

            boolean autoBlockIsNone = autoBlockValue.equals(0);

            Field isBlockingField = killAura.getClass().getDeclaredField(MyauMappings.FIELD_KILL_AURA_IS_BLOCKING);
            isBlockingField.setAccessible(true);
            boolean kaIsBlocking = isBlockingField.getBoolean(killAura);

            if ((autoBlockIsNone || !kaIsBlocking) && mc.thePlayer.isBlocking()) {
                cir.setReturnValue(false);
                cir.cancel();
            }

        } catch (Exception e) {
            MyauLogger.error("MixinNoSlow", e);
        }
    }
}
