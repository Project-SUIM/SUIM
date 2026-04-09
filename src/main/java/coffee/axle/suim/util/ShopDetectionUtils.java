package coffee.axle.suim.util;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.BlockPos;

public class ShopDetectionUtils {
    private static final Minecraft mc = Minecraft.getMinecraft();

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
        if (!Utils.nullCheck()) return null;
        BlockPos playerPos = mc.thePlayer.getPosition();
        int r = (int) range;
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (mc.theWorld.getBlockState(pos).getBlock() instanceof net.minecraft.block.BlockBed) {
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

    public static BlockPos findNearestEnderChest(double range) {
        if (!Utils.nullCheck()) return null;
        BlockPos playerPos = mc.thePlayer.getPosition();
        int r = (int) range;
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (mc.theWorld.getBlockState(pos).getBlock() instanceof net.minecraft.block.BlockEnderChest) {
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
        double dx = pos.getX() + 0.5 - mc.thePlayer.posX;
        double dy = pos.getY() + 0.5 - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dz = pos.getZ() + 0.5 - mc.thePlayer.posZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) -(Math.atan2(dy, dist) * 180.0 / Math.PI);
        return new float[]{yaw, pitch};
    }
}
