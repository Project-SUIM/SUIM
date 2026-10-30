package coffee.axle.suim.feature.render;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.ArrayList;

public class ChestESPExtras extends Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static ChestESPExtras instance;

    private Object chestESP;
    private Object chests;
    private Object color;
    private Object opacity;
    private Object tracers;
    private Object enderChests;
    private Object enderColor;
    private Object enderOpacity;
    private Object enderTracers;
    private Object trappedChests;
    private Object trappedColor;
    private Object trappedOpacity;
    private Object trappedTracers;
    private Object containers;
    private Object containersColor;
    private Object containersOpacity;
    private Object containersTracers;
    private Object tracerOpacity;

    @Override
    public String getName() {
        return "ChestESP:Extras";
    }

    @Override
    public boolean initialize() {
        try {
            chestESP = manager.findModule("ChestESP");
            if (chestESP == null) {
                MyauLogger.log(getName(), "MODULE_NOT_FOUND");
                return false;
            }
            color = manager.findProperty(chestESP, "color");
            opacity = manager.findProperty(chestESP, "opacity");
            tracers = manager.findProperty(chestESP, "tracers");
            rename(color, "chest-color");
            rename(opacity, "chest-opacity");
            rename(tracers, "chest-tracers");
            chests = creator.createBooleanProperty("chests", true);
            enderChests = creator.createBooleanProperty("ender-chests", true);
            enderColor = creator.createColorProperty("ender-chest-color", 0xAA00FF);
            enderOpacity = creator.createIntegerProperty("ender-chest-opacity", 100, 0, 100);
            enderTracers = creator.createBooleanProperty("ender-chest-tracers", false);
            trappedChests = creator.createBooleanProperty("trapped-chests", true);
            trappedColor = creator.createColorProperty("trapped-chest-color", 0xFF5500);
            trappedOpacity = creator.createIntegerProperty("trapped-chest-opacity", 100, 0, 100);
            trappedTracers = creator.createBooleanProperty("trapped-chest-tracers", false);
            containers = creator.createBooleanProperty("containers", false);
            containersColor = creator.createColorProperty("containers-color", 0xAAAAAA);
            containersOpacity = creator.createIntegerProperty("containers-opacity", 100, 0, 100);
            containersTracers = creator.createBooleanProperty("containers-tracers", false);

            creator.injectPropertyAfter(chestESP, chests, "chest-tracers");
            creator.injectPropertyAfter(chestESP, enderChests, "chests");
            creator.injectPropertyAfter(chestESP, enderColor, "ender-chests");
            creator.injectPropertyAfter(chestESP, enderOpacity, "ender-chest-color");
            creator.injectPropertyAfter(chestESP, enderTracers, "ender-chest-opacity");
            creator.injectPropertyAfter(chestESP, trappedChests, "ender-chest-tracers");
            creator.injectPropertyAfter(chestESP, trappedColor, "trapped-chests");
            creator.injectPropertyAfter(chestESP, trappedOpacity, "trapped-chest-color");
            creator.injectPropertyAfter(chestESP, trappedTracers, "trapped-chest-opacity");
            creator.injectPropertyAfter(chestESP, containers, "trapped-chest-tracers");
            creator.injectPropertyAfter(chestESP, containersColor, "containers");
            creator.injectPropertyAfter(chestESP, containersOpacity, "containers-color");
            creator.injectPropertyAfter(chestESP, containersTracers, "containers-opacity");
            Object tracersModule = manager.findModule("Tracers");
            tracerOpacity = tracersModule == null ? null : manager.findProperty(tracersModule, "opacity");

            instance = this;
            MinecraftForge.EVENT_BUS.register(this);
            return true;
        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    private void rename(Object property, String name) throws Exception {
        Class<?> type = property.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("W");
                field.setAccessible(true);
                field.set(property, name);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException("property name");
    }

    public static boolean shouldRender(TileEntity tile) {
        return instance == null || instance.enabled(tile);
    }

    private boolean enabled(TileEntity tile) {
        if (tile instanceof TileEntityEnderChest) return properties.getBoolean(enderChests, true);
        if (tile instanceof TileEntityChest) {
            return properties.getBoolean(isTrapped(tile) ? trappedChests : chests, true);
        }
        return false;
    }

    public static Color color(TileEntity tile, Color original) {
        if (instance == null) return original;
        if (tile instanceof TileEntityEnderChest) {
            return new Color(instance.properties.getInt(instance.enderColor, 0xAA00FF));
        }
        if (isTrapped(tile)) {
            return new Color(instance.properties.getInt(instance.trappedColor, 0xFF5500));
        }
        return original;
    }

    public static int opacity(TileEntity tile, int original) {
        if (instance == null) return original;
        if (tile instanceof TileEntityEnderChest) {
            return (int) (instance.properties.getInt(instance.enderOpacity, 100) / 100.0F * 255.0F);
        }
        if (isTrapped(tile)) {
            return (int) (instance.properties.getInt(instance.trappedOpacity, 100) / 100.0F * 255.0F);
        }
        return original;
    }

    // zero alpha hides the tracer without touching myau's own branch
    public static float tracerAlpha(TileEntity tile, float original) {
        if (instance == null) return original;
        if (tile instanceof TileEntityEnderChest) {
            return instance.properties.getBoolean(instance.enderTracers, false) ? original : 0.0F;
        }
        if (isTrapped(tile)) {
            return instance.properties.getBoolean(instance.trappedTracers, false) ? original : 0.0F;
        }
        return original;
    }

    private static boolean isTrapped(TileEntity tile) {
        return tile instanceof TileEntityChest
                && mc.theWorld != null
                && mc.theWorld.getBlockState(tile.getPos()).getBlock() == Blocks.trapped_chest;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null || mc.getRenderViewEntity() == null
                || !manager.isModuleEnabled(chestESP)
                || !properties.getBoolean(containers, false)) {
            return;
        }

        Color tint = new Color(properties.getInt(containersColor, 0xAAAAAA));
        float alpha = properties.getInt(containersOpacity, 100) / 100.0F;
        float tracerAlpha = properties.getInt(tracerOpacity, 100) / 100.0F;
        boolean drawTracers = properties.getBoolean(containersTracers, false);
        float r = tint.getRed() / 255.0F;
        float g = tint.getGreen() / 255.0F;
        float b = tint.getBlue() / 255.0F;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-mc.getRenderManager().viewerPosX,
                -mc.getRenderManager().viewerPosY,
                -mc.getRenderManager().viewerPosZ);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glLineWidth(1.5F);

        Vec3 start = drawTracers ? tracerStart(event.partialTicks) : null;

        for (TileEntity tile : new ArrayList<>(mc.theWorld.loadedTileEntityList)) {
            if (!(tile instanceof IInventory) || tile instanceof TileEntityChest
                    || tile instanceof TileEntityEnderChest) {
                continue;
            }
            BlockPos pos = tile.getPos();
            GlStateManager.color(r, g, b, alpha);
            drawBox(new AxisAlignedBB(
                    pos.getX() + 0.0625, pos.getY(), pos.getZ() + 0.0625,
                    pos.getX() + 0.9375, pos.getY() + 0.875, pos.getZ() + 0.9375));

            if (drawTracers) {
                GlStateManager.color(r, g, b, tracerAlpha);
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex3d(start.xCoord, start.yCoord, start.zCoord);
                GL11.glVertex3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                GL11.glEnd();
            }
        }

        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.resetColor();
        GlStateManager.popMatrix();
    }

    private void drawBox(AxisAlignedBB box) {
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(box.minX, box.minY, box.minZ);
        GL11.glVertex3d(box.maxX, box.minY, box.minZ);
        GL11.glVertex3d(box.maxX, box.minY, box.minZ);
        GL11.glVertex3d(box.maxX, box.minY, box.maxZ);
        GL11.glVertex3d(box.maxX, box.minY, box.maxZ);
        GL11.glVertex3d(box.minX, box.minY, box.maxZ);
        GL11.glVertex3d(box.minX, box.minY, box.maxZ);
        GL11.glVertex3d(box.minX, box.minY, box.minZ);
        GL11.glVertex3d(box.minX, box.maxY, box.minZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.minZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.minZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.minX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.minX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.minX, box.maxY, box.minZ);
        GL11.glVertex3d(box.minX, box.minY, box.minZ);
        GL11.glVertex3d(box.minX, box.maxY, box.minZ);
        GL11.glVertex3d(box.maxX, box.minY, box.minZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.minZ);
        GL11.glVertex3d(box.maxX, box.minY, box.maxZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.minX, box.minY, box.maxZ);
        GL11.glVertex3d(box.minX, box.maxY, box.maxZ);
        GL11.glEnd();
    }

    private Vec3 tracerStart(float partialTicks) {
        Entity view = mc.getRenderViewEntity();
        Vec3 direction;
        if (mc.gameSettings.thirdPersonView == 0) {
            direction = new Vec3(0.0, 0.0, 1.0)
                    .rotatePitch((float) -Math.toRadians(Utils.lerp(view.rotationPitch, view.prevRotationPitch, partialTicks)))
                    .rotateYaw((float) -Math.toRadians(Utils.lerp(view.rotationYaw, view.prevRotationYaw, partialTicks)));
        } else {
            direction = new Vec3(0.0, 0.0, 0.0)
                    .rotatePitch((float) -Math.toRadians(Utils.lerp(mc.thePlayer.cameraPitch, mc.thePlayer.prevCameraPitch, partialTicks)))
                    .rotateYaw((float) -Math.toRadians(Utils.lerp(mc.thePlayer.cameraYaw, mc.thePlayer.prevCameraYaw, partialTicks)));
        }
        return new Vec3(
                direction.xCoord + Utils.lerp(view.posX, view.lastTickPosX, partialTicks),
                direction.yCoord + Utils.lerp(view.posY, view.lastTickPosY, partialTicks) + view.getEyeHeight(),
                direction.zCoord + Utils.lerp(view.posZ, view.lastTickPosZ, partialTicks));
    }
}
