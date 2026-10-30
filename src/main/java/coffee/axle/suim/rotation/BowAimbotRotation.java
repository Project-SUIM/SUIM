package coffee.axle.suim.rotation;

public class BowAimbotRotation extends Rotation {

    public BowAimbotRotation(RotationState state) {
        super(state);
    }

    // lerp the real camera instead of the server angles, so the chain base is
    // the live client rotation
    public float[] aim(float targetYaw, float targetPitch, float smooth, float hSpeed, float vSpeed) {
        return smoothAndGCD(targetYaw, targetPitch, smooth, hSpeed, vSpeed,
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
    }
}
