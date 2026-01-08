package coffee.axle.suim.features;

import coffee.axle.suim.hooks.MyauHook;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * XRAY SpAWNER NAMETAGSSSS
 * 
 * @maybsomeday
 */
public class XraySpawnerNameTags implements Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final MyauHook hook = MyauHook.getInstance();

    private Object xrayModule;
    private Object spawnersProperty;
    private Object spawnersNametagsProperty;
    private Object rangeProperty;
    private Field trackedBlocksField;
    private Field pendingBlocksField;

    private final Map<BlockPos, String> spawnerCache = new HashMap<>();
    private long lastCacheClean = 0;
    private static final long CACHE_CLEAN_INTERVAL = 30000;
    private static final int MAX_CACHE_SIZE = 256;

    private static final float NAMETAG_SCALE = 1.0F;
    private static final int BACKGROUND_OPACITY = 40;

    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 1;

    private boolean initialized = false;
    private boolean failedTrackedBlocksAccess = false;

    @Override
    public String getName() {
        return "Xray:SpawnersNametags";
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");

            xrayModule = hook.findModule("Xray");
            if (xrayModule == null) {
                MyauLogger.log(getName(), "MODULE_NOT_FOUND");
                return false;
            }

            spawnersProperty = hook.findProperty(xrayModule, "spawners");
            if (spawnersProperty == null) {
                MyauLogger.log(getName(), "PROPERTY_NOT_FOUND");
                return false;
            }

            rangeProperty = hook.findProperty(xrayModule, "range");

            spawnersNametagsProperty = hook.createBooleanProperty("spawners-nametags", false);

            if (!hook.injectPropertyAfter(xrayModule, spawnersNametagsProperty, "spawners")) {
                MyauLogger.log(getName(), "PROPERTY_INJECT_FAIL");
                return false;
            }
            try {
                for (Field field : xrayModule.getClass().getDeclaredFields()) {
                    field.setAccessible(true);
                    if (field.getType() == CopyOnWriteArraySet.class) {
                        Object value = field.get(xrayModule);
                        if (value != null) {
                            String fieldName = field.getName();
                            if (fieldName.equals("X")) {
                                trackedBlocksField = field;
                            } else if (fieldName.equals("c")) {
                                pendingBlocksField = field;
                            }
                        }
                    }
                }

                if (trackedBlocksField == null) {
                    failedTrackedBlocksAccess = true;
                }

            } catch (Exception e) {
                failedTrackedBlocksAccess = true;
            }

            initialized = true;

            MinecraftForge.EVENT_BUS.register(this);

            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;

        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!initialized || failedTrackedBlocksAccess) {
            return;
        }

        tickCounter++;
        if (tickCounter % CHECK_INTERVAL != 0) {
            return;
        }

        try {
            if (mc.theWorld == null || mc.thePlayer == null) {
                return;
            }

            if (!hook.isModuleEnabled(xrayModule)) {
                return;
            }

            Boolean spawnersEnabled = safeGetBooleanProperty(spawnersProperty);
            if (!spawnersEnabled) {
                return;
            }

            Boolean nametagsEnabled = safeGetBooleanProperty(spawnersNametagsProperty);
            if (!nametagsEnabled) {
                return;
            }

            Integer range = safeGetIntegerProperty(rangeProperty, 64);

            CopyOnWriteArraySet<BlockPos> trackedBlocks = getTrackedBlocks();
            CopyOnWriteArraySet<BlockPos> pendingBlocks = getPendingBlocks();

            if ((trackedBlocks == null || trackedBlocks.isEmpty()) &&
                    (pendingBlocks == null || pendingBlocks.isEmpty())) {
                return;
            }

            cleanCacheIfNeeded();

            if (trackedBlocks != null) {
                renderSpawnerNametags(trackedBlocks, range);
            }
            if (pendingBlocks != null) {
                renderSpawnerNametags(pendingBlocks, range);
            }

        } catch (Exception ignored) {
        }
    }

    private void renderSpawnerNametags(CopyOnWriteArraySet<BlockPos> blocks, int range) {
        for (BlockPos blockPos : blocks) {
            double distance = mc.thePlayer.getDistance(blockPos.getX(), blockPos.getY(), blockPos.getZ());

            if (distance > range || distance > 512.0) {
                continue;
            }

            String mobType = spawnerCache.get(blockPos);

            if (mobType == null) {
                TileEntity tileEntity = mc.theWorld.getTileEntity(blockPos);

                if (tileEntity instanceof TileEntityMobSpawner) {
                    mobType = getSpawnerMobType((TileEntityMobSpawner) tileEntity);

                    if (mobType != null && !mobType.isEmpty()) {
                        if (spawnerCache.size() < MAX_CACHE_SIZE) {
                            spawnerCache.put(blockPos, mobType);
                        }
                    } else {
                        continue;
                    }
                } else {
                    continue;
                }
            }

            if (mobType != null && !mobType.isEmpty()) {
                renderNameTag(blockPos, mobType, distance);
            }
        }
    }

    private String getSpawnerMobType(TileEntityMobSpawner spawner) {
        try {
            NBTTagCompound spawnerNBT = new NBTTagCompound();
            spawner.writeToNBT(spawnerNBT);

            String mobId = spawnerNBT.getString("EntityId");
            if (mobId == null || mobId.isEmpty()) {
                return null;
            }

            String displayName = mobId.contains(":") ? mobId.split(":")[1] : mobId;

            if (displayName.length() > 0) {
                displayName = displayName.substring(0, 1).toUpperCase() + displayName.substring(1);
            }

            return displayName;

        } catch (Exception e) {
            return null;
        }
    }

    private void renderNameTag(BlockPos blockPos, String text, double distance) {
        try {
            double renderPosX = mc.getRenderManager().viewerPosX;
            double renderPosY = mc.getRenderManager().viewerPosY;
            double renderPosZ = mc.getRenderManager().viewerPosZ;

            double x = blockPos.getX() + 0.5 - renderPosX;
            double y = blockPos.getY() + 1.5 - renderPosY;
            double z = blockPos.getZ() + 0.5 - renderPosZ;

            double baseDistance = Math.min(Math.max(distance, 6.0), 128.0);
            double scale = Math.pow(baseDistance, 0.75) * 0.0075 * NAMETAG_SCALE;

            FontRenderer fontRenderer = mc.fontRendererObj;
            int textWidth = fontRenderer.getStringWidth(text);

            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, z);
            GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);

            float view = mc.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F;
            GlStateManager.rotate(mc.getRenderManager().playerViewX, view, 0.0F, 0.0F);

            GlStateManager.scale(-scale, -scale, scale);

            if (BACKGROUND_OPACITY > 0) {
                Color backgroundColor = new Color(0.0F, 0.0F, 0.0F, (float) BACKGROUND_OPACITY / 100.0F);

                enableRenderState();

                drawRect(
                        (float) (-textWidth) / 2.0F - 1.0F,
                        (float) (-fontRenderer.FONT_HEIGHT) - 1.0F,
                        (float) textWidth / 2.0F + 1.0F,
                        1.0F,
                        backgroundColor.getRGB());

                disableRenderState();
            }

            GlStateManager.disableDepth();
            fontRenderer.drawString(
                    text,
                    (float) (-textWidth) / 2.0F,
                    (float) (-fontRenderer.FONT_HEIGHT),
                    0xFFFFFFFF,
                    true);
            GlStateManager.enableDepth();

            GlStateManager.popMatrix();

        } catch (Exception ignored) {
        }
    }

    private void drawRect(float x1, float y1, float x2, float y2, int color) {
        if (color == 0) {
            return;
        }

        setColor(color);

        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_POLYGON);

        for (int i = 0; i < 2; i++) {
            GL11.glVertex2f(x1, y1);
            GL11.glVertex2f(x1, y2);
            GL11.glVertex2f(x2, y2);
            GL11.glVertex2f(x2, y1);
        }

        GL11.glEnd();
        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);

        GlStateManager.resetColor();
    }

    private void setColor(int color) {
        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        GlStateManager.color(red, green, blue, alpha);
    }

    private void enableRenderState() {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
    }

    private void disableRenderState() {
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @SuppressWarnings("unchecked")
    private CopyOnWriteArraySet<BlockPos> getTrackedBlocks() {
        if (trackedBlocksField == null) {
            return null;
        }

        try {
            return (CopyOnWriteArraySet<BlockPos>) trackedBlocksField.get(xrayModule);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private CopyOnWriteArraySet<BlockPos> getPendingBlocks() {
        if (pendingBlocksField == null) {
            return null;
        }

        try {
            return (CopyOnWriteArraySet<BlockPos>) pendingBlocksField.get(xrayModule);
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean safeGetBooleanProperty(Object property) {
        if (property == null) {
            return false;
        }

        try {
            Object value = hook.getPropertyValue(property);
            return value instanceof Boolean ? (Boolean) value : false;
        } catch (Exception e) {
            return false;
        }
    }

    private Integer safeGetIntegerProperty(Object property, int defaultValue) {
        if (property == null) {
            return defaultValue;
        }

        try {
            Object value = hook.getPropertyValue(property);
            return value instanceof Integer ? (Integer) value : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void cleanCacheIfNeeded() {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastCacheClean > CACHE_CLEAN_INTERVAL) {
            CopyOnWriteArraySet<BlockPos> trackedBlocks = getTrackedBlocks();
            CopyOnWriteArraySet<BlockPos> pendingBlocks = getPendingBlocks();

            spawnerCache.keySet().removeIf(pos -> {
                boolean inTracked = trackedBlocks != null && trackedBlocks.contains(pos);
                boolean inPending = pendingBlocks != null && pendingBlocks.contains(pos);
                return !inTracked && !inPending;
            });

            if (spawnerCache.size() > MAX_CACHE_SIZE) {
                spawnerCache.clear();
            }

            lastCacheClean = currentTime;
        }
    }
}
