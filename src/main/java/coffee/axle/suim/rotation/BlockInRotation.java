package coffee.axle.suim.rotation;

import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/**
 * Rotation controller for block placement (AutoBlockIn).
 * <p>
 * Computes server-side rotations toward block face hit vectors
 * with smoothing, per-tick speed scaling, and GCD correction.
 *
 * @author axle.coffee
 */
public class BlockInRotation extends Rotation {

    private float aimYaw;
    private float aimPitch;

    public BlockInRotation(RotationState state) {
        super(state);
    }

    /**
     * Compute a GCD-corrected smoothed rotation toward a block face hit vector.
     *
     * @param hitVec       the target point on the block face
     * @param smoothFactor 0.0-1.0 dampening factor
     * @param hSpeed       horizontal speed (0-10)
     * @param vSpeed       vertical speed (0-10)
     * @return {@code [yaw, pitch]} ready to set on the player
     */
    public float[] aimAtBlock(Vec3 hitVec, float smoothFactor, float hSpeed, float vSpeed) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        float[] raw = RotationMath.toRotations(eye, hitVec.xCoord, hitVec.yCoord, hitVec.zCoord);

        float[] result = smoothAndGCD(raw[0], raw[1], smoothFactor, hSpeed, vSpeed);
        aimYaw = result[0];
        aimPitch = result[1];
        return result;
    }

    /**
     * Check if the current aim is within tolerance of a target angle pair.
     */
    public boolean withinTolerance(float targetYaw, float targetPitch, int toleranceDegrees) {
        return withinTolerance(aimYaw, aimPitch, targetYaw, targetPitch, toleranceDegrees);
    }

    /**
     * Apply movement correction for the yaw spoof.
     *
     * @param preSpoofYaw the visual yaw before rotation was applied
     */
    public void applyMoveFix(float preSpoofYaw) {
        applyMoveFix(preSpoofYaw, aimYaw);
    }

    /**
     * Reset aim state (call on module enable).
     */
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
