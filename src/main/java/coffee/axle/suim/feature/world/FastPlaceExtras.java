package coffee.axle.suim.feature.world;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.property.Setting;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import static coffee.axle.suim.ProjectSUIM.mc;

/**
 * Adds skip-obsidian and skip-interactable properties to FastPlace module
 * - skip-obsidian: Makes canPlace() return false when holding obsidian
 * (default: true)
 * - skip-interactable: Makes canPlace() return false on interactable blocks
 * (default: false)
 */
public class FastPlaceExtras extends Feature {

    @Setting(after = "place-fix") private boolean skipObsidian = true;
    @Setting(after = "skip-obsidian") private boolean skipInteractable = false;

    private static FastPlaceExtras instance;

    public FastPlaceExtras() {
        super("FastPlace:Extras");
        instance = this;
    }

    @Override
    protected String targetModule() {
        return "FastPlace";
    }

    /**
     * Called from mixin to check if FastPlace should be skipped
     * Returns true if FastPlace should be DISABLED (canPlace should return false)
     */
    public static boolean shouldSkipFastPlace() {
        try {
            FastPlaceExtras f = instance;
            if (f == null || !f.isEnabled()) {
                return false;
            }

            if (mc.thePlayer == null || mc.theWorld == null) {
                return false;
            }

            ItemStack heldItem = mc.thePlayer.getHeldItem();

            if (f.skipObsidian && heldItem != null && heldItem.getItem() instanceof ItemBlock) {
                Block block = ((ItemBlock) heldItem.getItem()).getBlock();
                if (block == Blocks.obsidian) {
                    return true;
                }
            }

            if (f.skipInteractable && mc.objectMouseOver != null) {
                if (mc.objectMouseOver.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) {
                    net.minecraft.util.BlockPos pos = mc.objectMouseOver.getBlockPos();
                    Block block = mc.theWorld.getBlockState(pos).getBlock();

                    if (isInteractable(block)) {
                        return true;
                    }
                }
            }

            return false;

        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isInteractable(Block block) {
        if (block instanceof BlockContainer)
            return true;
        if (block instanceof BlockWorkbench)
            return true;
        if (block instanceof BlockAnvil)
            return true;
        if (block instanceof BlockBed)
            return true;
        if (block instanceof BlockDoor) {
            if (block.getMaterial() != Material.iron)
                return true;
        }
        if (block instanceof BlockTrapDoor)
            return true;
        if (block instanceof BlockFenceGate)
            return true;
        if (block instanceof BlockFence)
            return true;
        if (block instanceof BlockButton)
            return true;
        if (block instanceof BlockLever)
            return true;
        return false;
    }
}





