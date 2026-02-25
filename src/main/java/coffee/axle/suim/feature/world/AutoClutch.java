package coffee.axle.suim.feature.world;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;

import coffee.axle.suim.util.MyauLogger;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * AutoClutch — automatic fall-clutch via Scaffold.
 * Detects when the player is falling and enables Scaffold
 * to place blocks underneath, then disables on landing.
 *
 * Based on RSL AutoMeow (afk) and Clutch (Real Steve Bertos).
 * https://discord.com/channels/1334598495625154620/1455702511099973726/1461376350064738354
 */
public class AutoClutch extends Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private Object moduleInstance;

    // Properties
    private Object hurtTimeProperty;
    private Object turnOffModeProperty;
    private Object enableDelayProperty;
    private Object disableDelayProperty;
    private Object sprintReEnableProperty;
    private Object minFallDistProperty;

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

    private static final int FALL_SCAN_DEPTH = 10;
    private static final long CHAIN_WINDOW_MS = 750;

    @Override
    public String getName() {
        return "AutoClutch";
    }

    @Override
    public GuiCategory getGuiCategory() {
        return GuiCategory.WORLD;
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");

            scaffoldModule = manager.findModule("Scaffold");
            if (scaffoldModule == null) {
                MyauLogger.log(getName(), "Scaffold module not found");
                return false;
            }

            sprintModule = manager.findModule("Sprint");

            moduleInstance = createModule();
            creator.injectModule(moduleInstance, AutoClutch.class);

            hurtTimeProperty = creator.createBooleanProperty("hurt-time", true);
            turnOffModeProperty = creator.createEnumProperty(
                    "disable-mode", 0,
                    new String[] { "MOVE", "FORWARD", "NONE" });
            enableDelayProperty = creator.createIntegerProperty("enable-delay", 0, 0, 100);
            disableDelayProperty = creator.createIntegerProperty("disable-delay", 0, 0, 100);
            sprintReEnableProperty = creator.createIntegerProperty("sprint-re-enable", 150, 0, 500);
            minFallDistProperty = creator.createIntegerProperty("min-fall-distance", 0, 0, 10);

            creator.registerProperties(
                    moduleInstance,
                    hurtTimeProperty,
                    turnOffModeProperty,
                    enableDelayProperty,
                    disableDelayProperty,
                    sprintReEnableProperty,
                    minFallDistProperty);

            manager.reloadModuleCommand();

            MinecraftForge.EVENT_BUS.register(this);

            manager.registerModuleCallbacks(
                    moduleInstance,
                    this::onModuleEnabled,
                    this::onModuleDisabled);

            MyauLogger.log(getName(), "FEATURE_MEOWW");
            return true;

        } catch (Exception e) {
            MyauLogger.error("FEATURE_NO_TUNA", e);
            return false;
        }
    }

    private void onModuleEnabled() {
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
    }

    private void onModuleDisabled() {
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
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START)
            return;

        try {
            if (!manager.isModuleEnabled(moduleInstance))
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
            handleTurnoff(now);

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
                    shouldTrigger = dist == -1;
                } else {
                    shouldTrigger = dist == -1 || dist >= minFall;
                }

                if (getHurtTimeEnabled()) {
                    shouldTrigger = shouldTrigger
                            && mc.thePlayer.hurtTime > 0;
                }

                if (shouldTrigger
                        && !suppressed
                        && !manager.isModuleEnabled(scaffoldModule)
                        && !mc.thePlayer.capabilities.isFlying
                        && !fireballActive) {
                    if (now - airTimeStart >= enableDelay) {
                        manager.setModuleEnabled(scaffoldModule, true);
                        setSprint(false);
                        falling = true;
                        needsSprint = true;
                        sprintTimer = 0;
                        lastClutchEndTime = 0;
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

    private void handleTurnoff(long now) {
        if (!falling)
            return;

        int turnOffMode = getTurnOffMode();
        switch (turnOffMode) {
            case 0: // MOVE
                if (isMoving() || isAnyMovementKeyDown()) {
                    stopClutch(now, true);
                }
                break;
            case 1: // FORWARD
                if (mc.gameSettings.keyBindForward.isKeyDown()) {
                    stopClutch(now, true);
                }
                break;
            case 2: // NONE
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
        try {
            return (Boolean) properties.getPropertyValue(hurtTimeProperty);
        } catch (Exception e) {
            return true;
        }
    }

    private int getTurnOffMode() {
        try {
            return ((Number) properties.getPropertyValue(turnOffModeProperty))
                    .intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private long getEnableDelay() {
        try {
            return ((Number) properties.getPropertyValue(enableDelayProperty))
                    .longValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private long getDisableDelay() {
        try {
            return ((Number) properties.getPropertyValue(disableDelayProperty))
                    .longValue();
        } catch (Exception e) {
            return 50;
        }
    }

    private long getSprintReEnable() {
        try {
            return ((Number) properties.getPropertyValue(sprintReEnableProperty))
                    .longValue();
        } catch (Exception e) {
            return 150;
        }
    }

    private int getMinFallDist() {
        try {
            return ((Number) properties.getPropertyValue(minFallDistProperty))
                    .intValue();
        } catch (Exception e) {
            return 0;
        }
    }
}
