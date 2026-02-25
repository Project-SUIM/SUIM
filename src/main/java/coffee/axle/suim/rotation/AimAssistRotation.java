package coffee.axle.suim.rotation;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;

public class AimAssistRotation extends Rotation {

    public enum AimMode {
        DEFAULT,
        SIGHTLINE,
        VSPLIT,
        SUIM,
        PENIS
    }

    public static final String[] MODE_NAMES = { "DEFAULT", "SIGHTLINE", "VSPLIT", "SUIM", "PENIS" };

    private AimMode mode = AimMode.DEFAULT;

    private float hitboxBoundsMin = 0.05f;
    private float hitboxBoundsMax = 0.95f;

    // SUIM settings
    private float headBias = 0.3f;
    private boolean extrapolationEnabled = false;
    private boolean pingCompensation = false;
    private float contractionMin = 0.03f;
    private float contractionMax = 0.22f;
    private int lagDelayTicks = 0;

    private final TrackingContext tracking = new TrackingContext();

    public AimAssistRotation(RotationState state) {
        super(state);
    }

    public void setMode(AimMode mode) {
        this.mode = mode;
    }

    public void setMode(int ordinal) {
        AimMode[] modes = AimMode.values();
        this.mode = (ordinal >= 0 && ordinal < modes.length) ? modes[ordinal] : AimMode.DEFAULT;
    }

    public AimMode getMode() {
        return mode;
    }

    public boolean isDefaultMode() {
        return mode == AimMode.DEFAULT;
    }

    public void setHitboxBounds(float min, float max) {
        this.hitboxBoundsMin = MathHelper.clamp_float(min, 0.0f, 1.5f);
        this.hitboxBoundsMax = MathHelper.clamp_float(max, 0.0f, 1.5f);
    }

    public float getHitboxBoundsMin() {
        return hitboxBoundsMin;
    }

    public float getHitboxBoundsMax() {
        return hitboxBoundsMax;
    }

    public void setHeadBias(float headBias) {
        this.headBias = MathHelper.clamp_float(headBias, 0.0f, 1.0f);
    }

    public float getHeadBias() {
        return headBias;
    }

    public void setExtrapolationEnabled(boolean enabled) {
        this.extrapolationEnabled = enabled;
    }

    public boolean isExtrapolationEnabled() {
        return extrapolationEnabled;
    }

    public void setPingCompensation(boolean enabled) {
        this.pingCompensation = enabled;
    }

    public boolean isPingCompensation() {
        return pingCompensation;
    }

    public void setContractionRange(float min, float max) {
        this.contractionMin = MathHelper.clamp_float(min, 0.01f, 0.15f);
        this.contractionMax = MathHelper.clamp_float(max, 0.10f, 0.30f);
    }

    public float getContractionMin() {
        return contractionMin;
    }

    public float getContractionMax() {
        return contractionMax;
    }

    public void setLagDelayTicks(int ticks) {
        this.lagDelayTicks = Math.max(0, ticks);
    }

    public int getLagDelayTicks() {
        return lagDelayTicks;
    }

    public TrackingContext getTracking() {
        return tracking;
    }

    public float[] computeTarget(EntityLivingBase entity, boolean movementCorrection) {
        switch (mode) {
            case SIGHTLINE:
                return calcSkiddedClosestSightline(entity, movementCorrection);
            case VSPLIT:
                return calcCoffeeClientRots(entity);
            case SUIM:
                return calcEvilAxlePissAim(entity);
            case PENIS:
                return calcEvilAxlePenisCalculationRotationHitRotationCalculation(entity);
            case DEFAULT:
            default:
                return null;
        }
    }

    public float[] aimAtEntity(EntityLivingBase entity,
            float smoothFactor, float hSpeed, float vSpeed,
            boolean movementCorrection) {
        float[] raw = computeTarget(entity, movementCorrection);
        if (raw == null)
            return null;

        if (mode == AimMode.SUIM) {
            return aimAtEntitySuim(entity, raw, smoothFactor, hSpeed, vSpeed);
        }

        float effectiveSmooth = smoothFactor;
        if (movementCorrection && isPlayerMoving()) {
            effectiveSmooth *= 1.3f;
        }

        return smoothAndGCD(raw[0], raw[1], effectiveSmooth, hSpeed, vSpeed);
    }

