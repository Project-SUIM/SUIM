package coffee.axle.suim.mixin;

import coffee.axle.suim.feature.render.BedESPTeamColor;
import net.minecraft.util.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.awt.Color;

@Pseudo
@Mixin(targets = "myau.mb", remap = false)
public class MixinBedESP {

    private BlockPos currentBed = null;

    @ModifyVariable(method = "b(Lmyau/mj;)V", at = @At(value = "INVOKE_ASSIGN", target = "Ljava/util/Iterator;next()Ljava/lang/Object;"), ordinal = 0, remap = false)
    private BlockPos captureBed(BlockPos pos) {
        currentBed = pos;
        return pos;
    }

    @ModifyVariable(method = "b(Lmyau/mj;)V", at = @At(value = "STORE"), ordinal = 0, remap = false)
    private Color modifyBedColor(Color originalColor) {
        BedESPTeamColor teamColor = BedESPTeamColor.getInstance();

        if (teamColor != null && currentBed != null) {
            Color teamCol = teamColor.getTeamColorForBed(currentBed);
            if (teamCol != null) {
                return teamCol;
            }
        }

        return originalColor;
    }
}
