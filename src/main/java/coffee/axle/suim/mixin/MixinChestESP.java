package coffee.axle.suim.mixin;

import coffee.axle.suim.feature.render.ChestESPExtras;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.Color;

// keep Myau's render loop, only choose which chests it draws and their colors
@Pseudo
@Mixin(targets = "myau.J7", remap = false)
public class MixinChestESP {
    private TileEntity currentChest;

    @Inject(method = "lambda$onRender3D$0", at = @At("HEAD"), cancellable = true, remap = false)
    private static void filterChest(TileEntity tile, CallbackInfoReturnable<Boolean> cir) {
        if (!ChestESPExtras.shouldRender(tile)) cir.setReturnValue(false);
    }

    @ModifyVariable(method = "Lmyau/J7;u(Lmyau/k9;)V",
            at = @At(value = "INVOKE_ASSIGN", target = "Ljava/util/Iterator;next()Ljava/lang/Object;"),
            ordinal = 0, remap = false)
    private TileEntity captureChest(TileEntity tile) {
        currentChest = tile;
        return tile;
    }

    @ModifyVariable(method = "Lmyau/J7;u(Lmyau/k9;)V", at = @At("STORE"), ordinal = 0, remap = false)
    private Color changeColor(Color original) {
        return ChestESPExtras.color(currentChest, original);
    }

    @ModifyArg(method = "Lmyau/J7;u(Lmyau/k9;)V",
            at = @At(value = "INVOKE", target = "Lmyau/kl;d(Lnet/minecraft/util/AxisAlignedBB;IIIIF)V"),
            index = 4, remap = false)
    private int changeOpacity(int original) {
        return ChestESPExtras.opacity(currentChest, original);
    }

    // only float local in the loop, so ordinal 0 is the tracer alpha
    @ModifyVariable(method = "Lmyau/J7;u(Lmyau/k9;)V", at = @At("STORE"), ordinal = 0, remap = false)
    private float changeTracerAlpha(float original) {
        return ChestESPExtras.tracerAlpha(currentChest, original);
    }
}
