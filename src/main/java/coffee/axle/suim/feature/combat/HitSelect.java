package coffee.axle.suim.feature.combat;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.potion.Potion;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
@SuppressWarnings({ "unused" })
public class HitSelect extends Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double HIT_RANGE = 3.0;
    private static final double HIT_RANGE_SQ = HIT_RANGE * HIT_RANGE;
    private static final int HURT_WINDOW_TICKS = 10;
    private static final int SERVER_CONFIRM_COOLDOWN_TICKS = HURT_WINDOW_TICKS;
    private static final int SERVER_CONFIRM_TIMEOUT_TICKS = 30;

    private static final int BLOCK_WAIT_FIRST = 1;
    private static final int BLOCK_SERVER_COOLDOWN = 1 << 3;
    private static final int BLOCK_PREDICTED_BURST = 1 << 4;
    private static final int BLOCK_CRITICALS = 1 << 5;

    private static final String[] MODES = {"Burst", "Criticals"};

    private Object moduleInstance;
    private Object pauseDurationProperty;
    private Object modeProperty;
    private Object waitFirstHitProperty;
    private Object disableKBProperty;
    private Object onlyDamagedProperty;
    private Object serverAttackProperty;
    private Object fakeSwingProperty;
    private Object combatCancelProperty;
    private Object missedCancelProperty;

    private EntityPlayer currentTarget;
    private final Map<Integer, TargetState> targetStates = new HashMap<>();
    private int lastSelfHurtTime;
    private boolean takingKnockback;
    private boolean waitFirstTracking;
    private int waitFirstStartTick = -1;
    private boolean waitFirstUnlocked;
    private int tickCounter;

    @Override
    public String getName() {
        return "HitSelect";
    }

    @Override
    public GuiCategory getGuiCategory() {
        return GuiCategory.COMBAT;
    }

    @Override
    public boolean initialize() {
        try {
            moduleInstance = createModule();
            creator.injectModule(moduleInstance, HitSelect.class);

            pauseDurationProperty = creator.createIntegerProperty("pause-ms", 500, 0, 500);
            modeProperty = creator.createEnumProperty("mode", 0, MODES);
            waitFirstHitProperty = creator.createIntegerProperty("wait-first-ms", 0, 0, 500);
            disableKBProperty = creator.createBooleanProperty("disable-during-kb", false);
            onlyDamagedProperty = creator.createBooleanProperty("only-while-damaged", false);
            serverAttackProperty = creator.createBooleanProperty("server-attack-time", false);
            fakeSwingProperty = creator.createBooleanProperty("fake-swing", false);
            combatCancelProperty = creator.createIntegerProperty("combat-cancel-%", 100, 0, 100);
            missedCancelProperty = creator.createIntegerProperty("missed-cancel-%", 0, 0, 100);

            creator.registerProperties(moduleInstance, pauseDurationProperty, modeProperty,
                    waitFirstHitProperty, disableKBProperty, onlyDamagedProperty,
                    serverAttackProperty, fakeSwingProperty, combatCancelProperty, missedCancelProperty);

            manager.reloadModuleCommand();
            MinecraftForge.EVENT_BUS.register(this);

            manager.registerModuleCallbacks(moduleInstance,
                    () -> { tickCounter = 0; resetAllState(); },
                    this::resetAllState);

            return true;
        } catch (Exception e) {
            MyauLogger.error("HitSelect:init", e);
            return false;
        }
    }

    private int msToTicks(double ms) {
        if (ms <= 0.0) return 0;
        return (int) Math.ceil(ms / 50.0);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        if (!manager.isModuleEnabled(moduleInstance)) return;
        if (!Utils.nullCheck() || mc.thePlayer.isDead) {
            resetAllState();
            return;
        }

        tickCounter++;
        pruneTargetStates();

        EntityPlayer nextTarget = findTarget(HIT_RANGE_SQ);
        updateCurrentTarget(nextTarget, tickCounter);
        updateSelfDamage(tickCounter);
        updateTargetDamage(tickCounter);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(AttackEntityEvent e) {
        if (!manager.isModuleEnabled(moduleInstance)) return;
        if (!Utils.nullCheck() || mc.thePlayer.isDead) return;

        Entity target = e.target;

        MovingObjectPosition mop = mc.objectMouseOver;
        ClickType clickType = classifyClick(mop);

        if (clickType == ClickType.BLOCK_INTERACTION) return;

        if (clickType == ClickType.MISSED_SWING) {
            if (shouldCancel(properties.getInt(missedCancelProperty, 0))) {
                e.setCanceled(true);
            }
            return;
        }

        EntityPlayer clickedTarget = asValidPlayer(target, HIT_RANGE_SQ);
        if (clickedTarget == null) return;

        updateCurrentTarget(clickedTarget, tickCounter);
        TargetState state = getTargetState(clickedTarget, tickCounter);

        int blockMask = getValidHitBlockMask(state, tickCounter);

        boolean shouldBlock = (blockMask & BLOCK_WAIT_FIRST) != 0
                || (blockMask & BLOCK_PREDICTED_BURST) != 0
                || applyPauseDuration(state, blockMask & ~BLOCK_PREDICTED_BURST, tickCounter);

        if (shouldBlock && shouldCancel(properties.getInt(combatCancelProperty, 100))) {
            if (properties.getBoolean(fakeSwingProperty, false)) {
                mc.thePlayer.swingItem();
            }
            e.setCanceled(true);
            return;
        }

        recordPassedValidHit(clickedTarget, tickCounter);
    }

    private ClickType classifyClick(MovingObjectPosition mop) {
        if (mop == null) return ClickType.MISSED_SWING;
        if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) return ClickType.BLOCK_INTERACTION;
        if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            return asValidPlayer(mop.entityHit, HIT_RANGE_SQ) != null ? ClickType.VALID_HIT : ClickType.MISSED_SWING;
        }
        return ClickType.MISSED_SWING;
    }

    private void updateCurrentTarget(EntityPlayer nextTarget, int currentTick) {
        if (sameTarget(nextTarget)) {
            if (nextTarget != null) {
                currentTarget = nextTarget;
                getTargetState(nextTarget, currentTick);
            }
            return;
        }
        currentTarget = nextTarget;
        if (nextTarget == null) {
            resetWaitFirstState();
        } else if (!waitFirstTracking) {
            waitFirstTracking = true;
            waitFirstStartTick = currentTick;
            waitFirstUnlocked = false;
        }
        if (nextTarget != null) getTargetState(nextTarget, currentTick);
    }

    private void updateSelfDamage(int currentTick) {
        int hurtTime = mc.thePlayer.hurtTime;
        boolean hurtAgain = hurtTime > lastSelfHurtTime;
        if (hurtAgain) {
            if (waitFirstTracking && !waitFirstUnlocked) waitFirstUnlocked = true;
            if (!takingKnockback) takingKnockback = true;
            if (currentTarget != null) {
                TargetState state = getTargetState(currentTarget, currentTick);
                state.firstSelfHitSeen = true;
            }
        }
        if (takingKnockback && mc.thePlayer.onGround && !hurtAgain) takingKnockback = false;
        lastSelfHurtTime = hurtTime;
    }

    private void updateTargetDamage(int currentTick) {
        if (currentTarget == null || !properties.getBoolean(serverAttackProperty, false)) return;
        TargetState state = getTargetState(currentTarget, currentTick);
        int targetHurtTime = currentTarget.hurtTime;
        if (state.pendingServerConfirmationTick >= 0
                && currentTick - state.pendingServerConfirmationTick > SERVER_CONFIRM_TIMEOUT_TICKS)
            state.pendingServerConfirmationTick = -1;
        if (state.pendingServerConfirmationTick >= 0 && targetHurtTime > state.lastObservedTargetHurtTime) {
            state.pendingServerConfirmationTick = -1;
            state.lastConfirmedTargetDamageTick = currentTick;
            state.rawBlockMask = BLOCK_SERVER_COOLDOWN;
            state.rawBlockStartTick = currentTick;
        }
        state.lastObservedTargetHurtTime = targetHurtTime;
    }

    private int getValidHitBlockMask(TargetState state, int currentTick) {
        if (currentTarget == null) return 0;
        if (properties.getBoolean(disableKBProperty, false) && isTakingKnockback()) return 0;
        int blockMask = 0;
        if (isWaitingForFirstHit(currentTick)) blockMask |= BLOCK_WAIT_FIRST;
        blockMask |= getBurstBlockMask(state, currentTick);
        if (isCriticalsBlocked(state, currentTick)) blockMask |= BLOCK_CRITICALS;
        return blockMask;
    }

    private int getBurstBlockMask(TargetState state, int currentTick) {
        if (properties.getBoolean(serverAttackProperty, false)) {
            if (state.lastConfirmedTargetDamageTick >= 0
                    && currentTick - state.lastConfirmedTargetDamageTick < SERVER_CONFIRM_COOLDOWN_TICKS)
                return BLOCK_SERVER_COOLDOWN;
            return 0;
        }
        if (!isPredictedBurstWindowActive(state, currentTick)) return 0;
        int pauseTicks = msToTicks(properties.getInt(pauseDurationProperty, 500));
        return pauseTicks > 0 && currentTick - state.predictedBurstWindowStartTick < pauseTicks
                ? BLOCK_PREDICTED_BURST : 0;
    }

    private boolean isCriticalsBlocked(TargetState state, int currentTick) {
        if (properties.getInt(modeProperty, 0) != 1) return false;
        if (mc.thePlayer.onGround) return false;
        if (properties.getBoolean(onlyDamagedProperty, false) && !state.firstSelfHitSeen) return false;
        if (properties.getBoolean(disableKBProperty, false) && isTakingKnockback()) return false;
        return !canCriticalHit();
    }

    private boolean isWaitingForFirstHit(int currentTick) {
        int waitMs = properties.getInt(waitFirstHitProperty, 0);
        if (waitMs <= 0 || currentTarget == null || !waitFirstTracking || waitFirstUnlocked || waitFirstStartTick < 0)
            return false;
        int requiredTicks = msToTicks(waitMs);
        return requiredTicks > 0 && currentTick - waitFirstStartTick < requiredTicks;
    }

    private boolean canCriticalHit() {
        return mc.thePlayer.fallDistance > 0.0f
                && !mc.thePlayer.onGround
                && !mc.thePlayer.isOnLadder()
                && !mc.thePlayer.isInWater()
                && !mc.thePlayer.isPotionActive(Potion.blindness)
                && mc.thePlayer.ridingEntity == null;
    }

    private boolean isTakingKnockback() {
        return takingKnockback || mc.thePlayer.hurtTime > 0;
    }

    private boolean applyPauseDuration(TargetState state, int blockMask, int currentTick) {
        if (blockMask == 0) {
            state.rawBlockMask = 0;
            state.rawBlockStartTick = -1;
            return false;
        }
        int pauseMs = properties.getInt(pauseDurationProperty, 500);
        if (pauseMs <= 0) {
            state.rawBlockMask = blockMask;
            state.rawBlockStartTick = currentTick;
            return false;
        }
        if (blockMask != state.rawBlockMask) {
            state.rawBlockMask = blockMask;
            state.rawBlockStartTick = currentTick;
        } else if (state.rawBlockStartTick < 0) {
            state.rawBlockStartTick = currentTick;
        }
        int requiredTicks = msToTicks(pauseMs);
        return requiredTicks > 0 && currentTick - state.rawBlockStartTick < requiredTicks;
    }

    private void recordPassedValidHit(EntityPlayer target, int currentTick) {
        if (target == null) return;
        updateCurrentTarget(target, currentTick);
        TargetState state = getTargetState(target, currentTick);
        if (properties.getBoolean(serverAttackProperty, false)) {
            state.pendingServerConfirmationTick = currentTick;
            state.lastConfirmedTargetDamageTick = -1;
            return;
        }
        if (!isPredictedBurstWindowActive(state, currentTick))
            startPredictedBurstWindow(state, currentTick, HURT_WINDOW_TICKS);
    }

    private boolean shouldCancel(double chance) {
        if (chance <= 0.0) return false;
        if (chance >= 100.0) return true;
        return Math.random() * 100.0 < chance;
    }

    private boolean sameTarget(EntityPlayer nextTarget) {
        if (currentTarget == null || nextTarget == null) return currentTarget == nextTarget;
        return currentTarget.getEntityId() == nextTarget.getEntityId();
    }

    private void resetWaitFirstState() {
        waitFirstTracking = false;
        waitFirstStartTick = -1;
        waitFirstUnlocked = false;
    }

    private boolean isPredictedBurstWindowActive(TargetState state, int currentTick) {
        return state.predictedBurstWindowEndTick >= 0 && currentTick < state.predictedBurstWindowEndTick;
    }

    private void startPredictedBurstWindow(TargetState state, int startTick, int windowTicks) {
        state.predictedBurstWindowStartTick = startTick;
        state.predictedBurstWindowEndTick = startTick + Math.max(1, windowTicks);
    }

    private TargetState getTargetState(EntityPlayer target, int currentTick) {
        TargetState state = targetStates.get(target.getEntityId());
        if (state == null) {
            state = new TargetState();
            if (properties.getBoolean(serverAttackProperty, false))
                state.lastObservedTargetHurtTime = target.hurtTime;
            targetStates.put(target.getEntityId(), state);
        }
        return state;
    }

    private void pruneTargetStates() {
        if (mc.theWorld == null) {
            targetStates.clear();
            return;
        }
        Iterator<Map.Entry<Integer, TargetState>> it = targetStates.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, TargetState> entry = it.next();
            Entity entity = mc.theWorld.getEntityByID(entry.getKey());
            if (!(entity instanceof EntityPlayer) || entity.isDead || ((EntityPlayer) entity).deathTime != 0)
                it.remove();
        }
    }

    private void resetAllState() {
        currentTarget = null;
        targetStates.clear();
        lastSelfHurtTime = 0;
        takingKnockback = false;
        resetWaitFirstState();
    }

    private EntityPlayer findTarget(double rangeSq) {
        EntityPlayer closest = null;
        double closestDist = rangeSq;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || player.isDead || player.deathTime != 0) continue;
            double dist = mc.thePlayer.getDistanceSqToEntity(player);
            if (dist < closestDist) {
                closestDist = dist;
                closest = player;
            }
        }
        return closest;
    }

    private EntityPlayer asValidPlayer(Entity entity, double rangeSq) {
        if (!(entity instanceof EntityPlayer)) return null;
        EntityPlayer player = (EntityPlayer) entity;
        if (player == mc.thePlayer || player.isDead || player.deathTime != 0) return null;
        if (mc.thePlayer.getDistanceSqToEntity(player) > rangeSq) return null;
        return player;
    }

    private enum ClickType { VALID_HIT, BLOCK_INTERACTION, MISSED_SWING }

    private static class TargetState {
        boolean firstSelfHitSeen;
        int lastConfirmedTargetDamageTick = -1;
        int pendingServerConfirmationTick = -1;
        int predictedBurstWindowStartTick = -1;
        int predictedBurstWindowEndTick = -1;
        int lastObservedTargetHurtTime;
        int rawBlockStartTick = -1;
        int rawBlockMask;
    }
}
