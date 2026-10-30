package coffee.axle.suim.feature.movement;

import coffee.axle.suim.events.PreMotionEvent;
import coffee.axle.suim.events.PrePlayerInputEvent;
import coffee.axle.suim.events.SendPacketEvent;
import coffee.axle.suim.events.ReceivePacketEvent;
import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.property.Setting;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.PacketUtils;
import coffee.axle.suim.util.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import static coffee.axle.suim.ProjectSUIM.mc;

/**
 * herp
 * hypixel vs patching me
 */
public class Stasis extends Feature {
    private static Stasis instance;

    public enum Mode {
        SUIM, RESTORE, RAVEN, JADE
    }

    private static final int JADE_LOCK_TICKS = 2;

    private static final double MOTION_EPS = 1.0E-4;
    private static final double GRAVITY_MATCH_EPS = 0.02;

    @Setting(min = 2, max = 85) private int pulse = 60;
    @Setting(min = 1, max = 20) private int pulseWindow = 1;
    @Setting(min = 0, max = 20) private int velocityTicks = 2;
    @Setting(min = 0, max = 20) private int velocityBlock = 2;
    @Setting(min = 0, max = 5) private float maxFall = 0.5f;
    @Setting(min = 0, max = 20) private int blockAfterDisable = 3;
    @Setting(min = 1, max = 100) private int positionReminder = 20;
    @Setting(min = 1, max = 20) private int groundStable = 3;
    @Setting private Mode mode = Mode.JADE;
    @Setting private boolean flagless = false;
    @Setting private boolean cancelOnDeath = false;
    private Mode lastMode;

    public static boolean frozen;

    private boolean allowNextC03 = false;
    private double savedMotionX = 0.0;
    private double savedMotionY = 0.0;
    private double savedMotionZ = 0.0;

    private long pulseTick = 0;
    private boolean pulseActive = false;

    private double storedMotionX, storedMotionY, storedMotionZ;
    private boolean motionStored;
    private double anchorX, anchorY, anchorZ;
    private boolean anchorKnown;
    private int velocityTicksRemaining;
    private int velocityInputBlockRemaining;
    private int pulseTicksRemaining;
    private int pulseCooldownRemaining;
    private int blockAfterDisableRemaining;
    private double velocityFallStartY;
    private double lastMotionX, lastMotionY, lastMotionZ;
    private int noReminderTicks;
    private float lastSentYaw, lastSentPitch;
    private boolean lastSentLookValid;
    private int airReminderTicksRemaining;
    private boolean pendingAirReminder;
    private boolean holding;
    private boolean keysDown;
    private int groundStableTicks;
    private double watchPosX, watchPosY, watchPosZ;
    private int lastHurtTime;
    private int[] footXs, footYs, footZs;
    private Block[] footBlocks;
    private int[] footMetas;
    private boolean[] footSolid;
    private boolean feetSampled;
    private int ticksSinceTeleport = 999;

    private boolean jadeActive;
    private boolean jadeOnGround;
    private boolean jadeResync;
    private boolean jadeKnockback;
    private int jadeKnockbackTicks;
    private int jadeSinceSync;
    private int jadeLockTicks;
    private double jadeX, jadeY, jadeZ;
    private double jadeMotionX, jadeMotionY, jadeMotionZ;
    private boolean jadeMotionSaved;

    public Stasis() {
        super("Stasis", GuiCategory.WORLD);
        instance = this;
    }

    @Override
    protected void onEnable() {
        lastMode = mode;
        startEngine(mode);
    }

    @Override
    protected void onDisable() {
        stopEngine(mode);
    }

    private void startEngine(Mode mode) {
        if (mode == Mode.RAVEN) {
            onEnableLegacy();
        } else if (mode == Mode.JADE) {
            jadeEnable();
        } else {
            allowNextC03 = false;
            pulseTick = 0;
            pulseActive = false;
        }
    }

    private void stopEngine(Mode mode) {
        if (mode == Mode.RAVEN) {
            onDisableLegacy();
            frozen = false;
        } else if (mode == Mode.JADE) {
            jadeDisable();
            frozen = false;
        } else {
            allowNextC03 = false;
            pulseTick = 0;
            pulseActive = false;
            if (mode == Mode.RESTORE && Utils.nullCheck()) {
                mc.thePlayer.motionX = savedMotionX;
                mc.thePlayer.motionY = savedMotionY;
                mc.thePlayer.motionZ = savedMotionZ;
            }
        }
    }

    private Mode getMode() {
        return mode;
    }

    private boolean isFlagless() {
        return flagless;
    }

    private int getVelocityTicks() {
        return velocityTicks;
    }

    private int getPositionReminder() {
        return positionReminder;
    }

    @SubscribeEvent
    public void onPreMotion(PreMotionEvent e) {
        Mode mode = getMode();
        if (mode == Mode.RAVEN || mode == Mode.JADE) return;
        if (!isEnabled()) return;
        if (!Utils.nullCheck()) return;
        pulseActive = getMode() == Mode.SUIM && pulseTick % 80L < 4L;
        pulseTick++;
        if (mc.thePlayer.hurtTime != 0) return;
        if (pulseActive) return;
        savedMotionX = mc.thePlayer.motionX;
        savedMotionY = mc.thePlayer.motionY;
        savedMotionZ = mc.thePlayer.motionZ;
        zeroAllMotion(mc.thePlayer);
    }

