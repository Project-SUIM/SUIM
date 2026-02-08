package coffee.axle.suim.feature.combat;

import coffee.axle.suim.feature.Feature;

import coffee.axle.suim.hooks.MyauMappings;
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
public class AutoClickerExtras extends Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();

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

            autoClickerModule = manager.findModule("AutoClicker");
            if (autoClickerModule == null) {
                MyauLogger.log(getName(), "MODULE_NOT_FOUND");
                return false;
            }

            inventoryFillProperty = creator.createBooleanProperty("inventory-fill", false);
            requirePressProperty = creator.createBooleanProperty("require-press", true);

            creator.registerProperties(autoClickerModule, inventoryFillProperty, requirePressProperty);

            leftCpsMinProperty = manager.findProperty(autoClickerModule, "min-cps");
            leftCpsMaxProperty = manager.findProperty(autoClickerModule, "max-cps");

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
            tickEventClass = Class.forName(MyauMappings.CLASS_TICK_EVENT);

            eventTypeField = tickEventClass.getDeclaredField(MyauMappings.FIELD_TICK_EVENT_TYPE);
            eventTypeField.setAccessible(true);

            Class<?> eventTypeEnum = eventTypeField.getType();
            Object[] enumConstants = eventTypeEnum.getEnumConstants();
            if (enumConstants != null && enumConstants.length > 0) {
                preEventType = enumConstants[0];
            }

            creator.registerEventHandler(MyauMappings.CLASS_TICK_EVENT, this::onMyauTickPre, (byte) 1);
        } catch (Exception e) {
            MyauLogger.error("Failed to init event hook", e);
        }
    }

    private void onMyauTickPre(Object eventObj) {
        try {
            if (!manager.isModuleEnabled(autoClickerModule))
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
                Object val = properties.getPropertyValue(leftCpsMinProperty);
                if (val instanceof Number) {
                    minCps = ((Number) val).intValue();
                }
            }

            if (leftCpsMaxProperty != null) {
                Object val = properties.getPropertyValue(leftCpsMaxProperty);
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
            Object val = properties.getPropertyValue(property);
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
