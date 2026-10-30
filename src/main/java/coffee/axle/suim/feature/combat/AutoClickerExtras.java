package coffee.axle.suim.feature.combat;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.property.Setting;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Random;
import static coffee.axle.suim.ProjectSUIM.mc;


public class AutoClickerExtras extends Feature {

    @Setting(existing = true, min = 1, max = 20) private int minCps = 8;
    @Setting(existing = true, min = 1, max = 20) private int maxCps = 12;
    @Setting private boolean inventoryFill = false;
    @Setting private boolean requirePress = true;

    private long lastClickTime = 0;
    private final Random random = new Random();

    public AutoClickerExtras() {
        super("AutoClicker:Extras");
    }

    @Override
    protected String targetModule() {
        return "AutoClicker";
    }

    @Override
    protected void init() throws Exception {
        creator.registerEventHandler(MyauMappings.CLASS_TICK_EVENT, this::onMyauTickPre, (byte) 1);
    }

    private void onMyauTickPre(Object eventObj) {
        try {
            if (!isEnabled())
                return;

            if (!inventoryFill)
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
        int lo = Math.min(minCps, maxCps);
        int hi = Math.max(minCps, maxCps);
        int cps = lo + random.nextInt(Math.max(1, hi - lo + 1));
        return 1000L / Math.max(1, cps);
    }

    @Override
    public void disable() {
    }
}