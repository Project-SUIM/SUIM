package coffee.axle.suim.mixin;

import coffee.axle.suim.events.PrePlayerInputEvent;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MovementInputFromOptions.class, priority = 1011)
public abstract class MixinMovementInput extends MovementInput {

    @Inject(method = "updatePlayerMoveState", at = @At("RETURN"))
    private void onPostUpdatePlayerMoveState(CallbackInfo ci) {
        PrePlayerInputEvent event = new PrePlayerInputEvent(this.moveForward, this.moveStrafe);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            this.moveForward = 0.0f;
            this.moveStrafe = 0.0f;
        } else {
            this.moveForward = event.getForward();
            this.moveStrafe = event.getStrafe();
        }
    }
}
