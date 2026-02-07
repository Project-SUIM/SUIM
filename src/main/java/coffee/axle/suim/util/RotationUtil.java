package coffee.axle.suim.util;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.Random;

/**
 * Multi-point rotation utility ported from CoffeeClient.
 *
 * @author axle.coffee
 * @see <a href=
 *      "https://github.com/axlecoffee/CoffeeClient/blob/main/src/main/java/io/github/moulberry/notenoughupdates/coffeeclient/module/modules/AimAssistModule.java">CoffeeClient
 *      AimAssistModule</a>
 */
public class RotationUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random random = new Random();

    public static float clampAngle(float angle, float maxAngle) {
        maxAngle = Math.max(0.0f, Math.min(180.0f, maxAngle));
        if (angle > maxAngle) {
            angle = maxAngle;
        } else if (angle < -maxAngle) {
            angle = -maxAngle;
        }
        return angle;
    }

    public static float smoothAngle(float angle, float smoothFactor) {
        return angle * (0.5f + 0.5f * (1.0f - MathHelper.clamp_float(
                smoothFactor + (random.nextFloat() - 0.5f) * 0.2f, 0.0f, 1.0f)));
    }

    public static float quantizeAngle(float angle) {
        return (float) ((double) angle - (double) angle % 0.0096);
    }

    /** Standard rotation to box center. */
    public static float[] getRotationsToBox(AxisAlignedBB boundingBox, float yaw, float pitch,
            float maxAngle, float smoothFactor) {
        return getRotationsToBoxDynamic(boundingBox, yaw, pitch, maxAngle, smoothFactor, 0.5f);
    }

    /**
     * Rotations to a bounding box with dynamic vertical targeting (0.0=head,
     * 0.5=center, 1.0=feet).
     */
    public static float[] getRotationsToBoxDynamic(AxisAlignedBB boundingBox, float yaw, float pitch,
            float maxAngle, float smoothFactor,
            float verticalMultipoint) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        double boxHeight = boundingBox.maxY - boundingBox.minY;
        double targetY = boundingBox.maxY - (boxHeight * Math.max(0.05, Math.min(0.95, verticalMultipoint)));
        double deltaX = (boundingBox.minX + boundingBox.maxX) / 2.0 - eyePos.xCoord;
        double deltaY = targetY - eyePos.yCoord;
        double deltaZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0 - eyePos.zCoord;
        return getRotations(deltaX, deltaY, deltaZ, yaw, pitch, maxAngle, smoothFactor);
    }

    public static float[] getRotationsTo(double targetX, double targetY, double targetZ,
            float currentYaw, float currentPitch) {
        return getRotations(targetX, targetY, targetZ, currentYaw, currentPitch, 180.0f, 0.0f);
    }

    public static float[] getRotations(double deltaX, double deltaY, double deltaZ,
            float currentYaw, float currentPitch,
            float maxAngle, float smoothFactor) {
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yawDelta = MathHelper.wrapAngleTo180_float(
                (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0f - currentYaw);
        float pitchDelta = MathHelper.wrapAngleTo180_float(
                (float) (-Math.atan2(deltaY, horizontalDistance) * 180.0 / Math.PI) - currentPitch);
        yawDelta = Math.abs(yawDelta) <= 1.0f ? 0.0f : smoothAngle(clampAngle(yawDelta, maxAngle), smoothFactor);
        pitchDelta = Math.abs(pitchDelta) <= 1.0f ? 0.0f : smoothAngle(clampAngle(pitchDelta, maxAngle), smoothFactor);
        return new float[] { quantizeAngle(currentYaw + yawDelta), quantizeAngle(currentPitch + pitchDelta) };
    }

    public static double distanceToEntity(Entity entity) {
        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB boundingBox = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        return distanceToBox(boundingBox);
    }

    public static double distanceToBox(AxisAlignedBB boundingBox) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        if (boundingBox.isVecInside(eyePos)) {
            return 0.0;
        }
        Vec3 clamped = clampVecToBox(eyePos, boundingBox);
        double dx = clamped.xCoord - eyePos.xCoord;
        double dy = clamped.yCoord - eyePos.yCoord;
        double dz = clamped.zCoord - eyePos.zCoord;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static Vec3 clampVecToBox(Vec3 vector, AxisAlignedBB boundingBox) {
        double x = Math.max(boundingBox.minX, Math.min(vector.xCoord, boundingBox.maxX));
        double y = Math.max(boundingBox.minY, Math.min(vector.yCoord, boundingBox.maxY));
        double z = Math.max(boundingBox.minZ, Math.min(vector.zCoord, boundingBox.maxZ));
        return new Vec3(x, y, z);
    }

    public static float angleToEntity(Entity entity) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB boundingBox = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        if (boundingBox.isVecInside(eyePos)) {
            return 0.0f;
        }
        double deltaX = entity.posX - eyePos.xCoord;
        double deltaZ = entity.posZ - eyePos.zCoord;
        return Math.abs(MathHelper.wrapAngleTo180_float(
                (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0f - mc.thePlayer.rotationYaw))
                * 2.0f;
    }

    /**
     * Compute vertical multipoint from bounding box Y-diff (0.0=head, 0.5=center,
     * 1.0=feet).
     */
    public static float computeVerticalMultipoint(AxisAlignedBB box) {
        if (mc.thePlayer == null)
            return 0.5f;
        double boxCenterY = (box.minY + box.maxY) / 2.0;
        float yDiff = (float) (boxCenterY - mc.thePlayer.posY);
        if (yDiff > 0.5f)
            return 1.0f;
        else if (yDiff < -0.5f)
            return 0.0f;
        return 0.5f;
    }

    /** Compute vertical multipoint from entity Y-diff. */
    public static float computeVerticalMultipoint(EntityLivingBase target) {
        if (mc.thePlayer == null)
            return 0.5f;
        float yDiff = (float) (target.posY - mc.thePlayer.posY);
        if (yDiff > 0.5f)
            return 1.0f;
        else if (yDiff < -0.5f)
            return 0.0f;
        return 0.5f;
    }
}
