package coffee.axle.suim.mixin;

import coffee.axle.suim.feature.combat.ArmorExceptions;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Inject armor-exceptions check into KillAura's isValidTarget.
 * isValidTarget is B(long, EntityLivingBase)Z in 260729 (was v(EntityLivingBase, long)Z in 260317).
 */
@Pseudo
@Mixin(targets = "myau.Jh", remap = false)
public class MixinKillAuraArmorExceptions {

    @Inject(method = "B(JLnet/minecraft/entity/EntityLivingBase;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void onIsValidTarget(long zkm, EntityLivingBase entityLivingBase, CallbackInfoReturnable<Boolean> cir) {
        ArmorExceptions armorExceptions = ArmorExceptions.getInstance();
        if (armorExceptions != null && armorExceptions.shouldExcludeEntity(entityLivingBase)) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}





