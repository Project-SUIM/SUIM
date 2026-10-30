package coffee.axle.suim.feature.player;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.Utils;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Swaps to another block stack once the one you are placing with runs low.
 */
public class AutoSwap extends Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private Object moduleInstance;
    private Object swapAtProperty;
    private Object lookingDownProperty;
    private Object movingBackwardsProperty;

    private boolean lastHeldBlocks;
    private Object evilPumpkinsProperty;
    private int lastSlot = -1;

    @Override
    public String getName() {
        return "AutoSwap";
    }

    @Override
    public GuiCategory getGuiCategory() {
        return GuiCategory.PLAYER;
    }

    @Override
    public boolean initialize() {
        try {
            moduleInstance = createModule();
            creator.injectModule(moduleInstance, AutoSwap.class);

            swapAtProperty = creator.createIntegerProperty("swap-at", 1, 1, 64);
            lookingDownProperty = creator.createBooleanProperty("looking-down", false);
            evilPumpkinsProperty = creator.createBooleanProperty("hate-evil-pumpkins", true);
            movingBackwardsProperty = creator.createBooleanProperty("moving-backwards", false);

            creator.registerProperties(moduleInstance, swapAtProperty,
                    lookingDownProperty, evilPumpkinsProperty, movingBackwardsProperty);
            manager.reloadModuleCommand();

            MinecraftForge.EVENT_BUS.register(this);
            manager.registerModuleCallbacks(moduleInstance, this::reset, this::reset);

            return true;
        } catch (Exception e) {
            MyauLogger.error("AutoSwap:init", e);
            return false;
        }
    }

    private void reset() {
        lastHeldBlocks = false;
        lastSlot = -1;
    }


    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;

        if (!manager.isModuleEnabled(moduleInstance) || !Utils.nullCheck()
                || mc.currentScreen != null) {
            reset();
            return;
        }

        int slot = mc.thePlayer.inventory.currentItem;
        ItemStack held = mc.thePlayer.getHeldItem();
        boolean blocks = isFullBlock(held);

        if (slot == lastSlot && lastHeldBlocks
                && mc.gameSettings.keyBindUseItem.isKeyDown()
                && (!blocks || held.stackSize <= properties.getInt(swapAtProperty, 1))
                && conditionsMet()) {
            swapToBest();
            blocks = isFullBlock(mc.thePlayer.getHeldItem());
        }

        lastSlot = mc.thePlayer.inventory.currentItem;
        lastHeldBlocks = blocks;
    }

    private boolean conditionsMet() {
        if (properties.getBoolean(lookingDownProperty, false)
                && mc.thePlayer.rotationPitch < 70.0f) {
            return false;
        }
        return !properties.getBoolean(movingBackwardsProperty, false)
                || mc.gameSettings.keyBindBack.isKeyDown();
    }

    private void swapToBest() {
        int current = mc.thePlayer.inventory.currentItem;
        int best = -1;
        int bestCount = 1;
        boolean evilPumpkins = properties.getBoolean(evilPumpkinsProperty, true);

        for (int i = 0; i < 9; i++) {
            if (i == current) continue;
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (!isFullBlock(stack) || stack.stackSize <= bestCount) continue;
            Block block = ((ItemBlock) stack.getItem()).getBlock();
            // idk this might be #wise
            if (block == Blocks.obsidian) continue;
            // GOD I FUCKING HATE HYPIXEL HALLOWEEN GENUINELY PMO 
            if (evilPumpkins && (block == Blocks.pumpkin || block == Blocks.lit_pumpkin)) continue;
            best = i;
            bestCount = stack.stackSize;
        }

        if (best != -1) {
            mc.thePlayer.inventory.currentItem = best;
        }
    }

    private static boolean isFullBlock(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) return false;
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        return block != null && block.isFullBlock();
    }
}