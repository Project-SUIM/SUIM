package coffee.axle.suim.mixin;

import coffee.axle.suim.feature.SuffixRegistry;
import coffee.axle.suim.feature.world.BedwarsOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Base-module hooks. myau.V has no natives and no anti-leak hashes, so the
 * superclass is safe to patch even though most concrete modules are not.
 *
 * <p>X() is the bedwars gate: every native module handler early-returns on
 * !isEnabled() and none override it, so faking a false return turns a module
 * off without touching its protected bytecode. The enabled field the clickgui
 * reads is separate, so the module still shows as on.</p>
 *
 * <p>r() is the HUD suffix source, see {@link #suim$suffix}.</p>
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

    @Inject(method = "r()[Ljava/lang/String;", at = @At("HEAD"), cancellable = true, remap = false)
    private void suim$suffix(CallbackInfoReturnable<String[]> cir) {
        String[] suffix = SuffixRegistry.getSuffix((Object) this);
        if (suffix != null) {
            cir.setReturnValue(suffix);
        }
    }
}
