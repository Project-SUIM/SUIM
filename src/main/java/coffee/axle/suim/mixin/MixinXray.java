package coffee.axle.suim.mixin;

import coffee.axle.suim.feature.render.XrayExtras;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// dragon eggs use Xray's existing tracking and render path
@Pseudo
@Mixin(targets = "myau.Jr", remap = false)
public class MixinXray {
    @Inject(method = "D", at = @At("HEAD"), cancellable = true, remap = false)
    private void supportDragonEgg(int id, CallbackInfoReturnable<Boolean> cir) {
        if (XrayExtras.isDragonEggEnabled(id)) cir.setReturnValue(true);
    }

    @Inject(method = "q", at = @At("HEAD"), cancellable = true, remap = false)
    private void enableDragonEgg(int id, CallbackInfoReturnable<Boolean> cir) {
        if (XrayExtras.isDragonEggEnabled(id)) cir.setReturnValue(true);
    }
}
