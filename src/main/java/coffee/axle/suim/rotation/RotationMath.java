package coffee.axle.suim.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/**
 * math util goes meow
 * 
 * @author axle.coffee
 */
public final class RotationMath {

    private RotationMath() {
    }

    public static float[] toRotations(Vec3 eye, double tx, double ty, double tz) {
        double dx = tx - eye.xCoord;
        double dy = ty - eye.yCoord;
        double dz = tz - eye.zCoord;
        double hd = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        yaw = MathHelper.wrapAngleTo180_float(yaw);

        float pitch = (float) Math.toDegrees(-Math.atan2(dy, hd));
        return new float[] { yaw, pitch };
    }

    public static float[] toRotationsFromDelta(double dx, double dy, double dz) {
        double hd = Math.sqrt(dx * dx + dz * dz);
        float yaw = MathHelper.wrapAngleTo180_float(
                (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f);
        float pitch = (float) (-Math.atan2(dy, hd) * 180.0 / Math.PI);
        return new float[] { yaw, pitch };
    }

    public static float angleToEntity(Entity entity) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null)
            return Float.MAX_VALUE;
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        AxisAlignedBB bb = getExpandedBB(entity);
        if (bb.isVecInside(eye))
            return 0.0f;
        double dx = entity.posX - eye.xCoord;
        double dz = entity.posZ - eye.zCoord;
        return Math.abs(MathHelper.wrapAngleTo180_float(
                (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f
                        - mc.thePlayer.rotationYaw))
                * 2.0f;
    }

    public static double distanceToEntity(Entity entity) {
        return distanceToBox(getExpandedBB(entity));
    }

    public static double distanceToBox(AxisAlignedBB bb) {
        Minecraft mc = Minecraft.getMinecraft();
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        if (bb.isVecInside(eye))
            return 0.0;
        return eye.distanceTo(closestPointOnBox(eye, bb));
    }

    public static Vec3 closestPointOnBox(Vec3 point, AxisAlignedBB bb) {
        return new Vec3(
                MathHelper.clamp_double(point.xCoord, bb.minX, bb.maxX),
                MathHelper.clamp_double(point.yCoord, bb.minY, bb.maxY),
                MathHelper.clamp_double(point.zCoord, bb.minZ, bb.maxZ));
    }

    public static AxisAlignedBB getExpandedBB(Entity entity) {
        float border = entity.getCollisionBorderSize();
        return entity.getEntityBoundingBox().expand(border, border, border);
    }

    public static float computeVerticalMultipoint(double targetY) {
        return compVerticalMultipointNoMotion(targetY, 0.0);
    }

    /**
     * Computes a vertical aim for multipoint, no notion
     * @param targetY
     * @param targetMotionY
     */
    public static float compVerticalMultipointNoMotion(double targetY, double targetMotionY) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null)
            return 0.5f;
        float yDiff = (float) (targetY - mc.thePlayer.posY);
        float base;
        if (yDiff > 0.5f)
            base = 1.0f;
        else if (yDiff < -0.5f)
            base = 0.0f;
        else
            base = 0.5f;
        if (targetMotionY > 0.1)
            base = Math.max(0.0f, base - 0.25f);
        else if (targetMotionY < -0.1)
            base = Math.min(1.0f, base + 0.25f);
        return MathHelper.clamp_float(base, 0.0f, 1.0f);
    }

    // aim at the box center at a given vertical fraction, 0 is the bottom
    public static float[] toRotationsBox(AxisAlignedBB box, float verticalFraction) {
        Minecraft mc = Minecraft.getMinecraft();
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        double height = box.maxY - box.minY;
        double targetY = box.maxY - height * MathHelper.clamp_double(verticalFraction, 0.0, 1.0);
        return toRotationsFromDelta(
                (box.minX + box.maxX) / 2.0 - eye.xCoord,
                targetY - eye.yCoord,
                (box.minZ + box.maxZ) / 2.0 - eye.zCoord);
    }

    // same as toRotationsBox but the fraction is clamped to the hitbox bounds
    public static float[] toRotationsBoxBounded(AxisAlignedBB box, float verticalFraction,
            float boundsMin, float boundsMax) {
        return toRotationsBox(box,
                (float) MathHelper.clamp_double(verticalFraction, boundsMin, boundsMax));
    }

    public static float[] toRotationsClosestPoint(AxisAlignedBB boundingBox,
            double shrinkValue) {
        Minecraft mc = Minecraft.getMinecraft();
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        AxisAlignedBB contracted = boundingBox.contract(shrinkValue, shrinkValue, shrinkValue);
        return toRotationsFromDelta(
                MathHelper.clamp_double(eye.xCoord, contracted.minX, contracted.maxX) - eye.xCoord,
                MathHelper.clamp_double(eye.yCoord, contracted.minY, contracted.maxY) - eye.yCoord,
                MathHelper.clamp_double(eye.zCoord, contracted.minZ, contracted.maxZ) - eye.zCoord);
    }

    public static float computeDynamicContraction(double distance, float angularVelocity, double targetSpeed) {
        float base = 0.1f;
        float distFactor = 0.15f * (1.0f - (float) MathHelper.clamp_double(distance / 4.0, 0.0, 1.0));
        float angVelFactor = 0.05f * MathHelper.clamp_float(angularVelocity / 30.0f, 0.0f, 1.0f);
        float speedFactor = 0.03f * (float) MathHelper.clamp_double(targetSpeed / 0.2, 0.0, 1.0);
        float result = base + distFactor - angVelFactor - speedFactor;
        return MathHelper.clamp_float(result, 0.02f, 0.25f);
    }
    /**
     * Computes a vertical aiming factor for multipoint targets, includes motion
     * @param targetY The vertical position of the target
     * @param targetMotionY The vertical motion of the target
     * @return A vertical aiming factor between 0.0 and 1.0
     */
    public static float compVerticalMultipoint(double targetY, double targetMotionY) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null)
            return 0.5f;
        float yDiff = (float) (targetY - mc.thePlayer.posY);

        float base;
        if (yDiff > 0.5f)
            base = 1.0f;
        else if (yDiff < -0.5f)
            base = 0.0f;
        else
            base = 0.5f;

        if (targetMotionY > 0.1)
            base = Math.max(0.0f, base - 0.25f);
        else if (targetMotionY < -0.1)
            base = Math.min(1.0f, base + 0.25f);

        return MathHelper.clamp_float(base, 0.0f, 1.0f);
    }

    public static AxisAlignedBB extrapolateBB(AxisAlignedBB bb, double velX, double velY, double velZ,
            float ticksAhead, float confidence) {
        double ox = velX * ticksAhead * confidence;
        double oy = velY * ticksAhead * confidence;
        double oz = velZ * ticksAhead * confidence;
        return bb.offset(ox, oy, oz);
    }

    // verticalMultipoint biased toward the head, then clamped like the plain version
    public static float[] toRotationsSuim(AxisAlignedBB boundingBox,
            float hitboxBoundsMin, float hitboxBoundsMax,
            float verticalMultipoint, float headBias) {
        return toRotationsBoxBounded(boundingBox,
                verticalMultipoint * (1.0f - headBias), hitboxBoundsMin, hitboxBoundsMax);
    }
}