    @SubscribeEvent
    public void onPrePlayerInput(PrePlayerInputEvent e) {
        if (getMode() == Mode.RAVEN) {
            onPostPlayerInputAlways(e);
            return;
        }
        if (getMode() == Mode.JADE) {
            // always called: the 2 tick lock after a disable still needs to run
            jadeMoveInput(e);
            return;
        }
        if (!isEnabled()) return;
        e.setForward(0.0f);
        e.setStrafe(0.0f);
    }

    // EntityPlayerSP.onUpdate HEAD/RETURN, same two points Jade posts its
    // PreUpdateEvent/PostUpdateEvent from
    public static void jadePreUpdate(EntityPlayerSP p) {
        if (instance != null && instance.getMode() == Mode.JADE && instance.isEnabled()) {
            instance.jadePre(p);
            instance.updateFrozen();
        }
    }

    public static void jadePostUpdate(EntityPlayerSP p) {
        if (instance != null && instance.getMode() == Mode.JADE && instance.isEnabled()) {
            instance.jadePost(p);
            instance.updateFrozen();
        }
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (getMode() == Mode.RAVEN) {
            if (!onSendPacketAlways(e.getPacket())) {
                e.setCanceled(true);
            }
            return;
        }
        if (getMode() == Mode.JADE) {
            if (isEnabled() && !jadeSend(e.getPacket())) {
                e.setCanceled(true);
            }
            return;
        }
        if (!isEnabled()) return;
        if (!(e.getPacket() instanceof C03PacketPlayer)) return;
        if (allowNextC03) {
            allowNextC03 = false;
            return;
        }
        if (!Utils.nullCheck() || mc.thePlayer.hurtTime != 0) return;
        if (pulseActive) return;
        if (!(e.getPacket() instanceof C03PacketPlayer.C05PacketPlayerLook)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onReceivePacket(ReceivePacketEvent e) {
        if (getMode() == Mode.RAVEN) {
            if (!onReceivePacketAlways(e.getPacket())) {
                e.setCanceled(true);
            }
            return;
        }
        if (getMode() == Mode.JADE) {
            if (isEnabled() && !e.isCanceled()) {
                jadeReceive(e.getPacket());
            }
            return;
        }
        if (!isEnabled()) return;
        if (e.getPacket() instanceof S08PacketPlayerPosLook) {
            allowNextC03 = true;
            // a lagback/teleport invalidates the frozen position - restart the
            // cycle so the next pulse window measures from the corrected spot
            pulseTick = 0;
            pulseActive = false;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onClientTick(TickEvent.ClientTickEvent e) {
        Mode mode = getMode();
        if (mode != lastMode) {
            // engine switch mid run: stop old, start new. disabled engines are
            // already stopped so there's nothing to do
            if (isEnabled()) {
                stopEngine(lastMode);
                startEngine(mode);
            }
            lastMode = mode;
        }
        if (mode != Mode.RAVEN) return;
        if (e.phase == TickEvent.Phase.START) {
            onClientTickStart();
        } else {
            onClientTickEnd();
        }
    }

    @SubscribeEvent
    public void onUnload(WorldEvent.Unload e) {
        if (e.world == null || !e.world.isRemote) return;
        // jade keeps running across a world change, it only drops held packets
        if (getMode() == Mode.JADE) return;
        if (getMode() != Mode.RAVEN) return;
        if (isEnabled()) {
            reset();
        }
    }

    private boolean isPulseEnabled() {
        return getPulseInterval() != 2;
    }

    private int getPulseInterval() {
        return pulse;
    }

    private boolean isActive() {
        return this.isEnabled() && Utils.nullCheck();
    }

    private void onEnableLegacy() {
        this.motionStored = false;
        this.velocityTicksRemaining = 0;
        this.velocityInputBlockRemaining = 0;
        this.pulseTicksRemaining = 0;
        this.pulseCooldownRemaining = this.getPulseInterval();
        this.blockAfterDisableRemaining = 0;
        this.velocityFallStartY = 0.0;
        this.noReminderTicks = 0;
        this.lastSentLookValid = false;
        this.airReminderTicksRemaining = 0;
        this.pendingAirReminder = false;
        this.groundStableTicks = 0;
        this.feetSampled = false;
        EntityPlayerSP p = mc.thePlayer;
        this.snapshotWatchState(p);
        if (p != null && !p.onGround) {
            this.beginHolding(p);
        } else {
            this.enterIdle(p, false);
        }
        updateFrozen();
    }

    private void onDisableLegacy() {
        reset();
    }

    private void reset() {
        if (this.holding && this.isFrozen()) {
            this.restoreMotion();
        }
        this.holding = false;
        this.motionStored = false;
        this.velocityTicksRemaining = 0;
        this.velocityInputBlockRemaining = 0;
        this.pulseTicksRemaining = 0;
        this.pulseCooldownRemaining = 0;
        this.velocityFallStartY = 0.0;
        this.airReminderTicksRemaining = 0;
        this.pendingAirReminder = false;
        this.groundStableTicks = 0;
        this.feetSampled = false;
        this.anchorKnown = false;
        this.blockAfterDisableRemaining = blockAfterDisable;
        updateFrozen();
    }

    private void updateFrozen() {
        Mode mode = getMode();
        frozen = this.isEnabled() && (mode == Mode.JADE ? this.jadeIsFrozen() : this.isFrozen());
    }

    public static void afterPlayerUpdate(EntityPlayerSP p) {
        if (instance == null || instance.getMode() != Mode.RAVEN || p == null
                || !instance.isActive() || !instance.holding) {
            return;
        }
        if (instance.isFrozen()) {
            instance.applyFrozenState(p);
        } else {
            instance.maybeFinishVelocityForFall(p);
        }
    }

    private void maybeFinishVelocityForFall(EntityPlayerSP p) {
        float fall = maxFall;
        if (this.velocityTicksRemaining > 0 && fall > 0.0f && this.velocityFallStartY - p.posY >= fall) {            this.velocityTicksRemaining = 0;
            this.finishVelocityPassthrough(p);
        }
    }

    public boolean onReceivePacketAlways(Packet<?> packet) {
        if (!this.isActive()) {
            return true;
        }
        EntityPlayerSP p = mc.thePlayer;
        if (packet instanceof S08PacketPlayerPosLook) {
            S08PacketPlayerPosLook s = (S08PacketPlayerPosLook) packet;
            this.onServerTeleport(p, s.getX(), s.getY(), s.getZ());
        } else if (packet instanceof S27PacketExplosion) {
            S27PacketExplosion s = (S27PacketExplosion) packet;
            if (getVelocityTicks() > 0 && motionSpeed(s.func_149149_c(), s.func_149144_d(), s.func_149147_e()) > MOTION_EPS) {
                this.beginHolding(p);
                this.openVelocityWindow();
            }
        } else if (packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity s = (S12PacketEntityVelocity) packet;
            if (s.getEntityID() == p.getEntityId()) {
                double x = s.getMotionX() / 8000.0;
                double y = s.getMotionY() / 8000.0;
                double z = s.getMotionZ() / 8000.0;
                if (getVelocityTicks() > 0 && motionSpeed(x, y, z) > MOTION_EPS) {
                    this.beginHolding(p);
                    this.openVelocityWindow();
                } else if (this.holding) {
                    updateFrozen();
                    return false;
                }
            }
        }
        updateFrozen();
        return true;
    }

    private void onServerTeleport(EntityPlayerSP p, double x, double y, double z) {
        if (cancelOnDeath && !this.isLikelyFlagTeleport(p, x, y, z)) {
            try {
                manager.setModuleEnabled(getModuleInstance(), false);
            } catch (Exception ex) {
                MyauLogger.error("Stasis:disable", ex);
            }
            return;
        }
        this.beginHolding(p);
        this.velocityTicksRemaining = 0;
        this.velocityInputBlockRemaining = 0;
        this.pulseTicksRemaining = 0;
        this.airReminderTicksRemaining = 0;
        this.pendingAirReminder = false;
        this.pulseCooldownRemaining = this.getPulseInterval();
        this.groundStableTicks = 0;
        this.noReminderTicks = 0;
        this.clearStoredMotion();
        this.setAnchor(x, y, z);
        if (p != null) {
            zeroAllMotion(p);
            this.snapshotMotion();
            this.applyFrozenState(p);
        }
        this.snapshotWatchState(p);
        this.ticksSinceTeleport = 0;
    }

    private void clearStoredMotion() {
        this.storedMotionX = 0.0;
        this.storedMotionY = 0.0;
        this.storedMotionZ = 0.0;
        this.motionStored = true;
    }

    private void openVelocityWindow() {
        if (this.pulseTicksRemaining > 0) {
            this.pulseTicksRemaining = 0;
        }
        this.airReminderTicksRemaining = 0;
        this.pendingAirReminder = false;
        this.velocityTicksRemaining = getVelocityTicks();
        this.velocityInputBlockRemaining = velocityBlock;
        EntityPlayerSP p = mc.thePlayer;
        this.velocityFallStartY = p != null ? p.posY : 0.0;
        this.groundStableTicks = 0;
    }

    private void beginHolding(EntityPlayerSP p) {
        if (p == null) {
            return;
        }
        boolean was = this.holding;
        this.holding = true;
        this.groundStableTicks = 0;
        if (!was) {
            this.motionStored = false;
            this.captureAnchorFromPlayer(p);
            this.storeMotionAndClear();
            this.snapshotMotion();
            this.applyFrozenState(p);
            this.noReminderTicks = 0;
            this.lastSentLookValid = false;
            this.airReminderTicksRemaining = 0;
            this.pendingAirReminder = false;
        } else if (!this.anchorKnown) {
            this.captureAnchorFromPlayer(p);
        }
        this.snapshotWatchState(p);
    }

    private void enterIdle(EntityPlayerSP p, boolean zero) {
        this.holding = false;
        this.groundStableTicks = 0;
        this.velocityTicksRemaining = 0;
        this.velocityInputBlockRemaining = 0;
        this.pulseTicksRemaining = 0;
        this.airReminderTicksRemaining = 0;
        this.pendingAirReminder = false;
        this.noReminderTicks = 0;
        if (zero) {
            if (p != null) {
                zeroAllMotion(p);
            }
            this.motionStored = false;
        }
        this.snapshotWatchState(p);
    }

    private void snapshotWatchState(EntityPlayerSP p) {
        if (p == null) {
            this.feetSampled = false;
            return;
        }
        this.watchPosX = p.posX;
        this.watchPosY = p.posY;
        this.watchPosZ = p.posZ;
        this.lastHurtTime = p.hurtTime;
        this.snapshotMotion();
        this.sampleFeet(p);
    }

    private boolean shouldActivate(EntityPlayerSP p) {
        if (p == null) {
            return false;
        }
        if (!p.onGround) {
            return true;
        }
        if (p.hurtTime > this.lastHurtTime || p.hurtTime > 0 && this.lastHurtTime == 0) {
            return true;
        }
        if (Math.sqrt(p.motionX * p.motionX + p.motionZ * p.motionZ) > MOTION_EPS) {
            return true;
        }
        double dx = p.posX - this.watchPosX;
        double dy = p.posY - this.watchPosY;
        double dz = p.posZ - this.watchPosZ;
        if (Math.sqrt(dx * dx + dy * dy + dz * dz) > 0.001) {
            return true;
        }
        return this.feetChanged(p);
    }

    private void sampleFeet(EntityPlayerSP p) {
        AxisAlignedBB bb = p.getEntityBoundingBox();
        if (bb == null || p.worldObj == null) {
            this.feetSampled = false;
            return;
        }
        int x0 = MathHelper.floor_double(bb.minX);
        int x1 = MathHelper.floor_double(bb.maxX - 1.0E-7);
        int z0 = MathHelper.floor_double(bb.minZ);
        int z1 = MathHelper.floor_double(bb.maxZ - 1.0E-7);
        int y = MathHelper.floor_double(bb.minY - 0.001);
        int n = (x1 - x0 + 1) * (z1 - z0 + 1);
        this.footXs = new int[n];
        this.footYs = new int[n];
        this.footZs = new int[n];
        this.footBlocks = new Block[n];
        this.footMetas = new int[n];
        this.footSolid = new boolean[n];
        World w = p.worldObj;
        int i = 0;
        for (int x = x0; x <= x1; ++x) {
            for (int z = z0; z <= z1; ++z) {
                IBlockState state = w.getBlockState(new BlockPos(x, y, z));
                Block b = state == null ? null : state.getBlock();
                this.footXs[i] = x;
                this.footYs[i] = y;
                this.footZs[i] = z;
                this.footBlocks[i] = b;
                this.footMetas[i] = b != null ? b.getMetaFromState(state) : 0;
                this.footSolid[i] = b != null && b.getMaterial().blocksMovement();
                ++i;
            }
        }
        this.feetSampled = true;
    }

    private boolean feetChanged(EntityPlayerSP p) {
        if (!this.feetSampled) {
            this.sampleFeet(p);
            return false;
        }
        AxisAlignedBB bb = p.getEntityBoundingBox();
        if (bb == null || p.worldObj == null) {
            return false;
        }
        int x0 = MathHelper.floor_double(bb.minX);
        int x1 = MathHelper.floor_double(bb.maxX - 1.0E-7);
        int z0 = MathHelper.floor_double(bb.minZ);
        int z1 = MathHelper.floor_double(bb.maxZ - 1.0E-7);
        int y = MathHelper.floor_double(bb.minY - 0.001);
        if ((x1 - x0 + 1) * (z1 - z0 + 1) != this.footXs.length) {
            return true;
        }
        World w = p.worldObj;
        int i = 0;
        for (int x = x0; x <= x1; ++x) {
            for (int z = z0; z <= z1; ++z) {
                if (this.footXs[i] != x || this.footYs[i] != y || this.footZs[i] != z) {
                    return true;
                }
                IBlockState state = w.getBlockState(new BlockPos(x, y, z));
                Block b = state == null ? null : state.getBlock();
                int meta = b != null ? b.getMetaFromState(state) : 0;
                boolean solid = b != null && b.getMaterial().blocksMovement();
                if (b != this.footBlocks[i] || meta != this.footMetas[i] || solid != this.footSolid[i]) {
                    return true;
                }
                ++i;
            }
        }
        return false;
    }

    private boolean canEnterIdle(EntityPlayerSP p) {
        if (!this.holding || p == null || !p.onGround) {
            return false;
        }
        if (this.velocityTicksRemaining > 0 || this.pulseTicksRemaining > 0 || this.airReminderTicksRemaining > 0) {
            return false;
        }
        if (this.keysDown || motionSpeed(p.motionX, p.motionY, p.motionZ) > MOTION_EPS) {
            return false;
        }
        return this.groundStableTicks >= groundStable;
    }

    private void updateHoldingState(EntityPlayerSP p) {
        if (!this.holding) {
            if (this.shouldActivate(p)) {
                this.beginHolding(p);
            } else {
                this.snapshotWatchState(p);
            }
            return;
        }
        this.groundStableTicks = p.onGround && this.velocityTicksRemaining <= 0 && this.pulseTicksRemaining <= 0
                && this.airReminderTicksRemaining <= 0 ? this.groundStableTicks + 1 : 0;
        if (this.canEnterIdle(p)) {
            this.enterIdle(p, true);
        }
    }

    private void sendAnchorC04Reminder(EntityPlayerSP p) {
        if (p == null || !this.anchorKnown) {
            return;
        }
        this.syncPlayerToAnchor(p);
        zeroAllMotion(p);
        PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(this.anchorX, this.anchorY, this.anchorZ, p.onGround));
        this.noReminderTicks = 0;
    }

    private void startAirReminderPulse(EntityPlayerSP p, boolean now) {
        if (!now) {
            this.pendingAirReminder = true;
            return;
        }
        this.pendingAirReminder = false;
        this.airReminderTicksRemaining = 1;
        if (p != null) {
            this.applyStoredMotion();
        }
    }

    private void finishAirReminder(EntityPlayerSP p) {
        this.airReminderTicksRemaining = 0;
        this.captureAnchorFromPlayer(p);
        this.refreezeMotion(p);
        this.applyFrozenState(p);
        this.snapshotMotion();
        this.noReminderTicks = 0;
    }

    private boolean maybeForcePositionReminder(EntityPlayerSP p) {
        if (this.noReminderTicks < getPositionReminder()) {
            return false;
        }
        if (p != null && p.onGround) {
            this.sendAnchorC04Reminder(p);
        } else {
            this.startAirReminderPulse(p, false);
        }
        return true;
    }

    public boolean onSendPacketAlways(Packet<?> packet) {
        if (!this.holding) {
            return true;
        }
        return isFlagless() ? this.onSendPacketFlagless(packet) : this.onSendPacketFlag(packet);
    }

    private boolean onSendPacketFlag(Packet<?> packet) {
        if (!this.isActive() || !this.isFrozen() || !this.anchorKnown || !(packet instanceof C03PacketPlayer)) {
            return true;
        }
        C03PacketPlayer c = (C03PacketPlayer) packet;
        boolean moving = c instanceof C03PacketPlayer.C04PacketPlayerPosition || c instanceof C03PacketPlayer.C06PacketPlayerPosLook;
        if (this.ticksSinceTeleport <= 2 && moving) {
            return true;
        }
        if (c instanceof C03PacketPlayer.C04PacketPlayerPosition) {
            return false;
        }
        if (c instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
            sendLookPacket(c.getYaw(), c.getPitch(), c.isOnGround());
            return false;
        }
        return true;
    }

    private boolean onSendPacketFlagless(Packet<?> packet) {
        if (!this.isActive() || !this.anchorKnown || !(packet instanceof C03PacketPlayer)) {
            return true;
        }
        C03PacketPlayer c = (C03PacketPlayer) packet;
        if (!this.isFrozen()) {
            if (c instanceof C03PacketPlayer.C04PacketPlayerPosition || c instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
                this.noReminderTicks = 0;
            }
            return true;
        }
        EntityPlayerSP p = mc.thePlayer;
        if (c instanceof C03PacketPlayer.C04PacketPlayerPosition) {
            if (this.ticksSinceTeleport <= 2) {
                this.noReminderTicks = 0;
                return true;
            }
            if (this.noReminderTicks >= getPositionReminder()) {
                if (p != null && p.onGround) {
                    this.sendAnchorC04Reminder(p);
                } else {
                    this.startAirReminderPulse(p, false);
                }
            }
            return false;
        }
        if (c instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
            if (this.ticksSinceTeleport <= 2) {
                this.noReminderTicks = 0;
                return true;
            }
            if (!this.maybeForcePositionReminder(p)) {
                float yaw = c.getYaw();
                float pitch = c.getPitch();
                if (this.lastSentLookValid && yaw == this.lastSentYaw && pitch == this.lastSentPitch) {
                    PacketUtils.sendPacketNoEvent(new C03PacketPlayer(c.isOnGround()));
                } else {
                    sendLookPacket(yaw, pitch, c.isOnGround());
                    this.lastSentYaw = yaw;
                    this.lastSentPitch = pitch;
                    this.lastSentLookValid = true;
                }
                ++this.noReminderTicks;
            }
            return false;
        }
        if (this.maybeForcePositionReminder(p)) {
            return false;
        }
        if (c instanceof C03PacketPlayer.C05PacketPlayerLook) {
            this.lastSentYaw = c.getYaw();
            this.lastSentPitch = c.getPitch();
            this.lastSentLookValid = true;
        }
        ++this.noReminderTicks;
        return true;
    }

    // runs right after the input is applied, before the move packet
    private void onPostPlayerInputAlways(PrePlayerInputEvent e) {
        if (mc.thePlayer == null) {
            return;
        }
        if (!this.isEnabled()) {
            if (this.blockAfterDisableRemaining > 0) {
                blockInput(e);
            }
            return;
        }
        if (!Utils.nullCheck()) {
            return;
        }
        boolean down = Math.abs(e.getForward()) > MOTION_EPS || Math.abs(e.getStrafe()) > MOTION_EPS || e.isJump();
        this.keysDown = down;
        if (!this.holding) {
            if (!down) {
                return;
            }
            this.beginHolding(mc.thePlayer);
        }
        if (this.velocityTicksRemaining <= 0 || this.velocityInputBlockRemaining > 0) {
            blockInput(e);
        }
        updateFrozen();
    }

    private void onClientTickStart() {
        if (!this.isEnabled()) {
            if (this.blockAfterDisableRemaining > 0) {
                --this.blockAfterDisableRemaining;
            }
            return;
        }
        if (!Utils.nullCheck()) {
            return;
        }
        EntityPlayerSP p = mc.thePlayer;
        this.updateHoldingState(p);
        if (this.ticksSinceTeleport < 1000) {
            ++this.ticksSinceTeleport;
        }
        if (this.holding) {
            if (isFlagless()) {
                this.onClientTickStartFlagless(p);
            } else {
                this.onClientTickStartFlag(p);
            }
            this.tickReleaseWindows(p);
        }
        updateFrozen();
    }

    private void onClientTickStartFlag(EntityPlayerSP p) {
        if (this.isFrozen()) {
            this.clearMovementInputFlags();
            this.applyFrozenState(p);
        } else if (this.pulseTicksRemaining > 0 || this.velocityInputBlockRemaining > 0) {
            this.clearMovementInputFlags();
        }
    }

    private void onClientTickStartFlagless(EntityPlayerSP p) {
        if (this.isFrozen()) {
            this.clearMovementInputFlags();
            if (!this.pendingAirReminder && this.noReminderTicks < getPositionReminder()) {
                this.applyFrozenState(p);
            } else if (!this.pendingAirReminder && p.onGround) {
                this.applyFrozenState(p);
                this.sendAnchorC04Reminder(p);
            } else {
                this.startAirReminderPulse(p, true);
            }
        } else if (this.airReminderTicksRemaining > 0) {
            this.clearMovementInputFlags();
        } else if (this.pulseTicksRemaining > 0 || this.velocityInputBlockRemaining > 0) {
            this.clearMovementInputFlags();
            this.noReminderTicks = 0;
        } else if (this.velocityTicksRemaining > 0) {
            this.noReminderTicks = 0;
        }
    }

    private void onClientTickEnd() {
        if (!this.isActive()) {
            return;
        }
        EntityPlayerSP p = mc.thePlayer;
        if (!this.holding) {
            this.snapshotWatchState(p);
        } else if (isFlagless() && this.airReminderTicksRemaining > 0) {
            this.finishAirReminder(p);
        } else if (this.isFrozen()) {
            this.enforceFrozenMotion(p);
        } else {
            this.snapshotMotion();
        }
        updateFrozen();
    }

    private void tickReleaseWindows(EntityPlayerSP p) {
        if (this.pulseTicksRemaining > 0) {
            if (--this.pulseTicksRemaining == 0) {
                this.finishPulse(p);
            }
        } else if (this.velocityTicksRemaining > 0) {
            if (this.velocityInputBlockRemaining > 0) {
                --this.velocityInputBlockRemaining;
            }
            if (--this.velocityTicksRemaining == 0) {
                this.finishVelocityPassthrough(p);
            }
        } else if (this.holding && this.isPulseEnabled()) {
            this.tickPulseCooldown();
        }
    }

    private void finishPulse(EntityPlayerSP p) {
        this.captureAnchorFromPlayer(p);
        this.refreezeMotion(p);
        this.applyFrozenState(p);
        this.snapshotMotion();
        this.pulseCooldownRemaining = this.getPulseInterval();
    }

    private void finishVelocityPassthrough(EntityPlayerSP p) {
        this.velocityInputBlockRemaining = 0;
        this.captureAnchorFromPlayer(p);
        this.refreezeMotion(p);
        this.applyFrozenState(p);
        this.snapshotMotion();
    }

    private void enforceFrozenMotion(EntityPlayerSP p) {
        if (this.countsAsVelocityImpulse(p.motionX, p.motionY, p.motionZ) && getVelocityTicks() > 0) {
            this.openVelocityWindow();
        } else {
            zeroAllMotion(p);
            this.applyFrozenState(p);
            this.snapshotMotion();
        }
    }

    private void applyFrozenState(EntityPlayerSP p) {
        if (p != null && this.anchorKnown && this.holding) {
            zeroAllMotion(p);
            this.syncPlayerToAnchor(p);
        }
    }

    private boolean isFrozen() {
        if (!this.holding) {
            return false;
        }
        if (isFlagless()) {
            return this.pulseTicksRemaining <= 0 && this.velocityTicksRemaining <= 0 && this.airReminderTicksRemaining <= 0;
        }
        return this.pulseTicksRemaining <= 0 && this.velocityTicksRemaining <= 0;
    }

    private void tickPulseCooldown() {
        if (this.pulseCooldownRemaining > 0) {
            --this.pulseCooldownRemaining;
        }
        if (this.pulseCooldownRemaining <= 0) {
            this.startPulse();
        }
    }

    private void startPulse() {
        this.pulseTicksRemaining = pulseWindow;
        this.applyStoredMotion();
        this.pulseCooldownRemaining = this.getPulseInterval();
        this.groundStableTicks = 0;
    }

    private boolean countsAsVelocityImpulse(double x, double y, double z) {
        return motionSpeed(x, y, z) > MOTION_EPS && !this.isOnlyGravityMotion(x, y, z);
    }

    private boolean isOnlyGravityMotion(double x, double y, double z) {
        double dx = x - this.lastMotionX;
        double dy = y - this.lastMotionY;
        double dz = z - this.lastMotionZ;
        if (motionSpeed(dx, dy, dz) <= MOTION_EPS) {
            return false;
        }
        if (Math.abs(dx) > GRAVITY_MATCH_EPS || Math.abs(dz) > GRAVITY_MATCH_EPS) {
            return false;
        }
        return Math.abs(y - (this.lastMotionY - 0.08) * 0.98) < GRAVITY_MATCH_EPS;
    }

    private void setAnchor(double x, double y, double z) {
        this.anchorX = x;
        this.anchorY = y;
        this.anchorZ = z;
        this.anchorKnown = true;
    }

    private void captureAnchorFromPlayer(EntityPlayerSP p) {
        if (p != null) {
            this.setAnchor(p.posX, p.posY, p.posZ);
        }
    }

    private void syncPlayerToAnchor(EntityPlayerSP p) {
        if (p == null || !this.anchorKnown) {
            return;
        }
        double dx = p.posX - this.anchorX;
        double dy = p.posY - this.anchorY;
        double dz = p.posZ - this.anchorZ;
        if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= 0.001) {
            return;
        }
        p.setPosition(this.anchorX, this.anchorY, this.anchorZ);
        p.lastTickPosX = this.anchorX;
        p.lastTickPosY = this.anchorY;
        p.lastTickPosZ = this.anchorZ;
        p.prevPosX = this.anchorX;
        p.prevPosY = this.anchorY;
        p.prevPosZ = this.anchorZ;
    }

    private static void zeroAllMotion(EntityPlayerSP p) {
        p.motionX = 0.0;
        p.motionY = 0.0;
        p.motionZ = 0.0;
    }

    private static void blockInput(MovementInput in) {
        in.moveForward = 0.0f;
        in.moveStrafe = 0.0f;
        in.jump = false;
    }

    private static void blockInput(PrePlayerInputEvent e) {
        e.setForward(0.0f);
        e.setStrafe(0.0f);
        e.setJump(false);
    }

    private void storeMotionAndClear() {
        EntityPlayerSP p = mc.thePlayer;
        if (p == null) {
            return;
        }
        if (!this.motionStored) {
            this.storedMotionX = p.motionX;
            this.storedMotionY = p.motionY;
            this.storedMotionZ = p.motionZ;
            this.motionStored = true;
        }
        zeroAllMotion(p);
    }

    private void applyStoredMotion() {
        EntityPlayerSP p = mc.thePlayer;
        if (p != null && this.motionStored) {
            p.motionX = this.storedMotionX;
            p.motionY = this.storedMotionY;
            p.motionZ = this.storedMotionZ;
        }
    }

    private void refreezeMotion(EntityPlayerSP p) {
        if (p != null) {
            this.storedMotionX = p.motionX;
            this.storedMotionY = p.motionY;
            this.storedMotionZ = p.motionZ;
            this.motionStored = true;
            zeroAllMotion(p);
        }
    }

    private void restoreMotion() {
        this.applyStoredMotion();
        this.motionStored = false;
    }

    private void clearMovementInputFlags() {
        MovementInput in = mc.thePlayer.movementInput;
        if (in != null) {
            blockInput(in);
        }
    }

    private static void sendLookPacket(float yaw, float pitch, boolean ground) {
        PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, ground));
    }

