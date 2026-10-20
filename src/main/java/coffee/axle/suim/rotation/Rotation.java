package coffee.axle.suim.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

import java.util.Random;

/**
 * Abstract base for all rotation controllers.
 * MEOW
 *
 * @author axle.coffee
 */
public abstract class Rotation {

    protected static final Minecraft mc = Minecraft.getMinecraft();
    protected final RotationState state;
    protected final Random random = new Random();

    protected Rotation(RotationState state) {
        this.state = state;
    }

    // smallest angle increment MC can produce: f = sens * 0.6 + 0.2; gcd = f^3 * 8
    protected float computeGCD() {
        float sens = mc.gameSettings.mouseSensitivity;
        float f = sens * 0.6f + 0.2f;
        return f * f * f * 8.0f;
    }

    protected float applyGCD(float currentAngle, float targetAngle) {
        float gcd = computeGCD();
        float delta = targetAngle - currentAngle;
        return currentAngle + delta - (delta % gcd);
    }

    protected float clampAngle(float angle, float maxAngle) {
        maxAngle = Math.max(0.0f, Math.min(180.0f, maxAngle));
        return MathHelper.clamp_float(angle, -maxAngle, maxAngle);
    }

    // slight randomization for human-like jitter
    protected float smoothDelta(float delta, float smoothFactor) {
        float jitter = (float) (random.nextGaussian() * 0.015);
        return delta * (0.5f + 0.5f * (1.0f - MathHelper.clamp_float(
                smoothFactor + jitter, 0.0f, 1.0f)));
    }

    // Myau-native step semantics (G.e(k7) + kB.l/kB.G/kB.r): speed CAPS the
    // per-tick step (never scales it - a multiplier > 1 overshoots every tick),
    // smoothing scales the capped step, and the result chains onto the
    // mouse-sensitivity lattice
    protected float[] smoothAndGCD(float targetYaw, float targetPitch,
            float smoothFactor, float hSpeed, float vSpeed) {
        return smoothAndGCD(targetYaw, targetPitch, smoothFactor, hSpeed, vSpeed,
                state.getServerYaw(), state.getServerPitch());
    }

    protected float[] smoothAndGCD(float targetYaw, float targetPitch,
            float smoothFactor, float hSpeed, float vSpeed,
            float baseYaw, float basePitch) {
        float yawDelta = MathHelper.wrapAngleTo180_float(targetYaw - baseYaw);
        float pitchDelta = MathHelper.wrapAngleTo180_float(targetPitch - basePitch);

        // G.e's deadzone: sub-0.5 deg errors are left alone
        if (Math.abs(yawDelta) < 0.5f) yawDelta = 0.0f;
        if (Math.abs(pitchDelta) < 0.5f) pitchDelta = 0.0f;

        yawDelta = capStep(yawDelta, hSpeed * 6.0f * rand(0.75f, 1.0f));
        pitchDelta = capStep(pitchDelta, vSpeed * 6.0f * rand(0.75f, 1.0f) * 0.5f);

        float scale = 0.5f + 0.5f * (1.0f - MathHelper.clamp_float(
                smoothFactor + rand(-0.05f, 0.05f), 0.0f, 1.0f));
        yawDelta *= scale;
        pitchDelta *= scale;

        float newYaw = gcdChain(baseYaw + yawDelta, baseYaw);
        float newPitch = gcdChain(basePitch + pitchDelta, basePitch);
        newPitch = MathHelper.clamp_float(newPitch, -90f, 90f);

        return new float[] { newYaw, newPitch };
    }

    private static float rand(float min, float max) {
        return min + (float) Math.random() * (max - min);
    }

    // kB.l: clamp |delta| to the cap, keeping sign
    private static float capStep(float delta, float cap) {
        if (delta > cap) return cap;
        if (delta < -cap) return -cap;
        return delta;
    }

