package coffee.axle.suim.feature.world.blockin;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Block scoring and hotbar slot selection for AutoBlockIn.
 * Lower score = higher priority. Blocks not in the map
 * receive a fallback score so any solid block can be used.
 */
public final class BlockInScoring {

    /** Fallback score for blocks not explicitly listed. */
    private static final int FALLBACK_SCORE = 100;

    private static final Map<Block, Integer> BLOCK_SCORES = new HashMap<>();

    static {
        BLOCK_SCORES.put(Blocks.obsidian, 0);
        BLOCK_SCORES.put(Blocks.end_stone, 1);
        BLOCK_SCORES.put(Blocks.planks, 2);
        BLOCK_SCORES.put(Blocks.log, 2);
        BLOCK_SCORES.put(Blocks.log2, 2);
        BLOCK_SCORES.put(Blocks.glass, 3);
        BLOCK_SCORES.put(Blocks.stained_glass, 3);
        BLOCK_SCORES.put(Blocks.hardened_clay, 4);
        BLOCK_SCORES.put(Blocks.stained_hardened_clay, 4);
        BLOCK_SCORES.put(Blocks.wool, 5);
        BLOCK_SCORES.put(Blocks.sandstone, 6);
        BLOCK_SCORES.put(Blocks.cobblestone, 6);
        BLOCK_SCORES.put(Blocks.stone, 6);
        BLOCK_SCORES.put(Blocks.brick_block, 6);
        BLOCK_SCORES.put(Blocks.iron_block, 7);
        BLOCK_SCORES.put(Blocks.gold_block, 7);
        BLOCK_SCORES.put(Blocks.diamond_block, 7);
        BLOCK_SCORES.put(Blocks.emerald_block, 7);
    }

    private BlockInScoring() {
    }

    /**
     * Get the priority score for a block. Lower = better.
     * Blocks not in the map get {@link #FALLBACK_SCORE}.
     */
    public static int getScore(Block block) {
        Integer score = BLOCK_SCORES.get(block);
        return score != null ? score : FALLBACK_SCORE;
    }

    /**
     * Find the best hotbar slot containing a placeable block.
     * Picks the strongest (lowest score) block for cage walls/roof.
     *
     * @return slot index 0-8, or -1 if no valid block found
     */
    public static int findBestBlockSlot() {
        return findBlockSlot(false);
    }

    /**
     * Find the weakest hotbar slot containing a placeable block.
     * Picks the weakest (highest score) block for scaffold/support placements.
     * This preserves strong blocks for the actual cage.
     *
     * @return slot index 0-8, or -1 if no valid block found
     */
    public static int findWeakestBlockSlot() {
        return findBlockSlot(true);
    }

    /**
     * Internal slot finder. When {@code weakest} is true, picks the
     * highest-scored (weakest) block; otherwise picks the lowest-scored
     * (strongest) block.
     */
    private static int findBlockSlot(boolean weakest) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null)
            return -1;

        int bestSlot = -1;
        int bestScore = weakest ? -1 : Integer.MAX_VALUE;

        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack == null || stack.stackSize == 0)
                continue;
            if (!(stack.getItem() instanceof ItemBlock))
                continue;

            Block block = ((ItemBlock) stack.getItem()).getBlock();
            if (!block.isFullCube())
                continue;

            int score = getScore(block);
            if (weakest) {
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = slot;
                }
            } else {
                if (score < bestScore) {
                    bestScore = score;
                    bestSlot = slot;
                    if (score == 0)
                        break;
                }
            }
        }
        return bestSlot;
    }

    /**
     * Check if the player is holding any placeable block.
     */
    public static boolean isHoldingBlock() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null)
            return false;
        ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
        return stack != null && stack.getItem() instanceof ItemBlock;
    }
}
