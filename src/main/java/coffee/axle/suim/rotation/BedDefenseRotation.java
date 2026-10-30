package coffee.axle.suim.rotation;

import net.minecraft.util.MathHelper;

/**
 * Per-tick step for bed defence placement.
 * <p>
 * Same shape as the aim controllers but not the same curve: this snaps outright
 * once the error is under 3 degrees so the placement ray validates on the tick
 * the rotation lands, and it quantizes to a fixed lattice rather than the mouse
 * one because it drives clicks, not the camera.
 */
public class BedDefenseRotation extends Rotation {

    private static final float SMOOTH = 0.35f;
    private static final float GCD = 0.0096f;

    public BedDefenseRotation(RotationState state) {
        super(state);
    }

    public float[] step(float curYaw, float curPitch, float tgtYaw, float tgtPitch, float maxAngle) {
        float yawD = MathHelper.wrapAngleTo180_float(tgtYaw - curYaw);
        float pitchD = tgtPitch - curPitch;
        if (Math.abs(yawD) <= 3.0f && Math.abs(pitchD) <= 3.0f) {
            return new float[] { quant(curYaw + yawD), clampPitch(curPitch + pitchD) };
        }
        yawD = smooth(cap(yawD, maxAngle), SMOOTH);
        pitchD = smooth(cap(pitchD, maxAngle), SMOOTH);
        return new float[] { quant(curYaw + yawD), clampPitch(curPitch + pitchD) };
    }

    private static float clampPitch(float pitch) {
        return quant(MathHelper.clamp_float(pitch, -90.0f, 90.0f));
    }

    private static float quant(float angle) {
        return (float) ((double) Math.round((double) angle / GCD) * GCD);
    }

    private static float cap(float angle, float max) {
        max = Math.max(0.0f, Math.min(180.0f, max));
        if (angle > max) return max;
        if (angle < -max) return -max;
        return angle;
    }

    private static float smooth(float angle, float factor) {
        factor = Math.max(0.0f, Math.min(1.0f, factor));
        return angle * (0.5f + 0.5f * (1.0f - factor));
    }
}