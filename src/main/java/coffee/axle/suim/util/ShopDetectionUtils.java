package coffee.axle.suim.util;

import coffee.axle.suim.rotation.RotationMath;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.BlockPos;
import static coffee.axle.suim.ProjectSUIM.mc;

public class ShopDetectionUtils {

    private static final String[] SHOP_LABELS = {
            "RIGHT CLICK", "ITEM SHOP", "UPGRADES", "BANKER", "STREAK POWERS"
    };

    public static boolean isShopNPC(Entity entity) {
        if (entity == null || entity == mc.thePlayer) return false;
        if (entity instanceof EntityVillager) return true;
        if (entity instanceof EntityLivingBase) {
            return hasShopHologram((EntityLivingBase) entity);
        }
        return false;
    }

    private static boolean hasShopHologram(EntityLivingBase entity) {
        EntityLivingBase armorStand = mc.theWorld.findNearestEntityWithinAABB(
                EntityArmorStand.class, entity.getEntityBoundingBox(), entity);
        if (armorStand == null) return false;

        String name = armorStand.getName();
        for (String label : SHOP_LABELS) {
            if (name.contains(label)) return true;
        }
        return false;
    }

    public static boolean isInShopGui() {
        return mc.currentScreen != null
                && mc.currentScreen.getClass().getName().contains("chest");
    }

    public static BlockPos findNearestBed(double range) {
        return findNearestBlock(range, BlockBed.class);
    }

    public static BlockPos findNearestEnderChest(double range) {
        return findNearestBlock(range, BlockEnderChest.class);
    }

    private static BlockPos findNearestBlock(double range, Class<? extends Block> type) {
        if (!Utils.nullCheck()) return null;
        BlockPos playerPos = mc.thePlayer.getPosition();
        int r = (int) range;
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (type.isInstance(mc.theWorld.getBlockState(pos).getBlock())) {
                        double dist = mc.thePlayer.getDistanceSq(pos);
                        if (dist < closestDist) {
                            closestDist = dist;
                            closest = pos;
                        }
                    }
                }
            }
        }
        return closest;
    }

    public static float[] getRotationsToBlock(BlockPos pos) {
        if (pos == null || !Utils.nullCheck()) return null;
        return RotationMath.toRotationsFromDelta(
                pos.getX() + 0.5 - mc.thePlayer.posX,
                pos.getY() + 0.5 - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight()),
                pos.getZ() + 0.5 - mc.thePlayer.posZ);
    }
}
