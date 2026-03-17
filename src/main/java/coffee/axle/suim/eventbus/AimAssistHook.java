package coffee.axle.suim.eventbus;

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
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

import coffee.axle.suim.feature.SuffixRegistry;

/**
 * Event-bus replacement for MixinAimAssist.
 * my fucking god SuChen fuck you like actually fuck your antileak im going to jump
 *
 * <p>
 * <strong>Why not Mixin?</strong> In Myau 260313, {@code myau.HJ} (AimAssist) has SuChen's
 *  shitty anti-leak protection. The native hash check in {@code <clinit>} detects any bytecode
 * modification by the Mixin transformer, causing heap corruption crashes.
 * </p>
 *
 * <p>
 * <strong>How it works:</strong><br>
 * 1. Registers as a handler on Myau's internal event bus for {@code myau.b} (TickEvent POST)<br>
 * 2. Appended AFTER AimAssist's own handler in the handler list<br>
 * 3. After AimAssist runs and stores rotation deltas via {@code ZU.N(FFIZ)V},
 *    this handler reads the RM delta fields and overrides them with custom rotation<br>
 * 4. Deltas are consumed by RM's PreTick handler {@code ZU.G(V)} the next tick<br>
 * </p>
 *
 * <p>
 * <strong>Timing advantage over Mixin:</strong> The mixin injected before {@code ZU.N} and
 * set delta fields which were immediately overwritten by {@code ZU.N}. The event-bus handler
 * runs AFTER {@code ZU.N} has stored deltas, so overrides persist until consumed.
 * </p>
 *
 * @author axlecoffee
 */
public class AimAssistHook {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static AimAssistRotation aimRotation;
    private static boolean loggedInit = false;
    private static boolean suffixRegistered = false;
    private static long lastProcessedTick = -1;

    // Reflection caches — RotationManager
    private static Field rotationManagerField;
    private static Field yawDeltaField;
    private static Field pitchDeltaField;
    private static Field lastUpdateField;
    private static Field rmPriorityField;

    // Reflection caches — LagManager
    private static Field lagManagerField;
    private static Field lagMgrDelayTicksField;
    private static Field lagMgrIsLaggingField;
    private static boolean lagReflectionFailed = false;

    // Cached AimAssist module instance (from event bus handler map)
    private static Object aimAssistInstance;
    private static boolean instanceSearchDone = false;

    /**
     * Registers this handler on Myau's event bus.
     * Must be called after {@link MyauEventBusHook#tryInit()} returns {@code true}.
     *
     * @return {@code true} if registration succeeded
     */
    public static boolean register() {
        AimAssistHook hook = new AimAssistHook();
        return MyauEventBusHook.registerAfter(
                MyauMappings.CLASS_TICK_EVENT_POST, // myau.b
                hook,
                "onPostTick",
                Object.class,   // parameter type for dispatch compatibility
                0               // default priority
        );
    }

