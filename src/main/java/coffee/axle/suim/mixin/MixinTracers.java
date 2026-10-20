package coffee.axle.suim.mixin;

import coffee.axle.suim.feature.render.MurderMystery;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Filters Myau's Tracers (myau.Ja) player selection from MurderMystery.
 * N is the shared should-track check for both the 3D lines and 2D arrows;
 * the long param is the obfuscator checksum, unused on our early-return path.
 */
@Pseudo
@Mixin(targets = "myau.Ja", remap = false)
public class MixinTracers {

    @Inject(method = "N", at = @At("HEAD"), cancellable = true, remap = false)
    private void suim$filterTracer(EntityPlayer player, long checksum, CallbackInfoReturnable<Boolean> cir) {
        Boolean verdict = MurderMystery.tracerVerdict(player);
        if (verdict != null) cir.setReturnValue(verdict);
    }
}
