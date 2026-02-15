package coffee.axle.suim.feature.world.blockin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAnvil;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.BlockWorkbench;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;

import java.util.*;

/**
 * Placement finding logic for AutoBlockIn.
 * <p>
 * Handles goal generation (roof, sides, scaffold/below-feet),
 * support-block discovery, face grid scanning, and raytrace
 * validation. Stateless — all results returned via {@link PlaceResult}.
 */
public final class BlockInPlacement {

    private static final Minecraft mc = Minecraft.getMinecraft();

    /** Grid scan step size on block faces. */
    private static final double STEP = 0.2;

    /** Random jitter applied to grid points (fraction of STEP). */
    private static final double JIT = STEP * 0.1;

    /** Inset from block face edge to avoid edge hits. */
    private static final double INSET = 0.05;

    /** Cardinal directions for side goals (no vertical). */
    private static final int[][] DIRS = {
            { 1, 0, 0 }, { 0, 0, 1 }, { -1, 0, 0 }, { 0, 0, -1 }
    };

    /** Maximum BFS nodes for roof pathing. */
    private static final int MAX_BFS_NODES = 8964;

    /** Goal positions temporarily skipped due to repeated raytrace failures. */
    private static final Set<BlockPos> skippedGoals = new HashSet<>();

    private BlockInPlacement() {
    }

    /**
     * Mark a goal position as skipped (auto-skip after repeated failures).
     * Skipped goals are excluded from {@link #findBestPlacement}.
     */
    public static void addSkippedGoal(BlockPos pos) {
        skippedGoals.add(pos);
    }

    /** Clear all skipped goals (call on module enable/disable). */
    public static void clearSkippedGoals() {
        skippedGoals.clear();
    }

    /** Skip-interactable mode: 0 = NONE, 1 = SNEAK, 2 = SKIP. */
    private static int skipInteractableMode = 0;

    /** Set the skip-interactable mode (0=NONE, 1=SNEAK, 2=SKIP). */
    public static void setSkipInteractableMode(int mode) {
        skipInteractableMode = mode;
    }

    /**
     * Result of a placement search.
     */
    public static class PlaceResult {
        public final BlockPos supportBlock;
        public final EnumFacing face;
        public final Vec3 hitVec;
        public final float yaw;
        public final float pitch;
        /** True if this is a scaffold/support placement (use weakest block). */
        public final boolean isSupport;
        /** True if the player must hold sneak to avoid opening a GUI. */
        public final boolean requiresSneak;

        public PlaceResult(BlockPos supportBlock, EnumFacing face, Vec3 hitVec,
                float yaw, float pitch) {
            this(supportBlock, face, hitVec, yaw, pitch, false, false);
        }

        public PlaceResult(BlockPos supportBlock, EnumFacing face, Vec3 hitVec,
                float yaw, float pitch, boolean isSupport, boolean requiresSneak) {
            this.supportBlock = supportBlock;
            this.face = face;
            this.hitVec = hitVec;
            this.yaw = yaw;
            this.pitch = pitch;
            this.isSupport = isSupport;
            this.requiresSneak = requiresSneak;
        }
    }

    /**
     * Find the best block placement around the player.
     * <p>
     * Priority order:
     * <ol>
     * <li>Roof (feetY+2)</li>
     * <li>Sides at head level (feetY+1)</li>
     * <li>Sides at feet level (feetY)</li>
     * <li>Scaffold goals — blocks below empty side positions</li>
     * <li>BFS frontier expansion (depth 5) from primary goals</li>
     * </ol>
     *
     * @param reach     max placement range
     * @param orderMode 0 = RANDOM, 1 = ENEMY (sorted by nearest enemy)
     * @return placement result, or null if nothing found
     */
    public static PlaceResult findBestPlacement(double reach, int orderMode) {
        if (mc.thePlayer == null || mc.theWorld == null)
            return null;

        Vec3 playerPos = mc.thePlayer.getPositionVector();
        BlockPos feetPos = new BlockPos(playerPos.xCoord, playerPos.yCoord, playerPos.zCoord);
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);

