package coffee.axle.suim.feature.world;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.property.Setting;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import static coffee.axle.suim.ProjectSUIM.mc;

/**
 * AutoClutch - automatic fall-clutch via Scaffold.
 * Detects when the player is falling and enables Scaffold
 * to place blocks underneath, then disables on landing.
 *
 * Based on RSL AutoMeow (afk) and Clutch (Real Steve Bertos).
 * https://discord.com/channels/1334598495625154620/1455702511099973726/1461376350064738354
 */
public class AutoClutch extends Feature {

    public enum DisableMode {
        MOVE, FORWARD, NONE
    }

    @Setting(min = 0, max = 100) private int enableDelay = 0;
    @Setting(min = 0, max = 100) private int disableDelay = 50;
    @Setting(min = 0, max = 500) private int sprintReEnable = 150;
    @Setting(min = 0, max = 10) private int minFallDistance = 0;
    @Setting private DisableMode disableMode = DisableMode.MOVE;
    @Setting private boolean hurtTime = false;

    // Foreign modules
    private Object scaffoldModule;
    private Object sprintModule;

    // State
    private boolean falling = false;
    private boolean suppressed = false;
    private boolean needsSprint = false;
    private boolean fireballActive = false;
    private boolean fireballDelayActive = false;
    private int fireballDelayTicks = 0;
    private long airTimeStart = 0;
    private long landTimeStart = 0;
    private long sprintTimer = 0;
    private long lastClutchEndTime = 0;
    private long clutchStartTime = 0;
    private int prevHurtTime = 0;
    private long lastHurtAt = 0;

    private static final int FALL_SCAN_DEPTH = 10;
    private static final long CHAIN_WINDOW_MS = 750;
    private static final long HURT_WINDOW_MS = 1000;

    public AutoClutch() {
        super("AutoClutch", GuiCategory.WORLD);
    }

    @Override
    protected void init() throws Exception {
        scaffoldModule = manager.findModule("Scaffold");
        if (scaffoldModule == null) {
            // old gate: no Scaffold, no AutoClutch module at all
            throw new ModuleSetupException("Scaffold module not found");
        }

        sprintModule = manager.findModule("Sprint");

        MyauLogger.log(getName(), "FEATURE_READY");
    }

    @Override
    protected void onEnable() {
        falling = false;
        suppressed = false;
        needsSprint = false;
        fireballActive = false;
        fireballDelayActive = false;
        fireballDelayTicks = 0;
        airTimeStart = 0;
        landTimeStart = 0;
        sprintTimer = 0;
        lastClutchEndTime = 0;
        clutchStartTime = 0;
        prevHurtTime = 0;
        lastHurtAt = 0;
    }

    @Override
    protected void onDisable() {
        if (falling) {
            disableScaffold();
        }
        falling = false;
        suppressed = false;
        needsSprint = false;
        airTimeStart = 0;
        landTimeStart = 0;
        sprintTimer = 0;
        lastClutchEndTime = 0;
        clutchStartTime = 0;
        prevHurtTime = 0;
        lastHurtAt = 0;
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START)
            return;

