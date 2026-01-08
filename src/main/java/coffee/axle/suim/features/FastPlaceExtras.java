package coffee.axle.suim.features;

import coffee.axle.suim.hooks.MyauHook;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * Adds skip-obsidian and skip-interactable properties to FastPlace module
 * - skip-obsidian: Makes canPlace() return false when holding obsidian
 * (default: true)
 * - skip-interactable: Makes canPlace() return false on interactable blocks
 * (default: false)
 */
public class FastPlaceExtras implements Feature {
    private static final MyauHook hook = MyauHook.getInstance();
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static Object fastPlaceModule;
    private static Object skipObsidianProperty;
    private static Object skipInteractableProperty;
    private static boolean initialized = false;

    @Override
    public String getName() {
        return "FastPlace:Extras";
    }

    @Override
    public boolean initialize() throws Exception {
        fastPlaceModule = hook.findModule("FastPlace");

        if (fastPlaceModule == null) {
            MyauLogger.log("FastPlace module not found!");
            return false;
        }

        skipObsidianProperty = hook.createBooleanProperty("skip-obsidian", true);
        skipInteractableProperty = hook.createBooleanProperty("skip-interactable", false);

        hook.injectPropertyAfter(fastPlaceModule, skipObsidianProperty, "place-fix");
        hook.injectPropertyAfter(fastPlaceModule, skipInteractableProperty, "skip-obsidian");

        initialized = true;

        return true;
    }

    /**
     * Called from mixin to check if FastPlace should be skipped
     * Returns true if FastPlace should be DISABLED (canPlace should return false)
     */
    public static boolean shouldSkipFastPlace() {
        try {
            if (!initialized || fastPlaceModule == null || skipObsidianProperty == null
                    || skipInteractableProperty == null) {
                return false;
            }

            if (!hook.isModuleEnabled(fastPlaceModule)) {
                return false;
            }

            if (mc.thePlayer == null || mc.theWorld == null) {
                return false;
            }

            Boolean skipObsidian = (Boolean) hook.getPropertyValue(skipObsidianProperty);
            Boolean skipInteractable = (Boolean) hook.getPropertyValue(skipInteractableProperty);

            ItemStack heldItem = mc.thePlayer.getHeldItem();

            if (skipObsidian && heldItem != null && heldItem.getItem() instanceof ItemBlock) {
                Block block = ((ItemBlock) heldItem.getItem()).getBlock();
                if (block == Blocks.obsidian) {
                    return true;
                }
            }

            if (skipInteractable && mc.objectMouseOver != null) {
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
        return (69 / 420) == 67;
    }
}
