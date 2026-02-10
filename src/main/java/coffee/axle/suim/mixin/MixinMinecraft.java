package coffee.axle.suim.mixin;

import coffee.axle.suim.events.impl.PreKeyInputEvent;
import coffee.axle.suim.events.impl.PreMouseInputEvent;
import coffee.axle.suim.hooks.FreelookHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
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

    @Inject(method = { "runTick" }, at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;dispatchKeypresses()V") })
    public void keyPresses(CallbackInfo ci) {
        int k = (Keyboard.getEventKey() == 0) ? (Keyboard.getEventCharacter() + 256) : Keyboard.getEventKey();
        char character = Keyboard.getEventCharacter();
        if (Keyboard.getEventKeyState()) {
            MinecraftForge.EVENT_BUS.post(new PreKeyInputEvent(k, character));
        }
    }

    @Inject(method = { "runTick" }, at = {
            @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;getEventButton()I", remap = false) })
    public void mouseKeyPresses(CallbackInfo ci) {
        int k = Mouse.getEventButton();
        if (Mouse.getEventButtonState()) {
            MinecraftForge.EVENT_BUS.post(new PreMouseInputEvent(k));
        }
    }
}





