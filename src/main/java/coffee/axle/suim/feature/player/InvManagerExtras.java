package coffee.axle.suim.feature.player;

import coffee.axle.suim.feature.Feature;

import coffee.axle.suim.property.Setting;
import coffee.axle.suim.hooks.MyauMappings;

import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.commons.lang3.RandomUtils;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import static coffee.axle.suim.ProjectSUIM.mc;

/**
 * Invmanager drop-tools & drop-trash-except
 */
public class InvManagerExtras extends Feature {

    @Setting(after = "drop-trash") private String dropTrashExcept = "none";
    @Setting(after = "drop-trash-except") private boolean dropTools = false;

    private Set<String> exceptionItems = new HashSet<>();
    private String lastExceptionString = "";

    private int toolDropCooldown = 0;
    private int tickCounter = 0;
    private int lastInvManagerClickTick = 0;

    public InvManagerExtras() {
        super("InvManager:Extras");
    }

    @Override
    protected String targetModule() {
        return "InvManager";
    }

    @Override
    protected void init() throws Exception {
        creator.registerEventHandler(MyauMappings.CLASS_WINDOW_CLICK_EVENT, this::onWindowClick, (byte) 0);
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
            if (!manager.isModuleEnabled(module))
                return;
            Boolean dropTrash = isBool("drop-trash");
            if (dropTrash == null || !dropTrash)
                return;
            updateExceptionList();
            Class<?> eventClass = eventObj.getClass();
            Field modeField = eventClass.getDeclaredField(MyauMappings.FIELD_WINDOW_CLICK_MODE);
            modeField.setAccessible(true);
            int mode = modeField.getInt(eventObj);

            if (mode != 4)
                return;

            Field mouseField = eventClass.getDeclaredField(MyauMappings.FIELD_WINDOW_CLICK_BUTTON);
            mouseField.setAccessible(true);
            int mouseButton = mouseField.getInt(eventObj);

            if (mouseButton != 1)
                return;

            Field slotField = eventClass.getDeclaredField(MyauMappings.FIELD_WINDOW_CLICK_SLOT);
            slotField.setAccessible(true);
            int slotId = slotField.getInt(eventObj);

            int inventorySlot = convertSlotToInventory(slotId);

            if (inventorySlot >= 0 && inventorySlot < 36) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(inventorySlot);

                if (stack != null && isException(stack)) {
                    Field cancelledField = eventClass.getSuperclass()
                            .getDeclaredField(MyauMappings.FIELD_EVENT_CANCELLED);
                    cancelledField.setAccessible(true);
                    cancelledField.setBoolean(eventObj, true);
                }
            }

        } catch (Exception ignored) {
        }
    }

    private void tryDropTools() {
        try {
            if (!dropTools)
                return;

            if (!manager.isModuleEnabled(module))
                return;

            Boolean dropTrash = isBool("drop-trash");
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

                    int minDelay = getDelayValue("min-delay", 1);
                    int maxDelay = getDelayValue("max-delay", 2);
                    toolDropCooldown = RandomUtils.nextInt(minDelay + 1, maxDelay + 2);

                    return;
                }
            }

        } catch (Exception ignored) {
        }
    }

    private int getDelayValue(String name, int defaultValue) {
        try {
            Object property = manager.findProperty(module, name);
            if (property != null) {
                Object value = properties.getPropertyValue(property);
                if (value instanceof Integer)
                    return (Integer) value;
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

    private Boolean isBool(String name) {
        try {
            Object property = manager.findProperty(module, name);
            if (property == null) return null;
            Object value = properties.getPropertyValue(property);
            return value instanceof Boolean ? (Boolean) value : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void updateExceptionList() {
        try {
            String exceptionsValue = dropTrashExcept;
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





