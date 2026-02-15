package coffee.axle.suim.feature.world;

import coffee.axle.suim.events.SendPacketEvent;
import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.feature.world.blockin.BlockInPlacement;
import coffee.axle.suim.feature.world.blockin.BlockInPlacement.PlaceResult;
import coffee.axle.suim.feature.world.blockin.BlockInRenderer;
import coffee.axle.suim.feature.world.blockin.BlockInScoring;
import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.RotationUtil;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import java.util.Set;

/**
 * AutoBlockIn — automatic block-in/cage builder.
 * <p>
 * Uses purely Forge events for rotation spoofing (via outgoing packet
 * interception), placement, and rendering. No Myau UpdateEvent dependency.
 * <p>
 * Rotation uses the CoffeeClient aimassist model:
 * horizontal-speed + vertical-speed control per-tick rotation rate,
 * smoothing (0-100%) controls dampening factor.
 * <p>
 * Auto-disables when all 9 cage positions are filled.
 *
 * @see BlockInPlacement placement finding logic
 * @see BlockInScoring block priority scoring
 * @see BlockInRenderer HUD overlay
 * @see RotationUtil CoffeeClient-style smoothing math
 */
@SuppressWarnings("unused")
public class AutoBlockIn extends Feature {

    private static final Minecraft mc = Minecraft.getMinecraft();

    /** Max consecutive placement failures before auto-skipping a target. */
    private static final int MAX_FAIL_COUNT = 3;

    private Object moduleInstance;

    // Properties
    private Object rangeProperty;
    private Object hSpeedProperty;
    private Object vSpeedProperty;
    private Object smoothingProperty;
    private Object placeDelayProperty;
    private Object rotationToleranceProperty;
    private Object showProgressProperty;
    private Object moveFixProperty;
    private Object bedRangeProperty;
    private Object autoDisableProperty;
    private Object debugProperty;
    private Object skipInteractableProperty;
    private Object orderProperty;
    private Object disableOnMoveProperty;
    private Object placeRetryProperty;

    // Context module references (for suppression checks)
    private Object killAuraModule;
    private Object bedNukerModule;
    private Object bedEspModule;
    private Field killAuraTargetField;
    private Field killAuraIsBlockingField;
    private Field bedNukerTargetBedField;
    private Field bedNukerBreakingField;
    private Field bedEspBedsField;

    // Rotation state — tracked from outgoing packets
    private float serverYaw;
    private float serverPitch;

    // The visual yaw before we spoofed (for move-fix correction)
    private float preSpoofYaw;

    // Aim target (smoothed toward)
    private float aimYaw;
    private float aimPitch;

    // Placement state
    private PlaceResult currentTarget;
    private long lastPlaceTime;
    private float progress;
    private boolean operationActive;
    private int savedSlot = -1;

    // Disable-on-move tracking
    private BlockPos startPos;

    // Failure tracking for auto-skip
    private BlockPos lastFailedGoal;
    private int consecutiveFailures;

    @Override
    public String getName() {
        return "AutoBlockIn";
    }

