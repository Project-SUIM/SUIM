package coffee.axle.suim.rotation;

import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/**
 * Rotation controller for block placement (AutoBlockIn).
 *
 * @author axle.coffee
 */
public class BlockInRotation extends Rotation {

    private float aimYaw;
    private float aimPitch;

    public BlockInRotation(RotationState state) {
        super(state);
    }

    public float[] aimAtBlock(Vec3 hitVec, float smoothFactor, float hSpeed, float vSpeed) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        float[] raw = RotationMath.toRotations(eye, hitVec.xCoord, hitVec.yCoord, hitVec.zCoord);

        float[] result = smoothAndGCD(raw[0], raw[1], smoothFactor, hSpeed, vSpeed);
        aimYaw = result[0];
        aimPitch = result[1];
        return result;
    }

    public boolean withinTolerance(float targetYaw, float targetPitch, int toleranceDegrees) {
        return withinTolerance(aimYaw, aimPitch, targetYaw, targetPitch, toleranceDegrees);
    }

    public void applyMoveFix(float preSpoofYaw) {
        applyMoveFix(preSpoofYaw, aimYaw);
    }

    public void reset() {
        if (mc.thePlayer != null) {
            aimYaw = mc.thePlayer.rotationYaw;
            aimPitch = mc.thePlayer.rotationPitch;
            state.setServerAngles(aimYaw, aimPitch);
        }
    }

    public float getAimYaw() {
        return aimYaw;
    }

    public float getAimPitch() {
        return aimPitch;
    }
}
