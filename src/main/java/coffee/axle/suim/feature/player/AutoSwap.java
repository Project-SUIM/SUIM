package coffee.axle.suim.feature.player;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.property.Setting;
import coffee.axle.suim.util.Utils;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import static coffee.axle.suim.ProjectSUIM.mc;

/**
 * Swaps to another block stack once the one you are placing with runs low.
 */
public class AutoSwap extends Feature {

    @Setting(min = 1, max = 64) private int swapAt = 1;
    @Setting(name = "hate-evil-pumpkins") private boolean evilPumpkins = true;
    @Setting private boolean lookingDown = false;
    @Setting private boolean movingBackwards = false;

    private boolean lastHeldBlocks;
    private int lastSlot = -1;

    public AutoSwap() {
        super("AutoSwap", GuiCategory.PLAYER);
    }

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable() {
        reset();
    }

    private void reset() {
        lastHeldBlocks = false;
        lastSlot = -1;
    }


    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;

        if (!isEnabled() || !Utils.nullCheck()
                || mc.currentScreen != null) {
            reset();
            return;
        }

        int slot = mc.thePlayer.inventory.currentItem;
        ItemStack held = mc.thePlayer.getHeldItem();
        boolean blocks = isFullBlock(held);

        if (slot == lastSlot && lastHeldBlocks
                && mc.gameSettings.keyBindUseItem.isKeyDown()
                && (!blocks || held.stackSize <= swapAt)
                && conditionsMet()) {
            swapToBest();
            blocks = isFullBlock(mc.thePlayer.getHeldItem());
        }

        lastSlot = mc.thePlayer.inventory.currentItem;
        lastHeldBlocks = blocks;
    }

    private boolean conditionsMet() {
        if (lookingDown
                && mc.thePlayer.rotationPitch < 70.0f) {
            return false;
        }
        return !movingBackwards
                || mc.gameSettings.keyBindBack.isKeyDown();
    }

    private void swapToBest() {
        int current = mc.thePlayer.inventory.currentItem;
        int best = -1;
        int bestCount = 1;

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