    private void snapshotMotion() {
        EntityPlayerSP p = mc.thePlayer;
        if (p != null) {
            this.lastMotionX = p.motionX;
            this.lastMotionY = p.motionY;
            this.lastMotionZ = p.motionZ;
        }
    }

    private static double motionSpeed(double x, double y, double z) {
        return Math.sqrt(x * x + y * y + z * z);
    }

    private boolean isLikelyFlagTeleport(EntityPlayerSP p, double x, double y, double z) {
        if (p == null) {
            return true;
        }
        double dx = x - p.posX;
        double dy = y - p.posY;
        double dz = z - p.posZ;
        return dx * dx + dy * dy + dz * dz <= 100.0;
    }

    private boolean jadeIsFrozen() {
        return this.jadeActive && !this.jadeResync && !this.jadeKnockback;
    }

    private void jadeEnable() {
        EntityPlayerSP p = mc.thePlayer;
        this.jadeActive = p != null;
        this.jadeResync = false;
        this.jadeKnockback = false;
        this.jadeKnockbackTicks = 0;
        this.jadeSinceSync = 0;
        this.jadeLockTicks = 0;
        this.jadeMotionSaved = false;
        if (p == null) {
            return;
        }
        this.jadeMotionX = p.motionX;
        this.jadeMotionY = p.motionY;
        this.jadeMotionZ = p.motionZ;
        this.jadeMotionSaved = true;
        this.jadeCapture(p);
    }

