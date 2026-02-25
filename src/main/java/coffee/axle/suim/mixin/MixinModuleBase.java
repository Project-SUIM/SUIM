package coffee.axle.suim.mixin;

import coffee.axle.suim.feature.SuffixRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks {@code Module.getSuffix()} on the base Module class ({@code myau.mD})
 * to allow SUIM features to provide HUD arraylist suffixes.
 * <p>
 * Delegates to {@link SuffixRegistry}: if a suffix is registered for
 * {@code this} module instance, return it; otherwise fall through to
 * the original (which returns {@code new String[0]}).
 *
 * @author axle.coffee
 */
@Pseudo
@Mixin(targets = "myau.mD", remap = false)
public class MixinModuleBase {

    /**
     * Intercept getSuffix — obfuscated as E(J)[Ljava/lang/String;
     * The long param is a ZKM dummy.
     */
    @Inject(method = "E(J)[Ljava/lang/String;", at = @At("HEAD"), remap = false, require = 0, cancellable = true)
    private void suim$onGetSuffix(long unused, CallbackInfoReturnable<String[]> cir) {
        String[] suffix = SuffixRegistry.getSuffix(this);
        if (suffix != null) {
            cir.setReturnValue(suffix);
        }
    }
}
