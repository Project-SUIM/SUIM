package coffee.axle.suim.mixin;

import coffee.axle.suim.feature.combat.AimAssistExtras;
import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.rotation.AimAssistRotation;
import coffee.axle.suim.rotation.RotationMath;
import coffee.axle.suim.rotation.RotationState;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
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
import java.util.Locale;

import coffee.axle.suim.feature.SuffixRegistry;

/**
 * Hooks Myau's AimAssist (myau.mT) to override rotation deltas based on
 * the aim-mode selected in {@link AimAssistExtras}.
 * <p>
 * Strategy: HEAD saves current yaw/pitch. TAIL reads the RM delta fields
 * set by Myau, re-computes them using {@link AimAssistRotation} with the
 * selected mode, and overwrites the RM deltas.
 * <p>
 * For DEFAULT mode (ordinal 0), no override is applied — Myau's native
 * aim-assist runs unmodified.
 *
 * @author axle.coffee
 */
@Pseudo
@Mixin(targets = "myau.mT", remap = false)
public class MixinAimAssist {

    @Unique
    private static final Minecraft suim$mc = Minecraft.getMinecraft();

    @Unique
    private static AimAssistRotation suim$aimRotation;

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
    private static boolean suim$suffixRegistered = false;

    @Unique
    private static float suim$savedYaw, suim$savedPitch;

    @Unique
    private static long suim$lastProcessedTick = -1;

    @Unique
    private static Field suim$lagManagerField;
    @Unique
    private static Field suim$lagMgrDelayTicksField;
    @Unique
    private static Field suim$lagMgrIsLaggingField;
    @Unique
    private static boolean suim$lagReflectionFailed = false;

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
    private static void suim$ensureLagManagerReflection() {
        if (suim$lagReflectionFailed || suim$lagManagerField != null)
            return;
        try {
            Class<?> clientClass = Class.forName(MyauMappings.CLASS_MAIN);
            suim$lagManagerField = clientClass.getDeclaredField(MyauMappings.FIELD_LAG_MANAGER);
            suim$lagManagerField.setAccessible(true);

            Class<?> lagClass = Class.forName(MyauMappings.CLASS_LAG_MANAGER);
            suim$lagMgrDelayTicksField = lagClass.getDeclaredField(MyauMappings.FIELD_LAG_MGR_DELAY_TICKS);
            suim$lagMgrDelayTicksField.setAccessible(true);
            suim$lagMgrIsLaggingField = lagClass.getDeclaredField(MyauMappings.FIELD_LAG_MGR_IS_LAGGING);
            suim$lagMgrIsLaggingField.setAccessible(true);
        } catch (Exception e) {
            suim$lagReflectionFailed = true;
            MyauLogger.error("AimAssist:LagManager reflection", e);
        }
    }

    @Unique
    private static int suim$getLagDelayTicks() {
        if (suim$lagReflectionFailed)
            return 0;
        suim$ensureLagManagerReflection();
        try {
            Object lagMgr = suim$lagManagerField.get(null);
            if (lagMgr == null)
                return 0;
            boolean isLagging = suim$lagMgrIsLaggingField.getBoolean(lagMgr);
            if (!isLagging)
                return 0;
            return suim$lagMgrDelayTicksField.getInt(lagMgr);
        } catch (Exception e) {
            return 0;
        }
    }

