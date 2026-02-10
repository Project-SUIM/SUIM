package coffee.axle.suim.mixin;

import coffee.axle.suim.feature.world.FastPlaceExtras;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to hook into FastPlace's canPlace method
 * Makes canPlace return false when skip conditions are met
 */
@Pseudo
@Mixin(targets = "myau.m5", remap = false)
public class MixinFastPlace {

    @Inject(method = "b(III)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void onCanPlace(int param1, int param2, int param3, CallbackInfoReturnable<Boolean> cir) {
        if (FastPlaceExtras.shouldSkipFastPlace()) {
            cir.setReturnValue(false);
        }
    }
}