    @Override
    public GuiCategory getGuiCategory() {
        return GuiCategory.WORLD;
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");

            moduleInstance = createModule();
            creator.injectModule(moduleInstance, AutoBlockIn.class);

            rangeProperty = creator.createFloatProperty("range", 4.5f, 3.0f, 6.0f);
            hSpeedProperty = creator.createFloatProperty("horizontal-speed", 6.0f, 0.0f, 10.0f);
            vSpeedProperty = creator.createFloatProperty("vertical-speed", 6.0f, 0.0f, 10.0f);
            smoothingProperty = creator.createIntegerProperty("smoothing", 50, 0, 100);
            placeDelayProperty = creator.createIntegerProperty("place-delay", 50, 0, 200);
            rotationToleranceProperty = creator.createIntegerProperty(
                    "rotation-tolerance", 25, 5, 100);
            showProgressProperty = creator.createBooleanProperty("show-progress", true);
            moveFixProperty = creator.createEnumProperty("move-fix", 1,
                    new String[] { "NONE", "SILENT", "STRICT" });
            bedRangeProperty = creator.createFloatProperty("bed-range", 0f, 0f, 10f);
            autoDisableProperty = creator.createBooleanProperty("auto-disable", true);
            debugProperty = creator.createEnumProperty("debug", 0,
                    new String[] { "NONE", "PLACE" });
            skipInteractableProperty = creator.createEnumProperty(
                    "skip-interactable", 0,
                    new String[] { "NONE", "SNEAK", "SKIP" });
            orderProperty = creator.createEnumProperty("order", 1,
                    new String[] { "RANDOM", "ENEMY" });
            disableOnMoveProperty = creator.createBooleanProperty(
                    "disable-on-move", false);
            placeRetryProperty = creator.createIntegerProperty(
                    "place-retry", 1, 1, 10);

            creator.registerProperties(moduleInstance,
                    rangeProperty, hSpeedProperty, vSpeedProperty,
                    smoothingProperty, placeDelayProperty,
                    rotationToleranceProperty, showProgressProperty,
                    moveFixProperty, bedRangeProperty, autoDisableProperty,
                    debugProperty, skipInteractableProperty,
                    orderProperty, disableOnMoveProperty, placeRetryProperty);

            manager.reloadModuleCommand();
            hookContextModules();

            MinecraftForge.EVENT_BUS.register(this);

            manager.registerModuleCallbacks(moduleInstance,
                    this::onModuleEnabled, this::onModuleDisabled);

            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;

        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    // ==================== Context Module Hooks ====================

    private void hookContextModules() {
        try {
            bedEspModule = manager.findModule("BedESP");
            killAuraModule = manager.findModule("KillAura");
            bedNukerModule = manager.findModule("BedNuker");

            if (bedEspModule != null) {
                bedEspBedsField = manager.getCachedField(
                        bedEspModule.getClass(), MyauMappings.FIELD_BED_ESP_BEDS);
            }

            if (killAuraModule != null) {
                killAuraTargetField = findField(
                        killAuraModule.getClass(), MyauMappings.FIELD_KILL_AURA_TARGET);
                killAuraIsBlockingField = findField(
                        killAuraModule.getClass(), MyauMappings.FIELD_KILL_AURA_IS_BLOCKING);
            }

            if (bedNukerModule != null) {
                bedNukerTargetBedField = findField(bedNukerModule.getClass(), "I");
                bedNukerBreakingField = findField(bedNukerModule.getClass(), "K");
            }
        } catch (Exception e) {
            MyauLogger.error("AutoBlockIn hookContextModules", e);
        }
    }

    private static Field findField(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    // ==================== Module Lifecycle ====================

    private void onModuleEnabled() {
        if (mc.thePlayer != null) {
            serverYaw = mc.thePlayer.rotationYaw;
            serverPitch = mc.thePlayer.rotationPitch;
            preSpoofYaw = serverYaw;
            aimYaw = serverYaw;
            aimPitch = serverPitch;
            progress = 0;
            savedSlot = mc.thePlayer.inventory.currentItem;
            startPos = mc.thePlayer.getPosition();
            currentTarget = null;
            operationActive = false;
            lastPlaceTime = 0;
            lastFailedGoal = null;
            consecutiveFailures = 0;
            BlockInPlacement.clearSkippedGoals();
        }
        MyauLogger.log(getName(), "MODULE_ENABLED");
    }

    private void onModuleDisabled() {
        if (savedSlot != -1 && mc.thePlayer != null
                && mc.thePlayer.inventory.currentItem != savedSlot) {
            mc.thePlayer.inventory.currentItem = savedSlot;
        }
        progress = 0;
        currentTarget = null;
        operationActive = false;
        lastFailedGoal = null;
        consecutiveFailures = 0;
        startPos = null;
        BlockInPlacement.clearSkippedGoals();
        MyauLogger.log(getName(), "MODULE_DISABLED");
    }

    // ==================== Packet Interception (rotation tracking)
    // ====================

    /**
     * Track actual server-side yaw/pitch from outgoing movement packets.
     * This replaces the Myau UpdateEvent dependency entirely.
     */
    @SubscribeEvent
    public void onSendPacket(SendPacketEvent event) {
        if (!(event.getPacket() instanceof C03PacketPlayer))
            return;

        C03PacketPlayer packet = (C03PacketPlayer) event.getPacket();

        // C05 (look-only) and C06 (pos+look) carry rotation data
        if (packet instanceof C03PacketPlayer.C05PacketPlayerLook
                || packet instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
            serverYaw = packet.getYaw();
            serverPitch = packet.getPitch();
        }
    }

    // ==================== Main Tick Logic ====================

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START)
            return;

        try {
            if (!manager.isModuleEnabled(moduleInstance))
                return;
            if (mc.thePlayer == null || mc.theWorld == null)
                return;
            if (mc.currentScreen != null)
                return;

            operationActive = !isSuppressed() && isBedTriggerSatisfied();
            if (!operationActive) {
                currentTarget = null;
                progress = 0f;
                return;
            }

            // Disable-on-move: if player moved from starting position
            if (getDisableOnMove() && startPos != null) {
                BlockPos currentPos = mc.thePlayer.getPosition();
                if (!currentPos.equals(startPos)) {
                    debugMsg("Player moved — auto-disabling");
                    manager.setModuleEnabled(moduleInstance, false);
                    return;
                }
            }

            // Update progress
            Vec3 playerPos = mc.thePlayer.getPositionVector();
            BlockPos feetPos = new BlockPos(
                    playerPos.xCoord, playerPos.yCoord, playerPos.zCoord);
            int filled = BlockInPlacement.countFilledPositions(feetPos);
            progress = (float) filled / BlockInPlacement.TOTAL_POSITIONS;

            // Auto-disable at 100%
            if (filled >= BlockInPlacement.TOTAL_POSITIONS && getAutoDisable()) {
                MyauLogger.log(getName(), "Cage complete — auto-disabling");
                manager.setModuleEnabled(moduleInstance, false);
                return;
            }

            // Select best block slot
            int bestSlot = BlockInScoring.findBestBlockSlot();
            if (bestSlot != -1 && mc.thePlayer.inventory.currentItem != bestSlot) {
                mc.thePlayer.inventory.currentItem = bestSlot;
            }

            // Verify holding a block
            if (!BlockInScoring.isHoldingBlock()) {
                currentTarget = null;
                return;
            }

            // Find placement — re-scan every tick for fresh raytrace data
            BlockInPlacement.setSkipInteractableMode(getSkipInteractableMode());
            currentTarget = BlockInPlacement.findBestPlacement(getRange(),
                    getOrderMode());
            if (currentTarget == null)
                return;

            // If this is a support placement, switch to weakest block
            if (currentTarget.isSupport) {
                int weakSlot = BlockInScoring.findWeakestBlockSlot();
                if (weakSlot != -1
                        && mc.thePlayer.inventory.currentItem != weakSlot) {
                    mc.thePlayer.inventory.currentItem = weakSlot;
                }
            }

            // Check if this target's goal was auto-skipped
            BlockPos goalPos = currentTarget.supportBlock.offset(currentTarget.face);
            if (lastFailedGoal != null && lastFailedGoal.equals(goalPos)
                    && consecutiveFailures >= MAX_FAIL_COUNT) {
                // Skip this goal — tell placement to exclude it
                debugMsg("SKIP " + formatPos(goalPos) + " (failed " + MAX_FAIL_COUNT + "x)");
                BlockInPlacement.addSkippedGoal(goalPos);
                lastFailedGoal = null;
                consecutiveFailures = 0;
                currentTarget = null;
                return;
            }

            // Save visual yaw before spoofing (for move-fix correction)
            preSpoofYaw = mc.thePlayer.rotationYaw;

            // CoffeeClient-style smoothed rotations
            float[] smoothed = getSmoothedRotations(
                    currentTarget.yaw, currentTarget.pitch);
            aimYaw = smoothed[0];
            aimPitch = smoothed[1];

            // Apply server-side rotation via player fields
            mc.thePlayer.rotationYaw = aimYaw;
            mc.thePlayer.rotationPitch = aimPitch;

            // Apply move fix (correct WASD input relative to spoofed rotation)
            applyMoveFix();

            // Check if rotation is close enough to attempt placement
            if (!withinTolerance(currentTarget.yaw, currentTarget.pitch))
                return;

            // REVALIDATE raytrace at current aim angles every tick
            MovingObjectPosition mop = BlockInPlacement.rayTraceBlock(
                    aimYaw, aimPitch, getRange());
            if (mop == null
                    || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                    || !mop.getBlockPos().equals(currentTarget.supportBlock)
                    || mop.sideHit != currentTarget.face) {
                // Raytrace no longer valid — track failure
                trackFailure(goalPos);
                return;
            }

            // Verify the target position is still air (block might have been
            // placed by another source or already filled)
            if (!BlockInPlacement.isAir(goalPos)) {
                currentTarget = null;
                return;
            }

            // Place delay check
            long now = System.currentTimeMillis();
            if (now - lastPlaceTime < getPlaceDelay())
                return;
            lastPlaceTime = now;

            // Execute placement with retry support
            int maxRetries = getPlaceRetry();

            for (int attempt = 0; attempt < maxRetries; attempt++) {
                // Re-aim between retries (re-smooth toward target)
                if (attempt > 0) {
                    float[] resmoothed = getSmoothedRotations(
                            currentTarget.yaw, currentTarget.pitch);
                    aimYaw = resmoothed[0];
                    aimPitch = resmoothed[1];
                    mc.thePlayer.rotationYaw = aimYaw;
                    mc.thePlayer.rotationPitch = aimPitch;

                    // Re-validate raytrace for this retry
                    mop = BlockInPlacement.rayTraceBlock(
                            aimYaw, aimPitch, getRange());
                    if (mop == null
                            || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                            || !mop.getBlockPos().equals(currentTarget.supportBlock)
                            || mop.sideHit != currentTarget.face) {
                        continue;
                    }
                }

                ItemStack held = mc.thePlayer.inventory.getCurrentItem();
                if (held == null || !(held.getItem() instanceof ItemBlock))
                    break;

                Block block = ((ItemBlock) held.getItem()).getBlock();
                String blockName = Block.blockRegistry.getNameForObject(block)
                        .toString();

                debugMsg("PLACING " + blockName + " " + formatPos(goalPos)
                        + (attempt > 0 ? " (retry " + attempt + ")" : ""));

                // Sneak handling for interactable support blocks
                boolean wasSneaking = mc.thePlayer.isSneaking();
                if (currentTarget.requiresSneak && !wasSneaking) {
                    KeyBinding.setKeyBindState(
                            mc.gameSettings.keyBindSneak.getKeyCode(), true);
                    mc.thePlayer.setSneaking(true);
                }

                boolean placed = mc.playerController.onPlayerRightClick(
                        mc.thePlayer, mc.theWorld, held,
                        currentTarget.supportBlock, currentTarget.face,
                        mop.hitVec);

                // Restore sneak state
                if (currentTarget.requiresSneak && !wasSneaking) {
                    KeyBinding.setKeyBindState(
                            mc.gameSettings.keyBindSneak.getKeyCode(), false);
                    mc.thePlayer.setSneaking(false);
                }

                if (placed) {
                    mc.thePlayer.swingItem();
                    debugMsg("PLACED " + formatPos(goalPos));

                    if (lastFailedGoal != null
                            && lastFailedGoal.equals(goalPos)) {
                        lastFailedGoal = null;
                        consecutiveFailures = 0;
                    }
                    break;
                } else {
                    debugMsg("REJECTED " + formatPos(goalPos)
                            + " (attempt " + (attempt + 1) + "/"
                            + maxRetries + ")");
                    if (attempt == maxRetries - 1) {
                        trackFailure(goalPos);
                    }
                }
            }

            currentTarget = null;

        } catch (Exception e) {
            MyauLogger.error("AutoBlockIn onClientTick", e);
        }
    }

