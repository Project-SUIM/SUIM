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
 * <strong>Why not Mixin?</strong> In Myau 260729, {@code myau.G} (AimAssist) has SuChen's
 *  shitty anti-leak protection. The native hash check in {@code <clinit>} detects any bytecode
 * modification by the Mixin transformer, causing heap corruption crashes.
 * </p>
 *
 * <p>
 * <strong>How it works:</strong><br>
 * 1. Registers as a handler on Myau's internal event bus for {@code myau.k7} (LivingUpdateEvent)<br>
 * 2. Appended AFTER AimAssist's own handler in the handler list<br>
 * 3. After AimAssist runs and stores rotation deltas via {@code kj.W(FFIZ)V},
 *    this handler reads the RM delta fields and overrides them with custom rotation<br>
 * 4. Deltas are consumed by RotationManager on its next handler dispatch<br>
 * </p>
 *
 * <p>
 * <strong>Timing advantage over Mixin:</strong> The mixin injected before {@code v4.K} and
 * set delta fields which were immediately overwritten by {@code v4.K}. The event-bus handler
 * runs AFTER {@code v4.K} has stored deltas, so overrides persist until consumed.
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

    // Reflection caches - RotationManager
    private static Field moduleEnabledField;
    private static Field rotationManagerField;
    private static Field yawDeltaField;
    private static Field pitchDeltaField;
    private static Field lastUpdateField;
    private static Field rmPriorityField;

    // Reflection caches - LagManager
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

    /**
     * Event handler for {@code myau.k7} (LivingUpdateEvent).
     * Called by the event bus dispatch via {@code method.invoke(this, event)}.
     *
     * <p>This runs AFTER AimAssist's handler has stored rotation deltas.</p>
     *
     * @param event the LivingUpdateEvent instance (myau.k7) - treated as Object to avoid
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
            if (modeOrdinal == 0) // DEFAULT - let Myau's native aim-assist stand
                return;

            if (mc.thePlayer == null || mc.theWorld == null)
                return;

            ensureRotationManagerReflection();
            Object rotMgr = getRotationManager();
            if (rotMgr == null)
                return;

            Object aaInstance = getAimAssistInstance();

            // Myau gates its own e(k7) on isEnabled and leaves kj.f/I stale after
            // disable. Honor the module state so custom rotation dies with the toggle.
            if (!isAimAssistEnabled(aaInstance)) {
                yawDeltaField.setFloat(rotMgr, 0.0f);
                pitchDeltaField.setFloat(rotMgr, 0.0f);
                lastUpdateField.setFloat(rotMgr, 0.0f);
                return;
            }

            // Myau's AimAssist only processes while attacking; mirror that gate.
            // Outside it we must still CLEAR any delta Myau's own handler left
            // behind this tick, or its rotation leaks through on these paths.
            if (mc.currentScreen != null || !Mouse.isButtonDown(0)) {
                clearNativeDeltas(rotMgr);
                return;
            }

            long currentTick = mc.theWorld.getTotalWorldTime();
            if (currentTick == lastProcessedTick) {
                clearNativeDeltas(rotMgr);
                return;
            }
            lastProcessedTick = currentTick;

            // Read deltas set by AimAssist -> kj.f/I
            float yawDelta = yawDeltaField.getFloat(rotMgr);
            float pitchDelta = pitchDeltaField.getFloat(rotMgr);

            // Only override if AimAssist actually set a rotation delta
            if (Math.abs(yawDelta) < 0.001f && Math.abs(pitchDelta) < 0.001f) {
                clearNativeDeltas(rotMgr);
                return;
            }

            EntityPlayer target = findBestPlayer(aaInstance);
            if (target == null) {
                clearNativeDeltas(rotMgr);
                return;
            }

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

            // Override RM deltas - consumed by RotationManager on its next handler dispatch
            float currentYaw = mc.thePlayer.rotationYaw;
            float currentPitch = mc.thePlayer.rotationPitch;

            yawDeltaField.setFloat(rotMgr,
                    MathHelper.wrapAngleTo180_float(result[0] - currentYaw));
            pitchDeltaField.setFloat(rotMgr,
                    MathHelper.clamp_float(result[1] - currentPitch, -90.0f, 90.0f));
            lastUpdateField.setFloat(rotMgr, 0.0f);
            // park Myau's own integrator on the current view so it doesn't keep
            // pulling toward its multipoint target underneath our override
            resetNativeSmoothing(aaInstance);

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

    // Kill whatever rotation Myau's own AimAssist handler stored this tick.
    // Without this, its multipoint target leaks through on every path where we
    // don't override (no target, not attacking, etc).
    private static void clearNativeDeltas(Object rotMgr) {
        try {
            float y = yawDeltaField.getFloat(rotMgr);
            float p = pitchDeltaField.getFloat(rotMgr);
            if (Math.abs(y) >= 0.001f || Math.abs(p) >= 0.001f) {
                yawDeltaField.setFloat(rotMgr, 0.0f);
                pitchDeltaField.setFloat(rotMgr, 0.0f);
                lastUpdateField.setFloat(rotMgr, 0.0f);
            }
        } catch (Exception ignored) {
        }
    }

    // Reset Myau's internal smoothing state (G.P/S float[2]). Its e(k7)
    // integrates toward ITS multipoint target every tick even while we override
    // the output; leaving it running makes the two drivers fight and the aim
    // overrotates/jitters. Re-seeding P/S to the current view angles keeps its
    // integrator parked on where the player is actually looking.
    private static void resetNativeSmoothing(Object aaInstance) {
        if (aaInstance == null) return;
        try {
            for (String fn : new String[]{MyauMappings.FIELD_AIM_ASSIST_SMOOTH_STATE_YAW,
                                          MyauMappings.FIELD_AIM_ASSIST_SMOOTH_STATE_PITCH}) {
                Field f = aaInstance.getClass().getDeclaredField(fn);
                f.setAccessible(true);
                float[] arr = (float[]) f.get(aaInstance);
                if (arr == null || arr.length < 2) continue;
                arr[0] = mc.thePlayer.rotationYaw;
                arr[1] = mc.thePlayer.rotationPitch;
            }
        } catch (Exception ignored) {
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
            return true; // fail open - don't break rotation if reflection hiccups
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

    // Mirrors myau.G.s(): skip Hypixel bots when bot-check is on, same-team players when team filter is on
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

    private void registerSuffix() {
        SuffixRegistry.register(this, () -> {
            if (!AimAssistExtras.isActive()) return null;
            int ord = AimAssistExtras.getAimModeOrdinal();
            if (ord <= 0 || ord >= AimAssistRotation.MODE_NAMES.length) return null;
            return new String[]{ AimAssistRotation.MODE_NAMES[ord].toLowerCase(Locale.ROOT) };
        });
    }
}
