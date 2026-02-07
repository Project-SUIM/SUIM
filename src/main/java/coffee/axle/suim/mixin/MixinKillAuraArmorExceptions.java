package coffee.axle.suim.mixin;

import coffee.axle.suim.features.ArmorExceptions;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to inject armor-exceptions check into KillAura's isValidTarget method
 */
@Pseudo
@Mixin(targets = "myau.Kf", remap = false)
public class MixinKillAuraArmorExceptions {
    private static final long RANDOM_LONG = 0L;
    private static boolean loggedOnce = false;

    @Inject(method = "v(Lnet/minecraft/entity/EntityLivingBase;J)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void onIsValidTarget(EntityLivingBase entityLivingBase, long param, CallbackInfoReturnable<Boolean> cir) {
        try {
            ArmorExceptions armorExceptions = ArmorExceptions.getInstance();

            if (armorExceptions == null) {
                if (!loggedOnce) {
                    loggedOnce = true;
                }
                return;
            }

            if (!loggedOnce) {
                loggedOnce = true;
            }

            if (armorExceptions.shouldExcludeEntity(entityLivingBase)) {
                cir.setReturnValue(false);
                cir.cancel();
            }

        } catch (Exception e) {

        }
    }
}
