package coffee.axle.suim.features;

import coffee.axle.suim.hooks.MyauHook;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.Vec3;

import java.lang.reflect.Field;

/**
 * Half working hitselect
 * 
 * @maybsomeday
 */
public class HitSelect implements Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final MyauHook hook = MyauHook.getInstance();

    private Object moduleInstance;
    private Object modeProperty;

    private Object keepSprintModule;
    private Object keepSprintSlowProperty;

    private boolean sprintState = false;

    private boolean set = false;
    private double val = 0.0;
    private Class<?> updateEventClass = null;
    private int blockedHits = 0;
    private int allowedHits = 0;

    @Override
    public String getName() {
        return "HitSelect";
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");

            moduleInstance = hook.createModule(getName());
            hook.injectModule(moduleInstance, HitSelect.class);

            modeProperty = hook.createEnumProperty(
                    "mode",
                    0,
                    new String[] {
                            "second",
                            "criticals",
                            "wtap"
                    });
            hook.registerProperties(moduleInstance, modeProperty);

            keepSprintModule = hook.findModule("KeepSprint");
            if (keepSprintModule != null) {
                keepSprintSlowProperty = hook.findProperty(keepSprintModule, "slowdown");
            }

            hook.reloadModuleCommand();

            hook.registerEventHandler("myau.m6", this::onUpdate, (byte) 3);
            hook.registerEventHandler("myau.R", this::onPacket, (byte) 0);

            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;

        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    private void onUpdate(Object eventObj) {
        try {
            Class<?> ec = eventObj.getClass();

            Field typeField = ec.getDeclaredField("C");
            typeField.setAccessible(true);
            Object eventType = typeField.get(eventObj);

            if (eventType != null && eventType.toString().equals("POST")) {
                resetMotion();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onPacket(Object eventObj) {
        try {
            if (!hook.isModuleEnabled(moduleInstance))
                return;

            Class<?> eventClass = eventObj.getClass();

            Field packetField = eventClass.getDeclaredField("z");
            packetField.setAccessible(true);
            Packet<?> packet = (Packet<?>) packetField.get(eventObj);

            if (packet == null)
                return;

            if (packet instanceof C0BPacketEntityAction) {
                C0BPacketEntityAction p = (C0BPacketEntityAction) packet;

                switch (p.getAction()) {
                    case START_SPRINTING:
                        sprintState = true;
                        break;
                    case STOP_SPRINTING:
                        sprintState = false;
                        break;
                    default:
                        break;
                }
                return;
            }

            if (packet instanceof C02PacketUseEntity) {
                C02PacketUseEntity use = (C02PacketUseEntity) packet;

                if (use.getAction() != C02PacketUseEntity.Action.ATTACK)
                    return;

                Entity target = use.getEntityFromWorld(mc.theWorld);
                if (target == null || target instanceof EntityLargeFireball)
                    return;
                if (!(target instanceof EntityLivingBase))
                    return;

                EntityLivingBase living = (EntityLivingBase) target;

                int mode = getMode();
                boolean allow = true;

                switch (mode) {
                    case 0:
                        allow = prioritizeSecondHit(mc.thePlayer, living);
                        break;

                    case 1:
                        allow = prioritizeCriticalHits(mc.thePlayer);
                        break;

                    case 2:
                        allow = prioritizeWTapHits(mc.thePlayer, sprintState);
                        break;
                }

                if (!allow) {
                    Field cancelledField = eventClass.getSuperclass().getDeclaredField("H");
                    cancelledField.setAccessible(true);
                    cancelledField.setBoolean(eventObj, true);

                    blockedHits++;
                } else {
                    allowedHits++;
                }
            }

        } catch (Exception e) {
        }
    }

    private boolean prioritizeSecondHit(EntityLivingBase player, EntityLivingBase target) {
        if (target.hurtTime != 0)
            return true;

        if (player.hurtTime <= player.maxHurtTime - 1)
            return true;

        double dist = player.getDistanceToEntity(target);
        if (dist < 2.5)
            return true;

        if (!isMovingTowards(target, player, 60))
            return true;

        if (!isMovingTowards(player, target, 60))
            return true;

        fixMotion();

        return false;
    }

    private boolean prioritizeCriticalHits(EntityLivingBase player) {
        if (player.onGround)
            return true;
        if (player.hurtTime != 0)
            return true;
        if (player.fallDistance > 0)
            return true;

        fixMotion();

        return false;
    }

    private boolean prioritizeWTapHits(EntityLivingBase player, boolean sprinting) {
        if (player.isCollidedHorizontally)
            return true;
        if (!mc.gameSettings.keyBindForward.isKeyDown())
            return true;
        if (sprinting)
            return true;

        fixMotion();

        return false;
    }

    private void fixMotion() {
        if (set)
            return;
        if (keepSprintModule == null || keepSprintSlowProperty == null)
            return;

        try {
            Object raw = hook.getPropertyValue(keepSprintSlowProperty);
            if (raw instanceof Number)
                val = ((Number) raw).doubleValue();

            Field enabledField = hook.findFieldInHierarchy(keepSprintModule.getClass(), "p");
            if (enabledField != null) {
                enabledField.setAccessible(true);
                enabledField.setBoolean(keepSprintModule, true);
            }

            Field valueField = keepSprintSlowProperty.getClass().getSuperclass().getDeclaredField("J");
            valueField.setAccessible(true);
            valueField.set(keepSprintSlowProperty, 0);

            set = true;

        } catch (Exception e) {
        }
    }

    private void resetMotion() {
        if (!set)
            return;
        if (keepSprintModule == null || keepSprintSlowProperty == null)
            return;

        try {
            Field valueField = keepSprintSlowProperty.getClass().getSuperclass().getDeclaredField("J");
            valueField.setAccessible(true);
            valueField.set(keepSprintSlowProperty, (int) val);

            Field enabledField = hook.findFieldInHierarchy(keepSprintModule.getClass(), "p");
            if (enabledField != null) {
                enabledField.setAccessible(true);
                enabledField.setBoolean(keepSprintModule, false);
            }

        } catch (Exception e) {
        }

        set = false;
        val = 0.0;
    }

    private boolean isMovingTowards(EntityLivingBase source, EntityLivingBase target, double ang) {
        Vec3 cur = source.getPositionVector();
        Vec3 last = new Vec3(source.lastTickPosX, source.lastTickPosY, source.lastTickPosZ);
        Vec3 posT = target.getPositionVector();

        double mx = cur.xCoord - last.xCoord;
        double mz = cur.zCoord - last.zCoord;
        double ml = Math.sqrt(mx * mx + mz * mz);
        if (ml == 0)
            return false;
        mx /= ml;
        mz /= ml;

        double tx = posT.xCoord - cur.xCoord;
        double tz = posT.zCoord - cur.zCoord;
        double tl = Math.sqrt(tx * tx + tz * tz);
        if (tl == 0)
            return false;
        tx /= tl;
        tz /= tl;
        return (mx * tx + mz * tz) >= Math.cos(Math.toRadians(ang));
    }

    private int getMode() {
        try {
            return (Integer) hook.getPropertyValue(modeProperty);
        } catch (Exception e) {
            return 0;
        }
    }
}
