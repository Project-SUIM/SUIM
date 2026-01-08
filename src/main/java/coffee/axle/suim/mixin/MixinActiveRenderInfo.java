package coffee.axle.suim.mixin;

import coffee.axle.suim.hooks.FreelookHooks;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ActiveRenderInfo.class)
public class MixinActiveRenderInfo {

    @Redirect(method = "updateRenderInfo", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/EntityPlayer;rotationPitch:F"))
    private static float onGetPitch(EntityPlayer player) {
        return FreelookHooks.modifyPitch(player);
    }

    @Redirect(method = "updateRenderInfo", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/EntityPlayer;rotationYaw:F"))
    private static float onGetYaw(EntityPlayer player) {
        return FreelookHooks.modifyYaw(player);
    }
}
