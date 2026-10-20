package coffee.axle.suim.feature.world.blockin;

import coffee.axle.suim.util.PacketUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.Vec3i;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Block/placement helpers ported 1:1 from OpenB4's BlockUtils,
 * RotationUtils and PlayerUtils - only what AutoBlockIn uses.
 */
public final class BlockInUtils {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random RANDOM = new Random();
    private static final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();

    private BlockInUtils() {
    }

    public static final class AimPoint {
        public final float yaw;
        public final float pitch;
        public final Vec3 point;

        public AimPoint(float yaw, float pitch, Vec3 point) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.point = point;
        }
    }

    private enum Axis { X, Y, Z }

    // ---- BlockUtils ----

    public static double randomBetween(double min, double max) {
        return min + (max - min) * RANDOM.nextDouble();
    }

    public static IBlockState getState(BlockPos pos) {
        return mc.theWorld.getBlockState(pos);
    }

    public static Block getBlock(BlockPos pos) {
        return getState(pos).getBlock();
    }

    public static BlockPos playerPos() {
        return new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), MathHelper.floor_double(mc.thePlayer.posY), MathHelper.floor_double(mc.thePlayer.posZ));
    }

    public static boolean isPlayerBlock(BlockPos pos) {
        BlockPos p = playerPos();
        return pos.equals(p) || pos.equals(p.up());
    }

    public static boolean isReplaceable(BlockPos pos) {
        Block block = getBlock(pos);
        return block == Blocks.air || block == Blocks.fire || block instanceof BlockLiquid || block.getMaterial().isReplaceable();
    }

    public static boolean isContainer(Block block) {
        return block instanceof BlockContainer || block == Blocks.crafting_table || block == Blocks.anvil;
    }

    public static boolean isInteractable(Block block) {
        if (isContainer(block) || block == Blocks.bed || block == Blocks.lever) {
            return true;
        }
        if (block instanceof BlockTrapDoor || block instanceof BlockFence || block instanceof BlockFenceGate) {
            return true;
        }
        if (!(block instanceof BlockDoor)) {
            return false;
        }
        return block != Blocks.iron_door;
    }

    public static boolean isSupport(Block block) {
        if (block == null || block == Blocks.air) {
            return false;
        }
        if (block instanceof BlockLiquid) {
            return false;
        }
        if (block.getMaterial().isReplaceable()) {
            return false;
        }
        return !isInteractable(block);
    }

    public static boolean hasSupport(BlockPos pos) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (!isSupport(getBlock(pos.offset(facing.getOpposite())))) continue;
            return true;
        }
        return false;
    }

    public static boolean isPlaceable(Block block) {
        return block == Blocks.glass || block.isFullBlock() && block != Blocks.gravel && block != Blocks.sand && block != Blocks.soul_sand && block != Blocks.tnt && block != Blocks.crafting_table && block != Blocks.furnace && block != Blocks.dispenser && block != Blocks.dropper && block != Blocks.noteblock && block != Blocks.command_block;
    }

    public static boolean isBlockItem(ItemStack itemStack) {
        return itemStack != null && itemStack.getItem() instanceof ItemBlock;
    }

    public static Block blockOf(ItemStack itemStack) {
        return isBlockItem(itemStack) ? ((ItemBlock) itemStack.getItem()).getBlock() : null;
    }

    public static boolean bedAdjacent(BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        return getBlock(scratch.set(x + 1, y, z)) == Blocks.bed || getBlock(scratch.set(x - 1, y, z)) == Blocks.bed || getBlock(scratch.set(x, y, z + 1)) == Blocks.bed || getBlock(scratch.set(x, y, z - 1)) == Blocks.bed || getBlock(scratch.set(x, y - 1, z)) == Blocks.bed;
    }

    public static boolean isPassable(BlockPos pos) {
        if (isReplaceable(pos)) {
            return true;
        }
        Block block = getBlock(pos);
        if (!block.isFullBlock()) {
            return block instanceof BlockSlab || block == Blocks.snow_layer;
        }
        return false;
    }

    public static boolean canPlace(MovingObjectPosition hit, ItemStack itemStack) {
        return ((ItemBlock) itemStack.getItem()).canPlaceBlockOnSide(mc.theWorld, hit.getBlockPos(), hit.sideHit, (EntityPlayer) mc.thePlayer, itemStack);
    }

    public static boolean samePos(BlockPos a, BlockPos b) {
        return a == b || a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ();
    }

    public static int horizontalDistance(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ()));
    }

    public static void touching(List<BlockPos> out, Set<BlockPos> solids, Set<BlockPos> candidates, BlockPos center) {
        block0:
        for (BlockPos p : candidates) {
            if (horizontalDistance(p, center) > 3) continue;
            for (EnumFacing facing : EnumFacing.VALUES) {
                if (!solids.contains(p.offset(facing))) continue;
                out.add(p);
                continue block0;
            }
        }
    }

    public static int nearestDistance(BlockPos pos, Set<BlockPos> set) {
        int n = Integer.MAX_VALUE;
        for (BlockPos p : set) {
            int dx = Math.abs(pos.getX() - p.getX());
            int dz = Math.abs(pos.getZ() - p.getZ());
            int dy = Math.abs(pos.getY() - p.getY());
            int d = Math.max(dx, dz) + dy * 2;
            if (d >= n) continue;
            n = d;
        }
        return n == Integer.MAX_VALUE ? 0 : n;
    }

    public static void scan(Set<BlockPos> solids, Set<BlockPos> open, Set<BlockPos> exclude, BlockPos center, int r, int minY, int maxY) {
        int x = center.getX();
        int y = center.getY();
        int z = center.getZ();
        for (int i = -r; i <= r; ++i) {
            int ai = Math.abs(i);
            for (int j = -r; j <= r; ++j) {
                if (Math.max(ai, Math.abs(j)) > r) continue;
                for (int k = minY - 1; k <= maxY + 1; ++k) {
                    BlockPos p;
                    scratch.set(x + i, y + k, z + j);
                    if (isPlayerBlock(scratch)) continue;
                    if (isSupport(getBlock(scratch))) {
                        solids.add(new BlockPos((Vec3i) scratch));
                        continue;
                    }
                    if (k < minY || k > maxY || !isPassable(scratch) || exclude.contains(p = new BlockPos((Vec3i) scratch))) continue;
                    open.add(p);
                }
            }
        }
    }

    // ---- RotationUtils ----

    public static float clampPitch(float pitch) {
        return MathHelper.clamp_float(pitch, -90.0f, 90.0f);
    }

    public static float[] fixSensitivity(float yaw, float pitch, float lastYaw, float lastPitch) {
        float dYaw = yaw - lastYaw;
        float dPitch = pitch - lastPitch;
        float f = mc.gameSettings.mouseSensitivity * 0.6f + 0.2f;
        double d = (double) (f * f * f) * 1.2;
        float yawStep = (float) ((double) Math.round((double) dYaw / d) * d);
        float pitchStep = (float) ((double) Math.round((double) dPitch / d) * d);
        // openb4 adds a global jitter-yaw setting here, suim has none
        return new float[]{lastYaw + yawStep, clampPitch(lastPitch + pitchStep)};
    }

    public static float[] fromDelta(float dx, float dy, float dz, float curYaw, float curPitch) {
        double d4 = Math.sqrt((double) (dx * dx + dz * dz));
        float f = (float) (Math.atan2(dz, dx) * (double) 57.29578f) - 90.0f;
        float f2 = (float) (-(Math.atan2(dy, d4) * (double) 57.29578f));
        f = curYaw + MathHelper.wrapAngleTo180_float(f - curYaw);
        f2 = clampPitch(curPitch + MathHelper.wrapAngleTo180_float(f2 - curPitch));
        return new float[]{f, f2};
    }

    public static float[] toPosition(double x, double y, double z, float curYaw, float curPitch) {
        return fromDelta((float) (x - mc.thePlayer.posX),
                (float) (y - (mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight())),
                (float) (z - mc.thePlayer.posZ), curYaw, curPitch);
    }

    public static Vec3 getVectorForRotation(float yaw, float pitch) {
        float f3 = MathHelper.cos((yaw *= -((float) Math.PI / 180)) - (float) Math.PI);
        float f4 = MathHelper.sin(yaw - (float) Math.PI);
        float f5 = -MathHelper.cos(pitch *= -((float) Math.PI / 180));
        float f6 = MathHelper.sin(pitch);
        return new Vec3(f4 * f5, f6, f3 * f5);
    }

    public static AimPoint aimAtFace(BlockPos pos, EnumFacing facing, float curYaw, float curPitch) {
        double reach = mc.playerController.getBlockReachDistance();
        for (Vec3 p : facePoints(pos, facing)) {
            float[] r = toPosition(p.xCoord, p.yCoord, p.zCoord, curYaw, curPitch);
            if (rayTraceBlock(pos, facing, reach, r[0], r[1]) == null) continue;
            return new AimPoint(r[0], r[1], p);
        }
        return null;
    }

    public static List<Vec3> facePoints(BlockPos pos, EnumFacing facing) {
        Axis a;
        Axis b;
        java.util.ArrayList<Vec3> list = new java.util.ArrayList<Vec3>((2 * 2 + 1) * (2 * 2 + 1) + 8);
        double x = (double) pos.getX() + 0.5 + 0.5 * (double) facing.getFrontOffsetX();
        double y = (double) pos.getY() + 0.5 + 0.5 * (double) facing.getFrontOffsetY();
        double z = (double) pos.getZ() + 0.5 + 0.5 * (double) facing.getFrontOffsetZ();
        if (Math.abs(facing.getFrontOffsetY()) == 1) {
            a = Axis.X;
            b = Axis.Z;
        } else if (Math.abs(facing.getFrontOffsetZ()) == 1) {
            a = Axis.X;
            b = Axis.Y;
        } else {
            a = Axis.Z;
            b = Axis.Y;
        }
        double step = 0.49 / (double) 2;
        list.add(jitter(x, y, z));
        for (int i = 1; i <= 2; ++i) {
            list.add(offset(x, y, z, a, (double) i * step, b, 0.0));
            list.add(offset(x, y, z, a, (double) (-i) * step, b, 0.0));
            list.add(offset(x, y, z, a, 0.0, b, (double) i * step));
            list.add(offset(x, y, z, a, 0.0, b, (double) (-i) * step));
            for (int j = -i; j <= i; ++j) {
                for (int k = -i; k <= i; ++k) {
                    int ak;
                    int aj = Math.abs(j);
                    if (Math.max(aj, ak = Math.abs(k)) != i || j == 0 && ak == i || k == 0 && aj == i) continue;
                    list.add(offset(x, y, z, a, (double) j * step, b, (double) k * step));
                }
            }
        }
        return list;
    }

    private static Vec3 jitter(double x, double y, double z) {
        double d6 = 0.04;
        double d7 = 0.01;
        double lo;
        double hi;
        if (x % 1.0 != 0.0) {
            lo = Math.floor(x += randomBetween(-d6, d6));
            hi = Math.ceil(x);
            x = Math.max(lo + d7, Math.min(hi - d7, x));
        }
        if (y % 1.0 != 0.0) {
            lo = Math.floor(y += randomBetween(-d6, d6));
            hi = Math.ceil(y);
            y = Math.max(lo + d7, Math.min(hi - d7, y));
        }
        if (z % 1.0 != 0.0) {
            lo = Math.floor(z += randomBetween(-d6, d6));
            hi = Math.ceil(z);
            z = Math.max(lo + d7, Math.min(hi - d7, z));
        }
        return new Vec3(x, y, z);
    }

    private static Vec3 offset(double x, double y, double z, Axis a, double da, Axis b, double db) {
        if (a == Axis.X) {
            x += da;
        } else if (a == Axis.Y) {
            y += da;
        } else {
            z += da;
        }
        if (b == Axis.X) {
            x += db;
        } else if (b == Axis.Y) {
            y += db;
        } else {
            z += db;
        }
        return new Vec3(x, y, z);
    }

    // ---- PlayerUtils ----

    public static void swing(boolean packetOnly, boolean resetEquip) {
        if (packetOnly) {
            PacketUtils.sendPacketNoEvent(new C0APacketAnimation());
        } else {
            mc.thePlayer.swingItem();
            if (resetEquip) {
                mc.getItemRenderer().resetEquippedProgress();
            }
        }
    }

    public static MovingObjectPosition rayTraceFrom(double range, double x, double y, double z, float yaw, float pitch) {
        Vec3 from = new Vec3(x, y + (double) mc.thePlayer.getEyeHeight(), z);
        Vec3 dir = getVectorForRotation(yaw, pitch);
        Vec3 to = from.addVector(dir.xCoord * range, dir.yCoord * range, dir.zCoord * range);
        return mc.theWorld.rayTraceBlocks(from, to, false, false, false);
    }

    public static MovingObjectPosition rayTraceBlocks(double range, float yaw, float pitch) {
        return rayTraceFrom(range, mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, yaw, pitch);
    }

    public static MovingObjectPosition rayTraceBlock(BlockPos pos, EnumFacing facing, double range, float yaw, float pitch) {
        MovingObjectPosition hit = rayTraceBlocks(range, yaw, pitch);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return null;
        }
        if (!samePos(hit.getBlockPos(), pos) || facing != null && hit.sideHit != facing) {
            return null;
        }
        return hit;
    }

    // ---- break speed (for prioritize-stronger-blocks) ----

    public static float breakTicks(Block block, ItemStack stack, boolean ignoreSlowdowns) {
        float f = breakSpeed(block, stack, ignoreSlowdowns);
        if (f <= 0.0f) {
            return 0.0f;
        }
        return 1.0f / f;
    }

    public static float breakSpeed(Block block, ItemStack stack, boolean ignoreSlowdowns) {
        if (block == null) {
            return 0.0f;
        }
        float hardness = block.getBlockHardness(mc.theWorld, mc.thePlayer.getPosition());
        if (hardness < 0.0f) {
            return 0.0f;
        }
        boolean harvest = block.getMaterial().isToolNotRequired() || stack != null && stack.canHarvestBlock(block);
        if (harvest) {
            return digSpeed(stack, block, ignoreSlowdowns) / hardness / 30.0f;
        }
        return digSpeed(stack, block, ignoreSlowdowns) / hardness / 100.0f;
    }

    public static float digSpeed(ItemStack stack, Block block, boolean ignoreSlowdowns) {
        int n;
        float f = stack == null ? 1.0f : stack.getItem().getStrVsBlock(stack, block);
        if (f > 1.0f && (n = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, stack)) > 0) {
            f += (float) (n * n + 1);
        }
        if (mc.thePlayer.isPotionActive(Potion.digSpeed)) {
            f *= 1.0f + (float) (mc.thePlayer.getActivePotionEffect(Potion.digSpeed).getAmplifier() + 1) * 0.2f;
        }
        if (!ignoreSlowdowns) {
            if (mc.thePlayer.isPotionActive(Potion.digSlowdown)) {
                float f3;
                switch (mc.thePlayer.getActivePotionEffect(Potion.digSlowdown).getAmplifier()) {
                    case 0:
                        f3 = 0.3f;
                        break;
                    case 1:
                        f3 = 0.09f;
                        break;
                    case 2:
                        f3 = 0.0027f;
                        break;
                    default:
                        f3 = 8.1E-4f;
                }
                f *= f3;
            }
            if (mc.thePlayer.isInsideOfMaterial(Material.water) && !EnchantmentHelper.getAquaAffinityModifier(mc.thePlayer)) {
                f /= 5.0f;
            }
            if (!mc.thePlayer.onGround) {
                f /= 5.0f;
            }
        }
        return f;
    }
}
