package coffee.axle.suim.mixin;

import coffee.axle.suim.feature.exploit.Freeze;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityPlayerSP.class, priority = 1011)
public class MixinEntityPlayerSP {

    @Inject(method = "onUpdate", at = @At("HEAD"), cancellable = true)
    private void onUpdate(CallbackInfo ci) {
        if (Freeze.shouldCancelUpdate()) {
            ci.cancel();
        }
    }
}





