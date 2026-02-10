package coffee.axle.suim.feature.world;

import coffee.axle.suim.feature.Feature;

import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Method;

/**
 * Eagle auto-swap
 */
@SuppressWarnings("unused")
public class EagleAutoSwap extends Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private Object eagleModule;
    private Object autoSwapProperty;

    private Object directionCheckProperty;
    private Object pitchCheckProperty;
    private Object blocksOnlyProperty;

    private Method isEnabledMethod;
    private Method shouldSneakMethod;

    private int lastSwapTick = 0;
    private int swappedToSlot = -1;

    @Override
    public String getName() {
        return "Eagle:AutoSwap";
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");

            eagleModule = manager.findModule("Eagle");
            if (eagleModule == null) {
                MyauLogger.log(getName(), "MODULE_NOT_FOUND");
                return false;
            }

            directionCheckProperty = manager.findProperty(eagleModule, "direction-check");
            pitchCheckProperty = manager.findProperty(eagleModule, "pitch-check");
            blocksOnlyProperty = manager.findProperty(eagleModule, "blocks-only");

            autoSwapProperty = creator.createBooleanProperty("auto-swap", false);

            if (!creator.injectProperty(eagleModule, autoSwapProperty)) {
                MyauLogger.log(getName(), "PROPERTY_INJECT_FAIL");
                return false;
            }

            for (Method m : eagleModule.getClass().getMethods()) {
                if (m.getReturnType().equals(boolean.class) &&
                        m.getParameterCount() == 0 &&
                        m.getName().length() <= 2) {
                    isEnabledMethod = m;
                    break;
                }
            }

            for (Method m : eagleModule.getClass().getDeclaredMethods()) {
                if (m.getReturnType().equals(boolean.class) &&
                        m.getParameterCount() == 0 &&
                        m.getName().length() <= 2) {
                    m.setAccessible(true);
                    shouldSneakMethod = m;
                    break;
                }
            }

            MinecraftForge.EVENT_BUS.register(this);

            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;

        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START)
            return;

        try {
            if (!(Boolean) isEnabledMethod.invoke(eagleModule)) {
                swappedToSlot = -1;
                return;
            }

            Boolean autoSwap = (Boolean) properties.getPropertyValue(autoSwapProperty);
            if (autoSwap == null || !autoSwap) {
                swappedToSlot = -1;
                return;
            }

            if (!checkEagleConditions()) {
                swappedToSlot = -1;
                return;
            }

            int currentSlot = mc.thePlayer.inventory.currentItem;
            if (swappedToSlot != -1 && currentSlot != swappedToSlot) {
                swappedToSlot = -1;
            }

            if (!isHoldingBlock()) {
                swappedToSlot = -1;
                return;
            }

            ItemStack heldItem = mc.thePlayer.getHeldItem();
            if (heldItem != null && heldItem.stackSize > 1) {
                return;
            }

            int currentTick = mc.thePlayer.ticksExisted;
            if (currentTick - lastSwapTick < 5) {
                return;
            }

            int bestSlot = findBestBlockInHotbar(currentSlot);

            if (bestSlot != -1 && bestSlot != currentSlot) {
                mc.thePlayer.inventory.currentItem = bestSlot;
                swappedToSlot = bestSlot;
                lastSwapTick = currentTick;
            }

        } catch (Exception ignored) {
        }
    }

    private boolean checkEagleConditions() {
        try {
            if (directionCheckProperty != null) {
                Boolean directionCheck = (Boolean) properties.getPropertyValue(directionCheckProperty);
                if (directionCheck != null && directionCheck) {
                    if (mc.gameSettings.keyBindForward.isKeyDown()) {
                        return false;
                    }
                }
            }

            if (pitchCheckProperty != null) {
                Boolean pitchCheck = (Boolean) properties.getPropertyValue(pitchCheckProperty);
                if (pitchCheck != null && pitchCheck) {
                    if (mc.thePlayer.rotationPitch < 69.0F) {
                        return false;
                    }
                }
            }

            if (blocksOnlyProperty != null) {
                Boolean blocksOnly = (Boolean) properties.getPropertyValue(blocksOnlyProperty);
                if (blocksOnly != null && blocksOnly) {
                    if (!isHoldingBlock()) {
                        return false;
                    }
                }
            }

            if (!mc.thePlayer.onGround) {
                return false;
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    private boolean isHoldingBlock() {
        ItemStack heldItem = mc.thePlayer.getHeldItem();
        return heldItem != null && heldItem.getItem() instanceof ItemBlock;
    }

    private int findBestBlockInHotbar(int currentSlot) {
        int bestSlot = -1;
        int maxCount = 0;

        for (int i = 0; i < 9; i++) {
            if (i == currentSlot)
                continue;

            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemBlock) {
                if (stack.stackSize > maxCount) {
                    maxCount = stack.stackSize;
                    bestSlot = i;
                }
            }
        }

        return bestSlot;
    }
}





