package coffee.axle.suim.features;

import coffee.axle.suim.hooks.MyauHook;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.awt.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * BedPlates module
 * 
 * @maybsomeday
 *              Based off of https://github.com/PugrillaDev/Raven-Scripts/
 */
public class Bedplates implements Feature {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private final MyauHook hook = MyauHook.getInstance();
    private Object moduleInstance;

    private Object showCountersProperty;
    private Object scaleProperty;
    private Object renderDistanceProperty;
    private Object yOffsetProperty;
    private Object autoScaleProperty;
    private Object alignTopProperty;

    private final Map<String, BedData> bedPositions = new ConcurrentHashMap<>();
    private final Map<String, Boolean> searchedBlocks = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> stackCache = new ConcurrentHashMap<>();
    private final Set<Integer> yLevels = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final FloatBuffer modelView = GLAllocation.createDirectFloatBuffer(16);
    private final FloatBuffer projection = GLAllocation.createDirectFloatBuffer(16);
    private final IntBuffer viewport = GLAllocation.createDirectIntBuffer(16);

    private static final Set<String> INVALID_BLOCKS = new HashSet<>(Arrays.asList(
            "leaves", "water", "lava", "air", "oak_leaves", "spruce_leaves", "birch_leaves",
            "jungle_leaves", "acacia_leaves", "dark_oak_leaves", "torch", "redstone_torch",
            "wooden_slab", "stone_slab", "fire", "bed", "piston", "sticky_piston", "log",
            "oak_stairs", "spruce_stairs", "birch_stairs", "stone_stairs", "cobblestone_stairs",
            "redstone_wire", "daylight_sensor", "wheat", "carrots", "potatoes", "farmland",
            "oak_door", "spruce_door", "rails", "ladder", "furnace", "chest", "sign",
            "dispenser", "dropper", "hopper", "lever", "snow", "cactus", "sugar_cane",
            "trapdoor", "flower_pot", "skull", "anvil"));

    private static final int BG_COLOR = new Color(37, 37, 43, 200).getRGB();
    private static final int BORDER_COLOR = new Color(52, 54, 59, 200).getRGB();

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

            showCountersProperty = hook.createBooleanProperty("show-counters", true);
            scaleProperty = hook.createFloatProperty("scale", 0.8f, 0.1f, 1.5f);
            renderDistanceProperty = hook.createFloatProperty("render-distance", 150f, 10f, 200f);
            yOffsetProperty = hook.createFloatProperty("y-offset", 1f, -10f, 10f);
            autoScaleProperty = hook.createBooleanProperty("auto-scale", true);
            alignTopProperty = hook.createBooleanProperty("align-top", false);

            hook.registerProperties(moduleInstance, showCountersProperty, scaleProperty,
                    renderDistanceProperty, yOffsetProperty, autoScaleProperty, alignTopProperty);

