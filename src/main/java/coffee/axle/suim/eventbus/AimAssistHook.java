package coffee.axle.suim.eventbus;

import coffee.axle.suim.feature.combat.AimAssistExtras;
import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.rotation.AimAssistRotation;
import coffee.axle.suim.rotation.RotationMath;
import coffee.axle.suim.rotation.RotationState;
import net.minecraft.entity.EntityLivingBase;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

import coffee.axle.suim.feature.SuffixRegistry;

// event-bus registration leaves hash-checked AimAssist bytecode untouched
public class AimAssistHook {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static AimAssistRotation aimRotation;
    private static boolean loggedInit = false;
    private static boolean suffixRegistered = false;
    private static long lastProcessedTick = -1;
    private static World aimWorld;
    private static EntityLivingBase lastAimingTarget;
    private static EntityLivingBase outputTarget;
    private static EntityLivingBase pendingSnapTarget;
    private static long snapRequestTick = -1;

    private static Method nativeTargetMethod;
    private static long nativeTargetKey;
    private static Method nativeBoxRotationsMethod;
    private static Field nativeOutputField;
    private static Field nativeIntegratedField;

    private static Field moduleEnabledField;
    private static Field rotationManagerField;
    private static Field yawDeltaField;
    private static Field pitchDeltaField;
    private static Field lastUpdateField;
    private static Field rmPriorityField;
    private static Method rmSetRotationMethod;
    private static boolean rmSetRotationFailed = false;

    private static Field lagManagerField;
    private static Field lagMgrDelayTicksField;
    private static Field lagMgrIsLaggingField;
    private static boolean lagReflectionFailed = false;

    // Cached AimAssist module instance (from event bus handler map)
    private static Object aimAssistInstance;
    private static boolean instanceSearchDone = false;

    // Reflection caches - TeamUtil (myau.kY) bot/team filters
    private static Method isBotMethod;
    private static Method isTeamMethod;
    @SuppressWarnings("unused")
    private static boolean teamUtilFailed = false;

    /**
     * Registers this handler on Myau's event bus.
     * Must be called after {@link MyauEventBusHook#tryInit()} returns {@code true}.
     *
     * @return {@code true} if registration succeeded
     */
    public static boolean register() {
        AimAssistHook hook = new AimAssistHook();
        return MyauEventBusHook.registerAfter(
                MyauMappings.CLASS_LIVING_UPDATE_EVENT, // myau.k7
                hook,
                "onPostTick",
                Object.class,   // parameter type for dispatch compatibility
                0               // default priority
        );
    }

