package coffee.axle.suim.features;

import coffee.axle.suim.features.bedplates.BlockBedScanner;
import coffee.axle.suim.hooks.MyauHook;
import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.hooks.PropertyUtils;
import coffee.axle.suim.render.BedBillboardRenderer;
import coffee.axle.suim.util.HudUtils;
import coffee.axle.suim.util.MyauLogger;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

public class Bedplates implements Feature {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private final MyauHook hook = MyauHook.getInstance();
    private Object moduleInstance;

    private Object yShiftProperty;
    private Object updateRateProperty;
    private Object rangeProperty;
    private Object layersProperty;
    private Object firstBedOnlyProperty;
    private Object borderModeProperty;
    private Object borderThicknessProperty;
    private Object autoScaleProperty;
    private Object billboardScaleProperty;
    private Object itemSizeProperty;
    private Object backgroundModeProperty;
    private Object backgroundColorProperty;

    private final HudUtils hudUtils = HudUtils.getInstance();

    private Object bedESPModule;
    private Field bedsField;

    private final List<BlockPos> bedFeet = new ArrayList<>();
    private final List<BlockPos> bedHeads = new ArrayList<>();
    private final List<Map<Block, Integer>> bedLayers = new ArrayList<>();
    private long lastUpdateTime = 0;

    @Override
    public String getName() {
        return "BedPlates";
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");

            moduleInstance = hook.createModule(getName());
            hook.injectModule(moduleInstance, getClass());

            yShiftProperty = hook.createFloatProperty(
                    "y-offset", 2f, -5f, 10f);
            updateRateProperty = hook.createFloatProperty(
                    "update-rate", 1000f, 250f, 5000f);
            rangeProperty = hook.createFloatProperty(
                    "range", 0f, 0f, 1000f);
            layersProperty = hook.createFloatProperty(
                    "layers", 5f, 1f, 10f);
            firstBedOnlyProperty = hook.createBooleanProperty(
                    "first-bed-only", false);
            borderModeProperty = hook.createEnumProperty(
                    "border", 0,
                    new String[] { "NONE", "HUD" });
            borderThicknessProperty = hook.createFloatProperty(
                    "border-thickness", 1.5f, 0.5f, 5f);
            autoScaleProperty = hook.createEnumProperty(
                    "auto-scale", 1,
                    new String[] { "LINEAR", "SUBLINEAR" });
            billboardScaleProperty = hook.createFloatProperty(
                    "scale", 1f, 0.1f, 5f);
            itemSizeProperty = hook.createFloatProperty(
                    "item-size", 16f, 8f, 32f);
            backgroundModeProperty = hook.createEnumProperty(
                    "background", 1,
                    new String[] { "NONE", "DEFAULT",
                            "HUD", "CUSTOM" });
            backgroundColorProperty = hook.createStringProperty(
                    "background-color", "000000");

            hook.registerProperties(moduleInstance,
                    yShiftProperty, updateRateProperty,
                    rangeProperty, layersProperty,
                    firstBedOnlyProperty, borderModeProperty,
                    borderThicknessProperty, autoScaleProperty,
                    billboardScaleProperty, itemSizeProperty,
                    backgroundModeProperty,
                    backgroundColorProperty);

            hudUtils.initialize();
            hookBedESP();

            hook.reloadModuleCommand();
            MinecraftForge.EVENT_BUS.register(this);

            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;
        } catch (Exception t) {
            MyauLogger.error("FEATURE_FAIL", t);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void hookBedESP() {
        try {
            bedESPModule = hook.findModule("BedESP");
            if (bedESPModule == null) {
                throw new RuntimeException(
                        "BedESP module not found");
            }

            bedsField = hook.getCachedField(
                    bedESPModule.getClass(),
                    MyauMappings.FIELD_BED_ESP_BEDS);

            MyauLogger.log(getName(),
                    "Hooked into BedESP beds field");
        } catch (Exception e) {
            MyauLogger.error(
                    "BedPlates: BedESP hook fail", e);
        }
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) {
            clearBedData();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START)
            return;
        if (!hook.isModuleEnabled(moduleInstance))
            return;
        if (mc.thePlayer == null || mc.theWorld == null)
            return;

        long now = System.currentTimeMillis();
        long rate = (long) PropertyUtils.getFloat(
                updateRateProperty, 1000f);
        if (now - lastUpdateTime < rate)
            return;
        lastUpdateTime = now;

        refreshBedPositions();
        rebuildAllLayers();
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!hook.isModuleEnabled(moduleInstance))
            return;
        if (mc.thePlayer == null || mc.theWorld == null)
            return;
        if (bedFeet.isEmpty())
            return;

