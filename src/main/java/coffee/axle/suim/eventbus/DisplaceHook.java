package coffee.axle.suim.eventbus;

import coffee.axle.suim.feature.combat.Displace;
import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static coffee.axle.suim.ProjectSUIM.mc;

public final class DisplaceHook {

    private static Object autoClicker;
    private static Field acEnabled;
    private static Method canceled;
    private static Method cancel;
    private static boolean registered;

    private static Object killAura;
    private static Field kaEnabled;
    private static Field killTarget;
    private static Field attackClock;
    private static Field blocking;
    private static Field serverBlocking;
    private static Field moveFix;
    private static Field targetYaw;
    private static Field targetPitch;
    private static Field targetDistance;
    private static Field targetUsable;
    private static Field targetEntity;
    private static long clockDelta;
    private static long savedClock;

    private static boolean primmity;
    private static float flickYawSent;
    private static boolean flickDelivered;

    private DisplaceHook() {
    }

    public static boolean register() {
        if (registered) return true;
        try {
            boolean ac = regAutoClicker();
            boolean ka = regKillAura();
            registered = ac || ka;
            return registered;
        } catch (Exception e) {
            MyauLogger.error("Displace:Hook", e);
            return false;
        }
    }

    public static boolean isACActive() {
        if (autoClicker == null || acEnabled == null) return false;
        Displace d = Displace.instance;
        if (d == null || !d.isEnabled() || !d.autoClicker) return false;
        try {
            return acEnabled.getBoolean(autoClicker);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean regAutoClicker() {
        try {
            autoClicker = MyauEventBusHook.findModuleInstance(
                    MyauMappings.CLASS_HIT_BLOCK_EVENT,
                    MyauMappings.CLASS_AUTO_CLICKER);
            if (autoClicker == null) {
                MyauLogger.log("Displace:Hook", "AutoClicker instance not found");
                return false;
            }
            acEnabled = autoClicker.getClass().getSuperclass()
                    .getDeclaredField(MyauMappings.FIELD_MODULE_ENABLED);
            acEnabled.setAccessible(true);
            Class<?> eventClass = Class.forName(MyauMappings.CLASS_LEFT_CLICK_EVENT);
            canceled = eventClass.getMethod(MyauMappings.METHOD_LEFT_CLICK_IS_CANCELED);
            cancel = eventClass.getMethod(MyauMappings.METHOD_LEFT_CLICK_CANCEL, boolean.class);
            return MyauEventBusHook.registerAfter(
                    MyauMappings.CLASS_LEFT_CLICK_EVENT,
                    new DisplaceHook(),
                    "onLeftClick",
                    Object.class,
                    -1000);
        } catch (Exception e) {
            MyauLogger.error("Displace:AutoClicker", e);
            return false;
        }
    }


    private static boolean regKillAura() {
        try {
            killAura = MyauEventBusHook.findModuleInstance(
                    MyauMappings.CLASS_UPDATE_EVENT, MyauMappings.CLASS_KILL_AURA);
            if (killAura == null) return false;
            MyauLogger.log("Displace:KillAura", "KillAura instance found");
            kaEnabled = killAura.getClass().getSuperclass()
                    .getDeclaredField(MyauMappings.FIELD_MODULE_ENABLED);
            kaEnabled.setAccessible(true);
            killTarget = field(killAura, MyauMappings.FIELD_KILL_AURA_TARGET);
            attackClock = field(killAura, MyauMappings.FIELD_KILL_AURA_CLOCK);
            Method clockResolver = killAura.getClass().getDeclaredMethod(
                    MyauMappings.METHOD_KILL_AURA_CLOCK_RESOLVER, int.class, long.class);
            clockResolver.setAccessible(true);
            Field clockKey = field(killAura.getClass(), MyauMappings.FIELD_KILL_AURA_CLOCK_KEY);
            clockKey.setAccessible(true);
            long key = clockKey.getLong(null) ^ 0x343bec35323607L;
            clockDelta = ((Number) clockResolver.invoke(null, 24814, key)).longValue();
            blocking = field(killAura, MyauMappings.FIELD_KILL_AURA_IS_BLOCKING);
            serverBlocking = field(killAura, MyauMappings.FIELD_KILL_AURA_SERVER_BLOCKING);
            moveFix = field(killAura, MyauMappings.FIELD_KILL_AURA_MOVE_FIX);
            Class<?> targetClass = killTarget.getType();
            targetYaw = field(targetClass, MyauMappings.FIELD_KILL_AURA_TARGET_YAW);
            targetPitch = field(targetClass, MyauMappings.FIELD_KILL_AURA_TARGET_PITCH);
            targetDistance = field(targetClass, MyauMappings.FIELD_KILL_AURA_TARGET_DISTANCE);
            targetUsable = field(targetClass, MyauMappings.FIELD_KILL_AURA_TARGET_USABLE);
            targetEntity = field(targetClass, MyauMappings.FIELD_KILL_AURA_TARGET_ENTITY);
            return MyauEventBusHook.registerBefore(
                    MyauMappings.CLASS_UPDATE_EVENT,
                    new DisplaceHook(),
                    "onKaUpdate",
                    Object.class,
                    -50);
        } catch (Exception e) {
            MyauLogger.error("Displace:KillAura", e);
            return false;
        }
    }

    // --- KillAura handler: runs BEFORE Jh.U(kQ) every tick ---
    //
    // tick N (prime): push clock so native f skips, write flick at prio 2.
    //   C03 goes out with flick. server rotation = flick.
    // tick N+1 (attack): write NORMAL aim at prio 2 so f's ray check passes,
    //   restore clock so f attacks. C02 goes out. server rotation is still
    //   flick from tick N's C03, so knockback is displaced. C03 restores.

    // killaura's current target for other modules (backtrack scan). record
    // exists and the entity is still in the world, no enabled/usable check
    public static Entity kaTarget() {
        try {
            if (killAura == null || mc.thePlayer == null) return null;
            Object t = killTarget.get(killAura);
            if (t == null) return null;
            Entity entity = (Entity) targetEntity.get(t);
            if (entity == null) return null;
            if (mc.theWorld == null || mc.theWorld.getEntityByID(entity.getEntityId()) == null) return null;
            return entity;
        } catch (Exception e) {
            return null;
        }
    }

    public void onKaUpdate(Object event) {
        Displace d = Displace.instance;
        try {
            if (d == null || !d.isEnabled() || !d.killAura
                    || killAura == null || mc.thePlayer == null
                    || !kaEnabled.getBoolean(killAura)) {
                return;
            }
            // its like verity but if you're primed yk
            if (primmity) {
                if (!flickDelivered) {
                    return; 
                }
                primmity = false;
                flickDelivered = false;
                attackClock.setLong(killAura, savedClock);
                Object target = killTarget.get(killAura);
                if (target == null || !targetUsable.getBoolean(target)) return;
                float normalYaw = targetYaw.getFloat(target);
                float normalPitch = targetPitch.getFloat(target);
                event.getClass().getMethod(MyauMappings.METHOD_UPDATE_EVENT_SET_ROTATION,
                        float.class, float.class, int.class)
                        .invoke(event, normalYaw, normalPitch, 2);
                if (propertyInt(moveFix.get(killAura), 0) == 1) {
                    event.getClass().getMethod(MyauMappings.METHOD_UPDATE_EVENT_SET_MOVEMENT_YAW,
                            float.class, int.class)
                            .invoke(event, normalYaw, 2);
                }
                return;
            }

            if (mc.thePlayer.isUsingItem() || mc.gameSettings.keyBindUseItem.isKeyDown()
                    || blocking.getBoolean(killAura) || serverBlocking.getBoolean(killAura)) {
                return;
            }

            Object target = killTarget.get(killAura);
            if (target == null || !targetUsable.getBoolean(target)) return;

            Entity entity = (Entity) targetEntity.get(target);
            if (entity == null) return;

            double dist = ((Number) targetDistance.get(target)).doubleValue();
            if (dist > range()) return;

            if (!d.canDisplace(entity, true, true)) return;

            long clock = ((Number) attackClock.get(killAura)).longValue();
            if (clock > clockDelta) return;

            savedClock = clock;
            attackClock.setLong(killAura, Math.abs(clockDelta) * 4);

            float kaYaw = targetYaw.getFloat(target);
            float kaPitch = targetPitch.getFloat(target);
            float flickYaw = kaYaw + d.offsetForHook();

            d.lastAt = System.currentTimeMillis();
            d.startBlink();

            event.getClass().getMethod(MyauMappings.METHOD_UPDATE_EVENT_SET_ROTATION,
                    float.class, float.class, int.class)
                    .invoke(event, flickYaw, kaPitch, 2);
            if (propertyInt(moveFix.get(killAura), 0) == 1) {
                event.getClass().getMethod(MyauMappings.METHOD_UPDATE_EVENT_SET_MOVEMENT_YAW,
                        float.class, int.class)
                        .invoke(event, flickYaw, 2);
            }

            flickYawSent = flickYaw;
            flickDelivered = false;
            primmity = true;

        } catch (Exception e) {
            MyauLogger.error("Displace:KillAura update", e);
        }
    }

    public void onLeftClick(Object event) {
        Displace displace = Displace.instance;
        if (displace == null || !displace.isEnabled() || !displace.autoClicker
                || !isACActive() || isCanceled(event) || mc.thePlayer == null
                || mc.thePlayer.isUsingItem() || mc.gameSettings.keyBindUseItem.isKeyDown()) {
            return;
        }
        if (mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) {
            return;
        }
        Entity target = mc.objectMouseOver.entityHit;
        if (!displace.canDisplace(target, true, true)) {
            return;
        }
        displace.trigger(AimAssistHook.isAimingAt(target) ? 1 : 3);
        try {
            cancel.invoke(event, true);
        } catch (Exception e) {
            MyauLogger.error("Displace:Hook cancel", e);
        }
    }

    public static void reset() {
        primmity = false;
        savedClock = 0;
        flickDelivered = false;
    }

    public static void watchOut(Packet<?> packet) {
        if (!primmity || flickDelivered || !(packet instanceof C03PacketPlayer)) {
            return;
        }
        C03PacketPlayer c03 = (C03PacketPlayer) packet;
        if (!c03.getRotating()) {
            return;
        }
        if (Math.abs(MathHelper.wrapAngleTo180_float(c03.getYaw() - flickYawSent)) < 0.01f) {
            flickDelivered = true;
        }
    }

    private static Field field(Object owner, String name) throws Exception {
        return field(owner.getClass(), name);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static double range() {
        try {
            Field property = field(killAura, MyauMappings.FIELD_KILL_AURA_RANGE);
            Object value = property.get(killAura).getClass()
                    .getMethod(MyauMappings.METHOD_PROPERTY_GET_VALUE)
                    .invoke(property.get(killAura));
            return ((Number) value).doubleValue();
        } catch (Exception e) {
            return 3.0;
        }
    }

    private static int propertyInt(Object property, int fallback) {
        try {
            Object value = property.getClass().getMethod(MyauMappings.METHOD_PROPERTY_GET_VALUE)
                    .invoke(property);
            return value instanceof Number ? ((Number) value).intValue() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private boolean isCanceled(Object event) {
        try {
            return canceled.invoke(event).equals(Boolean.TRUE);
        } catch (Exception e) {
            return true;
        }
    }
}
