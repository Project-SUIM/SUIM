package coffee.axle.suim.util;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.Random;

/**
 * Multi-point rotation utility ported from CoffeeClient.
 *
 * current method is splitting hitbox into 3 vertical sections and aim at yeah
 * (read code for details)
 * 
 * @author axle.coffee
 * @see <a href=
 *      "https://github.com/axlecoffee/CoffeeClient/blob/main/src/main/java/io/github/moulberry/notenoughupdates/coffeeclient/module/modules/AimAssistModule.java">CoffeeClient
 *      AimAssistModule</a>
 */
public class RotationUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random random = new Random();

    private static Vec3 eyePos() {
        return mc.thePlayer.getPositionEyes(1.0f);
    }

    private static AxisAlignedBB getExpandedBB(Entity entity) {
        float border = entity.getCollisionBorderSize();
        return entity.getEntityBoundingBox().expand(border, border, border);
    }

    private static float applyDelta(float delta, float maxAngle, float smoothFactor) {
        if (Math.abs(delta) <= 1.0f)
            return 0.0f;
        return quantizeAngle(smoothAngle(clampAngle(delta, maxAngle), smoothFactor));
    }

    public static float clampAngle(float angle, float maxAngle) {
        maxAngle = Math.max(0.0f, Math.min(180.0f, maxAngle));
        return MathHelper.clamp_float(angle, -maxAngle, maxAngle);
    }

    public static float smoothAngle(float angle, float smoothFactor) {
        return angle * (0.5f + 0.5f * (1.0f - MathHelper.clamp_float(
                smoothFactor + (random.nextFloat() - 0.5f) * 0.2f, 0.0f, 1.0f)));
    }

    public static float quantizeAngle(float angle) {
        return (float) ((double) angle - (double) angle % 0.0096);
    }

    public static float[] getRotationsToBoxDynamic(AxisAlignedBB boundingBox, float yaw, float pitch,
            float maxAngle, float smoothFactor, float verticalMultipoint) {
        Vec3 eye = eyePos();
        double boxHeight = boundingBox.maxY - boundingBox.minY;
        double targetY = boundingBox.maxY - (boxHeight * MathHelper.clamp_double(verticalMultipoint, 0.05, 0.95));
        double deltaX = (boundingBox.minX + boundingBox.maxX) / 2.0 - eye.xCoord;
        double deltaY = targetY - eye.yCoord;
        double deltaZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0 - eye.zCoord;
        return getRotations(deltaX, deltaY, deltaZ, yaw, pitch, maxAngle, smoothFactor);
    }

    public static float[] getRotationsTo(double targetX, double targetY, double targetZ,
            float currentYaw, float currentPitch) {
        return getRotations(targetX, targetY, targetZ, currentYaw, currentPitch, 180.0f, 0.0f);
    }

    public static float[] getRotations(double deltaX, double deltaY, double deltaZ,
            float currentYaw, float currentPitch, float maxAngle, float smoothFactor) {
        double horizontalDist = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yawDelta = MathHelper.wrapAngleTo180_float(
                (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0f - currentYaw);
        float pitchDelta = MathHelper.wrapAngleTo180_float(
                (float) (-Math.atan2(deltaY, horizontalDist) * 180.0 / Math.PI) - currentPitch);
        return new float[] {
                currentYaw + applyDelta(yawDelta, maxAngle, smoothFactor),
                currentPitch + applyDelta(pitchDelta, maxAngle, smoothFactor)
        };
    }

    public static double distanceToEntity(Entity entity) {
        return distanceToBox(getExpandedBB(entity));
    }

    public static double distanceToBox(AxisAlignedBB boundingBox) {
        Vec3 eye = eyePos();
        if (boundingBox.isVecInside(eye))
            return 0.0;
        return eye.distanceTo(clampVecToBox(eye, boundingBox));
    }

    public static Vec3 clampVecToBox(Vec3 vector, AxisAlignedBB bb) {
        return new Vec3(
                MathHelper.clamp_double(vector.xCoord, bb.minX, bb.maxX),
                MathHelper.clamp_double(vector.yCoord, bb.minY, bb.maxY),
                MathHelper.clamp_double(vector.zCoord, bb.minZ, bb.maxZ));
    }

    public static float angleToEntity(Entity entity) {
        Vec3 eye = eyePos();
        AxisAlignedBB boundingBox = getExpandedBB(entity);
        if (boundingBox.isVecInside(eye))
            return 0.0f;
        double deltaX = entity.posX - eye.xCoord;
        double deltaZ = entity.posZ - eye.zCoord;
        return Math.abs(MathHelper.wrapAngleTo180_float(
                (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0f - mc.thePlayer.rotationYaw))
                * 2.0f;
    }

    public static float computeVerticalMultipoint(double targetY) {
        if (mc.thePlayer == null)
            return 0.5f;
        float yDiff = (float) (targetY - mc.thePlayer.posY);
        if (yDiff > 0.5f)
            return 1.0f;
        if (yDiff < -0.5f)
            return 0.0f;
        return 0.5f;
    }
}





