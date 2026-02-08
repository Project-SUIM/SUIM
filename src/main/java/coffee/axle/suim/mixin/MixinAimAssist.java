package coffee.axle.suim.mixin;

import coffee.axle.suim.feature.combat.MultiPointAiming;
import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Multi-point aiming for AimAssist (myau.mT).
 * <p>
 * AimAssist computes rotations via B.Q (through ZKM dispatch) then calls
 * {@code Myau.rotationManager.setRotation(yaw, pitch, priority, force)}
 * — it does NOT modify mc.thePlayer.rotationYaw/Pitch directly.
 * <p>
 * Strategy: in TAIL, find the aimed entity, compute multipoint pitch,
 * and call rotationManager.setRotation again to overwrite with adjusted
 * pitch. The second call with same priority overwrites the first.
 *
 * @author axle.coffee
 */
@Pseudo
@Mixin(targets = "myau.mT", remap = false)
public class MixinAimAssist {

    @Unique
    private static final Minecraft suim$mc = Minecraft.getMinecraft();

    @Unique
    private static Field suim$rotationManagerField;

    @Unique
    private static Field suim$yawDeltaField;

    @Unique
    private static Field suim$pitchDeltaField;

    @Unique
    private static Field suim$lastUpdateField;

    @Unique
    private static boolean suim$loggedInit = false;

    @Unique
    private static float suim$savedYaw, suim$savedPitch;

    @Unique
    private static void suim$ensureRotationManagerReflection()
            throws Exception {
        if (suim$rotationManagerField == null) {
            Class<?> clientClass = Class.forName(MyauMappings.CLASS_MAIN);
            suim$rotationManagerField = clientClass.getDeclaredField(MyauMappings.FIELD_ROTATION_MANAGER);
            suim$rotationManagerField.setAccessible(true);
        }
        Class<?> rmClass = Class.forName(MyauMappings.CLASS_ROTATION_MANAGER);
        if (suim$yawDeltaField == null) {
            suim$yawDeltaField = rmClass.getDeclaredField(MyauMappings.FIELD_ROT_MGR_YAW_DELTA);
            suim$yawDeltaField.setAccessible(true);
        }
        if (suim$pitchDeltaField == null) {
            suim$pitchDeltaField = rmClass.getDeclaredField(MyauMappings.FIELD_ROT_MGR_PITCH_DELTA);
            suim$pitchDeltaField.setAccessible(true);
        }
        if (suim$lastUpdateField == null) {
            suim$lastUpdateField = rmClass.getDeclaredField(MyauMappings.FIELD_ROT_MGR_LAST_UPDATE);
            suim$lastUpdateField.setAccessible(true);
        }
    }

    @Unique
    private static Object suim$getRotationManager() throws Exception {
        suim$ensureRotationManagerReflection();
        return suim$rotationManagerField.get(null);
    }

    @Unique
    private static EntityPlayer suim$findBestPlayer(
            Object aimAssistInstance) {
        if (suim$mc.theWorld == null || suim$mc.thePlayer == null) {
            return null;
        }

        float range;
        int fov;
        try {
            range = suim$getFloatProp(aimAssistInstance, MyauMappings.FIELD_AIM_ASSIST_RANGE, 4.5f);
            fov = suim$getIntProp(aimAssistInstance, MyauMappings.FIELD_AIM_ASSIST_FOV, 90);
        } catch (Exception e) {
            range = 4.5f;
            fov = 90;
        }

        EntityPlayer best = null;
        double closest = range;

        for (Entity entity : suim$mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityPlayer))
                continue;
            EntityPlayer player = (EntityPlayer) entity;
            if (player == suim$mc.thePlayer)
                continue;
            if (player.deathTime > 0)
                continue;
            if (player.isInvisible())
                continue;

            double dist = RotationUtil.distanceToEntity(player);
            if (dist > range)
                continue;

            float angle = RotationUtil.angleToEntity(player);
            if (angle > (float) fov)
                continue;

