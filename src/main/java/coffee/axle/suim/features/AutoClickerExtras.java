package coffee.axle.suim.features;

import coffee.axle.suim.hooks.MyauHook;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * AutoClicker Extras
 * 
 * @maybsomeday
 */
public class AutoClickerExtras implements Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final MyauHook hook = MyauHook.getInstance();

    private Object autoClickerModule;
    private Object inventoryFillProperty;
    private Object requirePressProperty;
    private Object leftCpsMinProperty;
    private Object leftCpsMaxProperty;

    private Class<?> tickEventClass;
    private Field eventTypeField;
    private Object preEventType;

    private long lastClickTime = 0;
    private final Random random = new Random();

    @Override
    public String getName() {
        return "AutoClicker:Extras";
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");

            autoClickerModule = hook.findModule("AutoClicker");
            if (autoClickerModule == null) {
                MyauLogger.log(getName(), "MODULE_NOT_FOUND");
                return false;
            }

            inventoryFillProperty = hook.createBooleanProperty("inventory-fill", false);
            requirePressProperty = hook.createBooleanProperty("require-press", true);

            hook.registerPropertiesToModule(autoClickerModule, inventoryFillProperty, requirePressProperty);

            leftCpsMinProperty = hook.findProperty(autoClickerModule, "left-cps-min");
            leftCpsMaxProperty = hook.findProperty(autoClickerModule, "left-cps-max");

            initializeEventHook();

            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;
        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    private void initializeEventHook() {
        try {
            tickEventClass = Class.forName("myau.KP");

            eventTypeField = tickEventClass.getDeclaredField("p");
            eventTypeField.setAccessible(true);

            Class<?> eventTypeEnum = Class.forName("myau.U");
            for (Object constant : eventTypeEnum.getEnumConstants()) {
                if (constant.toString().equals("PRE")) {
                    preEventType = constant;
                    break;
                }
            }

            hook.registerEventHandler("myau.KP", this::onMyauTickPre, (byte) 3);
        } catch (Exception e) {
            MyauLogger.error("Failed to init event hook", e);
        }
    }

    private void onMyauTickPre(Object eventObj) {
        try {
            if (!hook.isModuleEnabled(autoClickerModule))
                return;

            Object eventType = eventTypeField.get(eventObj);
            if (eventType != preEventType)
                return;

            boolean invFill = getBooleanProperty(inventoryFillProperty, false);
            boolean requirePress = getBooleanProperty(requirePressProperty, true);

            if (!invFill)
                return;

            if (!(mc.currentScreen instanceof GuiChest))
                return;

            if (requirePress && !mc.gameSettings.keyBindAttack.isKeyDown())
                return;

            GuiChest chest = (GuiChest) mc.currentScreen;
            ContainerChest container = (ContainerChest) chest.inventorySlots;

            int chestSize = container.getLowerChestInventory().getSizeInventory();

            ItemStack heldItem = mc.thePlayer.inventory.getItemStack();
            if (heldItem != null)
                return;

            long currentTime = System.currentTimeMillis();
            long delay = getClickDelay();
            if (currentTime - lastClickTime < delay)
                return;

            Slot targetSlot = findItemSlot(container, chestSize);
            if (targetSlot == null)
                return;

            inventoryClick(chest, targetSlot.slotNumber, 0, 1);
            lastClickTime = currentTime;

        } catch (Exception e) {
        }
    }

    private Slot findItemSlot(ContainerChest container, int chestSize) {
        for (int i = 0; i < chestSize; i++) {
            Slot slot = container.getSlot(i);
            if (slot != null && slot.getHasStack()) {
                ItemStack stack = slot.getStack();
                if (stack != null && stack.stackSize > 0) {
                    return slot;
                }
            }
        }
        return null;
    }

    private void inventoryClick(GuiChest gui, int slotId, int mouseButton, int mode) {
        try {
            Method handleMouseClick = GuiChest.class.getDeclaredMethod(
                    "handleMouseClick", Slot.class, int.class, int.class, int.class);
            handleMouseClick.setAccessible(true);
            handleMouseClick.invoke(gui, null, slotId, mouseButton, mode);
        } catch (Exception e) {
            try {
                mc.playerController.windowClick(
                        gui.inventorySlots.windowId,
                        slotId,
                        mouseButton,
                        mode,
                        mc.thePlayer);
            } catch (Exception e2) {
            }
        }
    }

    private long getClickDelay() {
        try {
            int minCps = 8;
            int maxCps = 12;

            if (leftCpsMinProperty != null) {
                Object val = hook.getPropertyValue(leftCpsMinProperty);
                if (val instanceof Number) {
                    minCps = ((Number) val).intValue();
                }
            }

            if (leftCpsMaxProperty != null) {
                Object val = hook.getPropertyValue(leftCpsMaxProperty);
                if (val instanceof Number) {
                    maxCps = ((Number) val).intValue();
                }
            }

            if (minCps > maxCps) {
                int temp = minCps;
                minCps = maxCps;
                maxCps = temp;
            }

            int cps = minCps + random.nextInt(Math.max(1, maxCps - minCps + 1));
            return 1000L / Math.max(1, cps);

        } catch (Exception e) {
            return 100L;
        }
    }

    private boolean getBooleanProperty(Object property, boolean defaultValue) {
        try {
            Object val = hook.getPropertyValue(property);
            if (val instanceof Boolean) {
                return (Boolean) val;
            }
        } catch (Exception e) {
        }
        return defaultValue;
    }

    @Override
    public void disable() {
    }
}