        try {
            if (!isEnabled() || scaffoldModule == null)
                return;
            if (mc.thePlayer == null || mc.theWorld == null)
                return;

            // Spectator check
            if (mc.thePlayer.isSpectator()) {
                if (manager.isModuleEnabled(scaffoldModule)) {
                    disableScaffold();
                }
                return;
            }

            long now = System.currentTimeMillis();
            int dist = fallDistance();
            int minFall = getMinFallDist();
            long enableDelay = getEnableDelay();
            long disableDelay = getDisableDelay();
            long sprintDelay = getSprintReEnable();

            updateFireballState();
            handleTurnoff(now, disableDelay);

            int curHurtTime = mc.thePlayer.hurtTime;
            if (curHurtTime > prevHurtTime) {
                lastHurtAt = now;
            }
            prevHurtTime = curHurtTime;

            // Fall detection
            if (!mc.thePlayer.onGround && mc.thePlayer.motionY < -0.01) {
                if (airTimeStart == 0) {
                    airTimeStart = now;
                }

                // Chain window: if we just finished a clutch cycle, skip
                // the fall distance check so re-hits trigger immediately
                boolean inChainWindow = lastClutchEndTime != 0
                        && now - lastClutchEndTime < CHAIN_WINDOW_MS;

                boolean shouldTrigger;
                if (inChainWindow) {
                    shouldTrigger = true;
                } else if (minFall == 0) {
                    // 0 = void only
                    shouldTrigger = dist == -1;
                } else {
                    shouldTrigger = dist == -1 || dist >= minFall;
                }

                if (getHurtTimeEnabled()) {
                    shouldTrigger = shouldTrigger && now - lastHurtAt < HURT_WINDOW_MS;
                }

                if (shouldTrigger
                        && !suppressed
                        && !manager.isModuleEnabled(scaffoldModule)
                        && !mc.thePlayer.capabilities.isFlying
                        && !fireballActive) {
                    if (now - airTimeStart >= enableDelay) {
                        MyauLogger.log(getName(), "clutch trigger - enabling Scaffold (dist=" + dist + ")");
                        manager.setModuleEnabled(scaffoldModule, true);
                        setSprint(false);
                        falling = true;
                        needsSprint = true;
                        sprintTimer = 0;
                        lastClutchEndTime = 0;
                        clutchStartTime = now;
                    }
                }
            } else if (mc.thePlayer.onGround) {
                airTimeStart = 0;
                suppressed = false;
                if (!falling) {
                    landTimeStart = 0;
                }
            }

            // Land detection
            if (falling
                    && (mc.thePlayer.onGround
                            || (dist != -1 && dist < 1))) {
                if (landTimeStart == 0) {
                    landTimeStart = now;
                }

                if (manager.isModuleEnabled(scaffoldModule)) {
                    if (now - landTimeStart >= disableDelay) {
                        stopClutch(now, false);
                    }
                }
            }

            // Sprint re-enable
            if (!falling && needsSprint && sprintTimer != 0) {
                if (now - sprintTimer >= sprintDelay) {
                    setSprint(true);
                    needsSprint = false;
                    sprintTimer = 0;
                }
            }

        } catch (Exception e) {
            MyauLogger.error("AutoClutch:tick", e);
        }
    }

    private void updateFireballState() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held != null
                && held.getUnlocalizedName().contains("fire_charge")
                && mc.gameSettings.keyBindUseItem.isKeyDown()) {
            fireballActive = true;
            if (!fireballDelayActive) {
                fireballDelayActive = true;
                fireballDelayTicks = 20;
            }
        }

        if (fireballDelayActive && fireballDelayTicks > 0) {
            fireballDelayTicks--;
            if (fireballDelayTicks < 2) {
                fireballActive = false;
                fireballDelayActive = false;
            }
        }
    }

    private void handleTurnoff(long now, long disableDelay) {
        if (!falling)
            return;

        // give Scaffold time to place before the movement-based turnoff can kill it -
        // otherwise (holding the key you walked off with) it disables a tick after enabling
        long grace = Math.max(disableDelay, 150);
        if (clutchStartTime != 0 && now - clutchStartTime < grace)
            return;

        switch (disableMode) {
            case MOVE:
                if (isMoving() || isAnyMovementKeyDown()) {
                    stopClutch(now, true);
                }
                break;
            case FORWARD:
                if (mc.gameSettings.keyBindForward.isKeyDown()) {
                    stopClutch(now, true);
                }
                break;
            case NONE:
                break;
        }
    }

    private void stopClutch(long now, boolean suppress) {
        disableScaffold();
        falling = false;
        suppressed = suppress;
        landTimeStart = 0;
        lastClutchEndTime = now;
        if (needsSprint) {
            sprintTimer = now;
        }
    }

    private void disableScaffold() {
        try {
            manager.setModuleEnabled(scaffoldModule, false);
            if (manager.isModuleEnabled(scaffoldModule)) {
                MyauLogger.log(getName(),
                        "Scaffold still enabled after setModuleEnabled(false), retrying");
                manager.setModuleEnabled(scaffoldModule, false);
            }
        } catch (Exception e) {
            MyauLogger.error("AutoClutch:disableScaffold", e);
        }
    }

    private void setSprint(boolean enabled) {
        if (sprintModule == null)
            return;
        try {
            manager.setModuleEnabled(sprintModule, enabled);
        } catch (Exception e) {
            MyauLogger.error("AutoClutch:setSprint", e);
        }
    }

    private int fallDistance() {
        Vec3 pos = mc.thePlayer.getPositionVector();
        int startY = MathHelper.floor_double(pos.yCoord) - 1;
        if (startY < 0)
            return -1;

        int minY = Math.max(0, startY - FALL_SCAN_DEPTH);
        int px = MathHelper.floor_double(pos.xCoord);
        int pz = MathHelper.floor_double(pos.zCoord);

        for (int i = startY; i >= minY; i--) {
            Block block = mc.theWorld
                    .getBlockState(new BlockPos(px, i, pz)).getBlock();

            if (block == Blocks.air)
                continue;

            String name = block.getUnlocalizedName();
            if (name.contains("sign") || name.contains("torch"))
                continue;

            return startY - i;
        }

        return -1;
    }

    private boolean isMoving() {
        return mc.thePlayer.movementInput.moveForward != 0.0f
                || mc.thePlayer.movementInput.moveStrafe != 0.0f;
    }

    private boolean isAnyMovementKeyDown() {
        return mc.gameSettings.keyBindForward.isKeyDown()
                || mc.gameSettings.keyBindBack.isKeyDown()
                || mc.gameSettings.keyBindLeft.isKeyDown()
                || mc.gameSettings.keyBindRight.isKeyDown()
                || mc.gameSettings.keyBindJump.isKeyDown()
                || mc.gameSettings.keyBindSneak.isKeyDown();
    }

    private boolean getHurtTimeEnabled() {
        return hurtTime;
    }

    private long getEnableDelay() {
        return enableDelay;
    }

    private long getDisableDelay() {
        return disableDelay;
    }

    private long getSprintReEnable() {
        return sprintReEnable;
    }

    private int getMinFallDist() {
        return minFallDistance;
    }
}
