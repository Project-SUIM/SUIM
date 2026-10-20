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
        }
    }

    // PlayerTick.END runs inside super.onUpdate(), before the walking packet
    @Inject(method = "onUpdateWalkingPlayer", at = @At("RETURN"), require = 1)
    private void suim$afterWalkingUpdate(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new PostMotionEvent());
    }

    // openb4 fires stasis.afterplayerupdate from this same tail point
    @Inject(method = "onUpdate", at = @At("RETURN"))
    private void suim$afterUpdate(CallbackInfo ci) {
        Stasis.afterPlayerUpdate((EntityPlayerSP) (Object) this);
    }

// hateful horrible code
//     // same injection point Myau's mixin fires its k2 event at (after
//     // updatePlayerMoveState) - priority 1011 vs Myau's 1000 means this runs
//     // AFTER Eagle's handler forced sneak, so the undo sticks.
//     // key-down is NEVER touched: holding shift is vanilla sneak, always.
//     // key-up: undo Eagle's force (sneak flag + its 0.3 movement scale)
//     @Inject(method = "onLivingUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MovementInput;updatePlayerMoveState()V", shift = At.Shift.AFTER))
//     private void suim$afterMoveState(CallbackInfo ci) {
//         if (!EagleRequireSneak.shouldCancelSneak()) {
//             return;
//         }
//         Minecraft mc = Minecraft.getMinecraft();
//         if (mc.thePlayer == null || mc.thePlayer.movementInput == null) {
//             return;
//         }
//         if (!mc.gameSettings.keyBindSneak.isKeyDown()
//                 && mc.thePlayer.movementInput.sneak) {
//             mc.thePlayer.movementInput.sneak = false;
//             mc.thePlayer.movementInput.moveStrafe /= 0.3f;
//             mc.thePlayer.movementInput.moveForward /= 0.3f;
//         }
//     }
}