    // ==================== Failure Tracking ====================

    private void trackFailure(BlockPos goalPos) {
        if (lastFailedGoal != null && lastFailedGoal.equals(goalPos)) {
            consecutiveFailures++;
        } else {
            lastFailedGoal = goalPos;
            consecutiveFailures = 1;
        }

        if (consecutiveFailures >= MAX_FAIL_COUNT) {
            debugMsg("FAILED_PLACE " + formatPos(goalPos)
                    + " (" + consecutiveFailures + "/" + MAX_FAIL_COUNT + ")");
        }
    }

    // ==================== Rotation Smoothing (CoffeeClient-style)
    // ====================

    /**
     * CoffeeClient aimassist smoothing model:
     * <ol>
     * <li>{@link RotationUtil#getRotations} computes smoothed target angles
     * using the smoothing factor (0-100% mapped to 0.0-1.0)</li>
     * <li>Per-tick step is: {@code (target - current) * 0.1 * speed}</li>
     * </ol>
     *
     * @see <a href="https://github.com/axlecoffee/CoffeeClient">CoffeeClient
     *      AimAssist</a>
     */
    private float[] getSmoothedRotations(float targetYaw, float targetPitch) {
        float currentYaw = serverYaw;
        float currentPitch = serverPitch;

        // Compute target hit vector delta from eye
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);