    @Unique
    private static AimAssistRotation suim$getAimRotation() {
        if (suim$aimRotation == null) {
            suim$aimRotation = new AimAssistRotation(RotationState.getInstance());
        }
        return suim$aimRotation;
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

            double dist = RotationMath.distanceToEntity(player);
            if (dist > range)
                continue;

            float angle = RotationMath.angleToEntity(player);
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
                    "AimAssist:Modes", "mixin applied, injects active");
        }
        if (!suim$suffixRegistered) {
            suim$suffixRegistered = true;
            SuffixRegistry.register(this, () -> {
                if (!AimAssistExtras.isActive()) return null;
                int ord = AimAssistExtras.getAimModeOrdinal();
                if (ord <= 0 || ord >= AimAssistRotation.MODE_NAMES.length) return null;
                return new String[] { AimAssistRotation.MODE_NAMES[ord].toLowerCase(Locale.ROOT) };
            });
        }
        if (suim$mc.thePlayer != null) {
            suim$savedYaw = suim$mc.thePlayer.rotationYaw;
            suim$savedPitch = suim$mc.thePlayer.rotationPitch;
            RotationState.getInstance().updatePing();
        }
    }

    /**
     * After Myau's AimAssist runs, check the aim-mode from AimAssistExtras.
     * If not DEFAULT: find the target, compute rotations via AimAssistRotation,
     * and overwrite the RM delta fields.
     */
    @Inject(method = "d(Lmyau/KP;)V", at = @At("TAIL"), remap = false, require = 0)
    private void suim$onTickTail(
            @Coerce Object tickEvent, CallbackInfo ci) {
        if (!AimAssistExtras.isActive())
            return;

        int modeOrdinal = AimAssistExtras.getAimModeOrdinal();
        if (modeOrdinal == 0) // DEFAULT — passthrough
            return;

        if (suim$mc.thePlayer == null || suim$mc.theWorld == null)
            return;

        long currentTick = suim$mc.theWorld.getTotalWorldTime();
        if (currentTick == suim$lastProcessedTick)
            return;
        suim$lastProcessedTick = currentTick;

        try {
            suim$ensureRotationManagerReflection();
            Object rotMgr = suim$getRotationManager();
            if (rotMgr == null)
                return;

            float yawDelta = suim$yawDeltaField.getFloat(rotMgr);
            float pitchDelta = suim$pitchDeltaField.getFloat(rotMgr);
            // Only override if Myau actually set a rotation delta
            if (Math.abs(yawDelta) < 0.001f
                    && Math.abs(pitchDelta) < 0.001f)
                return;

            EntityPlayer target = suim$findBestPlayer(this);
            if (target == null)
                return;

            // Configure AimAssistRotation from injected properties
            AimAssistRotation aim = suim$getAimRotation();
            aim.setMode(modeOrdinal);

            float hitboxBounds = AimAssistExtras.getHitboxBounds();
            aim.setHitboxBounds(1.0f - hitboxBounds, hitboxBounds);

            aim.setHeadBias(AimAssistExtras.getHeadBias());
            aim.setExtrapolationEnabled(AimAssistExtras.isExtrapolationEnabled());
            aim.setPingCompensation(AimAssistExtras.isPingCompEnabled());
            aim.setContractionRange(AimAssistExtras.getContractionMin(), AimAssistExtras.getContractionMax());

            float smoothing = suim$getFloatProp(this, MyauMappings.FIELD_AIM_ASSIST_SMOOTHING, 50.0f) / 100.0f;
            float hSpeed = Math.min(
                    Math.abs(suim$getFloatProp(this, MyauMappings.FIELD_AIM_ASSIST_HSPEED, 3.0f)), 10.0f);
            float vSpeed = Math.min(
                    Math.abs(suim$getFloatProp(this, MyauMappings.FIELD_AIM_ASSIST_VSPEED, 0.0f)), 10.0f);

            aim.setLagDelayTicks(suim$getLagDelayTicks());

            float[] result = aim.aimAtEntity(target, smoothing, hSpeed, vSpeed, true);
            if (result == null)
                return;

            float currentYaw = suim$mc.thePlayer.rotationYaw;
            float currentPitch = suim$mc.thePlayer.rotationPitch;

            suim$yawDeltaField.setFloat(rotMgr,
                    MathHelper.wrapAngleTo180_float(
                            result[0] - currentYaw));
            suim$pitchDeltaField.setFloat(rotMgr,
                    MathHelper.clamp_float(
                            result[1] - currentPitch,
                            -90.0f, 90.0f));
            suim$lastUpdateField.setFloat(rotMgr, 0.0f);
        } catch (Exception e) {
            MyauLogger.error("AimAssist:Modes TAIL", e);
        }
    }

}