    private void jadeDisable() {
        EntityPlayerSP p = mc.thePlayer;
        boolean lock = this.jadeActive && Utils.nullCheck();
        if (p != null && this.jadeMotionSaved) {
            p.motionX = this.jadeMotionX;
            p.motionY = this.jadeMotionY;
            p.motionZ = this.jadeMotionZ;
        }
        this.jadeActive = false;
        this.jadeMotionSaved = false;
        this.jadeResync = false;
        this.jadeKnockback = false;
        this.jadeKnockbackTicks = 0;
        this.jadeSinceSync = 0;
        this.jadeLockTicks = lock ? JADE_LOCK_TICKS : 0;
    }

    private void jadePre(EntityPlayerSP p) {
        if (!this.jadeActive) {
            return;
        }
        if (!Utils.nullCheck()) {
            this.jadeActive = false;
            return;
        }
        // jade only runs on a loaded block, same guard it uses before posting
        // its PreUpdateEvent
        if (!p.worldObj.isBlockLoaded(new BlockPos(p.posX, 0.0, p.posZ))) {
            return;
        }
        // knockback and lagback get to move the player, that's the point
        if (this.jadeResync || this.jadeKnockback) {
            return;
        }
        this.jadeFreeze(p);
    }

    private void jadePost(EntityPlayerSP p) {
        if (!this.jadeActive) {
            return;
        }
        if (!Utils.nullCheck()) {
            this.jadeActive = false;
            return;
        }
        if (!p.worldObj.isBlockLoaded(new BlockPos(p.posX, 0.0, p.posZ))) {
            return;
        }
        if (this.jadeResync) {
            this.jadeCapture(p);
            this.jadeResync = false;
        } else if (this.jadeKnockback) {
            if (++this.jadeKnockbackTicks >= 2) {
                this.jadeCapture(p);
                this.jadeKnockback = false;
                this.jadeKnockbackTicks = 0;
                this.jadeFreeze(p);
            }
        } else {
            this.jadeFreeze(p);
        }
    }