    private float[] calcEvilAxlePissAim(EntityLivingBase entity) {
        int entityId = entity.getEntityId();
        if (entityId != tracking.targetEntityId) {
            tracking.reset();
            tracking.targetEntityId = entityId;
        }

        AxisAlignedBB bb = entity.getEntityBoundingBox();
        float border = entity.getCollisionBorderSize();
        AxisAlignedBB expandedBB = bb.expand(border, border, border);

        double distance = RotationMath.distanceToBox(expandedBB);
        float angularVelocity = tracking.computeAngularVelocity(
                state.getServerYaw(), state.getServerPitch());
        double targetSpeed = tracking.computeTargetSpeed(entity.posX, entity.posZ);
        float contraction = RotationMath.computeDynamicContraction(distance, angularVelocity, targetSpeed);
        contraction = MathHelper.clamp_float(contraction, contractionMin, contractionMax);
        AxisAlignedBB contractedBB = expandedBB.contract(contraction, contraction, contraction);

        AxisAlignedBB workingBB = contractedBB;
        if (extrapolationEnabled) {
            double velX = entity.posX - entity.prevPosX;
            double velY = entity.posY - entity.prevPosY;
            double velZ = entity.posZ - entity.prevPosZ;
            tracking.pushVelocity(velX, velZ);

            double variance = tracking.computeVelocityVariance();
            float confidence = 1.0f - MathHelper.clamp_float((float) (variance / 1.5), 0.0f, 1.0f);
            float ticksAhead = 3.0f;
            if (pingCompensation) {
                ticksAhead += state.getEstimatedPingTicks();
            }

            workingBB = RotationMath.extrapolateBB(contractedBB, velX, velY, velZ, ticksAhead, confidence);
        }

        float vertMultipoint = RotationMath.computeVerticalMultipointMotionAware(entity.posY, entity.motionY);

        float[] vsplitRot = RotationMath.toRotationsBoxDynamic(
                workingBB, hitboxBoundsMin, hitboxBoundsMax, vertMultipoint);
        float[] sightlineRot = RotationMath.toRotationsClosestPoint(workingBB, 0.0);
        float[] suimRot = RotationMath.toRotationsSuim(
                workingBB, hitboxBoundsMin, hitboxBoundsMax, vertMultipoint, headBias);

        float angularOffset = computeAngularOffset(suimRot);
        float sightlineWeight = calcSkiddedClosestSightlineWeight(distance, angularOffset, tracking.trackingConfidence);

        if (!tracking.firstHitLanded) {
            if (angularOffset > 15.0f) {
                sightlineWeight *= 0.3f;
            } else {
                sightlineWeight = Math.max(sightlineWeight, 0.7f);
            }
        }

        float finalYaw = larp(suimRot[0], sightlineRot[0], sightlineWeight);
        float finalPitch = larp(suimRot[1], sightlineRot[1], sightlineWeight);

        tracking.storeTargetPosition(entity.posX, entity.posY, entity.posZ);

        return new float[] { finalYaw, finalPitch };
    }

    private float[] aimAtEntitySuim(EntityLivingBase entity, float[] raw,
            float smoothFactor, float hSpeed, float vSpeed) {
        double distance = mc.thePlayer.getDistanceToEntity(entity);
        double velX = entity.posX - entity.prevPosX;
        double velZ = entity.posZ - entity.prevPosZ;
        double targetLateralVel = Math.sqrt(velX * velX + velZ * velZ);

        float[] result = smoothAndGCDAdaptive(
                raw[0], raw[1], smoothFactor, hSpeed, vSpeed,
                tracking.trackingConfidence, targetLateralVel, distance, lagDelayTicks);

        float tolerance = 3.0f;
        boolean onTarget = withinTolerance(result[0], result[1], raw[0], raw[1], tolerance);
        tracking.updateOnTarget(onTarget);
        tracking.storeOutputAngles(result[0], result[1]);

        return result;
    }

    private float calcSkiddedClosestSightlineWeight(double distance, float angularOffset, float confidence) {
        float distWeight;
        if (distance < 2.0) {
            distWeight = 0.8f;
        } else if (distance > 3.0) {
            distWeight = 0.2f;
        } else {
            distWeight = larp(0.8f, 0.2f, (float) (distance - 2.0));
        }

        float offsetWeight;
        if (angularOffset < 5.0f) {
            offsetWeight = 0.9f;
        } else if (angularOffset > 30.0f) {
            offsetWeight = 0.2f;
        } else {
            offsetWeight = larp(0.9f, 0.2f, (angularOffset - 5.0f) / 25.0f);
        }

        float confWeight = 0.5f + confidence * 0.4f;

        return (distWeight + offsetWeight + confWeight) / 3.0f;
    }

    private float computeAngularOffset(float[] targetRot) {
        float sYaw = state.getServerYaw();
        float sPitch = state.getServerPitch();
        float dy = MathHelper.wrapAngleTo180_float(targetRot[0] - sYaw);
        float dp = MathHelper.wrapAngleTo180_float(targetRot[1] - sPitch);
        return (float) Math.sqrt(dy * dy + dp * dp);
    }

    private static float larp(float a, float b, float t) {
        return a + (b - a) * MathHelper.clamp_float(t, 0.0f, 1.0f);
    }

    // axle skidding any% speedrun wr
    private float[] calcSkiddedClosestSightline(EntityLivingBase entity, boolean movementCorrection) {
        AxisAlignedBB bb = entity.getEntityBoundingBox();
        double shrinkValue = 0.1;
        if (movementCorrection && isPlayerMoving()) {
            shrinkValue = 0.2;
        }
        return RotationMath.toRotationsClosestPoint(bb, shrinkValue);
    }

    private float[] calcCoffeeClientRots(EntityLivingBase entity) {
        AxisAlignedBB bb = entity.getEntityBoundingBox();
        float border = entity.getCollisionBorderSize();
        AxisAlignedBB expandedBox = bb.expand(border, border, border);
        float verticalMultipoint = RotationMath.computeVerticalMultipoint(entity.posY);
        return RotationMath.toRotationsBoxDynamic(
                expandedBox, hitboxBoundsMin, hitboxBoundsMax, verticalMultipoint);
    }

    private float[] calcEvilAxlePenisCalculationRotationHitRotationCalculation(EntityLivingBase entity) {
        AxisAlignedBB bb = entity.getEntityBoundingBox();
        float border = entity.getCollisionBorderSize();
        AxisAlignedBB expandedBox = bb.expand(border, border, border);
        return RotationMath.toRotationsFixedPoint(expandedBox, 0.75f);
    }

    private boolean isPlayerMoving() {
        return mc.thePlayer != null
                && (mc.thePlayer.moveForward != 0 || mc.thePlayer.moveStrafing != 0);
    }
}
