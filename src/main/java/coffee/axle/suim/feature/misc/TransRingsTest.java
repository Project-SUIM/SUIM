package coffee.axle.suim.feature.misc;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.Utils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import static coffee.axle.suim.ProjectSUIM.mc;

/**
 * Test module - renders a trans flag (5 horizontal ring layers) 2 blocks in
 * front of the player at their feet.
 * TODO: Add config options for distance, size, colours, and maybe a "pride flag
 * mode" that cycles through different flags each time it's toggled.
 */
public class TransRingsTest extends Feature {


    private static final Color CYAN = new Color(91, 206, 250);
    private static final Color PINK = new Color(245, 169, 184);
    private static final Color WHITE = Color.WHITE;

    private static final float FLAG_WIDTH = 1.0f;
    private static final float FLAG_HEIGHT = 1.5f;
    private static final float DISTANCE = 2.0f;

    private boolean enabled = false;

    public TransRingsTest() {
        super("TransRingsTest", GuiCategory.MISC);
    }

    @Override
    protected void init() throws Exception {
        ArrayList<String> commandNames = new ArrayList<>(Collections.singletonList("transringstest"));
        creator.registerCommand(commandNames, this::handleCommand);

        MyauLogger.info("TransRingsTest initialized");
    }

    @Override
    protected void onEnable() {
        enabled = true;
        MyauLogger.info("TransRingsTest enabled");
    }

    @Override
    protected void onDisable() {
        enabled = false;
        MyauLogger.info("TransRingsTest disabled");
    }

    private void handleCommand(ArrayList<String> args) {
        try {
            if (getModuleInstance() != null) {
                manager.toggleModule(getModuleInstance());
            }
        } catch (Exception e) {
            MyauLogger.error("TransRingsTest:command", e);
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!enabled || mc.thePlayer == null || mc.theWorld == null)
            return;

        // Sync with module state each frame
        enabled = manager.isModuleEnabled(this.getName());
        if (!enabled)
            return;

        float partialTicks = event.partialTicks;

        Entity player = mc.thePlayer;
        @SuppressWarnings("unused")
        Vec3 look = player.getLookVec();

        Vec3 p = Utils.interpolate(player, partialTicks);
        double px = p.xCoord;
        double py = p.yCoord;
        double pz = p.zCoord;

        double yaw = Math.toRadians(player.rotationYaw);
        double dx = -Math.sin(yaw);
        double dz = Math.cos(yaw);

        double cx = px + dx * DISTANCE;
        double cy = py;
        double cz = pz + dz * DISTANCE;

        renderTransFlag(cx, cy, cz, FLAG_WIDTH, FLAG_HEIGHT, partialTicks);
    }

    private void renderTransFlag(double x, double y, double z,
            float width, float height, float partialTicks) {
        drawSquare(x, y + 0.01, z, width, width, CYAN, 4f, false, partialTicks);
        drawSquare(x, y + height * 0.25, z, width, width, PINK, 4f, false, partialTicks);
        drawSquare(x, y + height * 0.5, z, width, width, WHITE, 4f, false, partialTicks);
        drawSquare(x, y + height * 0.75, z, width, width, PINK, 4f, false, partialTicks);
        drawSquare(x, y + height, z, width, width, CYAN, 4f, false, partialTicks);
    }

    private void drawSquare(double x, double y, double z,
            float xWidth, float zWidth,
            Color color, float thickness, boolean phase,
            float partialTicks) {
        double viewX = mc.getRenderManager().viewerPosX;
        double viewY = mc.getRenderManager().viewerPosY;
        double viewZ = mc.getRenderManager().viewerPosZ;

        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glLineWidth(thickness);
        if (phase)
            GlStateManager.disableDepth();
        GlStateManager.disableTexture2D();

        GlStateManager.pushMatrix();
        GlStateManager.translate(-viewX, -viewY, -viewZ);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();

        wr.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION);
        GlStateManager.color(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, color.getAlpha() / 255f);

        double halfX = xWidth / 2.0;
        double halfZ = zWidth / 2.0;

        wr.pos(x + halfX, y, z + halfZ).endVertex();
        wr.pos(x + halfX, y, z - halfZ).endVertex();
        wr.pos(x - halfX, y, z - halfZ).endVertex();
        wr.pos(x - halfX, y, z + halfZ).endVertex();
        wr.pos(x + halfX, y, z + halfZ).endVertex();

        tessellator.draw();

        GlStateManager.popMatrix();
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
    }

    @Override
    public void disable() {
        try {
            enabled = false;
            if (getModuleInstance() != null) {
                manager.setModuleEnabled(getModuleInstance(), false);
            }
        } catch (Exception e) {
            MyauLogger.error("TransRingsTest:disable", e);
        }
    }
}