    private void jadeMoveInput(PrePlayerInputEvent e) {
        // the 2 tick lock after a disable is jade's, it stops the restored
        // motion from being eaten by the input pass on the very next tick
        if (this.jadeLockTicks > 0) {
            this.jadeLockTicks--;
        } else if (!this.jadeActive) {
            return;
        }
        e.setForward(0.0f);
        e.setStrafe(0.0f);
        e.setJump(false);
        // jade also clears sneak here. its default mode never shift-places, so
        // it is always false outside hypixel mode
        if (mc.thePlayer.movementInput != null) {
            mc.thePlayer.movementInput.sneak = false;
        }
    }

    private void jadeReceive(Packet<?> packet) {
        if (!this.jadeActive) {
            return;
        }
        if (packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity v = (S12PacketEntityVelocity) packet;
            EntityPlayerSP p = mc.thePlayer;
            if (p != null && v.getEntityID() == p.getEntityId()) {
                this.jadeKnockback = true;
                this.jadeKnockbackTicks = 0;
            }
        } else if (packet instanceof S08PacketPlayerPosLook) {
            this.jadeResync = true;
        }
    }

    private boolean jadeSend(Packet<?> packet) {
        if (!this.jadeActive || !Utils.nullCheck() || this.jadeResync || this.jadeKnockback) {
            return true;
        }
        if (!(packet instanceof C03PacketPlayer)) {
            return true;
        }
        C03PacketPlayer c = (C03PacketPlayer) packet;
        boolean moved = c instanceof C03PacketPlayer.C04PacketPlayerPosition
                || c instanceof C03PacketPlayer.C06PacketPlayerPosLook;
        if (this.jadeSinceSync >= 20) {
            this.jadeSendPosition(c);
            this.jadeSinceSync = 0;
            return false;
        }
        if (!moved) {
            this.jadeSinceSync++;
            return true;
        }
        this.jadeSendStationary(c);
        this.jadeSinceSync++;
        return false;
    }

