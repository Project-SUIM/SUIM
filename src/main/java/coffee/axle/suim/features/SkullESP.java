package coffee.axle.suim.features;

import coffee.axle.suim.hooks.MyauHook;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SkullESP
 * 
 * @maybsomeday
 */
public class SkullESP implements Feature {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private final MyauHook hook = MyauHook.getInstance();

    private Object moduleInstance;
    private Object outlineProperty;
    private Object tracersProperty;
    private Object lineWidthProperty;
    private Object skullTypeProperty;

    private final java.util.List<Vec3> skullList = new CopyOnWriteArrayList<>();
    private final Color skullColor = new Color(255, 0, 255);

    private static final int SKELETON = 0;
    private static final int WITHER_SKELETON = 1;
    private static final int ZOMBIE = 2;
    private static final int PLAYER = 3;
    private static final int CREEPER = 4;

    private int tickCounter = 0;
    private static final int SCAN_INTERVAL = 5;

    @Override
    public String getName() {
        return "SkullESP";
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");
            moduleInstance = hook.createModule(getName());
            hook.injectModule(moduleInstance, getClass());

            outlineProperty = hook.createBooleanProperty("outline", true);
            tracersProperty = hook.createBooleanProperty("tracers", true);
            lineWidthProperty = hook.createFloatProperty("line-width", 2.0f, 1.0f, 5.0f);

            String[] skullTypes = { "SKELETON", "WITHER-SKELETON", "ZOMBIE", "PLAYER", "CREEPER" };
            skullTypeProperty = hook.createEnumProperty("type", 3, skullTypes);

            hook.registerProperties(moduleInstance, outlineProperty, tracersProperty,
                    lineWidthProperty, skullTypeProperty);

            hook.registerModuleCallbacks(moduleInstance, this::onEnable, this::onDisable);

            hook.reloadModuleCommand();

            MinecraftForge.EVENT_BUS.register(this);

            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;

        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    private void onEnable() {
        skullList.clear();
        tickCounter = 0;
    }

    private void onDisable() {
        skullList.clear();
    }

    @SubscribeEvent
    public void onWorldJoin(net.minecraftforge.event.entity.EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) {
            skullList.clear();
            tickCounter = 0;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START)
            return;
        if (!hook.isModuleEnabled(moduleInstance))
            return;
        if (mc.thePlayer == null || mc.theWorld == null)
            return;

        tickCounter++;

        if (tickCounter % SCAN_INTERVAL == 0) {
            scanForSkulls();
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!hook.isModuleEnabled(moduleInstance))
            return;
        if (mc.thePlayer == null || mc.theWorld == null)
            return;
        if (skullList.isEmpty())
            return;

        boolean renderOutline = getBooleanSafe(outlineProperty, true);
        boolean renderTracers = getBooleanSafe(tracersProperty, true);
        float lineWidth = getFloatSafe(lineWidthProperty, 2.0f);

        Entity viewEntity = mc.getRenderViewEntity();
        if (viewEntity == null)
            return;

        float partialTicks = event.partialTicks;

        double viewX = lerpDouble(viewEntity.posX, viewEntity.lastTickPosX, partialTicks);
        double viewY = lerpDouble(viewEntity.posY, viewEntity.lastTickPosY, partialTicks);
        double viewZ = lerpDouble(viewEntity.posZ, viewEntity.lastTickPosZ, partialTicks);

        GlStateManager.pushMatrix();
        GlStateManager.translate(-viewX, -viewY, -viewZ);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableCull();

        GL11.glLineWidth(lineWidth);

        int color = skullColor.getRGB();
        float r = (color >> 16 & 0xFF) / 255.0f;
        float g = (color >> 8 & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        Vec3 tracerStartPos = getTracerStartPosition(partialTicks);

        for (Vec3 skullPos : skullList) {
            double x = skullPos.xCoord;
            double y = skullPos.yCoord;
            double z = skullPos.zCoord;

            AxisAlignedBB box = new AxisAlignedBB(
                    x, y, z,
                    x + 1, y + 1, z + 1);

            if (renderOutline) {
                renderBlockOutline(box, r, g, b, 1.0f);
            }

            if (renderTracers) {
                double endX = x + 0.5;
                double endY = y + 0.5;
                double endZ = z + 0.5;

                GL11.glBegin(GL11.GL_LINES);
                GL11.glColor4f(r, g, b, 0.8f);
                GL11.glVertex3d(tracerStartPos.xCoord, tracerStartPos.yCoord, tracerStartPos.zCoord);
                GL11.glVertex3d(endX, endY, endZ);
                GL11.glEnd();
            }
        }

        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private Vec3 getTracerStartPosition(float partialTicks) {
        Entity viewEntity = mc.getRenderViewEntity();

        Vec3 position;
        if (mc.gameSettings.thirdPersonView == 0) {
            position = new Vec3(0.0, 0.0, 1.0)
                    .rotatePitch((float) -Math
                            .toRadians(lerpFloat(viewEntity.rotationPitch, viewEntity.prevRotationPitch, partialTicks)))
                    .rotateYaw((float) -Math
                            .toRadians(lerpFloat(viewEntity.rotationYaw, viewEntity.prevRotationYaw, partialTicks)));
        } else {
            position = new Vec3(0.0, 0.0, 0.0)
                    .rotatePitch((float) -Math
                            .toRadians(lerpFloat(mc.thePlayer.cameraPitch, mc.thePlayer.prevCameraPitch, partialTicks)))
                    .rotateYaw((float) -Math
                            .toRadians(lerpFloat(mc.thePlayer.cameraYaw, mc.thePlayer.prevCameraYaw, partialTicks)));
        }

        double playerX = lerpDouble(viewEntity.posX, viewEntity.lastTickPosX, partialTicks);
        double playerY = lerpDouble(viewEntity.posY, viewEntity.lastTickPosY, partialTicks);
        double playerZ = lerpDouble(viewEntity.posZ, viewEntity.lastTickPosZ, partialTicks);

        return new Vec3(
                position.xCoord + playerX,
                position.yCoord + playerY + viewEntity.getEyeHeight(),
                position.zCoord + playerZ);
    }

    private void renderBlockOutline(AxisAlignedBB box, float r, float g, float b, float a) {
        GL11.glBegin(GL11.GL_LINES);
        GL11.glColor4f(r, g, b, a);

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

    private void scanForSkulls() {
        if (mc.theWorld == null || mc.thePlayer == null)
            return;

        skullList.clear();

        int targetSkullType = getIntSafe(skullTypeProperty, PLAYER);

        for (TileEntity tileEntity : new ArrayList<>(mc.theWorld.loadedTileEntityList)) {
            if (!(tileEntity instanceof TileEntitySkull))
                continue;

            TileEntitySkull skull = (TileEntitySkull) tileEntity;

            try {
                int skullType = skull.getSkullType();

                if (skullType == targetSkullType) {
                    BlockPos pos = skull.getPos();
                    Vec3 skullPosition = new Vec3(pos.getX(), pos.getY(), pos.getZ());
                    skullList.add(skullPosition);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private double lerpDouble(double current, double previous, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

    private float lerpFloat(float current, float previous, float partialTicks) {
        return previous + (current - previous) * partialTicks;
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

    private int getIntSafe(Object property, int defaultValue) {
        try {
            return (Integer) hook.getPropertyValue(property);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public void disable() {
        skullList.clear();
    }
}
