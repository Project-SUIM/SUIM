package coffee.axle.suim.feature.world;

import net.minecraft.util.MathHelper;

// AutoDef RotationUtil port: step + smooth + quantize, the exact native math
final class RotStep {
    private static final float SMOOTH = 0.35f;
    private static final float GCD = 0.0096f;

    private RotStep() {
    }

    private static float clamp(float angle, float max) {
        max = Math.max(0.0f, Math.min(180.0f, max));
        if (angle > max) return max;
        if (angle < -max) return -max;
        return angle;
    }

    private static float smooth(float angle, float factor) {
        factor = Math.max(0.0f, Math.min(1.0f, factor));
        return angle * (0.5f + 0.5f * (1.0f - factor));
    }

    private static float quant(float angle) {
        return (float) ((double) Math.round((double) angle / GCD) * GCD);
    }

    static float[] step(float curYaw, float curPitch, float tgtYaw, float tgtPitch, float maxAngle) {
        float yawD = MathHelper.wrapAngleTo180_float(tgtYaw - curYaw);
        float pitchD = tgtPitch - curPitch;
        if (Math.abs(yawD) <= 3.0f && Math.abs(pitchD) <= 3.0f) {
            return new float[]{quant(curYaw + yawD),
                    quant(MathHelper.clamp_float(curPitch + pitchD, -90.0f, 90.0f))};
        }
        yawD = smooth(clamp(yawD, maxAngle), SMOOTH);
        pitchD = smooth(clamp(pitchD, maxAngle), SMOOTH);
        return new float[]{quant(curYaw + yawD),
                quant(MathHelper.clamp_float(curPitch + pitchD, -90.0f, 90.0f))};
    }
}
