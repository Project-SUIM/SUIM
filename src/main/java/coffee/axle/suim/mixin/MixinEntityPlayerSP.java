package coffee.axle.suim.mixin;

import coffee.axle.suim.events.PostMotionEvent;
import coffee.axle.suim.feature.exploit.Freeze;
import coffee.axle.suim.feature.movement.Stasis;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.common.MinecraftForge;
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
            return;
        }
        // Jade's stasis freezes from onUpdate HEAD
        Stasis.jadePreUpdate((EntityPlayerSP) (Object) this);
    }

    // PlayerTick.END runs inside super.onUpdate(), before the walking packet
    @Inject(method = "onUpdateWalkingPlayer", at = @At("RETURN"), require = 1)
    private void suim$afterWalkingUpdate(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new PostMotionEvent());
    }

    // stasis.afterPlayerUpdate has to run from this same tail point
    @Inject(method = "onUpdate", at = @At("RETURN"))
    private void suim$afterUpdate(CallbackInfo ci) {
        EntityPlayerSP self = (EntityPlayerSP) (Object) this;
        Stasis.afterPlayerUpdate(self);
        Stasis.jadePostUpdate(self);
    }
}





