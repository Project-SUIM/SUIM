package coffee.axle.suim.mixin;

import coffee.axle.suim.feature.SuffixRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Hooks {@code Module.getSuffix()} on the base Module class ({@code myau.mD})
 * to allow SUIM features to provide HUD arraylist suffixes.
 * <p>
 * Delegates to {@link SuffixRegistry}: if a suffix is registered for
 * {@code this} module instance, return it; otherwise fall through to
 * the original behavior (returns {@code new String[0]}).
 * <p>
 * Uses {@code @Overwrite} instead of {@code @Inject(cancellable = true)}
 * to work around a Mixin 0.7.11 bytecode generation bug where
 * {@code CallbackInfoReturnable.getReturnValue()} returns {@code Object}
 * without a {@code checkcast} to {@code String[]}, causing a
 * {@code VerifyError: Bad return type} at runtime.
 *
 * @author axle.coffee
 */
@Pseudo
@Mixin(targets = "myau.mD", remap = false)
public class MixinModuleBase {

    /**
     * Replaces getSuffix — obfuscated as E(J)[Ljava/lang/String;
     * The long param is a ZKM dummy.
     * <p>
     * Returns the SUIM-registered suffix if present, otherwise
     * returns an empty array (original behavior).
     *
     * @author axle.coffee
     * @reason Mixin 0.7.11 VerifyError workaround for array return types
     */
    @Overwrite
    public String[] E(long unused) {
        String[] suffix = SuffixRegistry.getSuffix(this);
        if (suffix != null) {
            return suffix;
        }
        return new String[0];
    }
}