        // Use RotationUtil to get smoothed intermediate rotations
        // smoothFactor: 0 = no smoothing (full step), 100 = max smoothing
        float smoothFactor = getSmoothing() / 100.0f;

        // Get the ideal rotation with smoothing applied (from RotationUtil)
        double dx = currentTarget.hitVec.xCoord - eye.xCoord;
        double dy = currentTarget.hitVec.yCoord - eye.yCoord;
        double dz = currentTarget.hitVec.zCoord - eye.zCoord;

        float[] smoothedTarget = RotationUtil.getRotations(
                dx, dy, dz,
                currentYaw, currentPitch,
                180.0f,
                smoothFactor);

        // Apply per-tick speed multiplier (CoffeeClient approach)
        float hSpeed = Math.min(Math.abs(getHSpeed()), 10.0f);
        float vSpeed = Math.min(Math.abs(getVSpeed()), 10.0f);

        float newYaw = currentYaw
                + (smoothedTarget[0] - currentYaw) * 0.1f * hSpeed;
        float newPitch = currentPitch
                + (smoothedTarget[1] - currentPitch) * 0.1f * vSpeed;

        newPitch = MathHelper.clamp_float(newPitch, -90f, 90f);

        return new float[] { newYaw, newPitch };
    }

    // ==================== Move Fix ====================

    /**
     * Input-based movement correction. Transforms WASD movement
     * relative to the spoofed server yaw so the player walks in
     * the direction they expect visually.
     */
    private void applyMoveFix() {
        if (mc.thePlayer == null || mc.thePlayer.movementInput == null)
            return;

        int mode = getMoveFixMode();
        if (mode <= 0)
            return;
        if (currentTarget == null)
            return;

        float forward = mc.thePlayer.movementInput.moveForward;
        float strafe = mc.thePlayer.movementInput.moveStrafe;
        if (Math.abs(forward) < 1.0E-4f && Math.abs(strafe) < 1.0E-4f)
            return;

        float delta = (float) Math.toRadians(preSpoofYaw - aimYaw);
        float cos = MathHelper.cos(delta);
        float sin = MathHelper.sin(delta);

        mc.thePlayer.movementInput.moveForward = forward * cos + strafe * sin;
        mc.thePlayer.movementInput.moveStrafe = strafe * cos - forward * sin;
    }

    // ==================== Rotation Tolerance ====================

    private boolean withinTolerance(float targetYaw, float targetPitch) {
        float dy = Math.abs(MathHelper.wrapAngleTo180_float(aimYaw - targetYaw));
        float dp = Math.abs(MathHelper.wrapAngleTo180_float(aimPitch - targetPitch));
        int tol = getRotationTolerance();
        return dy <= tol && dp <= tol;
    }

    // ==================== HUD Rendering ====================

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL)
            return;
        if (!manager.isModuleEnabled(moduleInstance))
            return;
        if (mc.currentScreen != null)
            return;
        if (!getShowProgress())
            return;
        if (!operationActive)
            return;

        BlockInRenderer.renderProgress(progress);
    }

    // ==================== Debug ====================

    private void debugMsg(String msg) {
        if (getDebugMode() <= 0)
            return;
        manager.sendMessage("&8[&eAutoBlockIn&8] &7" + msg);
    }

    private String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    // ==================== Suppression Checks ====================

    private boolean isSuppressed() {
        if (killAuraModule == null)
            killAuraModule = manager.findModule("KillAura");
        if (bedNukerModule == null)
            bedNukerModule = manager.findModule("BedNuker");

        return isKillAuraActive() || isBedNukerActive();
    }

    private boolean isKillAuraActive() {
        try {
            if (killAuraModule == null
                    || !manager.isModuleEnabled(killAuraModule))
                return false;
            if (killAuraTargetField != null
                    && killAuraTargetField.get(killAuraModule) != null)
                return true;
            return killAuraIsBlockingField != null
                    && killAuraIsBlockingField.getBoolean(killAuraModule);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isBedNukerActive() {
        try {
            if (bedNukerModule == null
                    || !manager.isModuleEnabled(bedNukerModule))
                return false;
            if (bedNukerBreakingField != null
                    && bedNukerBreakingField.getBoolean(bedNukerModule))
                return true;
            return bedNukerTargetBedField != null
                    && bedNukerTargetBedField.get(bedNukerModule) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    // ==================== Bed Trigger ====================

    private boolean isBedTriggerSatisfied() {
        float bedRange = getBedRange();
        if (bedRange <= 0f)
            return true;
        if (mc.thePlayer == null || mc.theWorld == null)
            return false;

        double nearest = getNearestBedDistanceFromEsp();
        if (nearest >= 0d)
            return nearest <= bedRange;

        // Fallback: local block scan
        BlockPos feet = mc.thePlayer.getPosition();
        int r = MathHelper.ceiling_float_int(bedRange);
        double best = Double.POSITIVE_INFINITY;

        for (int x = -r; x <= r; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = feet.add(x, y, z);
                    if (!(mc.theWorld.getBlockState(p).getBlock() instanceof BlockBed))
                        continue;
                    double dist = mc.thePlayer.getDistance(
                            p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                    if (dist < best)
                        best = dist;
                }
            }
        }

        return best <= bedRange;
    }

    @SuppressWarnings("unchecked")
    private double getNearestBedDistanceFromEsp() {
        try {
            if (bedEspModule == null)
                bedEspModule = manager.findModule("BedESP");
            if (bedEspModule == null)
                return -1d;

            if (bedEspBedsField == null) {
                bedEspBedsField = manager.getCachedField(
                        bedEspModule.getClass(),
                        MyauMappings.FIELD_BED_ESP_BEDS);
            }

            Set<BlockPos> beds = (Set<BlockPos>) bedEspBedsField.get(bedEspModule);
            if (beds == null || beds.isEmpty())
                return -1d;

            double best = Double.POSITIVE_INFINITY;
            for (BlockPos p : beds) {
                double dist = mc.thePlayer.getDistance(
                        p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                if (dist < best)
                    best = dist;
            }
            return best;
        } catch (Exception e) {
            return -1d;
        }
    }

    // ==================== Property Accessors ====================

    private float getRange() {
        try {
            return ((Number) properties.getPropertyValue(
                    rangeProperty)).floatValue();
        } catch (Exception e) {
            return 4.5f;
        }
    }

    private float getHSpeed() {
        try {
            return ((Number) properties.getPropertyValue(
                    hSpeedProperty)).floatValue();
        } catch (Exception e) {
            return 6.0f;
        }
    }

    private float getVSpeed() {
        try {
            return ((Number) properties.getPropertyValue(
                    vSpeedProperty)).floatValue();
        } catch (Exception e) {
            return 6.0f;
        }
    }

    private float getSmoothing() {
        try {
            return ((Number) properties.getPropertyValue(
                    smoothingProperty)).floatValue();
        } catch (Exception e) {
            return 50f;
        }
    }

    private int getPlaceDelay() {
        try {
            return ((Number) properties.getPropertyValue(
                    placeDelayProperty)).intValue();
        } catch (Exception e) {
            return 50;
        }
    }

    private int getRotationTolerance() {
        try {
            return ((Number) properties.getPropertyValue(
                    rotationToleranceProperty)).intValue();
        } catch (Exception e) {
            return 25;
        }
    }

    private boolean getShowProgress() {
        try {
            return (Boolean) properties.getPropertyValue(showProgressProperty);
        } catch (Exception e) {
            return true;
        }
    }

    private int getMoveFixMode() {
        try {
            return ((Number) properties.getPropertyValue(
                    moveFixProperty)).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private float getBedRange() {
        try {
            return ((Number) properties.getPropertyValue(
                    bedRangeProperty)).floatValue();
        } catch (Exception e) {
            return 0f;
        }
    }

    private boolean getAutoDisable() {
        try {
            return (Boolean) properties.getPropertyValue(autoDisableProperty);
        } catch (Exception e) {
            return true;
        }
    }

    private int getDebugMode() {
        try {
            return ((Number) properties.getPropertyValue(
                    debugProperty)).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private int getSkipInteractableMode() {
        try {
            return ((Number) properties.getPropertyValue(
                    skipInteractableProperty)).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private int getOrderMode() {
        try {
            return ((Number) properties.getPropertyValue(
                    orderProperty)).intValue();
        } catch (Exception e) {
            return 1;
        }
    }

    private boolean getDisableOnMove() {
        try {
            return (Boolean) properties.getPropertyValue(disableOnMoveProperty);
        } catch (Exception e) {
            return false;
        }
    }

    private int getPlaceRetry() {
        try {
            return ((Number) properties.getPropertyValue(
                    placeRetryProperty)).intValue();
        } catch (Exception e) {
            return 1;
        }
    }

    @Override
    public void disable() {
        onModuleDisabled();
    }
}