        // Step 1: Roof
        BlockPos roofTarget = feetPos.up(2);
        if (isAir(roofTarget) && !skippedGoals.contains(roofTarget)
                && !isEntityBlocking(roofTarget)) {
            PlaceResult roofResult = findRoofPlacement(eye, reach, roofTarget);
            if (roofResult != null)
                return roofResult;
        }

        // Step 2+3: Sides — head level first, then feet level
        List<BlockPos> sideGoals = buildSideGoals(feetPos, orderMode);
        sideGoals.removeAll(skippedGoals);

        // Step 4: Scaffold goals — one block below each empty side position
        List<BlockPos> scaffoldGoals = buildScaffoldGoals(sideGoals, feetPos);

        // Try primary side goals first
        PlaceResult sideResult = findBestForGoals(sideGoals, eye, reach, false);
        if (sideResult != null)
            return sideResult;

        // Try scaffold goals (below-feet support blocks) — flagged as support
        PlaceResult scaffoldResult = findBestForGoals(scaffoldGoals, eye, reach, true);
        if (scaffoldResult != null)
            return scaffoldResult;

        // Step 5: BFS frontier expansion from all primary goals
        return findBfsFrontier(sideGoals, eye, reach, 5);
    }

    /**
     * Build side goal positions.
     * Head-level positions come before feet-level for each direction.
     *
     * @param orderMode 0 = RANDOM, 1 = ENEMY
     */
    private static List<BlockPos> buildSideGoals(BlockPos feetPos, int orderMode) {
        List<BlockPos> goals = new ArrayList<>();

        // Head-level first (more important for cage completeness)
        for (int[] d : DIRS) {
            BlockPos headPos = feetPos.add(d[0], 1, d[2]);
            if (isAir(headPos) && !isEntityBlocking(headPos))
                goals.add(headPos);
        }

        // Feet-level
        for (int[] d : DIRS) {
            BlockPos feetGoal = feetPos.add(d[0], 0, d[2]);
            if (isAir(feetGoal) && !isEntityBlocking(feetGoal))
                goals.add(feetGoal);
        }

        if (orderMode == 0) {
            // RANDOM order
            Collections.shuffle(goals);
        } else {
            // ENEMY order — sort by distance to nearest enemy (closest first)
            Vec3 enemyPos = getClosestEnemyPos();
            if (enemyPos != null) {
                goals.sort(Comparator.comparingDouble(pos -> {
                    double dx = (pos.getX() + 0.5) - enemyPos.xCoord;
                    double dy = (pos.getY() + 0.5) - enemyPos.yCoord;
                    double dz = (pos.getZ() + 0.5) - enemyPos.zCoord;
                    return dx * dx + dy * dy + dz * dz;
                }));
            }
        }

        return goals;
    }

    /**
     * Build scaffold goals: for each empty side position, if the block
     * below it is also empty but the block two below is solid, add
     * the below-position as a scaffold placement target.
     * This enables placing support blocks at feet-1 level.
     */
    private static List<BlockPos> buildScaffoldGoals(List<BlockPos> sideGoals,
            BlockPos feetPos) {
        List<BlockPos> scaffold = new ArrayList<>();
        for (BlockPos goal : sideGoals) {
            BlockPos below = goal.down();
            if (below.getY() < feetPos.getY() - 1)
                continue;
            if (!isAir(below))
                continue;

            // Check if there's a solid block below the scaffold position
            BlockPos twoBelow = below.down();
            if (!isAir(twoBelow)) {
                scaffold.add(below);
                continue;
            }

            // Also allow scaffold if any adjacent block at that level is solid
            for (EnumFacing f : EnumFacing.HORIZONTALS) {
                if (!isAir(below.offset(f))) {
                    scaffold.add(below);
                    break;
                }
            }
        }
        return scaffold;
    }

    /**
     * Find a placement for the roof block by scanning nearby support blocks.
     */
    private static PlaceResult findRoofPlacement(Vec3 eye, double reach,
            BlockPos roofTarget) {
        double reachSq = reach * reach;
        double searchSq = (reach + 1) * (reach + 1);

        // Collect reachable solid blocks
        List<ScoredBlock> supports = new ArrayList<>();
        int minX = MathHelper.floor_double(eye.xCoord - reach);
        int maxX = MathHelper.floor_double(eye.xCoord + reach);
        int minY = MathHelper.floor_double(eye.yCoord - 1);
        int maxY = MathHelper.floor_double(eye.yCoord + reach);
        int minZ = MathHelper.floor_double(eye.zCoord - reach);
        int maxZ = MathHelper.floor_double(eye.zCoord + reach);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!isValidSupport(p))
                        continue;

                    double d2 = dist2PointAABB(eye, x, y, z);
                    if (d2 > reachSq)
                        continue;

                    // Verify line-of-sight to block center
                    Vec3 mid = new Vec3(x + 0.5, y + 0.5, z + 0.5);
                    MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(
                            eye, mid, false, false, false);
                    if (mop == null || !mop.getBlockPos().equals(p))
                        continue;

                    supports.add(new ScoredBlock(p, d2));
                }
            }
        }

        if (supports.isEmpty())
            return null;

        supports.sort(Comparator.comparingDouble(a -> a.distance));

        // Try direct placement on each support
        for (ScoredBlock bd : supports) {
            PlaceResult r = tryPlaceOnBlock(bd.pos, eye, reach, roofTarget);
            if (r != null)
                return r;
        }

        // BFS from supports to roof
        return bfsPathToTarget(supports, eye, reach, roofTarget);
    }

    /**
     * BFS pathing from support blocks toward a target position.
     */
    private static PlaceResult bfsPathToTarget(List<ScoredBlock> supports,
            Vec3 eye, double reach,
            BlockPos target) {
        Queue<BlockPos> queue = new LinkedList<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        Set<BlockPos> visited = new HashSet<>();

        for (ScoredBlock bd : supports) {
            for (EnumFacing f : EnumFacing.values()) {
                BlockPos node = bd.pos.offset(f);
                if (!isAir(node) || visited.contains(node))
                    continue;
                visited.add(node);
                parent.put(node, null);
                queue.add(node);
            }
        }

        BlockPos endNode = null;
        int nodesSeen = 0;
        while (!queue.isEmpty() && nodesSeen < MAX_BFS_NODES) {
            BlockPos cur = queue.poll();
            nodesSeen++;
            if (cur.distanceSq(target) <= 1.5) {
                endNode = cur;
                break;
            }
            for (EnumFacing f : EnumFacing.values()) {
                BlockPos next = cur.offset(f);
                if (visited.contains(next) || !isAir(next))
                    continue;
                visited.add(next);
                parent.put(next, cur);
                queue.add(next);
            }
        }

        if (endNode == null)
            return null;

        // Reconstruct path
        List<BlockPos> path = new ArrayList<>();
        for (BlockPos cur = endNode; cur != null; cur = parent.get(cur)) {
            path.add(cur);
        }
        Collections.reverse(path);

        // Try to place along path
        for (BlockPos place : path) {
            if (!isAir(place))
                continue;
            if (isEntityBlocking(place))
                continue;
            for (EnumFacing f : EnumFacing.values()) {
                BlockPos sup = place.offset(f);
                if (!isValidSupport(sup))
                    continue;
                PlaceResult r = tryPlaceOnBlock(sup, eye, reach, place);
                if (r != null)
                    return r;
            }
        }

        return null;
    }

    /**
     * BFS frontier expansion from goal positions outward.
     * Tries to find reachable placements up to the given depth.
     */
    private static PlaceResult findBfsFrontier(List<BlockPos> goals, Vec3 eye,
            double reach, int maxDepth) {
        if (goals.isEmpty())
            return null;

        Set<BlockPos> visited = new HashSet<>(goals);
        List<BlockPos> frontier = new ArrayList<>(goals);

        for (int depth = 0; depth < maxDepth; depth++) {
            List<BlockPos> nextFrontier = new ArrayList<>();
            for (BlockPos pos : frontier) {
                for (EnumFacing f : EnumFacing.values()) {
                    BlockPos neighbor = pos.offset(f);
                    if (visited.contains(neighbor))
                        continue;
                    visited.add(neighbor);

                    if (isAir(neighbor)) {
                        nextFrontier.add(neighbor);
                        continue;
                    }

                    // Solid block found — try to place against it
                    for (BlockPos goal : frontier) {
                        if (!isAir(goal))
                            continue;
                        if (isEntityBlocking(goal))
                            continue;
                        if (!isAdjacent(neighbor, goal))
                            continue;
                        PlaceResult r = tryPlaceOnBlock(neighbor, eye, reach, goal);
                        if (r != null)
                            return r;
                    }
                }
            }
            frontier = nextFrontier;
        }

        return null;
    }

    /**
     * Find the best placement for any of the given goal positions.
     * Scans all 6 faces of adjacent solid blocks, grid-scans hit
     * points on each face, and validates via raytrace.
     */
    public static PlaceResult findBestForGoals(List<BlockPos> goals, Vec3 eye,
            double reach, boolean isSupport) {
        double reachSq = reach * reach;

        for (BlockPos goal : goals) {
            // Skip goals where an entity (including the player) occupies
            // the block — vanilla will reject placement via
            // checkNoEntityCollision anyway.
            if (isEntityBlocking(goal))
                continue;

            for (EnumFacing facing : EnumFacing.values()) {
                BlockPos support = goal.offset(facing);
                boolean needsSneak = false;

                // Check support validity with sneak-mode awareness
                if (isAir(support))
                    continue;
                if (skipInteractableMode == 2) {
                    // SKIP mode — reject interactable supports entirely
                    Block block = mc.theWorld.getBlockState(support).getBlock();
                    if (isInteractable(block))
                        continue;
                } else if (skipInteractableMode == 1) {
                    // SNEAK mode — allow but flag for sneak
                    Block block = mc.theWorld.getBlockState(support).getBlock();
                    if (isInteractable(block))
                        needsSneak = true;
                }
                // mode 0 (NONE) — no filtering

                double d2 = dist2PointAABB(eye, support.getX(), support.getY(),
                        support.getZ());
                if (d2 > reachSq)
                    continue;

                EnumFacing placeFace = facing.getOpposite();
                PlaceResult r = gridScanFace(support, placeFace, eye, reach, goal);
                if (r != null)
                    return new PlaceResult(r.supportBlock, r.face, r.hitVec,
                            r.yaw, r.pitch, isSupport, needsSneak);
            }
        }
        return null;
    }

    /**
     * Try to place a block at targetPos by clicking on supportBlock.
     */
    private static PlaceResult tryPlaceOnBlock(BlockPos supportBlock, Vec3 eye,
            double reach, BlockPos targetPos) {
        for (EnumFacing facing : EnumFacing.values()) {
            BlockPos placementPos = supportBlock.offset(facing);
            if (!placementPos.equals(targetPos))
                continue;

            PlaceResult r = gridScanFace(supportBlock, facing, eye, reach, targetPos);
            if (r != null)
                return r;
        }
        return null;
    }

    /**
     * Grid-scan a face of a support block, raytrace each point,
     * and return the first valid hit that would place at the goal position.
     */
    private static PlaceResult gridScanFace(BlockPos support, EnumFacing face,
            Vec3 eye, double reach,
            BlockPos expectedPlacement) {
        int n = (int) Math.round(1.0 / STEP);
        Random random = new Random();

        for (int r = 0; r <= n; r++) {
            double v = r * STEP + (random.nextDouble() * JIT * 2 - JIT);
            v = clamp01(v);

            for (int c = 0; c <= n; c++) {
                double u = c * STEP + (random.nextDouble() * JIT * 2 - JIT);
                u = clamp01(u);

                Vec3 hitPos = getHitPosOnFace(support, face, u, v);
                float[] rot = getRotations(eye, hitPos.xCoord, hitPos.yCoord,
                        hitPos.zCoord);

                MovingObjectPosition mop = rayTraceBlock(rot[0], rot[1], reach);
                if (mop == null)
                    continue;
                if (mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)
                    continue;
                if (!mop.getBlockPos().equals(support))
                    continue;
                if (mop.sideHit != face)
                    continue;

                // Verify the placement position matches the goal
                BlockPos actualPlacement = support.offset(face);
                if (!actualPlacement.equals(expectedPlacement))
                    continue;

                return new PlaceResult(support, face, mop.hitVec, rot[0], rot[1]);
            }
        }
        return null;
    }

    // ==================== Geometry Utilities ====================

    /**
     * Compute a hit position on a face of a block at (u, v) parametric coords.
     */
    static Vec3 getHitPosOnFace(BlockPos block, EnumFacing face,
            double u, double v) {
        double x = block.getX() + 0.5;
        double y = block.getY() + 0.5;
        double z = block.getZ() + 0.5;

        switch (face) {
            case DOWN:
                return new Vec3(block.getX() + u, block.getY() + INSET, block.getZ() + v);
            case UP:
                return new Vec3(block.getX() + u, block.getY() + 1.0 - INSET, block.getZ() + v);
            case NORTH:
                return new Vec3(block.getX() + u, block.getY() + v, block.getZ() + INSET);
            case SOUTH:
                return new Vec3(block.getX() + u, block.getY() + v, block.getZ() + 1.0 - INSET);
            case WEST:
                return new Vec3(block.getX() + INSET, block.getY() + v, block.getZ() + u);
            case EAST:
                return new Vec3(block.getX() + 1.0 - INSET, block.getY() + v, block.getZ() + u);
            default:
                return new Vec3(x, y, z);
        }
    }

    /**
     * Calculate yaw/pitch to look at a target point from eye position.
     */
    public static float[] getRotations(Vec3 eye, double tx, double ty, double tz) {
        double dx = tx - eye.xCoord;
        double dy = ty - eye.yCoord;
        double dz = tz - eye.zCoord;
        double hd = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        yaw = MathHelper.wrapAngleTo180_float(yaw);

        float pitch = (float) Math.toDegrees(-Math.atan2(dy, hd));
        return new float[] { yaw, pitch };
    }

    /**
     * Raytrace from eye position along the given yaw/pitch direction.
     */
    public static MovingObjectPosition rayTraceBlock(float yaw, float pitch,
            double range) {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);

        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);

        Vec3 start = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 end = start.addVector(x * range, y * range, z * range);

        return mc.theWorld.rayTraceBlocks(start, end);
    }

    // ==================== Helper Methods ====================

    /**
     * Check if a block position is effectively empty (air/liquid/fire).
     */
    public static boolean isAir(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return block == Blocks.air
                || block == Blocks.water
                || block == Blocks.flowing_water
                || block == Blocks.lava
                || block == Blocks.flowing_lava
                || block == Blocks.fire;
    }

    /**
     * Check if any entity's bounding box intersects the full block AABB
     * at the given position. Used to skip goals where the player's body
     * would prevent block placement.
     *
     * @see net.minecraft.world.World#checkNoEntityCollision
     */
    public static boolean isEntityBlocking(BlockPos pos) {
        AxisAlignedBB blockBox = new AxisAlignedBB(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity.getEntityBoundingBox() != null
                    && entity.getEntityBoundingBox().intersectsWith(blockBox)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a block is interactable (chests, workbenches, doors, etc.).
     * When skip-interactable is enabled, these blocks are excluded as
     * support blocks since right-clicking them opens a GUI.
     */
    public static boolean isInteractable(Block block) {
        if (block instanceof BlockContainer)
            return true;
        if (block instanceof BlockWorkbench)
            return true;
        if (block instanceof BlockAnvil)
            return true;
        if (block instanceof BlockBed)
            return true;
        if (block instanceof BlockDoor && block.getMaterial() != Material.iron)
            return true;
        if (block instanceof BlockTrapDoor)
            return true;
        if (block instanceof BlockFenceGate)
            return true;
        if (block instanceof BlockButton)
            return true;
        if (block instanceof BlockLever)
            return true;
        return false;
    }

    /**
     * Check if a solid block can be used as a support.
     * Used by internal methods (roof, BFS) that don't track sneak state.
     * For sneak-aware checking, see {@link #findBestForGoals}.
     */
    private static boolean isValidSupport(BlockPos pos) {
        if (isAir(pos))
            return false;
        if (skipInteractableMode == 2) {
            Block block = mc.theWorld.getBlockState(pos).getBlock();
            if (isInteractable(block))
                return false;
        }
        // SNEAK mode (1) and NONE mode (0): treat as valid support here
        // sneak handling is done at the result level in findBestForGoals
        return true;
    }

    /**
     * Get the closest other player's position.
     */
    private static Vec3 getClosestEnemyPos() {
        if (mc.theWorld == null || mc.thePlayer == null)
            return null;

        Vec3 myPos = mc.thePlayer.getPositionVector();
        Vec3 best = null;
        double bestD2 = Double.POSITIVE_INFINITY;

        for (Object o : mc.theWorld.playerEntities) {
            if (!(o instanceof net.minecraft.entity.player.EntityPlayer))
                continue;
            net.minecraft.entity.player.EntityPlayer p = (net.minecraft.entity.player.EntityPlayer) o;
            if (p == mc.thePlayer)
                continue;

            Vec3 pos = p.getPositionVector();
            double dx = pos.xCoord - myPos.xCoord;
            double dy = pos.yCoord - myPos.yCoord;
            double dz = pos.zCoord - myPos.zCoord;
            double d2 = dx * dx + dy * dy + dz * dz;

            if (d2 < bestD2) {
                bestD2 = d2;
                best = pos;
            }
        }

        return best;
    }

    private static boolean isAdjacent(BlockPos a, BlockPos b) {
        int dx = Math.abs(a.getX() - b.getX());
        int dy = Math.abs(a.getY() - b.getY());
        int dz = Math.abs(a.getZ() - b.getZ());
        return (dx + dy + dz) == 1;
    }

    private static double dist2PointAABB(Vec3 p, int x, int y, int z) {
        double cx = clamp(p.xCoord, x, x + 1);
        double cy = clamp(p.yCoord, y, y + 1);
        double cz = clamp(p.zCoord, z, z + 1);
        double dx = p.xCoord - cx;
        double dy = p.yCoord - cy;
        double dz = p.zCoord - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    /**
     * Count how many of the 9 cage positions (roof + 8 sides) are filled.
     */
    public static int countFilledPositions(BlockPos feetPos) {
        int filled = 0;

        if (!isAir(feetPos.up(2)))
            filled++;

        for (int[] d : DIRS) {
            if (!isAir(feetPos.add(d[0], 0, d[2])))
                filled++;
            if (!isAir(feetPos.add(d[0], 1, d[2])))
                filled++;
        }

        return filled;
    }

    /** Total cage positions: 1 roof + 4 feet-level + 4 head-level. */
    public static final int TOTAL_POSITIONS = 9;

    private static class ScoredBlock {
        final BlockPos pos;
        final double distance;

        ScoredBlock(BlockPos pos, double distance) {
            this.pos = pos;
            this.distance = distance;
        }
    }
}