    public void onPostTick(Object event) {
        try {
            if (!loggedInit) {
                loggedInit = true;
                MyauLogger.log("AimAssist:Hook", "event-bus handler active");
            }

            updateWorld();
            if (mc.thePlayer == null || mc.theWorld == null) {
                lastAimingTarget = null;
                pendingSnapTarget = null;
                return;
            }

            Object aaInstance = getAimAssistInstance();

            // keyed on the myau.G instance, not this hook - the HUD looks
            // the suffix up by module object
            if (!suffixRegistered && aaInstance != null) {
                suffixRegistered = true;
                registerSuffix(aaInstance);
            }

            if (!isAimAssistEnabled(aaInstance)) {
                lastAimingTarget = null;
                pendingSnapTarget = null;
                return;
            }

            ensureRotationManagerReflection();
            Object rotMgr = getRotationManager();
            if (rotMgr == null || lastUpdateField.getFloat(rotMgr) != 0.0f
                    || rmPriorityField.getInt(rotMgr) != 0) {
                lastAimingTarget = null;
                pendingSnapTarget = null;
                return;
            }

            long currentTick = mc.theWorld.getTotalWorldTime();
            if (currentTick == lastProcessedTick)
                return;
            lastProcessedTick = currentTick;
            lastAimingTarget = null;

            EntityLivingBase requested = pendingSnapTarget;
            pendingSnapTarget = null;
            if (currentTick < snapRequestTick || currentTick - snapRequestTick > 1)
                requested = null;

            int modeOrdinal = AimAssistExtras.isActive() ? AimAssistExtras.getAimModeOrdinal() : 0;
            EntityLivingBase target;
            if (modeOrdinal == 0) {
                if (nativeTargetMethod == null) {
                    Class<?> aaClass = Class.forName(MyauMappings.CLASS_AIM_ASSIST);
                    Field key = aaClass.getDeclaredField("c");
                    key.setAccessible(true);
                    nativeTargetKey = key.getLong(null) ^ 0x03b549d59f77L;
                    nativeTargetMethod = aaClass.getDeclaredMethod("B", long.class);
                    nativeTargetMethod.setAccessible(true);
                }
                target = (EntityLivingBase) nativeTargetMethod.invoke(aaInstance, nativeTargetKey);
            } else {
                target = findBestPlayer(aaInstance);
            }
            if (target == null)
                return;

            outputTarget = target;
            if (modeOrdinal == 0)
                lastAimingTarget = target;
            if (requested == target) {
                if (snapTo(target) != null)
                    lastAimingTarget = target;
                return;
            }
            if (modeOrdinal == 0)
                return;

            AimAssistRotation aim = getAimRotation();
            aim.setMode(modeOrdinal);

            float hitboxBounds = AimAssistExtras.getHitboxBounds();
            aim.setHitboxBounds(1.0f - hitboxBounds, hitboxBounds);
            aim.setHeadBias(AimAssistExtras.getHeadBias());
            aim.setExtrapolationEnabled(AimAssistExtras.isExtrapolationEnabled());
            aim.setPingCompensation(AimAssistExtras.isPingCompEnabled());
            aim.setContractionRange(AimAssistExtras.getContractionMin(), AimAssistExtras.getContractionMax());

            // Myau's own tuning: smoothing %, hSpeed, vSpeed (260729 fields O/L/u).
            float smoothing = getFloatProp(aaInstance, MyauMappings.FIELD_AIM_ASSIST_SMOOTHING, 50.0f) / 100.0f;
            float hSpeed = Math.abs(getFloatProp(aaInstance, MyauMappings.FIELD_AIM_ASSIST_HSPEED, 3.0f));
            float vSpeed = Math.abs(getFloatProp(aaInstance, MyauMappings.FIELD_AIM_ASSIST_VSPEED, 0.0f));
            aim.setLagDelayTicks(getLagDelayTicks());

            float[] result = aim.aimAtEntity(target, smoothing, hSpeed, vSpeed, true);
            if (result == null)
                return;

            float currentYaw = mc.thePlayer.rotationYaw;
            float currentPitch = mc.thePlayer.rotationPitch;

            yawDeltaField.setFloat(rotMgr,
                    MathHelper.wrapAngleTo180_float(result[0] - currentYaw));
            pitchDeltaField.setFloat(rotMgr,
                    MathHelper.clamp_float(result[1] - currentPitch, -90.0f, 90.0f));
            lastUpdateField.setFloat(rotMgr, 0.0f);
            lastAimingTarget = target;

        } catch (Exception e) {
            lastAimingTarget = null;
            pendingSnapTarget = null;
            MyauLogger.error("AimAssist:Hook tick", e);
        } finally {
            outputTarget = null;
        }
    }

    public static boolean isAimingAt(Entity entity) {
        updateWorld();
        if (mc.thePlayer == null || mc.theWorld == null || !isAimAssistEnabled(getAimAssistInstance())) {
            lastAimingTarget = null;
            pendingSnapTarget = null;
            return false;
        }
        return entity != null && entity == lastAimingTarget && entity.worldObj == aimWorld
            && lastProcessedTick == mc.theWorld.getTotalWorldTime();
    }

    // aim assist's live target, for other modules (backtrack scan)
    public static EntityLivingBase currentTarget() {
        return outputTarget;
    }

    public static void requestSnap(EntityLivingBase target) {
        updateWorld();
        pendingSnapTarget = null;
        if (mc.thePlayer == null || mc.theWorld == null || !isAimAssistEnabled(getAimAssistInstance())) {
            lastAimingTarget = null;
            return;
        }
        if (target == null || target.worldObj != aimWorld)
            return;
        pendingSnapTarget = target;
        snapRequestTick = mc.theWorld.getTotalWorldTime();
    }