            if (dist < closest) {
                closest = dist;
                best = player;
            }
        }
        return best;
    }

    @Unique
    private static float suim$getFloatProp(
            Object instance, String fieldName, float fallback) {
        try {
            Field f = instance.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object prop = f.get(instance);
            if (prop == null)
                return fallback;
            Method getValue = prop.getClass().getMethod(MyauMappings.METHOD_PROPERTY_GET_VALUE);
            Object val = getValue.invoke(prop);
            if (val instanceof Number)
                return ((Number) val).floatValue();
        } catch (Exception ignored) {
        }
        return fallback;
    }

    @Unique
    private static int suim$getIntProp(
            Object instance, String fieldName, int fallback) {
        try {
            Field f = instance.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object prop = f.get(instance);
            if (prop == null)
                return fallback;
            Method getValue = prop.getClass().getMethod(MyauMappings.METHOD_PROPERTY_GET_VALUE);
            Object val = getValue.invoke(prop);
            if (val instanceof Number)
                return ((Number) val).intValue();
        } catch (Exception ignored) {
        }
        return fallback;
    }

    /**
     * Save player rotation before AimAssist processes.
     */
    @Inject(method = "d(Lmyau/KP;)V", at = @At("HEAD"), remap = false, require = 0)
    private void suim$onTickHead(
            @Coerce Object tickEvent, CallbackInfo ci) {
        if (!suim$loggedInit) {
            suim$loggedInit = true;
            MyauLogger.log(
                    "AimAssist:MultiPoint", "mixin applied, injects active");
        }
        if (suim$mc.thePlayer != null) {
            suim$savedYaw = suim$mc.thePlayer.rotationYaw;
            suim$savedPitch = suim$mc.thePlayer.rotationPitch;
        }
    }

    /**
     * After AimAssist runs, check if a rotation delta was set on the
     * rotation manager. If multi-point is enabled: find the target,
     * compute adjusted pitch, and overwrite the RM delta fields.
     * <p>
     * Note: RM field G stores the 'force' flag, not whether rotation
     * was set. We detect rotation by checking non-zero yaw/pitch deltas.
     */
    @Inject(method = "d(Lmyau/KP;)V", at = @At("TAIL"), remap = false, require = 0)
    private void suim$onTickTail(
            @Coerce Object tickEvent, CallbackInfo ci) {
        if (!MultiPointAiming.isAimAssistDynamic())
            return;
        if (suim$mc.thePlayer == null || suim$mc.theWorld == null)
            return;

        try {
            suim$ensureRotationManagerReflection();
            Object rotMgr = suim$getRotationManager();
            if (rotMgr == null)
                return;

            float yawDelta = suim$yawDeltaField.getFloat(rotMgr);
            float pitchDelta = suim$pitchDeltaField.getFloat(rotMgr);
            if (Math.abs(yawDelta) < 0.001f
                    && Math.abs(pitchDelta) < 0.001f)
                return;

            EntityPlayer target = suim$findBestPlayer(this);
            if (target == null)
                return;

            AxisAlignedBB box = target.getEntityBoundingBox();
            float border = target.getCollisionBorderSize();
            AxisAlignedBB expandedBox = box.expand(border, border, border);

            float verticalMultipoint = RotationUtil.computeVerticalMultipoint(target.posY);
            if (verticalMultipoint == 0.5f)
                return;

            float smoothing = suim$getFloatProp(this, MyauMappings.FIELD_AIM_ASSIST_SMOOTHING, 50.0f) / 100.0f;
            float hSpeed = Math.min(
                    Math.abs(suim$getFloatProp(this, MyauMappings.FIELD_AIM_ASSIST_HSPEED, 3.0f)), 10.0f);
            float vSpeed = Math.min(
                    Math.abs(suim$getFloatProp(this, MyauMappings.FIELD_AIM_ASSIST_VSPEED, 0.0f)), 10.0f);

            float[] mpRotations = RotationUtil.getRotationsToBoxDynamic(
                    expandedBox,
                    suim$savedYaw, suim$savedPitch,
                    180.0f, smoothing, verticalMultipoint);

            float adjustedYaw = suim$savedYaw
                    + (mpRotations[0] - suim$savedYaw) * 0.1f * hSpeed;
            float adjustedPitch = suim$savedPitch
                    + (mpRotations[1] - suim$savedPitch) * 0.1f * vSpeed;

            float currentYaw = suim$mc.thePlayer.rotationYaw;
            float currentPitch = suim$mc.thePlayer.rotationPitch;

            suim$yawDeltaField.setFloat(rotMgr,
                    MathHelper.wrapAngleTo180_float(
                            adjustedYaw - currentYaw));
            suim$pitchDeltaField.setFloat(rotMgr,
                    MathHelper.clamp_float(
                            adjustedPitch - currentPitch,
                            -90.0f, 90.0f));
            suim$lastUpdateField.setFloat(rotMgr, 0.0f);
        } catch (Exception e) {
            MyauLogger.error("AimAssist:MultiPoint TAIL", e);
        }
    }
}
