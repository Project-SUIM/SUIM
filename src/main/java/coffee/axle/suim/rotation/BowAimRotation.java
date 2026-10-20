package coffee.axle.suim.rotation;

/**
 * BowAim's rotation controller. Breeze lerps the real camera, so the chain
 * base is the live client angles, not the server ones.
 */
public class BowAimRotation extends Rotation {

    public BowAimRotation(RotationState state) {
        super(state);
    }

    public float[] aim(float targetYaw, float targetPitch, float smooth, float hSpeed, float vSpeed) {
        return smoothAndGCD(targetYaw, targetPitch, smooth, hSpeed, vSpeed,
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
    }
}
