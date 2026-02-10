package coffee.axle.suim.mixin;

import coffee.axle.suim.hooks.FreelookHooks;
import net.minecraft.client.renderer.entity.RenderManager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderManager.class)
public class MixinRenderManager {

    @Redirect(method = "cacheActiveRenderInfo", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/RenderManager;playerViewX:F", opcode = Opcodes.PUTFIELD))
    private void onSetPlayerViewX(RenderManager instance, float value) {
        FreelookHooks.playerViewXModifier(instance, value);
    }

    @Redirect(method = "cacheActiveRenderInfo", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/RenderManager;playerViewY:F", opcode = Opcodes.PUTFIELD))
    private void onSetPlayerViewY(RenderManager instance, float value) {
        FreelookHooks.playerViewYModifier(instance, value);
    }
}





