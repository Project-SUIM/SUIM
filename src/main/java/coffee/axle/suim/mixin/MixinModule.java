package coffee.axle.suim.mixin;

import coffee.axle.suim.feature.world.BedwarsOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gates myau module functionality for bedwars-only. Every native module
 * handler early-returns on !isEnabled() and none of them override it, so
 * faking a false return from the base myau.V.X turns a module off without
 * touching its protected bytecode. The enabled field the clickgui reads is
 * separate, so the module still shows as on.
 */
@Pseudo
@Mixin(targets = "myau.V", remap = false)
public class MixinModule {

    @Inject(method = "X", at = @At("HEAD"), cancellable = true, remap = false)
    private void suim$bedwarsGate(CallbackInfoReturnable<Boolean> cir) {
        if (BedwarsOnly.isSuppressed((Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