    private static void updateWorld() {
        if (aimWorld == mc.theWorld)
            return;
        aimWorld = mc.theWorld;
        lastProcessedTick = -1;
        lastAimingTarget = null;
        outputTarget = null;
        pendingSnapTarget = null;
        snapRequestTick = -1;
    }

    private static void ensureRotationManagerReflection() throws Exception {
        if (rotationManagerField == null) {
            Class<?> clientClass = Class.forName(MyauMappings.CLASS_MAIN);
            rotationManagerField = clientClass.getDeclaredField(MyauMappings.FIELD_ROTATION_MANAGER);
            rotationManagerField.setAccessible(true);
        }
        Class<?> rmClass = Class.forName(MyauMappings.CLASS_ROTATION_MANAGER);
        if (yawDeltaField == null) {
            yawDeltaField = rmClass.getDeclaredField(MyauMappings.FIELD_ROT_MGR_YAW_DELTA);
            yawDeltaField.setAccessible(true);
        }
        if (pitchDeltaField == null) {
            pitchDeltaField = rmClass.getDeclaredField(MyauMappings.FIELD_ROT_MGR_PITCH_DELTA);
            pitchDeltaField.setAccessible(true);
        }
        if (lastUpdateField == null) {
            lastUpdateField = rmClass.getDeclaredField(MyauMappings.FIELD_ROT_MGR_LAST_UPDATE);
            lastUpdateField.setAccessible(true);
        }
        if (rmPriorityField == null) {
            rmPriorityField = rmClass.getDeclaredField(MyauMappings.FIELD_ROT_MGR_PRIORITY);
            rmPriorityField.setAccessible(true);
        }
        if (rmSetRotationMethod == null && !rmSetRotationFailed) {
            try {
                rmSetRotationMethod = rmClass.getDeclaredMethod(
                        MyauMappings.METHOD_ROT_MGR_SET_ROTATION,
                        float.class, float.class, int.class, boolean.class);
                rmSetRotationMethod.setAccessible(true);
            } catch (Exception e) {
                rmSetRotationFailed = true;
                MyauLogger.error("AimAssist:Hook rot", e);
            }
        }
    }

    public static float[] snapTo(EntityLivingBase target) {
        try {
            updateWorld();
            if (mc.thePlayer == null || mc.theWorld == null || target == null
                    || target != outputTarget || target.worldObj != aimWorld)
                return null;
            ensureRotationManagerReflection();
            if (rmSetRotationMethod == null)
                return null;
            Object rotMgr = getRotationManager();
            if (rotMgr == null || lastUpdateField.getFloat(rotMgr) != 0.0f
                    || rmPriorityField.getInt(rotMgr) != 0)
                return null;
            Object aaInstance = getAimAssistInstance();
            if (!isAimAssistEnabled(aaInstance))
                return null;

            Class<?> aaClass = Class.forName(MyauMappings.CLASS_AIM_ASSIST);
            if (nativeOutputField == null) {
                nativeOutputField = aaClass.getDeclaredField("P");
                nativeOutputField.setAccessible(true);
            }
            if (nativeIntegratedField == null) {
                nativeIntegratedField = aaClass.getDeclaredField("S");
                nativeIntegratedField.setAccessible(true);
            }
            float[] output = (float[]) nativeOutputField.get(aaInstance);
            float[] integrated = (float[]) nativeIntegratedField.get(aaInstance);

            AimAssistRotation aim = getAimRotation();
            aim.setMode(AimAssistExtras.isActive() ? AimAssistExtras.getAimModeOrdinal() : 0);
            float hitboxBounds = AimAssistExtras.getHitboxBounds();
            aim.setHitboxBounds(1.0f - hitboxBounds, hitboxBounds);
            aim.setHeadBias(AimAssistExtras.getHeadBias());
            aim.setExtrapolationEnabled(AimAssistExtras.isExtrapolationEnabled());
            aim.setPingCompensation(AimAssistExtras.isPingCompEnabled());
            aim.setContractionRange(AimAssistExtras.getContractionMin(), AimAssistExtras.getContractionMax());
            aim.setLagDelayTicks(getLagDelayTicks());

            float[] raw;
            if (aim.isDefaultMode()) {
                if (nativeBoxRotationsMethod == null) {
                    nativeBoxRotationsMethod = Class.forName(MyauMappings.CLASS_ROTATION_UTIL).getMethod(
                            MyauMappings.METHOD_GET_ROTATIONS_TO_BOX, AxisAlignedBB.class);
                }
                float border = target.getCollisionBorderSize();
                raw = (float[]) nativeBoxRotationsMethod.invoke(null,
                        target.getEntityBoundingBox().expand(border, border, border));
            } else {
                raw = aim.computeTarget(target, true);
            }
            if (raw == null)
                return null;
            float[] snapped = aim.snapToTarget(raw[0], raw[1]);
            // myau class (260719) kj.W subtracts the camera angles itself
            rmSetRotationMethod.invoke(rotMgr, snapped[0], snapped[1], 0, false);
            output[0] = integrated[0] = snapped[0];
            output[1] = integrated[1] = snapped[1];
            return snapped;
        } catch (Exception e) {
            MyauLogger.error("AimAssist:Hook snap", e);
            return null;
        }
    }