    private void jadeFreeze(EntityPlayerSP p) {
        p.motionX = 0.0;
        p.motionY = 0.0;
        p.motionZ = 0.0;
        p.fallDistance = 0.0f;
        p.setPosition(this.jadeX, this.jadeY, this.jadeZ);
        p.onGround = this.jadeOnGround;
    }

    private void jadeCapture(EntityPlayerSP p) {
        this.jadeX = p.posX;
        this.jadeY = p.getEntityBoundingBox().minY;
        this.jadeZ = p.posZ;
        this.jadeOnGround = p.onGround;
    }

    private void jadeSendPosition(C03PacketPlayer c) {
        if (c.getRotating()) {
            PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C06PacketPlayerPosLook(
                    this.jadeX, this.jadeY, this.jadeZ,
                    c.getYaw(), c.getPitch(), this.jadeOnGround));
        } else {
            PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(
                    this.jadeX, this.jadeY, this.jadeZ, this.jadeOnGround));
        }
    }

    private void jadeSendStationary(C03PacketPlayer c) {
        if (c.getRotating()) {
            PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C05PacketPlayerLook(
                    c.getYaw(), c.getPitch(), this.jadeOnGround));
        } else {
            PacketUtils.sendPacketNoEvent(new C03PacketPlayer(this.jadeOnGround));
        }
    }
}
