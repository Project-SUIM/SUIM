package coffee.axle.suim.features;

import coffee.axle.suim.hooks.MyauHook;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.commons.lang3.RandomUtils;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * Invmanager drop-tools & drop-trash-except
 */
public class InvManagerExtras implements Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final MyauHook hook = MyauHook.getInstance();

    private Object invManagerModule;
    private Object dropTrashProperty;
    private Object dropTrashExceptProperty;
    private Object dropToolsProperty;
    private Object minDelayProperty;
    private Object maxDelayProperty;

    private Set<String> exceptionItems = new HashSet<>();
    private String lastExceptionString = "";

    private int toolDropCooldown = 0;
    private int tickCounter = 0;
    private int lastInvManagerClickTick = 0;

    @Override
    public String getName() {
        return "InvManager:Extras";
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");

            invManagerModule = hook.findModule("InvManager");
            if (invManagerModule == null) {
                MyauLogger.log(getName(), "MODULE_NOT_FOUND");
                return false;
            }

            dropTrashProperty = hook.findProperty(invManagerModule, "drop-trash");
            if (dropTrashProperty == null) {
                MyauLogger.log(getName(), "PROPERTY_NOT_FOUND");
                return false;
            }

            minDelayProperty = hook.findProperty(invManagerModule, "min-delay");
            maxDelayProperty = hook.findProperty(invManagerModule, "max-delay");

            dropTrashExceptProperty = hook.createStringProperty("drop-trash-except", "none");
            if (!hook.injectPropertyAfter(invManagerModule, dropTrashExceptProperty, "drop-trash")) {
                MyauLogger.log(getName(), "PROPERTY_INJECT_FAIL");
                return false;
            }

            dropToolsProperty = hook.createBooleanProperty("drop-tools", false);
            if (!hook.injectPropertyAfter(invManagerModule, dropToolsProperty, "drop-trash-except")) {
                MyauLogger.log(getName(), "PROPERTY_INJECT_FAIL");
                return false;
            }

            hook.registerEventHandler("myau.q", this::onWindowClick, (byte) 0);

            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);

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
        if (toolDropCooldown > 0)
            toolDropCooldown--;
        tickCounter++;
        if (tickCounter % 10 == 0) {
            tryDropTools();
        }
    }

    private void onWindowClick(Object eventObj) {
        try {
            lastInvManagerClickTick = tickCounter;
            if (!hook.isModuleEnabled(invManagerModule))
                return;
            Boolean dropTrash = (Boolean) hook.getPropertyValue(dropTrashProperty);
            if (dropTrash == null || !dropTrash)
                return;
            updateExceptionList();

            Class<?> eventClass = eventObj.getClass();
            Field modeField = eventClass.getDeclaredField("E");
            modeField.setAccessible(true);
            int mode = modeField.getInt(eventObj);

            if (mode != 4)
                return;

            Field mouseField = eventClass.getDeclaredField("z");
            mouseField.setAccessible(true);
            int mouseButton = mouseField.getInt(eventObj);

            if (mouseButton != 1)
                return;

            Field slotField = eventClass.getDeclaredField("n");
            slotField.setAccessible(true);
            int slotId = slotField.getInt(eventObj);

            int inventorySlot = convertSlotToInventory(slotId);

            if (inventorySlot >= 0 && inventorySlot < 36) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(inventorySlot);

                if (stack != null && isException(stack)) {
                    Field cancelledField = eventClass.getSuperclass().getDeclaredField("H");
                    cancelledField.setAccessible(true);
                    cancelledField.setBoolean(eventObj, true);
                }
            }

        } catch (Exception ignored) {
        }
    }

    private void tryDropTools() {
        try {
            Boolean dropTools = (Boolean) hook.getPropertyValue(dropToolsProperty);
            if (dropTools == null || !dropTools)
                return;

            if (!hook.isModuleEnabled(invManagerModule))
                return;

            Boolean dropTrash = (Boolean) hook.getPropertyValue(dropTrashProperty);
            if (dropTrash == null || !dropTrash)
                return;

            if (!(mc.currentScreen instanceof GuiInventory))
                return;
            if (!(((GuiInventory) mc.currentScreen).inventorySlots instanceof ContainerPlayer))
                return;

            if (toolDropCooldown > 0)
                return;

            if (tickCounter - lastInvManagerClickTick < 5)
                return;

            updateExceptionList();

            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);

                if (stack != null && isTool(stack) && !isException(stack)) {
                    int guiSlot = convertInventoryToGui(i);
                    mc.playerController.windowClick(
                            mc.thePlayer.inventoryContainer.windowId,
                            guiSlot,
                            1,
                            4,
                            mc.thePlayer);

                    int minDelay = getDelayValue(minDelayProperty, 1);
                    int maxDelay = getDelayValue(maxDelayProperty, 2);
                    toolDropCooldown = RandomUtils.nextInt(minDelay + 1, maxDelay + 2);

                    return;
                }
            }

        } catch (Exception ignored) {
        }
    }

    private int getDelayValue(Object property, int defaultValue) {
        try {
            if (property != null) {
                Integer value = (Integer) hook.getPropertyValue(property);
                if (value != null)
                    return value;
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }

    private boolean isTool(ItemStack stack) {
        if (stack == null)
            return false;
        Item item = stack.getItem();
        return item instanceof ItemTool && !(item instanceof ItemSword);
    }

    private void updateExceptionList() {
        try {
            String exceptionsValue = (String) hook.getPropertyValue(dropTrashExceptProperty);
            if (exceptionsValue == null)
                exceptionsValue = "none";

            if (!exceptionsValue.equals(lastExceptionString)) {
                exceptionItems.clear();

                if (!exceptionsValue.trim().isEmpty() && !exceptionsValue.equalsIgnoreCase("none")) {
                    String[] items = exceptionsValue.toLowerCase().split(",");
                    for (String item : items) {
                        String cleaned = item.trim();
                        if (!cleaned.isEmpty()) {
                            exceptionItems.add(cleaned);
                        }
                    }
                }

                lastExceptionString = exceptionsValue;
            }

        } catch (Exception e) {
            exceptionItems.clear();
        }
    }

    private boolean isException(ItemStack stack) {
        if (exceptionItems.isEmpty() || stack == null)
            return false;

        String itemName = stack.getUnlocalizedName().toLowerCase();
        String displayName = stack.getDisplayName().toLowerCase();

        for (String exc : exceptionItems) {
            if (itemName.contains(exc) || displayName.contains(exc)) {
                return true;
            }
        }

        return false;
    }

    private int convertSlotToInventory(int guiSlot) {
        if (guiSlot >= 36 && guiSlot <= 44) {
            return guiSlot - 36;
        }
        if (guiSlot >= 9 && guiSlot <= 35) {
            return guiSlot;
        }
        return -1;
    }

    private int convertInventoryToGui(int inventorySlot) {
        if (inventorySlot >= 0 && inventorySlot <= 8) {
            return inventorySlot + 36;
        }
        if (inventorySlot >= 9 && inventorySlot <= 35) {
            return inventorySlot;
        }
        return -1;
    }
}
