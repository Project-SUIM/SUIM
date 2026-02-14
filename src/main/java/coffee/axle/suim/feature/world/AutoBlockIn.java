package coffee.axle.suim.feature.world;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;

import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;
import java.util.Queue;

/**
 * AutoBlockIn — automatic block-in/cage builder
 * Hybrid event model: myau UpdateEvent for rotation spoofing,
 * Forge events for tick placement and rendering.
 */
@SuppressWarnings("unused")
public class AutoBlockIn extends Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private Object moduleInstance;

    // Properties
    private Object rangeProperty;
    private Object speedProperty;
    private Object placeDelayProperty;
    private Object rotationToleranceProperty;
    private Object itemSpoofProperty;
    private Object showProgressProperty;
    private Object moveFixProperty;
    private Object bedRangeProperty;
    private Object centeringProperty;

    private Object bedEspModule;
    private Object killAuraModule;
    private Object bedNukerModule;
    private Field bedEspBedsField;
    private Field killAuraTargetField;
    private Field killAuraIsBlockingField;
    private Field bedNukerTargetBedField;
    private Field bedNukerBreakingField;

    // Cached reflection — only UpdateEvent (the sole myau hook)
    private Method setRotationMethod;
    private Method getYawMethod;
    private Method getPitchMethod;
    private Field updateEventTypeField;
    private Object updatePreEventType;

    // Block priority scores
    private final Map<Block, Integer> blockScore = new HashMap<>();

    // State
    private long lastPlaceTime = 0;
    private float serverYaw;
    private float serverPitch;
    private float progress;
    private float aimYaw;
    private float aimPitch;
    private BlockPos targetBlock;
    private EnumFacing targetFacing;
    private Vec3 targetHitVec;
    private Vec3 queuedHitVec;
    private boolean placeQueued;
    private boolean operationActive;
    private int lastSlot = -1;
    private boolean updateEventWorking = false;

    private static final int[][] DIRS = {
            { 1, 0, 0 }, { 0, 0, 1 }, { -1, 0, 0 }, { 0, 0, -1 }
    };
    private static final double INSET = 0.05;
    private static final double STEP = 0.2;
    private static final double JIT = STEP * 0.1;

    @Override
    public String getName() {
        return "AutoBlockIn";
    }

    @Override
    public GuiCategory getGuiCategory() {
        return GuiCategory.WORLD;
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");

            moduleInstance = createModule();
            creator.injectModule(moduleInstance, AutoBlockIn.class);

            rangeProperty = creator.createFloatProperty("range", 4.5f, 3.0f, 6.0f);
            speedProperty = creator.createIntegerProperty("speed", 20, 5, 100);
            placeDelayProperty = creator.createIntegerProperty("place-delay", 50, 0, 200);
            rotationToleranceProperty = creator.createIntegerProperty("rotation-tolerance", 25, 5, 100);
            itemSpoofProperty = creator.createBooleanProperty("item-spoof", true);
            showProgressProperty = creator.createBooleanProperty("show-progress", true);
            moveFixProperty = creator.createEnumProperty("move-fix", 1,
                    new String[] { "NONE", "SILENT", "STRICT" });
            bedRangeProperty = creator.createFloatProperty("bed-range", 0f, 0f, 10f);
            centeringProperty = creator.createEnumProperty("centering", 0,
                    new String[] { "OFF", "SOFT", "STRICT" });

            creator.registerProperties(moduleInstance,
                    rangeProperty, speedProperty, placeDelayProperty,
                    rotationToleranceProperty, itemSpoofProperty,
                    showProgressProperty, moveFixProperty,
                    bedRangeProperty, centeringProperty);

            manager.reloadModuleCommand();
            initBlockScores();
            cacheUpdateEventMethods();
            hookContextModules();

            // Single myau hook: UpdateEvent for server-side rotation spoofing
            creator.registerEventHandler(
                    MyauMappings.CLASS_UPDATE_EVENT, this::onMyauUpdate, (byte) 1);

            // Forge handles placement + rendering
            MinecraftForge.EVENT_BUS.register(this);

            manager.registerModuleCallbacks(moduleInstance,
                    this::onModuleEnabled, this::onModuleDisabled);

            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;

        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    private void initBlockScores() {
        blockScore.put(Blocks.obsidian, 0);
        blockScore.put(Blocks.end_stone, 1);
        blockScore.put(Blocks.planks, 2);
        blockScore.put(Blocks.log, 2);
        blockScore.put(Blocks.log2, 2);
        blockScore.put(Blocks.glass, 3);
        blockScore.put(Blocks.stained_glass, 3);
        blockScore.put(Blocks.hardened_clay, 4);
        blockScore.put(Blocks.stained_hardened_clay, 4);
        blockScore.put(Blocks.wool, 5);
    }

    private void cacheUpdateEventMethods() {
        try {
            Class<?> updateEventClass = manager.getCachedClass(MyauMappings.CLASS_UPDATE_EVENT);

            setRotationMethod = updateEventClass.getDeclaredMethod(
                    MyauMappings.METHOD_UPDATE_EVENT_SET_ROTATION,
                    float.class, float.class, int.class);
            setRotationMethod.setAccessible(true);

            getYawMethod = updateEventClass.getDeclaredMethod(
                    MyauMappings.METHOD_UPDATE_EVENT_GET_YAW);
            getYawMethod.setAccessible(true);

            getPitchMethod = updateEventClass.getDeclaredMethod(
                    MyauMappings.METHOD_UPDATE_EVENT_GET_PITCH);
            getPitchMethod.setAccessible(true);

            updateEventTypeField = findDeclaredField(updateEventClass,
                    MyauMappings.FIELD_UPDATE_EVENT_TYPE);
            if (updateEventTypeField != null) {
                updateEventTypeField.setAccessible(true);
                Object[] enumConstants = updateEventTypeField.getType().getEnumConstants();
                if (enumConstants != null && enumConstants.length > 0) {
                    updatePreEventType = enumConstants[0];
                }
            }

            MyauLogger.log(getName(), "UpdateEvent cache OK");
        } catch (Exception e) {
            MyauLogger.error("UpdateEvent cache failed", e);
        }
    }

    private static Field findDeclaredField(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private void hookContextModules() {
        try {
            bedEspModule = manager.findModule("BedESP");
            killAuraModule = manager.findModule("KillAura");
            bedNukerModule = manager.findModule("BedNuker");

            if (bedEspModule != null) {
                bedEspBedsField = manager.getCachedField(
                        bedEspModule.getClass(), MyauMappings.FIELD_BED_ESP_BEDS);
            }

            if (killAuraModule != null) {
                killAuraTargetField = findDeclaredField(
                        killAuraModule.getClass(),
                        MyauMappings.FIELD_KILL_AURA_TARGET);
                if (killAuraTargetField != null) {
                    killAuraTargetField.setAccessible(true);
                }

                killAuraIsBlockingField = findDeclaredField(
                        killAuraModule.getClass(),
                        MyauMappings.FIELD_KILL_AURA_IS_BLOCKING);
                if (killAuraIsBlockingField != null) {
                    killAuraIsBlockingField.setAccessible(true);
                }
            }

            if (bedNukerModule != null) {
                bedNukerTargetBedField = findDeclaredField(bedNukerModule.getClass(), "I");
                if (bedNukerTargetBedField != null) {
                    bedNukerTargetBedField.setAccessible(true);
                }

                bedNukerBreakingField = findDeclaredField(bedNukerModule.getClass(), "K");
                if (bedNukerBreakingField != null) {
                    bedNukerBreakingField.setAccessible(true);
                }
            }
        } catch (Exception e) {
            MyauLogger.error("AutoBlockIn hookContextModules", e);
        }
    }

    // ==================== Module Lifecycle ====================

    private void onModuleEnabled() {
        if (mc.thePlayer != null) {
            serverYaw = mc.thePlayer.rotationYaw;
            serverPitch = mc.thePlayer.rotationPitch;
            aimYaw = serverYaw;
            aimPitch = serverPitch;
            progress = 0;
            lastSlot = mc.thePlayer.inventory.currentItem;
            targetBlock = null;
            targetFacing = null;
            targetHitVec = null;
            queuedHitVec = null;
            placeQueued = false;
            operationActive = false;
            lastPlaceTime = 0;
            updateEventWorking = false;
        }
        MyauLogger.log(getName(), "MODULE_ENABLED");
    }

    private void onModuleDisabled() {
        if (lastSlot != -1 && mc.thePlayer != null
                && mc.thePlayer.inventory.currentItem != lastSlot) {
            mc.thePlayer.inventory.currentItem = lastSlot;
        }
        progress = 0;
        targetBlock = null;
        targetFacing = null;
        targetHitVec = null;
        queuedHitVec = null;
        placeQueued = false;
        operationActive = false;
        updateEventWorking = false;
        MyauLogger.log(getName(), "MODULE_DISABLED");
    }

    // ==================== Myau UpdateEvent (rotation spoofing)
    // ====================

    private void onMyauUpdate(Object eventObj) {
        try {
            if (!manager.isModuleEnabled(moduleInstance))
                return;
            if (mc.thePlayer == null || mc.theWorld == null)
                return;
            if (updateEventTypeField == null || updatePreEventType == null)
                return;

            Object eventType = updateEventTypeField.get(eventObj);
            if (eventType != updatePreEventType)
                return;

            if (mc.currentScreen != null)
                return;

            if (!updateEventWorking) {
                updateEventWorking = true;
                MyauLogger.log(getName(), "UpdateEvent firing OK");
            }

            serverYaw = ((Number) getYawMethod.invoke(eventObj)).floatValue();
            serverPitch = ((Number) getPitchMethod.invoke(eventObj)).floatValue();

            operationActive = !isSuppressedByOtherModules() && isBedTriggerSatisfied();
            if (!operationActive) {
                clearTargeting();
                progress = 0f;
                return;
            }

            applyCentering();

            updateProgress();

            int blockSlot = findBestBlockSlot();
            if (blockSlot != -1
                    && mc.thePlayer.inventory.currentItem != blockSlot) {
                mc.thePlayer.inventory.currentItem = blockSlot;
            }

            ItemStack currentHeld = mc.thePlayer.inventory.getCurrentItem();
            boolean holdingBlock = currentHeld != null
                    && currentHeld.getItem() instanceof ItemBlock;
            if (!holdingBlock) {
                targetBlock = null;
                targetFacing = null;
                targetHitVec = null;
                return;
            }

            findBestPlacement();

            if (targetBlock != null && targetFacing != null && targetHitVec != null) {
                Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
                double dx = targetHitVec.xCoord - eyes.xCoord;
                double dy = targetHitVec.yCoord - eyes.yCoord;
                double dz = targetHitVec.zCoord - eyes.zCoord;
                double dist = Math.sqrt(dx * dx + dz * dz);

                float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
                float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
                targetYaw = MathHelper.wrapAngleTo180_float(targetYaw);

                float[] smoothed = getSmoothedRotations(targetYaw, targetPitch);
                aimYaw = smoothed[0];
                aimPitch = smoothed[1];

                setRotationMethod.invoke(eventObj, aimYaw, aimPitch, 6);
                updatePlacementQueue();
            } else {
                placeQueued = false;
                queuedHitVec = null;
            }

        } catch (Exception e) {
            MyauLogger.error("AutoBlockIn onMyauUpdate", e);
        }
    }

    // ==================== Forge Tick (block placement) ====================

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START)
            return;

        try {
            if (!manager.isModuleEnabled(moduleInstance))
                return;
            if (mc.thePlayer == null || mc.theWorld == null)
                return;
            if (mc.currentScreen != null)
                return;
            operationActive = !isSuppressedByOtherModules() && isBedTriggerSatisfied();
            if (!operationActive) {
                clearTargeting();
                progress = 0f;
                return;
            }

            applyCentering();

            applyLocalMoveFix();

            if (targetBlock == null || targetFacing == null || targetHitVec == null)
                return;
            if (!placeQueued || queuedHitVec == null)
                return;

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastPlaceTime < getPlaceDelay())
                return;

            lastPlaceTime = currentTime;

            ItemStack heldStack = mc.thePlayer.inventory.getCurrentItem();
            if (heldStack != null && heldStack.getItem() instanceof ItemBlock) {
                mc.playerController.onPlayerRightClick(
                        mc.thePlayer, mc.theWorld, heldStack,
                        targetBlock, targetFacing, queuedHitVec);
                mc.thePlayer.swingItem();

                clearTargeting();
            }
        } catch (Exception e) {
            MyauLogger.error("AutoBlockIn onClientTick", e);
        }
    }

    // ==================== Forge Render Event ====================

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL)
            return;
        if (!manager.isModuleEnabled(moduleInstance))
            return;
        if (mc.currentScreen != null)
            return;
        if (!getShowProgress())
            return;
        if (mc.fontRendererObj == null)
            return;
        if (!operationActive)
            return;

        float scale = 1.0f;
        String text = String.format("Blocking: %.0f%%", progress * 100.0f);

        GL11.glPushMatrix();
        GL11.glScaled(scale, scale, 0.0);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        ScaledResolution sr = new ScaledResolution(mc);
        int width = mc.fontRendererObj.getStringWidth(text);
        Color color = getProgressColor();

        mc.fontRendererObj.drawString(
                text,
                (float) sr.getScaledWidth() / 2.0F / scale - (float) width / 2.0F,
                (float) sr.getScaledHeight() / 5.0F * 2.0F / scale,
                color.getRGB() & 16777215 | -1090519040,
                true);

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GL11.glPopMatrix();
    }

    // ==================== Block Selection ====================

    private int findBestBlockSlot() {
        int bestSlot = -1;
        int bestScore = Integer.MAX_VALUE;

        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack == null || stack.stackSize == 0)
                continue;
            if (stack.getItem() instanceof ItemBlock) {
                Block block = ((ItemBlock) stack.getItem()).getBlock();
                Integer score = blockScore.get(block);
                if (score != null && score < bestScore) {
                    bestScore = score;
                    bestSlot = slot;
                    if (score == 0)
                        break;
                }
            }
        }
        return bestSlot;
    }

    // ==================== Placement Logic ====================

    private void findBestPlacement() {
        Vec3 playerPos = mc.thePlayer.getPositionVector();
        BlockPos feetPos = new BlockPos(
                playerPos.xCoord, playerPos.yCoord, playerPos.zCoord);
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        double reach = getRange();
        double reachSq = reach * reach;
        double rp12 = (reach + 1) * (reach + 1);

        BlockPos roofTarget = feetPos.up(2);

        if (!isAir(roofTarget)) {
            sidesAim(eye, reach, feetPos);
            return;
        }

        List<BlockData> supports = new ArrayList<>();
        int minX = (int) Math.floor(eye.xCoord - reach);
        int maxX = (int) Math.floor(eye.xCoord + reach);
        int minY = (int) Math.floor(eye.yCoord - 1);
        int maxY = (int) Math.floor(eye.yCoord + reach);
        int minZ = (int) Math.floor(eye.zCoord - reach);
        int maxZ = (int) Math.floor(eye.zCoord + reach);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (isAir(p))
                        continue;
                    double dx = (x + 0.5) - eye.xCoord;
                    double dy = (y + 0.5) - eye.yCoord;
                    double dz = (z + 0.5) - eye.zCoord;
                    if (dx * dx + dy * dy + dz * dz > rp12)
                        continue;
                    double d2 = dist2PointAABB(eye, x, y, z);
                    if (d2 > reachSq)
                        continue;
                    Vec3 mid = new Vec3(x + 0.5, y + 0.5, z + 0.5);
                    MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(
                            eye, mid, false, false, false);
                    if (mop == null)
                        continue;
                    if (!mop.getBlockPos().equals(p))
                        continue;
                    supports.add(new BlockData(p, d2));
                }
            }
        }

        if (supports.isEmpty()) {
            sidesAim(eye, reach, feetPos);
            return;
        }

        supports.sort(Comparator.comparingDouble(a -> a.distance));

        for (BlockData bd : supports) {
            if (tryPlaceOnBlock(bd.pos, eye, reach, roofTarget))
                return;
        }

        // BFS path from supports to roof
        Queue<BlockPos> q = new LinkedList<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        Set<BlockPos> visited = new HashSet<>();

        for (BlockData bd : supports) {
            BlockPos sup = bd.pos;
            for (EnumFacing f : EnumFacing.values()) {
                BlockPos node = sup.offset(f);
                if (!isAir(node) || visited.contains(node))
                    continue;
                visited.add(node);
                parent.put(node, null);
                q.add(node);
            }
        }

        BlockPos endNode = null;
        int nodesSeen = 0;
        while (!q.isEmpty() && nodesSeen < 8964) {
            BlockPos cur = q.poll();
            nodesSeen++;
            if (cur.distanceSq(roofTarget) <= 1.5) {
                endNode = cur;
                break;
            }
            for (EnumFacing f : EnumFacing.values()) {
                BlockPos nxt = cur.offset(f);
                if (visited.contains(nxt) || !isAir(nxt))
                    continue;
                visited.add(nxt);
                parent.put(nxt, cur);
                q.add(nxt);
            }
        }

        if (endNode == null) {
            sidesAim(eye, reach, feetPos);
            return;
        }

        List<BlockPos> path = new ArrayList<>();
        for (BlockPos cur = endNode; cur != null; cur = parent.get(cur)) {
            path.add(cur);
        }
        Collections.reverse(path);

        for (BlockPos place : path) {
            if (!isAir(place))
                continue;
            for (BlockData bd : supports) {
                if (!isAdjacent(bd.pos, place))
                    continue;
                if (tryPlaceOnBlock(bd.pos, eye, reach, place))
                    return;
            }
            for (EnumFacing f : EnumFacing.values()) {
                BlockPos sup = place.offset(f);
                if (isAir(sup))
                    continue;
                if (tryPlaceOnBlock(sup, eye, reach, place))
                    return;
            }
        }

        sidesAim(eye, reach, feetPos);
    }

    private boolean tryPlaceOnBlock(BlockPos supportBlock, Vec3 eye,
            double reach, BlockPos targetPos) {
        for (EnumFacing facing : EnumFacing.values()) {
            BlockPos placementPos = supportBlock.offset(facing);
            if (!placementPos.equals(targetPos))
                continue;

            int n = (int) Math.round(1 / STEP);
            for (int r = 0; r <= n; r++) {
                double v = r * STEP + (Math.random() * JIT * 2 - JIT);
                if (v < 0)
                    v = 0;
                else if (v > 1)
                    v = 1;

                for (int c = 0; c <= n; c++) {
                    double u = c * STEP + (Math.random() * JIT * 2 - JIT);
                    if (u < 0)
                        u = 0;
                    else if (u > 1)
                        u = 1;

                    Vec3 hitPos = getHitPosOnFace(supportBlock, facing, u, v);
                    float[] rot = getRotationsWrapped(eye,
                            hitPos.xCoord, hitPos.yCoord, hitPos.zCoord);

                    MovingObjectPosition mop = rayTraceBlock(rot[0], rot[1], reach);
                    if (mop != null
                            && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                            && mop.getBlockPos().equals(supportBlock)
                            && mop.sideHit == facing) {
                        targetBlock = supportBlock;
                        targetFacing = facing;
                        targetHitVec = mop.hitVec;
                        aimYaw = rot[0];
                        aimPitch = rot[1];
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void sidesAim(Vec3 eye, double reach, BlockPos feetPos) {
        List<BlockPos> goals = new ArrayList<>();
        for (int[] d : DIRS) {
            BlockPos headPos = feetPos.add(d[0], 1, d[2]);
            if (isAir(headPos))
                goals.add(headPos);
        }
        for (int[] d : DIRS) {
            BlockPos feetGoal = feetPos.add(d[0], 0, d[2]);
            if (isAir(feetGoal))
                goals.add(feetGoal);
        }

        Vec3 closestEnemy = getClosestOtherPlayerPos();
        if (closestEnemy != null) {
            goals.sort(Comparator.comparingDouble(pos -> {
                double dx = (pos.getX() + 0.5) - closestEnemy.xCoord;
                double dy = (pos.getY() + 0.5) - closestEnemy.yCoord;
                double dz = (pos.getZ() + 0.5) - closestEnemy.zCoord;
                return dx * dx + dy * dy + dz * dz;
            }));
        }

        findBestForGoals(goals, eye, reach);
    }

    private Vec3 getClosestOtherPlayerPos() {
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

    private void findBestForGoals(List<BlockPos> goals, Vec3 eye, double reach) {
        for (BlockPos goal : goals) {
            for (EnumFacing facing : EnumFacing.values()) {
                BlockPos support = goal.offset(facing);
                if (isAir(support))
                    continue;

                Vec3 center = new Vec3(
                        support.getX() + 0.5,
                        support.getY() + 0.5,
                        support.getZ() + 0.5);
                if (eye.distanceTo(center) > reach)
                    continue;

                int n = (int) Math.round(1 / STEP);
                for (int r = 0; r <= n; r++) {
                    double v = r * STEP + (Math.random() * JIT * 2 - JIT);
                    if (v < 0)
                        v = 0;
                    else if (v > 1)
                        v = 1;

                    for (int c = 0; c <= n; c++) {
                        double u = c * STEP + (Math.random() * JIT * 2 - JIT);
                        if (u < 0)
                            u = 0;
                        else if (u > 1)
                            u = 1;

                        Vec3 hitPos = getHitPosOnFace(support,
                                facing.getOpposite(), u, v);
                        float[] rot = getRotationsWrapped(eye,
                                hitPos.xCoord, hitPos.yCoord, hitPos.zCoord);

                        MovingObjectPosition mop = rayTraceBlock(rot[0], rot[1], reach);
                        if (mop != null
                                && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                                && mop.getBlockPos().equals(support)
                                && mop.sideHit == facing.getOpposite()) {
                            targetBlock = support;
                            targetFacing = facing.getOpposite();
                            targetHitVec = mop.hitVec;
                            aimYaw = rot[0];
                            aimPitch = rot[1];
                            return;
                        }
                    }
                }
            }
        }
    }

    // ==================== Utility Methods ====================

    private Vec3 getHitPosOnFace(BlockPos block, EnumFacing face,
            double u, double v) {
        double x = block.getX() + 0.5;
        double y = block.getY() + 0.5;
        double z = block.getZ() + 0.5;

        switch (face) {
            case DOWN:
                y = block.getY() + INSET;
                x = block.getX() + u;
                z = block.getZ() + v;
                break;
            case UP:
                y = block.getY() + 1.0 - INSET;
                x = block.getX() + u;
                z = block.getZ() + v;
                break;
            case NORTH:
                z = block.getZ() + INSET;
                x = block.getX() + u;
                y = block.getY() + v;
                break;
            case SOUTH:
                z = block.getZ() + 1.0 - INSET;
                x = block.getX() + u;
                y = block.getY() + v;
                break;
            case WEST:
                x = block.getX() + INSET;
                z = block.getZ() + u;
                y = block.getY() + v;
                break;
            case EAST:
                x = block.getX() + 1.0 - INSET;
                z = block.getZ() + u;
                y = block.getY() + v;
                break;
        }
        return new Vec3(x, y, z);
    }

    private boolean isAir(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return block == Blocks.air
                || block == Blocks.water
                || block == Blocks.flowing_water
                || block == Blocks.lava
                || block == Blocks.flowing_lava
                || block == Blocks.fire;
    }

    private boolean isAdjacent(BlockPos a, BlockPos b) {
        int dx = Math.abs(a.getX() - b.getX());
        int dy = Math.abs(a.getY() - b.getY());
        int dz = Math.abs(a.getZ() - b.getZ());
        return (dx + dy + dz) == 1;
    }

    private void updateProgress() {
        Vec3 playerPos = mc.thePlayer.getPositionVector();
        BlockPos feetPos = new BlockPos(
                playerPos.xCoord, playerPos.yCoord, playerPos.zCoord);

        int filled = 0;
        int total = 9;

        if (!isAir(feetPos.up(2)))
            filled++;

        for (int[] d : DIRS) {
            if (!isAir(feetPos.add(d[0], 0, d[2])))
                filled++;
            if (!isAir(feetPos.add(d[0], 1, d[2])))
                filled++;
        }

        progress = (float) filled / (float) total;
    }

    private Color getProgressColor() {
        if (progress <= 0.33f)
            return new Color(255, 85, 85);
        else if (progress <= 0.66f)
            return new Color(255, 255, 85);
        else
            return new Color(85, 255, 85);
    }

    private MovingObjectPosition rayTraceBlock(float yaw, float pitch, double range) {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);

        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);

        Vec3 start = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 end = start.addVector(x * range, y * range, z * range);

        return mc.theWorld.rayTraceBlocks(start, end);
    }

    private boolean withinRotationTolerance(float targetYaw, float targetPitch) {
        float dy = Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - serverYaw));
        float dp = Math.abs(MathHelper.wrapAngleTo180_float(targetPitch - serverPitch));
        int tol = getRotationTolerance();
        return dy <= tol && dp <= tol;
    }

    private double dist2PointAABB(Vec3 p, int x, int y, int z) {
        double cx = clamp(p.xCoord, x, x + 1);
        double cy = clamp(p.yCoord, y, y + 1);
        double cz = clamp(p.zCoord, z, z + 1);

        double dx = p.xCoord - cx;
        double dy = p.yCoord - cy;
        double dz = p.zCoord - cz;

        return dx * dx + dy * dy + dz * dz;
    }

    private double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (Math.min(v, hi));
    }

    private float[] getRotationsWrapped(Vec3 eye, double tx, double ty, double tz) {
        double dx = tx - eye.xCoord;
        double dy = ty - eye.yCoord;
        double dz = tz - eye.zCoord;
        double hd = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        yaw = normYaw(yaw);

        float pitch = (float) Math.toDegrees(-Math.atan2(dy, hd));
        return new float[] { yaw, pitch };
    }

    private float normYaw(float yaw) {
        yaw = ((yaw % 360f) + 360f) % 360f;
        return (yaw > 180f) ? (yaw - 360f) : yaw;
    }

    private float wrapYawDelta(float base, float target) {
        float d = target - base;
        while (d <= -180f)
            d += 360f;
        while (d > 180f)
            d -= 360f;
        return d;
    }

    private float unwrapYaw(float yaw, float prevYaw) {
        return prevYaw + ((((yaw - prevYaw + 180f) % 360f) + 360f) % 360f - 180f);
    }

    private float[] getSmoothedRotations(float targetYaw, float targetPitch) {
        float currentYaw = serverYaw;
        float currentPitch = serverPitch;

        float targetWrapped = normYaw(targetYaw);
        float targetUnwrapped = unwrapYaw(targetWrapped, currentYaw);

        float dYaw = targetUnwrapped - currentYaw;
        float dPitch = targetPitch - currentPitch;

        if (Math.abs(dYaw) < 0.1f)
            currentYaw = targetUnwrapped;
        if (Math.abs(dPitch) < 0.1f)
            currentPitch = targetPitch;

        if (currentYaw == targetUnwrapped && currentPitch == targetPitch) {
            return new float[] { currentYaw, currentPitch };
        }

        float maxStep = getSpeed();
        float stepYaw = MathHelper.clamp_float(dYaw, -maxStep, maxStep);
        float stepPitch = MathHelper.clamp_float(dPitch, -maxStep, maxStep);

        currentYaw += stepYaw;
        currentPitch = MathHelper.clamp_float(currentPitch + stepPitch, -90f, 90f);

        if (Math.signum(targetUnwrapped - currentYaw) != Math.signum(dYaw))
            currentYaw = targetUnwrapped;
        if (Math.signum(targetPitch - currentPitch) != Math.signum(dPitch))
            currentPitch = targetPitch;

        return new float[] { currentYaw, currentPitch };
    }

    private void applyLocalMoveFix() {
        if (mc.thePlayer == null || mc.thePlayer.movementInput == null)
            return;

        int mode = getMoveFixMode();
        if (mode <= 0)
            return;

        float forward = mc.thePlayer.movementInput.moveForward;
        float strafe = mc.thePlayer.movementInput.moveStrafe;
        if (Math.abs(forward) < 1.0E-4f && Math.abs(strafe) < 1.0E-4f)
            return;

        float fixYaw;
        if (mode == 1) {
            if (Math.abs(forward) < 1.0E-4f)
                return;
            if (targetBlock == null || targetFacing == null)
                return;
            fixYaw = aimYaw;
        } else {
            if (targetBlock == null || targetFacing == null)
                return;
            fixYaw = aimYaw;
        }

        float delta = (float) Math.toRadians(mc.thePlayer.rotationYaw - fixYaw);
        float cos = MathHelper.cos(delta);
        float sin = MathHelper.sin(delta);

        float fixedForward = forward * cos + strafe * sin;
        float fixedStrafe = strafe * cos - forward * sin;

        mc.thePlayer.movementInput.moveForward = fixedForward;
        mc.thePlayer.movementInput.moveStrafe = fixedStrafe;
    }

    private void applyCentering() {
        if (mc.thePlayer == null)
            return;

        int mode = getCenteringMode();
        if (mode <= 0)
            return;

        double centerX = Math.floor(mc.thePlayer.posX) + 0.5;
        double centerZ = Math.floor(mc.thePlayer.posZ) + 0.5;
        double dx = centerX - mc.thePlayer.posX;
        double dz = centerZ - mc.thePlayer.posZ;

        if (mode == 2) {
            if (Math.abs(dx) > 0.001 || Math.abs(dz) > 0.001) {
                mc.thePlayer.setPosition(centerX, mc.thePlayer.posY, centerZ);
                mc.thePlayer.motionX = 0.0;
                mc.thePlayer.motionZ = 0.0;
            }
            return;
        }

        double softTolerance = 0.18;
        if (Math.abs(dx) <= softTolerance && Math.abs(dz) <= softTolerance)
            return;

        double xStep = clamp(dx * 0.35, -0.08, 0.08);
        double zStep = clamp(dz * 0.35, -0.08, 0.08);
        mc.thePlayer.motionX = xStep;
        mc.thePlayer.motionZ = zStep;
    }

    private void updatePlacementQueue() {
        placeQueued = false;
        queuedHitVec = null;

        if (targetBlock == null || targetFacing == null)
            return;
        if (!withinRotationTolerance(aimYaw, aimPitch))
            return;

        MovingObjectPosition mop = rayTraceBlock(aimYaw, aimPitch, getRange());
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)
            return;
        if (!targetBlock.equals(mop.getBlockPos()) || mop.sideHit != targetFacing)
            return;

        placeQueued = true;
        queuedHitVec = mop.hitVec;
    }

    private void clearTargeting() {
        targetBlock = null;
        targetFacing = null;
        targetHitVec = null;
        queuedHitVec = null;
        placeQueued = false;
    }

    private boolean isSuppressedByOtherModules() {
        if (killAuraModule == null)
            killAuraModule = manager.findModule("KillAura");
        if (bedNukerModule == null)
            bedNukerModule = manager.findModule("BedNuker");

        return isKillAuraActivelyControlling() || isBedNukerActivelyControlling();
    }

    private boolean isKillAuraActivelyControlling() {
        try {
            if (killAuraModule == null || !manager.isModuleEnabled(killAuraModule)) {
                return false;
            }

            if (killAuraTargetField != null) {
                Object target = killAuraTargetField.get(killAuraModule);
                if (target != null) {
                    return true;
                }
            }

            if (killAuraIsBlockingField != null && killAuraIsBlockingField.getBoolean(killAuraModule)) {
                return true;
            }

            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isBedNukerActivelyControlling() {
        try {
            if (bedNukerModule == null || !manager.isModuleEnabled(bedNukerModule)) {
                return false;
            }

            if (bedNukerBreakingField != null && bedNukerBreakingField.getBoolean(bedNukerModule)) {
                return true;
            }

            if (bedNukerTargetBedField != null) {
                Object targetBed = bedNukerTargetBedField.get(bedNukerModule);
                return targetBed != null;
            }

            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isBedTriggerSatisfied() {
        float bedRange = getBedRange();
        if (bedRange <= 0f)
            return true;

        if (mc.thePlayer == null || mc.theWorld == null)
            return false;

        double nearest = getNearestBedDistanceFromEsp();
        if (nearest >= 0d)
            return nearest <= bedRange;

        BlockPos feet = mc.thePlayer.getPosition();
        int r = MathHelper.ceiling_float_int(bedRange);
        double best = Double.POSITIVE_INFINITY;

        for (int x = -r; x <= r; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = feet.add(x, y, z);
                    Block block = mc.theWorld.getBlockState(p).getBlock();
                    if (!(block instanceof BlockBed))
                        continue;

                    double dist = mc.thePlayer.getDistance(
                            p.getX() + 0.5,
                            p.getY(),
                            p.getZ() + 0.5);
                    if (dist < best)
                        best = dist;
                }
            }
        }

        return best <= bedRange;
    }

    @SuppressWarnings("unchecked")
    private double getNearestBedDistanceFromEsp() {
        try {
            if (bedEspModule == null)
                bedEspModule = manager.findModule("BedESP");
            if (bedEspModule == null)
                return -1d;

            if (bedEspBedsField == null) {
                bedEspBedsField = manager.getCachedField(
                        bedEspModule.getClass(), MyauMappings.FIELD_BED_ESP_BEDS);
            }

            Set<BlockPos> beds = (Set<BlockPos>) bedEspBedsField.get(bedEspModule);
            if (beds == null || beds.isEmpty())
                return -1d;

            double best = Double.POSITIVE_INFINITY;
            for (BlockPos p : beds) {
                double dist = mc.thePlayer.getDistance(
                        p.getX() + 0.5,
                        p.getY(),
                        p.getZ() + 0.5);
                if (dist < best)
                    best = dist;
            }
            return best;
        } catch (Exception e) {
            return -1d;
        }
    }

    // ==================== Property Accessors ====================

    private float getRange() {
        try {
            return ((Number) properties.getPropertyValue(rangeProperty)).floatValue();
        } catch (Exception e) {
            return 4.5f;
        }
    }

    private float getSpeed() {
        try {
            return ((Number) properties.getPropertyValue(speedProperty)).floatValue();
        } catch (Exception e) {
            return 20f;
        }
    }

    private int getPlaceDelay() {
        try {
            return ((Number) properties.getPropertyValue(placeDelayProperty)).intValue();
        } catch (Exception e) {
            return 50;
        }
    }

    private int getRotationTolerance() {
        try {
            return ((Number) properties.getPropertyValue(rotationToleranceProperty)).intValue();
        } catch (Exception e) {
            return 25;
        }
    }

    private boolean getShowProgress() {
        try {
            return (Boolean) properties.getPropertyValue(showProgressProperty);
        } catch (Exception e) {
            return true;
        }
    }

    private int getMoveFixMode() {
        try {
            return ((Number) properties.getPropertyValue(moveFixProperty)).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private float getBedRange() {
        try {
            return ((Number) properties.getPropertyValue(bedRangeProperty)).floatValue();
        } catch (Exception e) {
            return 0f;
        }
    }

    private int getCenteringMode() {
        try {
            return ((Number) properties.getPropertyValue(centeringProperty)).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void disable() {
        onModuleDisabled();
    }

    // ==================== Inner Classes ====================

    private static class BlockData {
        BlockPos pos;
        double distance;

        BlockData(BlockPos pos, double distance) {
            this.pos = pos;
            this.distance = distance;
        }
    }
}