    protected float gcdChain(float smoothed, float baseAngle) {
        float f = mc.gameSettings.mouseSensitivity * 0.6f + 0.2f;
        float gcd = f * f * f * 8.0f * 0.15f;
        float delta = MathHelper.wrapAngleTo180_float(smoothed - baseAngle);
        return baseAngle + (float) (Math.round(delta / gcd) * (double) gcd);
    }

    public float[] snapToTarget(float targetYaw, float targetPitch) {
        float yaw = gcdChain(targetYaw, mc.thePlayer.rotationYaw);
        float pitch = gcdChain(targetPitch, mc.thePlayer.rotationPitch);
        pitch = MathHelper.clamp_float(pitch, -90f, 90f);
        return new float[] { yaw, pitch };
    }

    // rotate WASD inputs to compensate for a yaw spoof; call after setting rotationYaw
    protected void applyMoveFix(float preSpoofYaw, float postSpoofYaw) {
        if (mc.thePlayer == null || mc.thePlayer.movementInput == null)
            return;

        float forward = mc.thePlayer.movementInput.moveForward;
        float strafe = mc.thePlayer.movementInput.moveStrafe;
        if (Math.abs(forward) < 1.0E-4f && Math.abs(strafe) < 1.0E-4f)
            return;

        float delta = (float) Math.toRadians(preSpoofYaw - postSpoofYaw);
        float cos = MathHelper.cos(delta);
        float sin = MathHelper.sin(delta);

        mc.thePlayer.movementInput.moveForward = forward * cos + strafe * sin;
        mc.thePlayer.movementInput.moveStrafe = strafe * cos - forward * sin;
    }

    protected boolean withinTolerance(float currentYaw, float currentPitch,
            float targetYaw, float targetPitch,
            float tolerance) {
        float dy = Math.abs(MathHelper.wrapAngleTo180_float(currentYaw - targetYaw));
        float dp = Math.abs(MathHelper.wrapAngleTo180_float(currentPitch - targetPitch));
        return dy <= tolerance && dp <= tolerance;
    }

    protected float[] smoothAndGCDAdaptive(float targetYaw, float targetPitch,
            float baseSmoothFactor, float hSpeed, float vSpeed,
            float trackingConfidence, double targetLateralVel,
            double distance, int lagDelayTicks) {
        return smoothAndGCDAdaptive(targetYaw, targetPitch, baseSmoothFactor, hSpeed, vSpeed,
                trackingConfidence, targetLateralVel, distance, lagDelayTicks,
                state.getServerYaw(), state.getServerPitch());
    }

    protected float[] smoothAndGCDAdaptive(float targetYaw, float targetPitch,
            float baseSmoothFactor, float hSpeed, float vSpeed,
            float trackingConfidence, double targetLateralVel,
            double distance, int lagDelayTicks, float baseYaw, float basePitch) {
        float yawError = Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - baseYaw));
        float pitchError = Math.abs(MathHelper.wrapAngleTo180_float(targetPitch - basePitch));
        float totalError = (float) Math.sqrt(yawError * yawError + pitchError * pitchError);

        float errorFactor = 1.0f - MathHelper.clamp_float(totalError / 30.0f, 0.0f, 0.7f);
        float confFactor = 1.0f - trackingConfidence * 0.5f;
        float moveFactor = 1.0f - MathHelper.clamp_float((float) (targetLateralVel / 0.25), 0.0f, 0.3f);
        float distFactor = MathHelper.clamp_float((float) (distance / 4.0), 0.5f, 1.0f);

        float effectiveSmooth = baseSmoothFactor * errorFactor * confFactor * moveFactor * distFactor;

        if (lagDelayTicks > 0) {
            effectiveSmooth *= Math.max(0.3f, 1.0f - (lagDelayTicks / 20.0f));
        }

        effectiveSmooth = Math.max(effectiveSmooth, 0.05f);

        return smoothAndGCD(targetYaw, targetPitch, effectiveSmooth, hSpeed, vSpeed,
                baseYaw, basePitch);
    }
}