            hook.reloadModuleCommand();
            MinecraftForge.EVENT_BUS.register(this);

            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;

        } catch (Exception t) {
            MyauLogger.error("FEATURE_FAIL", t);
            return false;
        }
    }

    @SubscribeEvent
    public void onWorldJoin(net.minecraftforge.event.entity.EntityJoinWorldEvent e) {
        if (e.entity != mc.thePlayer)
            return;

        yLevels.clear();
        bedPositions.clear();
        searchedBlocks.clear();

        executor.submit(() -> {
            try {
                Thread.sleep(2000);
                if (mc.theWorld != null && !mc.theWorld.playerEntities.isEmpty()) {
                    for (EntityPlayer player : mc.theWorld.playerEntities) {
                        if (player == null)
                            continue;
                        int y = player.getPosition().getY();
                        for (int i = -5; i <= 5; i++) {
                            yLevels.add(y + i);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END)
            return;
        if (!hook.isModuleEnabled(moduleInstance))
            return;
        if (mc.thePlayer == null || mc.theWorld == null)
            return;

        int ticks = mc.thePlayer.ticksExisted;
        if (ticks % 20 == 0)
            searchForBeds();
        if (ticks % 3 == 0)
            findYLevels();
        if (ticks % 300 == 0)
            searchedBlocks.clear();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START)
            return;
        if (!hook.isModuleEnabled(moduleInstance))
            return;
        if (mc.thePlayer == null || mc.theWorld == null)
            return;

        updateBeds();
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!hook.isModuleEnabled(moduleInstance))
            return;
        if (mc.thePlayer == null || bedPositions.isEmpty())
            return;

        renderBedplates(e.partialTicks);
    }

    private void renderBedplates(float partialTicks) {
        float scale = getFloatSafe(scaleProperty, 0.8f);
        float maxDistance = getFloatSafe(renderDistanceProperty, 150f);
        float yOffset = getFloatSafe(yOffsetProperty, 1f);
        boolean autoScale = getBooleanSafe(autoScaleProperty, true);
        boolean alignTop = getBooleanSafe(alignTopProperty, false);
        boolean showCounters = getBooleanSafe(showCountersProperty, true);

        ScaledResolution sr = new ScaledResolution(mc);
        Entity view = mc.getRenderViewEntity();
        if (view == null)
            return;

        double viewX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        double viewY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        double viewZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;

        modelView.clear();
        projection.clear();
        viewport.clear();

        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);

        float projectionFov = projection.get(0);
        float normalProjectionFov = 1.0f / (float) Math.tan(Math.toRadians(mc.gameSettings.fovSetting) / 2.0f);
        float fovMultiplier = projectionFov / normalProjectionFov;

        List<BedData> visibleBeds = new ArrayList<>();
        for (BedData bedData : bedPositions.values()) {
            if (!bedData.visible || bedData.layers == null || bedData.layers.isEmpty())
                continue;

            double interpolatedDistance = bedData.lastDistance
                    + (bedData.distance - bedData.lastDistance) * partialTicks;
            if (interpolatedDistance > maxDistance)
                continue;

            visibleBeds.add(bedData);
        }

        if (visibleBeds.isEmpty())
            return;

        Map<BedData, Vec3> screenPositions = new HashMap<>();

        for (BedData bedData : visibleBeds) {
            Vec3 bedCenter = new Vec3(
                    (bedData.pos1.xCoord + bedData.pos2.xCoord) / 2.0 + 0.5,
                    bedData.pos1.yCoord + yOffset,
                    (bedData.pos1.zCoord + bedData.pos2.zCoord) / 2.0 + 0.5);

            Vec3 relativePos = new Vec3(
                    bedCenter.xCoord - viewX,
                    bedCenter.yCoord - viewY,
                    bedCenter.zCoord - viewZ);

            Vec3 screenPos = projectToScreen(relativePos, sr);
            if (screenPos != null) {
                screenPositions.put(bedData, screenPos);
            }
        }

        if (screenPositions.isEmpty())
            return;

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();

        mc.entityRenderer.setupOverlayRendering();

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        RenderHelper.enableGUIStandardItemLighting();

        for (Map.Entry<BedData, Vec3> entry : screenPositions.entrySet()) {
            BedData bedData = entry.getKey();
            Vec3 screenPos = entry.getValue();

            double interpolatedDistance = bedData.lastDistance
                    + (bedData.distance - bedData.lastDistance) * partialTicks;

            List<String> layerNames = new ArrayList<>(bedData.layers.keySet());

            float currentScale;
            if (autoScale) {
                float distanceRatio = (float) (interpolatedDistance / maxDistance);
                float scaleFactor = (float) Math.max(0.5f, 1.0f - Math.pow(distanceRatio, 1.5));

                float zoomCompensation = 1.0f;
                if (fovMultiplier > 1.0f) {
                    zoomCompensation = Math.min(1.5f, fovMultiplier * 0.7f);
                }

                currentScale = scale * scaleFactor * zoomCompensation;
                currentScale = Math.max(0.3f, currentScale);
            } else {
                currentScale = scale;
            }

            float itemSize = 16 * currentScale;
            float itemPadding = 2 * currentScale;
            float boxSize = itemSize + itemPadding;
            float rectWidth = layerNames.size() * boxSize;
            float rectHeight = boxSize;

            float startX = (float) screenPos.xCoord - rectWidth / 2f;
            float startY = alignTop ? (float) screenPos.yCoord : (float) screenPos.yCoord - rectHeight;

            Gui.drawRect((int) startX - 2, (int) startY - 2,
                    (int) (startX + rectWidth) + 2, (int) (startY + rectHeight) + 2, BG_COLOR);

            for (int i = 0; i < layerNames.size(); i++) {
                String layer = layerNames.get(i);
                ItemStack stack = getStackFromName(layer);

                float itemX = startX + i * boxSize;
                float itemY = startY;

                Gui.drawRect((int) itemX, (int) itemY, (int) (itemX + boxSize), (int) (itemY + boxSize), BORDER_COLOR);

                GlStateManager.pushMatrix();
                GlStateManager.translate(itemX + itemPadding / 2f, itemY + itemPadding / 2f, 0);
                GlStateManager.scale(currentScale, currentScale, 1);

                mc.getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);

                GlStateManager.popMatrix();

                if (showCounters) {
                    int count = bedData.layers.getOrDefault(layer, 0);
                    if (count > 1) {
                        String txt = String.valueOf(count);
                        GlStateManager.pushMatrix();
                        float textScale = currentScale * 0.6f;
                        GlStateManager.scale(textScale, textScale, 1);
                        mc.fontRendererObj.drawStringWithShadow(txt,
                                (itemX + boxSize - 10) / textScale,
                                (itemY + boxSize - 10) / textScale, 0xFFFFFF);
                        GlStateManager.popMatrix();
                    }
                }
            }
        }

        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }

    private Vec3 projectToScreen(Vec3 relativePos, ScaledResolution sr) {
        FloatBuffer result = GLAllocation.createDirectFloatBuffer(3);

        if (GLU.gluProject((float) relativePos.xCoord, (float) relativePos.yCoord, (float) relativePos.zCoord,
                modelView, projection, viewport, result)) {

            float screenX = result.get(0);
            float screenY = result.get(1);
            float screenZ = result.get(2);

            if (screenZ >= 1.0f || screenZ < 0.0f)
                return null;

            float finalX = (screenX / Display.getWidth()) * sr.getScaledWidth();
            float finalY = ((Display.getHeight() - screenY) / Display.getHeight()) * sr.getScaledHeight();

            if (finalX < -100 || finalX > sr.getScaledWidth() + 100 ||
                    finalY < -100 || finalY > sr.getScaledHeight() + 100) {
                return null;
            }

            return new Vec3(finalX, finalY, screenZ);
        }

        return null;
    }

    private void searchForBeds() {
        if (yLevels.isEmpty() || mc.theWorld == null)
            return;

        List<EntityPlayer> players = new ArrayList<>(mc.theWorld.playerEntities);
        Vec3 myPos = mc.thePlayer.getPositionVector();

        float maxDistance = getFloatSafe(renderDistanceProperty, 150f);
        int searchRadius = (int) Math.min(20, maxDistance / 5);
        executor.submit(() -> {
            try {
                for (EntityPlayer player : players) {
                    if (player == null || player == mc.thePlayer)
                        continue;

                    if (mc.thePlayer.getDistanceToEntity(player) > maxDistance)
                        continue;

                    BlockPos playerPos = player.getPosition();
                    for (int yLevel : yLevels) {
                        for (int x = playerPos.getX() - searchRadius; x <= playerPos.getX() + searchRadius; x++) {
                            for (int z = playerPos.getZ() - searchRadius; z <= playerPos.getZ() + searchRadius; z++) {
                                String blockKey = "1" + x + "," + yLevel + "," + z;
                                if (searchedBlocks.containsKey(blockKey))
                                    continue;

                                BlockPos pos = new BlockPos(x, yLevel, z);
                                Block block = mc.theWorld.getBlockState(pos).getBlock();
                                if (block != Blocks.bed) {
                                    searchedBlocks.put(blockKey, true);
                                    continue;
                                }

                                Block bedXPlus = mc.theWorld.getBlockState(pos.east()).getBlock();
                                Block bedZPlus = mc.theWorld.getBlockState(pos.south()).getBlock();
                                if (bedXPlus == Blocks.bed || bedZPlus == Blocks.bed) {
                                    searchedBlocks.put(blockKey, true);
                                    continue;
                                }

                                Vec3 position1 = new Vec3(x, yLevel, z);
                                Vec3 position2 = position1;

                                Block bedXMinus = mc.theWorld.getBlockState(pos.west()).getBlock();
                                if (bedXMinus == Blocks.bed) {
                                    position2 = new Vec3(x - 1, yLevel, z);
                                } else {
                                    Block bedZMinus = mc.theWorld.getBlockState(pos.north()).getBlock();
                                    if (bedZMinus == Blocks.bed) {
                                        position2 = new Vec3(x, yLevel, z - 1);
                                    }
                                }

                                BedData bedData = new BedData();
                                bedData.visible = true;
                                bedData.distance = myPos.distanceTo(position1);
                                bedData.lastDistance = bedData.distance;
                                bedData.pos1 = position1;
                                bedData.pos2 = position2;
                                bedData.layers = getBedDefenseLayers(position1, position2);
                                bedData.lastCheck = System.currentTimeMillis();

                                bedPositions.put(blockKey, bedData);
                                searchedBlocks.put(blockKey, true);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void findYLevels() {
        if (mc.theWorld == null)
            return;

        executor.submit(() -> {
            try {
                for (EntityPlayer player : mc.theWorld.playerEntities) {
                    if (player == null || player == mc.thePlayer)
                        continue;
                    if (player.swingProgress == 0 || player.getHeldItem() == null)
                        continue;
                    if (!(player.getHeldItem().getItem() instanceof net.minecraft.item.ItemBlock))
                        continue;

                    BlockPos playerPos = player.getPosition();
                    for (int x = playerPos.getX() - 4; x <= playerPos.getX() + 4; x++) {
                        for (int y = playerPos.getY() - 4; y <= playerPos.getY() + 4; y++) {
                            for (int z = playerPos.getZ() - 4; z <= playerPos.getZ() + 4; z++) {
                                String blockKey = "2" + x + "," + y + "," + z;
                                if (searchedBlocks.containsKey(blockKey))
                                    continue;

                                Block block = mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock();
                                if (block == Blocks.bed)
                                    yLevels.add(y);

                                searchedBlocks.put(blockKey, true);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void updateBeds() {
        if (bedPositions.isEmpty() || mc.thePlayer == null)
            return;

        Vec3 pos = mc.thePlayer.getPositionVector();

        try {
            for (BedData bedData : bedPositions.values()) {
                bedData.lastDistance = bedData.distance;
                bedData.distance = pos.distanceTo(bedData.pos1);

                BlockPos bedPos = new BlockPos(bedData.pos1.xCoord, bedData.pos1.yCoord, bedData.pos1.zCoord);
                bedData.visible = (mc.theWorld.getBlockState(bedPos).getBlock() == Blocks.bed);

                if (bedData.visible && System.currentTimeMillis() > bedData.lastCheck + getDelay(bedData.distance)) {
                    final Vec3 p1 = bedData.pos1;
                    final Vec3 p2 = bedData.pos2;
                    executor.submit(() -> {
                        bedData.layers = getBedDefenseLayers(p1, p2);
                        bedData.lastCheck = System.currentTimeMillis();
                    });
                }
            }
        } catch (Exception ignored) {
        }
    }

    private Map<String, Integer> getBedDefenseLayers(Vec3 pos1, Vec3 pos2) {
        boolean facingZ = Math.abs(pos2.zCoord - pos1.zCoord) > Math.abs(pos2.xCoord - pos1.xCoord);
        Vec3[] beds = { pos1, pos2 };
        Map<String, Integer> finalCounts = new HashMap<>();

        for (int layer = 1; layer <= 5; layer++) {
            Map<String, Integer> layerCounts = new HashMap<>();
            int layerTotalBlocks = 0;
            int layerAirBlocks = 0;

            for (int bedPart = 0; bedPart < beds.length; bedPart++) {
                Vec3 bed = beds[bedPart];
                int offset = (bedPart == 0 ? layer : -layer);
                Vec3 startPos = facingZ
                        ? new Vec3(bed.xCoord, bed.yCoord, bed.zCoord + offset)
                        : new Vec3(bed.xCoord + offset, bed.yCoord, bed.zCoord);

                for (int step1 = 0; step1 <= layer; step1++) {
                    int yOff = 0;
                    for (int step2 = step1; step2 >= 0; step2--) {
                        Vec3 p1, p2;
                        if (facingZ) {
                            p1 = new Vec3(startPos.xCoord - step2, startPos.yCoord + yOff,
                                    startPos.zCoord - (bedPart == 0 ? step1 : -step1));
                            p2 = new Vec3(startPos.xCoord + step2, startPos.yCoord + yOff,
                                    startPos.zCoord - (bedPart == 0 ? step1 : -step1));
                        } else {
                            p1 = new Vec3(startPos.xCoord - (bedPart == 0 ? step1 : -step1), startPos.yCoord + yOff,
                                    startPos.zCoord - step2);
                            p2 = new Vec3(startPos.xCoord - (bedPart == 0 ? step1 : -step1), startPos.yCoord + yOff,
                                    startPos.zCoord + step2);
                        }

                        if (addBlockToCount(p1, layerCounts).equals("air"))
                            layerAirBlocks++;
                        layerTotalBlocks++;
                        if (!p1.equals(p2)) {
                            if (addBlockToCount(p2, layerCounts).equals("air"))
                                layerAirBlocks++;
                            layerTotalBlocks++;
                        }
                        if (step2 > 0)
                            yOff++;
                    }
                }
            }

            if (layerTotalBlocks > 0 && ((float) layerAirBlocks / layerTotalBlocks) <= 0.2f) {
                for (Map.Entry<String, Integer> e : layerCounts.entrySet()) {
                    if (!"air".equals(e.getKey()) && ((float) e.getValue() / layerTotalBlocks) >= 0.2f) {
                        finalCounts.merge(e.getKey(), e.getValue(), Integer::sum);
                    }
                }
            }
        }

        return finalCounts;
    }

    private String addBlockToCount(Vec3 pos, Map<String, Integer> blockCounts) {
        BlockPos blockPos = new BlockPos(Math.floor(pos.xCoord), Math.floor(pos.yCoord), Math.floor(pos.zCoord));
        IBlockState state = mc.theWorld.getBlockState(blockPos);
        Block block = state.getBlock();

        String blockName = Block.blockRegistry.getNameForObject(block).toString().replace("minecraft:", "");
        int meta = block.getMetaFromState(state);

        String blockType = blockName + (meta != 0 && !INVALID_BLOCKS.contains(blockName) ? ":" + meta : "");
        blockCounts.put(blockType, blockCounts.getOrDefault(blockType, 0) + 1);
        return blockType;
    }

    private int getDelay(double distance) {
        if (distance > 100)
            return 4000;
        if (distance > 50)
            return 3000;
        if (distance > 25)
            return 2000;
        return 1000;
    }

    private ItemStack getStackFromName(String name) {
        return stackCache.computeIfAbsent(name, key -> {
            try {
                if (name.equals("water"))
                    return new ItemStack(Item.getItemById(326));
                if (name.equals("lava"))
                    return new ItemStack(Item.getItemById(327));
                if (name.equals("fire"))
                    return new ItemStack(Item.getItemById(259));

                String[] parts = name.split(":");
                int meta = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                Item item = Item.getByNameOrId("minecraft:" + parts[0]);
                return new ItemStack(item, 1, meta);
            } catch (Exception e) {
                return new ItemStack(Blocks.barrier);
            }
        });
    }

    private boolean getBooleanSafe(Object property, boolean defaultValue) {
        try {
            return (Boolean) hook.getPropertyValue(property);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private float getFloatSafe(Object property, float defaultValue) {
        try {
            return (Float) hook.getPropertyValue(property);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public void disable() {
        bedPositions.clear();
        searchedBlocks.clear();
        yLevels.clear();
        executor.shutdownNow();
    }

    private static class BedData {
        Vec3 pos1, pos2;
        boolean visible;
        double distance, lastDistance;
        Map<String, Integer> layers;
        long lastCheck;
    }
}