    private static boolean isAimAssistEnabled(Object aaInstance) {
        if (aaInstance == null) return false;
        try {
            if (moduleEnabledField == null) {
                Class<?> moduleBase = Class.forName(MyauMappings.CLASS_MODULE_BASE);
                moduleEnabledField = moduleBase.getDeclaredField(MyauMappings.FIELD_MODULE_ENABLED);
                moduleEnabledField.setAccessible(true);
            }
            return moduleEnabledField.getBoolean(aaInstance);
        } catch (Exception e) {
            MyauLogger.error("AimAssist:Hook enabled", e);
            return false;
        }
    }

    private static Object getRotationManager() throws Exception {
        return rotationManagerField.get(null);
    }

    private static Object getAimAssistInstance() {
        if (aimAssistInstance != null) return aimAssistInstance;
        if (instanceSearchDone) return null;
        instanceSearchDone = true;
        aimAssistInstance = MyauEventBusHook.findModuleInstance(
                MyauMappings.CLASS_LIVING_UPDATE_EVENT,
                MyauMappings.CLASS_AIM_ASSIST
        );
        if (aimAssistInstance == null) {
            MyauLogger.log("AimAssist:Hook", "WARNING: could not find AimAssist instance in event bus");
        }
        return aimAssistInstance;
    }

    private static void ensureLagManagerReflection() {
        if (lagReflectionFailed || lagManagerField != null) return;
        try {
            Class<?> clientClass = Class.forName(MyauMappings.CLASS_MAIN);
            lagManagerField = clientClass.getDeclaredField(MyauMappings.FIELD_LAG_MANAGER);
            lagManagerField.setAccessible(true);
            Class<?> lagClass = Class.forName(MyauMappings.CLASS_LAG_MANAGER);
            lagMgrDelayTicksField = lagClass.getDeclaredField(MyauMappings.FIELD_LAG_MGR_DELAY_TICKS);
            lagMgrDelayTicksField.setAccessible(true);
            lagMgrIsLaggingField = lagClass.getDeclaredField(MyauMappings.FIELD_LAG_MGR_IS_LAGGING);
            lagMgrIsLaggingField.setAccessible(true);
        } catch (Exception e) {
            lagReflectionFailed = true;
            MyauLogger.error("AimAssist:Hook LagManager reflection", e);
        }
    }

    private static int getLagDelayTicks() {
        if (lagReflectionFailed) return 0;
        ensureLagManagerReflection();
        try {
            Object lagMgr = lagManagerField.get(null);
            if (lagMgr == null) return 0;
            boolean isLagging = lagMgrIsLaggingField.getBoolean(lagMgr);
            if (!isLagging) return 0;
            return lagMgrDelayTicksField.getInt(lagMgr);
        } catch (Exception e) {
            return 0;
        }
    }

