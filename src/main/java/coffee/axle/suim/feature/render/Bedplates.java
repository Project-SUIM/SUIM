package coffee.axle.suim.feature.render;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;

import coffee.axle.suim.feature.render.bedplates.BlockBedScanner;
import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.property.Setting;
import coffee.axle.suim.render.BedBillboardRenderer;
import coffee.axle.suim.util.HypixelUtils;
import coffee.axle.suim.util.HudUtils;
import coffee.axle.suim.util.MyauLogger;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import static coffee.axle.suim.ProjectSUIM.mc;

public class Bedplates extends Feature {


    public enum Border {
        NONE, HUD
    }

    public enum AutoScale {
        LINEAR, SUBLINEAR
    }

    public enum Background {
        NONE, DEFAULT, HUD, CUSTOM
    }

    @Setting(name = "y-offset", min = -5f, max = 10f) private float yShift = 2f;
    @Setting(name = "update-rate", min = 250f, max = 5000f) private float updateRate = 1000f;
    @Setting(min = 0f, max = 1000f) private float range = 0f;
    @Setting(min = 1f, max = 10f) private float layers = 5f;
    @Setting(name = "border-thickness", min = 0.5f, max = 5f) private float borderThickness = 1.5f;
    @Setting(name = "scale", min = 0.1f, max = 5f) private float billboardScale = 1f;
    @Setting(name = "item-size", min = 8f, max = 32f) private float itemSize = 16f;

    @Setting(name = "background-color") private String backgroundColor = "000000";

    @Setting private Border border = Border.NONE;
    @Setting private AutoScale autoScale = AutoScale.SUBLINEAR;
    @Setting private Background background = Background.DEFAULT;

    @Setting(name = "bedwars-only") private boolean bedwarsOnly = false;
    @Setting(name = "first-bed-only") private boolean firstBedOnly = false;

    private final HudUtils hudUtils = HudUtils.getInstance();

    private Object bedESPModule;
    private Field bedsField;

    private final List<BlockPos> bedFeet = new ArrayList<>();
    private final List<BlockPos> bedHeads = new ArrayList<>();
    private final List<Map<Block, Integer>> bedLayers = new ArrayList<>();
    private long lastUpdateTime = 0;

    public Bedplates() {
        super("BedPlates", GuiCategory.RENDER);
    }

    @Override
    protected void init() throws Exception {
        hudUtils.initialize();
        hookBedESP();
    }

    private void hookBedESP() {
        try {
            bedESPModule = manager.findModule("BedESP");
            if (bedESPModule == null) {
                throw new RuntimeException(
                        "BedESP module not found");
            }

            bedsField = manager.getCachedField(
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
        if (!isEnabled())
            return;
        if (mc.thePlayer == null || mc.theWorld == null)
            return;
        if (bedwarsOnly
                && !HypixelUtils.inBedWarsGame())
            return;

        long now = System.currentTimeMillis();
        long rate = (long) updateRate;
        if (now - lastUpdateTime < rate)
            return;
        lastUpdateTime = now;

        refreshBedPositions();
        rebuildAllLayers();
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!isEnabled())
            return;
        if (mc.thePlayer == null || mc.theWorld == null)
            return;
        if (bedwarsOnly
                && !HypixelUtils.inBedWarsGame())
            return;
        if (bedFeet.isEmpty())
            return;

        boolean firstOnly = firstBedOnly;
        int limit = firstOnly
                ? Math.min(1, bedFeet.size())
                : bedFeet.size();

        float maxRange = range;

        for (int i = 0; i < limit; i++) {
            if (i < bedLayers.size()
                    && !bedLayers.get(i).isEmpty()) {
                BlockPos pos = bedFeet.get(i);
                if (maxRange > 0) {
                    double dist = mc.thePlayer.getDistance(
                            pos.getX() + 0.5,
                            pos.getY(),
                            pos.getZ() + 0.5);
                    if (dist > maxRange)
                        continue;
                }
                drawPlate(pos, i);
            }
        }
    }

    @SuppressWarnings({ "unchecked", "unused" })
    private void refreshBedPositions() {
        float maxRange = range;
        boolean firstOnly = firstBedOnly;

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
        int maxLayers = (int) layers;

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
        float plateY = yShift;

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
        double centerY = footPos.getY() + plateY + 1;

        double distance = mc.thePlayer.getDistance(
                centerX, footPos.getY(), centerZ);

        float maxDist = range;
        int scaleMode = autoScale.ordinal();
        float scaleMultiplier = billboardScale;
        double billboardScale = BedBillboardRenderer.DEFAULT_BILLBOARD_SCALE
                * scaleMultiplier;
        double scaleFactor = BedBillboardRenderer.computeScale(
                distance, maxDist, scaleMode, billboardScale);

        if (scaleFactor <= 0)
            return;

        int borderMode = border.ordinal();
        float thickness = borderThickness;
        Color borderColor = (borderMode == 1)
                ? hudUtils.getHudColor(
                        new Color(80, 200, 220))
                : null;

        int size = (int) itemSize;

        int bgMode = background.ordinal();
        Color bgColor = null;
        if (bgMode == BedBillboardRenderer.BG_HUD) {
            bgColor = hudUtils.getHudColor(
                    new Color(80, 200, 220));
        } else if (bgMode == BedBillboardRenderer.BG_CUSTOM) {
            String hex = backgroundColor;
            bgColor = BedBillboardRenderer.parseHexColor(hex);
        }

        BedBillboardRenderer.draw(
                layerMap, centerX, centerY, centerZ,
                scaleFactor, borderColor, thickness,
                size, bgMode, bgColor);
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





