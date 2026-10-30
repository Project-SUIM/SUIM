package coffee.axle.suim.mixin;

import coffee.axle.suim.events.ClickEvent;
import coffee.axle.suim.hooks.FreelookHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.common.MinecraftForge;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Redirect(method = "runTick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;thirdPersonView:I", opcode = Opcodes.PUTFIELD))
    private void onSetThirdPersonView(GameSettings settings, int value) {
        FreelookHooks.modifyThirdPerson(settings, value);
    }

    // input-level click cancel. HEAD means a cancelled click never builds the
    // swing/attack packets and never runs the client-side attack prediction
    // (hit-slowdown, sprint drop, crit)
    @Inject(method = "clickMouse", at = @At("HEAD"), cancellable = true)
    private void onClickMouse(CallbackInfo ci) {
        ClickEvent event = new ClickEvent();
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            ci.cancel();
        }
    }
}





