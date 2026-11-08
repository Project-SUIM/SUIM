package coffee.axle.suim.mixin;

import coffee.axle.suim.feature.SuffixRegistry;
import coffee.axle.suim.feature.world.BedwarsOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
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
 * <p>r() is the HUD suffix source. It cannot be a cancellable @Inject: mixin
 * 0.7.11 only emits the CHECKCAST when the return type sort is OBJECT, not
 * ARRAY (upstream later changed it to {@code >= Type.ARRAY}), so cancelling a
 * String[] returning method leaves an areturn of Object in the frame and the
 * class dies with a VerifyError. Hence @Overwrite, which builds no
 * CallbackInfoReturnable at all.</p>
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

    /**
     * @author axlecoffee
     * @reason cancellable @Inject is broken for array returns on mixin 0.7.11,
     *         see the class javadoc. Falls back to the original default
     *         ({@code return new String[0]}) when nothing is registered.
     */
    @Overwrite(remap = false)
    public String[] r() {
        String[] suffix = SuffixRegistry.getSuffix(this);
        return suffix != null ? suffix : new String[0];
    }
}