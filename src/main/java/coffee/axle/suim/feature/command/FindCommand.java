package coffee.axle.suim.feature.command;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.Arrays;

public class FindCommand extends Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();

    @Override
    public String getName() {
        return "Command:Find";
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");

            ArrayList<String> commandNames = new ArrayList<>(Arrays.asList("find", "locate"));
            creator.registerCommand(commandNames, this::handleCommand);

            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;
        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    private void handleCommand(ArrayList<String> args) {
        if (args.size() < 2) {
            manager.sendMessage("&cUsage: &f.find &7<block_name>");
            return;
        }

        if (mc.theWorld == null || mc.thePlayer == null) {
            manager.sendMessage("&cYou must be in a world to use this command.");
            return;
        }

        String blockName = args.get(1).toLowerCase().replace("_", " ");
        manager.sendMessage("&7Searching for &e" + blockName + "&7...");

        new Thread(() -> {
            try {
                BlockPos closestPos = findClosestBlock(blockName);

                mc.addScheduledTask(() -> {
                    if (closestPos == null) {
                        manager.sendMessage("&cCould not find any &e" + blockName + "&c nearby.");
                    } else {
                        int distance = (int) mc.thePlayer.getDistance(
                                closestPos.getX(),
                                closestPos.getY(),
                                closestPos.getZ());

                        manager.sendMessage("&aFound &e" + blockName + "&a at &b" +
                                closestPos.getX() + " " +
                                closestPos.getY() + " " +
                                closestPos.getZ() +
                                " &7(" + distance + " blocks away)");
                    }
                });
            } catch (Exception e) {
                mc.addScheduledTask(() -> manager.sendMessage("&cError: " + e.getMessage()));
            }
        }, "dmyau-find").start();
    }

    private BlockPos findClosestBlock(String blockName) {
        BlockPos playerPos = mc.thePlayer.getPosition();
        BlockPos closestPos = null;
        double closestDistSq = Double.MAX_VALUE;

        int searchRadius = 4;
        int chunkX = playerPos.getX() >> 4;
        int chunkZ = playerPos.getZ() >> 4;

        for (int radius = 0; radius <= searchRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    int cx = chunkX + dx;
                    int cz = chunkZ + dz;

                    if (!mc.theWorld.getChunkProvider().chunkExists(cx, cz)) {
                        continue;
                    }

                    Chunk chunk = mc.theWorld.getChunkFromChunkCoords(cx, cz);
                    if (chunk == null) {
                        continue;
                    }

                    int minY = Math.max(0, playerPos.getY() - 64);
                    int maxY = Math.min(256, playerPos.getY() + 64);

                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = minY; y < maxY; y++) {
                                BlockPos pos = new BlockPos(cx * 16 + x, y, cz * 16 + z);
                                double distSq = mc.thePlayer.getDistanceSq(pos);
                                if (distSq > closestDistSq) {
                                    continue;
                                }

                                Block block = mc.theWorld.getBlockState(pos).getBlock();
                                if (block == null || block.getMaterial() == Material.air) {
                                    continue;
                                }

                                String localizedName = block.getLocalizedName().toLowerCase();
                                String registryName = String.valueOf(Block.blockRegistry.getNameForObject(block))
                                        .toLowerCase();

                                if (localizedName.contains(blockName) || registryName.contains(blockName)) {
                                    closestDistSq = distSq;
                                    closestPos = pos;
                                }
                            }
                        }
                    }
                }
            }

            if (closestPos != null && closestDistSq < 256) {
                break;
            }
        }

        return closestPos;
    }
}