        boolean firstOnly = PropertyUtils.getBoolean(
                firstBedOnlyProperty, false);
        int limit = firstOnly
                ? Math.min(1, bedFeet.size())
                : bedFeet.size();

        float range = PropertyUtils.getFloat(
                rangeProperty, 0f);

        for (int i = 0; i < limit; i++) {
            if (i < bedLayers.size()
                    && !bedLayers.get(i).isEmpty()) {
                BlockPos pos = bedFeet.get(i);
                if (range > 0) {
                    double dist = mc.thePlayer.getDistance(
                            pos.getX() + 0.5,
                            pos.getY(),
                            pos.getZ() + 0.5);
                    if (dist > range)
                        continue;
                }
                drawPlate(pos, i);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void refreshBedPositions() {
        float range = PropertyUtils.getFloat(
                rangeProperty, 0f);
        boolean firstOnly = PropertyUtils.getBoolean(
                firstBedOnlyProperty, false);

        Set<BlockPos> espBeds = null;
        if (bedESPModule != null && bedsField != null) {
            try {
                espBeds = (CopyOnWriteArraySet<BlockPos>) bedsField.get(bedESPModule);
            } catch (Exception ex) {
                espBeds = null;
            }
        }

        pruneDestroyedBeds();
        pruneOutOfRangeBeds(range);

        if (espBeds != null && !espBeds.isEmpty()) {
            readBedsFromESP(espBeds, range, firstOnly);
        }
    }

    private void pruneOutOfRangeBeds(float range) {
        if (range <= 0)
            return;
        Iterator<BlockPos> it = bedFeet.iterator();
        int idx = 0;
        while (it.hasNext()) {
            BlockPos pos = it.next();
            double dist = mc.thePlayer.getDistance(
                    pos.getX() + 0.5,
                    pos.getY(),
                    pos.getZ() + 0.5);
            if (dist > range) {
                it.remove();
                if (idx < bedHeads.size())
                    bedHeads.remove(idx);
                if (idx < bedLayers.size())
                    bedLayers.remove(idx);
            } else {
                idx++;
            }
        }
    }

    private void pruneDestroyedBeds() {
        Iterator<BlockPos> it = bedFeet.iterator();
        int idx = 0;
        while (it.hasNext()) {
            BlockPos pos = it.next();
            IBlockState state = mc.theWorld.getBlockState(pos);
            if (!(state.getBlock() instanceof BlockBed)) {
                it.remove();
                if (idx < bedHeads.size())
                    bedHeads.remove(idx);
                if (idx < bedLayers.size())
                    bedLayers.remove(idx);
            } else {
                idx++;
            }
        }
    }

    private void readBedsFromESP(
            Set<BlockPos> espBeds,
            float range, boolean firstOnly) {
        Set<Long> knownFeet = new HashSet<>();
        for (BlockPos p : bedFeet) {
            knownFeet.add(p.toLong());
        }

        for (BlockPos pos : espBeds) {
            double dist = mc.thePlayer.getDistance(
                    pos.getX() + 0.5,
                    pos.getY(),
                    pos.getZ() + 0.5);
            if (range > 0 && dist > range)
                continue;

            IBlockState state = mc.theWorld.getBlockState(pos);
            if (!(state.getBlock() instanceof BlockBed))
                continue;

            BlockPos footPos;
            BlockPos headPos;

            if (state.getValue(BlockBed.PART) == BlockBed.EnumPartType.FOOT) {
                footPos = pos;
                headPos = findBedHead(pos, state);
            } else {
                headPos = pos;
                footPos = findBedFoot(pos, state);
                if (footPos == null)
                    continue;
            }

            if (knownFeet.contains(footPos.toLong()))
                continue;

            if (firstOnly) {
                clearBedData();
                bedFeet.add(footPos);
                bedHeads.add(headPos);
                bedLayers.add(new LinkedHashMap<>());
                return;
            }

            bedFeet.add(footPos);
            bedHeads.add(headPos);
            bedLayers.add(new LinkedHashMap<>());
            knownFeet.add(footPos.toLong());
        }
    }

    private void rebuildAllLayers() {
        int maxLayers = (int) PropertyUtils.getFloat(
                layersProperty, 5f);

        for (int i = 0; i < bedFeet.size()
                && i < bedLayers.size(); i++) {
            BlockPos footPos = bedFeet.get(i);
            IBlockState footState = mc.theWorld.getBlockState(footPos);
            if (!(footState.getBlock() instanceof BlockBed))
                continue;

            BlockPos headPos = i < bedHeads.size()
                    ? bedHeads.get(i)
                    : null;
            if (headPos == null)
                continue;

            BlockBedScanner.scan(
                    footPos, headPos,
                    maxLayers, bedLayers.get(i));
        }
    }

    private BlockPos findBedHead(
            BlockPos footPos, IBlockState footState) {
        try {
            EnumFacing facing = footState.getValue(BlockBed.FACING);
            BlockPos headPos = footPos.offset(facing);
            IBlockState headState = mc.theWorld.getBlockState(headPos);
            if (headState.getBlock() instanceof BlockBed
                    && headState.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD) {
                return headPos;
            }
        } catch (Exception ignored) {
        }

        for (EnumFacing face : EnumFacing.HORIZONTALS) {
            BlockPos check = footPos.offset(face);
            IBlockState state = mc.theWorld.getBlockState(check);
            if (state.getBlock() instanceof BlockBed
                    && state.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD) {
                return check;
            }
        }
        return null;
    }

    private BlockPos findBedFoot(
            BlockPos headPos, IBlockState headState) {
        try {
            EnumFacing facing = headState.getValue(BlockBed.FACING);
            BlockPos footPos = headPos.offset(facing.getOpposite());
            IBlockState footState = mc.theWorld.getBlockState(footPos);
            if (footState.getBlock() instanceof BlockBed
                    && footState.getValue(BlockBed.PART) == BlockBed.EnumPartType.FOOT) {
                return footPos;
            }
        } catch (Exception ignored) {
        }

        for (EnumFacing face : EnumFacing.HORIZONTALS) {
            BlockPos check = headPos.offset(face);
            IBlockState state = mc.theWorld.getBlockState(check);
            if (state.getBlock() instanceof BlockBed
                    && state.getValue(BlockBed.PART) == BlockBed.EnumPartType.FOOT) {
                return check;
            }
        }
        return null;
    }

    private void drawPlate(BlockPos footPos, int index) {
        Map<Block, Integer> layerMap = bedLayers.get(index);
        float yShift = PropertyUtils.getFloat(
                yShiftProperty, 2f);

        BlockPos headPos = index < bedHeads.size()
                ? bedHeads.get(index)
                : null;

        double centerX, centerZ;
        if (headPos != null) {
            centerX = (footPos.getX() + headPos.getX())
                    / 2.0 + 0.5;
            centerZ = (footPos.getZ() + headPos.getZ())
                    / 2.0 + 0.5;
        } else {
            centerX = footPos.getX() + 0.5;
            centerZ = footPos.getZ() + 0.5;
        }
        double centerY = footPos.getY() + yShift + 1;

        double distance = mc.thePlayer.getDistance(
                centerX, footPos.getY(), centerZ);

        float maxDist = PropertyUtils.getFloat(
                rangeProperty, 0f);
        int scaleMode = PropertyUtils.getInt(
                autoScaleProperty, 1);
        float scaleMultiplier = PropertyUtils.getFloat(
                billboardScaleProperty, 1f);
        double billboardScale = BedBillboardRenderer.DEFAULT_BILLBOARD_SCALE
                * scaleMultiplier;
        double scaleFactor = BedBillboardRenderer.computeScale(
                distance, maxDist, scaleMode, billboardScale);

        if (scaleFactor <= 0)
            return;

        int borderMode = PropertyUtils.getInt(
                borderModeProperty, 0);
        float borderThickness = PropertyUtils.getFloat(
                borderThicknessProperty, 1.5f);
        Color borderColor = (borderMode == 1)
                ? hudUtils.getHudColor(
                        new Color(80, 200, 220))
                : null;

        int itemSize = (int) PropertyUtils.getFloat(
                itemSizeProperty, 16f);

        int bgMode = PropertyUtils.getInt(
                backgroundModeProperty, 1);
        Color bgColor = null;
        if (bgMode == BedBillboardRenderer.BG_HUD) {
            bgColor = hudUtils.getHudColor(
                    new Color(80, 200, 220));
        } else if (bgMode == BedBillboardRenderer.BG_CUSTOM) {
            String hex = PropertyUtils.getString(
                    backgroundColorProperty, "000000");
            bgColor = BedBillboardRenderer.parseHexColor(hex);
        }

        BedBillboardRenderer.draw(
                layerMap, centerX, centerY, centerZ,
                scaleFactor, borderColor, borderThickness,
                itemSize, bgMode, bgColor);
    }

    private void clearBedData() {
        bedFeet.clear();
        bedHeads.clear();
        bedLayers.clear();
    }

    @Override
    public void disable() {
        clearBedData();
    }
}