    private static AimAssistRotation getAimRotation() {
        if (aimRotation == null) {
            aimRotation = new AimAssistRotation(RotationState.getInstance());
        }
        return aimRotation;
    }

    private static EntityPlayer findBestPlayer(Object aaInstance) {
        if (mc.theWorld == null || mc.thePlayer == null) return null;

        float range = getFloatProp(aaInstance, MyauMappings.FIELD_AIM_ASSIST_RANGE, 4.5f);
        int fov = getIntProp(aaInstance, MyauMappings.FIELD_AIM_ASSIST_FOV, 90);

        EntityPlayer best = null;
        double closest = range;

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityPlayer)) continue;
            EntityPlayer player = (EntityPlayer) entity;
            if (player == mc.thePlayer) continue;
            if (player.deathTime > 0) continue;
            if (player.isInvisible()) continue;

            double dist = RotationMath.distanceToEntity(player);
            if (dist > range) continue;
            float angle = RotationMath.angleToEntity(player);
            if (angle > (float) fov) continue;

            if (isBotOrTeam(aaInstance, player)) continue;

            if (dist < closest) {
                closest = dist;
                best = player;
            }
        }
        return best;
    }

    private static float getFloatProp(Object instance, String fieldName, float fallback) {
        Object val = getProp(instance, fieldName);
        return val instanceof Number ? ((Number) val).floatValue() : fallback;
    }

    private static int getIntProp(Object instance, String fieldName, int fallback) {
        Object val = getProp(instance, fieldName);
        return val instanceof Number ? ((Number) val).intValue() : fallback;
    }

    private static boolean getBoolProp(Object instance, String fieldName, boolean fallback) {
        Object val = getProp(instance, fieldName);
        return val instanceof Boolean ? (Boolean) val : fallback;
    }

    private static Object getProp(Object instance, String fieldName) {
        if (instance == null) return null;
        try {
            Field f = instance.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object prop = f.get(instance);
            if (prop == null) return null;
            return prop.getClass().getMethod(MyauMappings.METHOD_PROPERTY_GET_VALUE).invoke(prop);
        } catch (Exception ignored) {}
        return null;
    }

    // myau.G.s() skip Hypixel bots when bot-check is on, same-team players when team filter is on
    private static boolean isBotOrTeam(Object aaInstance, EntityPlayer player) {
        if (aaInstance == null) return false;
        try {
            if (getBoolProp(aaInstance, MyauMappings.FIELD_AIM_ASSIST_BOT_CHECKS, false)
                    && (Boolean) isBotMethod().invoke(null, player)) {
                return true;
            }
            return getBoolProp(aaInstance, MyauMappings.FIELD_AIM_ASSIST_TEAM, false)
                    && (Boolean) isTeamMethod().invoke(null, player);
        } catch (Exception e) {
            teamUtilFailed = true;
            return false;
        }
    }

    private static Method isBotMethod() throws Exception {
        if (isBotMethod == null) {
            isBotMethod = Class.forName(MyauMappings.CLASS_TEAM_UTIL)
                    .getMethod(MyauMappings.METHOD_TEAM_UTIL_IS_BOT, EntityPlayer.class);
        }
        return isBotMethod;
    }

    private static Method isTeamMethod() throws Exception {
        if (isTeamMethod == null) {
            isTeamMethod = Class.forName(MyauMappings.CLASS_TEAM_UTIL)
                    .getMethod(MyauMappings.METHOD_TEAM_UTIL_IS_TEAM, EntityPlayer.class);
        }
        return isTeamMethod;
    }

    private static void registerSuffix(Object module) {
        SuffixRegistry.register(module, () -> {
            if (!AimAssistExtras.isActive()) return null;
            int ord = AimAssistExtras.getAimModeOrdinal();
            if (ord <= 0 || ord >= AimAssistRotation.MODE_NAMES.length) return null;
            return new String[]{ AimAssistRotation.MODE_NAMES[ord].toLowerCase(Locale.ROOT) };
        });
    }
}
