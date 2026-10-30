package coffee.axle.suim.feature.render;

import coffee.axle.suim.feature.Feature;
import static coffee.axle.suim.ProjectSUIM.mc;
import coffee.axle.suim.property.Setting;
import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.util.Utils;
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
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.ArrayList;

public class ChestESPExtras extends Feature {
    private static ChestESPExtras instance;

    private Object chests;
    private Object color;
    private Object opacity;
    private Object tracers;
    @Setting(name = "ender-chests", after = "chests") private boolean enderChests = true;
    @Setting(name = "ender-chest-color", color = true, after = "ender-chests") private int enderColor = 0xAA00FF;
    @Setting(name = "ender-chest-opacity", min = 0, max = 100, after = "ender-chest-color") private int enderOpacity = 100;
    @Setting(name = "ender-chest-tracers", after = "ender-chest-opacity") private boolean enderTracers = false;

    @Setting(name = "trapped-chests", after = "ender-chest-tracers") private boolean trappedChests = true;
    @Setting(name = "trapped-chest-color", color = true, after = "trapped-chests") private int trappedColor = 0xFF5500;
    @Setting(name = "trapped-chest-opacity", min = 0, max = 100, after = "trapped-chest-color") private int trappedOpacity = 100;
    @Setting(name = "trapped-chest-tracers", after = "trapped-chest-opacity") private boolean trappedTracers = false;

    @Setting(name = "containers", after = "trapped-chest-tracers") private boolean containers = false;
    @Setting(name = "containers-color", color = true, after = "containers") private int containersColor = 0xAAAAAA;
    @Setting(name = "containers-opacity", min = 0, max = 100, after = "containers-color") private int containersOpacity = 100;
    @Setting(name = "containers-tracers", after = "containers-opacity") private boolean containersTracers = false;
    private Object tracerOpacity;

    public ChestESPExtras() {
        super("ChestESP:Extras");
    }

    @Override
    protected String targetModule() {
        return "ChestESP";
    }

    @Override
    protected void init() throws Exception {
        color = manager.findProperty(module, "color");
        opacity = manager.findProperty(module, "opacity");
        tracers = manager.findProperty(module, "tracers");
        rename(color, "chest-color");
        rename(opacity, "chest-opacity");
        rename(tracers, "chest-tracers");
        chests = creator.createBooleanProperty("chests", true);
        creator.injectPropertyAfter(module, chests, "chest-tracers");

        Object tracersModule = manager.findModule("Tracers");
        tracerOpacity = tracersModule == null ? null : manager.findProperty(tracersModule, "opacity");

        instance = this;
    }

    private void rename(Object property, String name) throws Exception {
        Class<?> type = property.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(MyauMappings.FIELD_VALUE_NAME);
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
        if (tile instanceof TileEntityEnderChest) return enderChests;
        if (tile instanceof TileEntityChest) {
            if (isTrapped(tile)) return trappedChests;
            try {
                return (Boolean) properties.getPropertyValue(chests);
            } catch (Exception e) {
                return true;
            }
        }
        return false;
    }

    public static Color color(TileEntity tile, Color original) {
        if (instance == null) return original;
        if (tile instanceof TileEntityEnderChest) {
            return new Color(instance.enderColor);
        }
        if (isTrapped(tile)) {
            return new Color(instance.trappedColor);
        }
        return original;
    }

    public static int opacity(TileEntity tile, int original) {
        if (instance == null) return original;
        if (tile instanceof TileEntityEnderChest) {
            return (int) (instance.enderOpacity / 100.0F * 255.0F);
        }
        if (isTrapped(tile)) {
            return (int) (instance.trappedOpacity / 100.0F * 255.0F);
        }
        return original;
    }

    // zero alpha hides the tracer without touching myau's own branch
    public static float tracerAlpha(TileEntity tile, float original) {
        if (instance == null) return original;
        if (tile instanceof TileEntityEnderChest) {
            return instance.enderTracers ? original : 0.0F;
        }
        if (isTrapped(tile)) {
            return instance.trappedTracers ? original : 0.0F;
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
                || !manager.isModuleEnabled(module)
                || !containers) {
            return;
        }

        Color tint = new Color(containersColor);
        float alpha = containersOpacity / 100.0F;
        float tracerAlpha = properties.getInt(tracerOpacity, 100) / 100.0F;
        boolean drawTracers = containersTracers;
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