    /**
     * Event handler for {@code myau.b} (TickEvent POST).
     * Called by the event bus dispatch via {@code method.invoke(this, event)}.
     *
     * <p>This runs AFTER AimAssist's handler has stored rotation deltas.</p>
     *
     * @param event the TickEvent POST instance (myau.b) — treated as Object to avoid
     *              compile-time dependency on Myau event types
     */
    public void onPostTick(Object event) {
        try {
            if (!loggedInit) {
                loggedInit = true;
                MyauLogger.log("AimAssist:Hook", "event-bus handler active");
            }

            // Register suffix display
            if (!suffixRegistered) {
                suffixRegistered = true;
                registerSuffix();
            }

            if (!AimAssistExtras.isActive())
                return;

            int modeOrdinal = AimAssistExtras.getAimModeOrdinal();
            if (modeOrdinal == 0) // DEFAULT — let Myau's native aim-assist stand
                return;

            if (mc.thePlayer == null || mc.theWorld == null)
                return;

            // Myau's AimAssist only processes when the player is attacking (LMB held).
            // The mixin approach inherited this check by running inside HJ.g().
            // The eventbus approach runs every tick, so we must check explicitly.
            if (mc.currentScreen != null || !Mouse.isButtonDown(0))
                return;

            long currentTick = mc.theWorld.getTotalWorldTime();
            if (currentTick == lastProcessedTick)
                return;
            lastProcessedTick = currentTick;

            ensureRotationManagerReflection();
            Object rotMgr = getRotationManager();
            if (rotMgr == null)
                return;

            // Read deltas set by AimAssist → ZU.N
            float yawDelta = yawDeltaField.getFloat(rotMgr);
            float pitchDelta = pitchDeltaField.getFloat(rotMgr);

            // Only override if AimAssist actually set a rotation delta
            if (Math.abs(yawDelta) < 0.001f && Math.abs(pitchDelta) < 0.001f)
                return;

            // Find AimAssist instance for reading properties
            Object aaInstance = getAimAssistInstance();

            EntityPlayer target = findBestPlayer(aaInstance);
            if (target == null)
                return;

            // Configure rotation from AimAssistExtras settings
            AimAssistRotation aim = getAimRotation();
            aim.setMode(modeOrdinal);

            float hitboxBounds = AimAssistExtras.getHitboxBounds();
            aim.setHitboxBounds(1.0f - hitboxBounds, hitboxBounds);
            aim.setHeadBias(AimAssistExtras.getHeadBias());
            aim.setExtrapolationEnabled(AimAssistExtras.isExtrapolationEnabled());
            aim.setPingCompensation(AimAssistExtras.isPingCompEnabled());
            aim.setContractionRange(AimAssistExtras.getContractionMin(), AimAssistExtras.getContractionMax());

            float smoothing = getFloatProp(aaInstance, MyauMappings.FIELD_AIM_ASSIST_SMOOTHING, 50.0f) / 100.0f;
            float hSpeed = Math.min(
                    Math.abs(getFloatProp(aaInstance, MyauMappings.FIELD_AIM_ASSIST_HSPEED, 3.0f)), 10.0f);
            float vSpeed = Math.min(
                    Math.abs(getFloatProp(aaInstance, MyauMappings.FIELD_AIM_ASSIST_VSPEED, 0.0f)), 10.0f);
            aim.setLagDelayTicks(getLagDelayTicks());

            float[] result = aim.aimAtEntity(target, smoothing, hSpeed, vSpeed, true);
            if (result == null)
                return;

            // Override RM deltas — these will be consumed by ZU.G(V) next tick
            float currentYaw = mc.thePlayer.rotationYaw;
            float currentPitch = mc.thePlayer.rotationPitch;

            yawDeltaField.setFloat(rotMgr,
                    MathHelper.wrapAngleTo180_float(result[0] - currentYaw));
            pitchDeltaField.setFloat(rotMgr,
                    MathHelper.clamp_float(result[1] - currentPitch, -90.0f, 90.0f));
            lastUpdateField.setFloat(rotMgr, 0.0f);

        } catch (Exception e) {
            MyauLogger.error("AimAssist:Hook tick", e);
        }
    }

    // --- Reflection helpers (ported from MixinAimAssist @Unique methods) ---

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
    }

    private static Object getRotationManager() throws Exception {
        return rotationManagerField.get(null);
    }

    private static Object getAimAssistInstance() {
        if (aimAssistInstance != null) return aimAssistInstance;
        if (instanceSearchDone) return null;
        instanceSearchDone = true;
        aimAssistInstance = MyauEventBusHook.findModuleInstance(
                MyauMappings.CLASS_TICK_EVENT_POST,
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

        float range;
        int fov;
        try {
            range = getFloatProp(aaInstance, MyauMappings.FIELD_AIM_ASSIST_RANGE, 4.5f);
            fov = getIntProp(aaInstance, MyauMappings.FIELD_AIM_ASSIST_FOV, 90);
        } catch (Exception e) {
            range = 4.5f;
            fov = 90;
        }

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

            if (dist < closest) {
                closest = dist;
                best = player;
            }
        }
        return best;
    }

    private static float getFloatProp(Object instance, String fieldName, float fallback) {
        if (instance == null) return fallback;
        try {
            Field f = instance.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object prop = f.get(instance);
            if (prop == null) return fallback;
            Method getValue = prop.getClass().getMethod(MyauMappings.METHOD_PROPERTY_GET_VALUE);
            Object val = getValue.invoke(prop);
            if (val instanceof Number) return ((Number) val).floatValue();
        } catch (Exception ignored) {}
        return fallback;
    }

    private static int getIntProp(Object instance, String fieldName, int fallback) {
        if (instance == null) return fallback;
        try {
            Field f = instance.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object prop = f.get(instance);
            if (prop == null) return fallback;
            Method getValue = prop.getClass().getMethod(MyauMappings.METHOD_PROPERTY_GET_VALUE);
            Object val = getValue.invoke(prop);
            if (val instanceof Number) return ((Number) val).intValue();
        } catch (Exception ignored) {}
        return fallback;
    }

    private void registerSuffix() {
        SuffixRegistry.register(this, () -> {
            if (!AimAssistExtras.isActive()) return null;
            int ord = AimAssistExtras.getAimModeOrdinal();
            if (ord <= 0 || ord >= AimAssistRotation.MODE_NAMES.length) return null;
            return new String[]{ AimAssistRotation.MODE_NAMES[ord].toLowerCase(Locale.ROOT) };
        });
    }
}
