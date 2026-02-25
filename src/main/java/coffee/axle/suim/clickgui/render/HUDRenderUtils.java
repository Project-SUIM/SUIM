package coffee.axle.suim.clickgui.render;

import coffee.axle.suim.clickgui.util.ColorUtil;
import coffee.axle.suim.clickgui.util.FontUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static net.minecraftforge.fml.client.config.GuiUtils.drawGradientRect;

/**
 * A Collection of methods for rendering 2D Objects in orthographic projection
 * for the HUD or for a gui.
 *
 * <h3>Coordinate space</h3>
 * The coordinate space used by the methods here sees the top left corner of
 * your window as the origin 0,0.
 * The x-axis is pointing towards the right of the screen. and the y-axis is
 * pointing <b>downwards</b>.
 *
 * <p>
 * Heavily based on the rendering for
 * <a href=
 * "https://github.com/Harry282/FunnyMap/blob/master/src/main/kotlin/funnymap/utils/RenderUtils.kt">Funny
 * Map by Harry282</a>.
 * </p>
 *
 * @author Aton
 */
public class HUDRenderUtils {
    /**
     * @deprecated Use static convenience methods where available, e.g.
     *             {@code HUDRenderUtils.rect(...)}.
     */
    @Deprecated
    public static final HUDRenderUtils INSTANCE = new HUDRenderUtils();

    private final Minecraft mc = Minecraft.getMinecraft();
    private static final double CLICK_GUI_SCALE = 2.0;

    private final Tessellator tessellator = Tessellator.getInstance();
    private final WorldRenderer worldRenderer = tessellator.getWorldRenderer();

    private final List<Scissor> scissorList = new ArrayList<>();

    private HUDRenderUtils() {
        scissorList.add(new Scissor(0.0, 0.0, 16000.0, 16000.0, 0));
    }

    public ScaledResolution getSr() {
        return new ScaledResolution(mc);
    }

    public double getScale() {
        return CLICK_GUI_SCALE / getSr().getScaleFactor();
    }

    public int getDisplayWidth() {
        return Display.getDesktopDisplayMode().getWidth();
    }

    public int getDisplayHeight() {
        return Display.getDesktopDisplayMode().getHeight();
    }

    public void renderRect(double x, double y, double w, double h, Color color) {
        if (color.getAlpha() == 0)
            return;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                color.getAlpha() / 255f);

        worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        addQuadVertices(x, y, w, h);
        tessellator.draw();

        GlStateManager.disableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    public void renderRectBorder(double x, double y, double w, double h, double thickness, Color color) {
        if (color.getAlpha() == 0)
            return;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.translate(0f, 0f, 0f);
        GlStateManager.color(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                color.getAlpha() / 255f);

        worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        GlStateManager.shadeModel(GL11.GL_FLAT);

        addQuadVertices(x - thickness, y, thickness, h);
        addQuadVertices(x - thickness, y - thickness, w + thickness * 2, thickness);
        addQuadVertices(x + w, y, thickness, h);
        addQuadVertices(x - thickness, y + h, w + thickness * 2, thickness);

        tessellator.draw();

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
    }

    private void addQuadVertices(double x, double y, double w, double h) {
        worldRenderer.pos(x, y + h, 0.0).endVertex();
        worldRenderer.pos(x + w, y + h, 0.0).endVertex();
        worldRenderer.pos(x + w, y, 0.0).endVertex();
        worldRenderer.pos(x, y, 0.0).endVertex();
    }

    public void drawTexturedModalRect(int x, int y, int width, int height) {
        worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        worldRenderer.pos((double) x, (double) (y + height), 0.0).tex(0.0, 1.0).endVertex();
        worldRenderer.pos((double) (x + width), (double) (y + height), 0.0).tex(1.0, 1.0).endVertex();
        worldRenderer.pos((double) (x + width), (double) y, 0.0).tex(1.0, 0.0).endVertex();
        worldRenderer.pos((double) x, (double) y, 0.0).tex(0.0, 0.0).endVertex();
        tessellator.draw();
    }

    public static class Scissor {
        public final double x;
        public final double y;
        public final double width;
        public final double height;
        public final int context;

        public Scissor(double x, double y, double width, double height, int context) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.context = context;
        }
    }

    public Scissor scissor(double x, double y, double width, double height) {
        double scale = mc.displayHeight / (double) new ScaledResolution(mc).getScaledHeight();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
                (int) (x * scale),
                (int) (mc.displayHeight - (height + y) * scale),
                (int) (width * scale),
                (int) (height * scale));
        Scissor scissor = new Scissor(x, y, width, height, scissorList.size());
        scissorList.add(scissor);
        return scissor;
    }

    public void resetScissor(Scissor scissor) {
        Scissor nextScissor = scissorList.get(scissor.context - 1);
        double scale = mc.displayHeight / (double) new ScaledResolution(mc).getScaledHeight();
        GL11.glScissor(
                (int) (nextScissor.x * scale),
                (int) (nextScissor.y * scale),
                (int) (nextScissor.width * scale),
                (int) (nextScissor.height * scale));
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        scissorList.remove(scissorList.size() - 1);
    }

    /**
     * Sets up a GL scissor test for the specified region of the screen.
     * Uses the same coordinate system as all the rendering methods.
     */
    public void setUpScissorAbsolute(int left, int top, int right, int bottom) {
        setUpScissor(left, top, Math.max(right - left, 0), Math.max(bottom - top, 0));
    }

    /**
     * Sets up a GL scissor test for the specified region of the screen.
     * Uses the same coordinate system as all the rendering methods.
     */
    public void setUpScissor(int x, int y, int width, int height) {
        double scale = mc.displayHeight / (double) new ScaledResolution(mc).getScaledHeight();
        GL11.glScissor(
                (int) (x * scale),
                (int) (mc.displayHeight - (height + y) * scale),
                (int) (width * scale),
                (int) (height * scale));
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
    }

    public void setUpScissor(double x, double y, double width, double height) {
        this.setUpScissor((int) x, (int) y, (int) width, (int) height);
    }

    /**
     * Disables the GL scissor test.
     */
    public void endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void setColor(int color) {
        float a = (color >> 24 & 0xFF) / 255.0f;
        float r = (color >> 16 & 0xFF) / 255.0f;
        float g = (color >> 8 & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        GL11.glColor4f(r, g, b, a);
    }

    public void drawBorderedRect(double x, double y, double width, double height, double thickness, Color colour1,
            Color colour2) {
        renderRect(x, y, width, height, colour1);
        renderRectBorder(x, y, width, height, thickness, colour2);
    }

    public void drawRoundedBorderedRect(double x, double y, double width, double height, double radius,
            double thickness, Color colour1, Color colour2) {
        drawRoundedRect(x, y, width, height, radius, colour1);
        drawRoundedOutline(x, y, width, height, radius, thickness, colour2);
    }

    public void drawRoundedRect(double x, double y, double width, double height, double radius, Color colour) {
        if (colour.getAlpha() == 0)
            return;
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        int color = colour.getRGB();
        double x1 = x;
        double y1 = y;
        double x2 = x1 + width;
        double y2 = y1 + height;
        float f = ((color >> 24) & 0xFF) / 255.0f;
        float f2 = ((color >> 16) & 0xFF) / 255.0f;
        float f3 = ((color >> 8) & 0xFF) / 255.0f;
        float f4 = (color & 0xFF) / 255.0f;

        GL11.glPushAttrib(0);
        GL11.glScaled(0.5, 0.5, 0.5);

        x1 *= 2.0;
        y1 *= 2.0;
        double x2Scaled = x2 * 2.0;
        double y2Scaled = y2 * 2.0;

        GL11.glDisable(3553);
        GL11.glColor4f(f2, f3, f4, f);
        GL11.glEnable(2848);
        GL11.glBegin(9);

        drawArc(x1 + radius, y1 + radius, -radius, -radius, 0, 90, 3);
        drawArc(x1 + radius, y2Scaled - radius, -radius, -radius, 90, 180, 3);
        drawArc(x2Scaled - radius, y2Scaled - radius, radius, radius, 0, 90, 3);
        drawArc(x2Scaled - radius, y1 + radius, radius, radius, 90, 180, 3);

        GL11.glEnd();
        GL11.glEnable(3553);
        GL11.glDisable(2848);
        GL11.glEnable(3553);
        GL11.glScaled(2.0, 2.0, 2.0);
        GL11.glPopAttrib();
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    public void drawRoundedOutline(double x, double y, double width, double height, double radius, double thickness,
            Color colour) {
        if (colour.getAlpha() == 0)
            return;
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        double x2 = x + width;
        double y2 = y + height;
        float f = (colour.getRGB() >> 24 & 0xFF) / 255.0f;
        float f2 = (colour.getRGB() >> 16 & 0xFF) / 255.0f;
        float f3 = (colour.getRGB() >> 8 & 0xFF) / 255.0f;
        float f4 = (colour.getRGB() & 0xFF) / 255.0f;
        GL11.glPushAttrib(0);
        GL11.glScaled(0.5, 0.5, 0.5);
        double x1 = x * 2.0;
        double y1 = y * 2.0;
        x2 *= 2.0;
        y2 *= 2.0;
        GL11.glLineWidth((float) thickness);
        GL11.glDisable(3553);
        GL11.glColor4f(f2, f3, f4, f);
        GL11.glEnable(2848);
        GL11.glBegin(2);

        drawArc(x1 + radius, y1 + radius, -radius, -radius, 0, 90, 3);
        drawArc(x1 + radius, y2 - radius, -radius, -radius, 90, 180, 3);
        drawArc(x2 - radius, y2 - radius, radius, radius, 0, 90, 3);
        drawArc(x2 - radius, y1 + radius, radius, radius, 90, 180, 3);

        GL11.glEnd();
        GL11.glEnable(3553);
        GL11.glDisable(2848);
        GL11.glEnable(3553);
        GL11.glScaled(2.0, 2.0, 2.0);
        GL11.glPopAttrib();
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void drawArc(double centerX, double centerY, double radiusX, double radiusY, int startAngle, int endAngle,
            int step) {
        for (int i = startAngle; i <= endAngle; i += step) {
            double angle = Math.toRadians((double) i);
            GL11.glVertex2d(centerX + Math.sin(angle) * radiusX, centerY + Math.cos(angle) * radiusY);
        }
    }

    public void drawTexturedRect(ResourceLocation resource, double x, double y, double width, double height) {
        drawTexturedRect(resource, x, y, width, height, 0.0, 1.0, 0.0, 1.0, GL11.GL_NEAREST);
    }

    public void drawTexturedRect(ResourceLocation resource, double x, double y, double width, double height,
            double uMin, double uMax, double vMin, double vMax, int filter) {
        mc.getTextureManager().bindTexture(resource);
        drawTexturedRect((float) x, (float) y, (float) width, (float) height, (float) uMin, (float) uMax, (float) vMin,
                (float) vMax, filter);
    }

    public void drawTexturedRect(float x, float y, float width, float height,
            float uMin, float uMax, float vMin, float vMax, int filter) {
        GlStateManager.enableBlend();
        GL14.glBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ONE_MINUS_SRC_ALPHA);

        drawTexturedRectNoBlend(x, y, width, height, uMin, uMax, vMin, vMax, filter);

        GlStateManager.disableBlend();
    }

    private void drawTexturedRectNoBlend(float x, float y, float width, float height,
            float uMin, float uMax, float vMin, float vMax, int filter) {
        GlStateManager.enableTexture2D();

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);

        worldRenderer.begin(7, DefaultVertexFormats.POSITION_TEX);
        worldRenderer.pos((double) x, (double) (y + height), 0.0).tex((double) uMin, (double) vMax).endVertex();
        worldRenderer.pos((double) (x + width), (double) (y + height), 0.0).tex((double) uMax, (double) vMax)
                .endVertex();
        worldRenderer.pos((double) (x + width), (double) y, 0.0).tex((double) uMax, (double) vMin).endVertex();
        worldRenderer.pos((double) x, (double) y, 0.0).tex((double) uMin, (double) vMin).endVertex();
        tessellator.draw();

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    }

    public void drawSBBox(double x, double y, double width, double height, int topRight) {
        drawSBBox(x, y, width, height, topRight, Color.WHITE.getRGB(), Color.black.getRGB(), Color.black.getRGB());
    }

    public void drawSBBox(double x, double y, double width, double height,
            int topRight, int topLeft, int botRight, int botLeft) {
        double x2 = x + width;
        double y2 = y + height;

        float a1 = (topRight >> 24 & 0xFF) / 255.0f;
        float r1 = (topRight >> 16 & 0xFF) / 255.0f;
        float g1 = (topRight >> 8 & 0xFF) / 255.0f;
        float b1 = (topRight & 0xFF) / 255.0f;

        float a2 = (topLeft >> 24 & 0xFF) / 255.0f;
        float r2 = (topLeft >> 16 & 0xFF) / 255.0f;
        float g2 = (topLeft >> 8 & 0xFF) / 255.0f;
        float b2 = (topLeft & 0xFF) / 255.0f;

        float a3 = (botRight >> 24 & 0xFF) / 255.0f;
        float r3 = (botRight >> 16 & 0xFF) / 255.0f;
        float g3 = (botRight >> 8 & 0xFF) / 255.0f;
        float b3 = (botRight & 0xFF) / 255.0f;

        float a4 = (botLeft >> 24 & 0xFF) / 255.0f;
        float r4 = (botLeft >> 16 & 0xFF) / 255.0f;
        float g4 = (botLeft >> 8 & 0xFF) / 255.0f;
        float b4 = (botLeft & 0xFF) / 255.0f;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);

        worldRenderer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);

        // 1st triangle
        worldRenderer.pos(x, y2, 0.0).color(r4, g4, b4, a4).endVertex(); // bot left
        worldRenderer.pos(x2, y2, 0.0).color(r3, g3, b3, a3).endVertex(); // bot right
        worldRenderer.pos(x2, y, 0.0).color(r1, g1, b1, a1).endVertex(); // top right

        // 2nd triangle
        worldRenderer.pos(x, y, 0.0).color(r2, g2, b2, a2).endVertex(); // top left
        worldRenderer.pos(x, y2, 0.0).color(r4, g4, b4, a4).endVertex(); // bot left
        worldRenderer.pos(x2, y, 0.0).color(r1, g1, b1, a1).endVertex(); // top right

        tessellator.draw();

        GL11.glShadeModel(GL11.GL_FLAT);
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    public void drawRoundedSBBox(double x, double y, double width, double height, double radius, int topRight) {
        drawRoundedSBBox(x, y, width, height, radius, topRight, Color.WHITE.getRGB(), Color.BLACK.getRGB(),
                Color.BLACK.getRGB());
    }

    public void drawRoundedSBBox(double x, double y, double width, double height,
            double radius, int topRight, int topLeft, int botRight, int botLeft) {
        double x2 = x + width;
        double y2 = y + height;

        float a1 = (topRight >> 24 & 0xFF) / 255.0f;
        float r1 = (topRight >> 16 & 0xFF) / 255.0f;
        float g1 = (topRight >> 8 & 0xFF) / 255.0f;
        float b1 = (topRight & 0xFF) / 255.0f;

        float a2 = (topLeft >> 24 & 0xFF) / 255.0f;
        float r2 = (topLeft >> 16 & 0xFF) / 255.0f;
        float g2 = (topLeft >> 8 & 0xFF) / 255.0f;
        float b2 = (topLeft & 0xFF) / 255.0f;

        float a3 = (botRight >> 24 & 0xFF) / 255.0f;
        float r3 = (botRight >> 16 & 0xFF) / 255.0f;
        float g3 = (botRight >> 8 & 0xFF) / 255.0f;
        float b3 = (botRight & 0xFF) / 255.0f;

        float a4 = (botLeft >> 24 & 0xFF) / 255.0f;
        float r4 = (botLeft >> 16 & 0xFF) / 255.0f;
        float g4 = (botLeft >> 8 & 0xFF) / 255.0f;
        float b4 = (botLeft & 0xFF) / 255.0f;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GL11.glShadeModel(GL11.GL_SMOOTH);

        drawRoundedRect(x, y, width, height, radius, new Color(r1, g1, b1, a1));
        drawRoundedRect(x, y, width, height, radius, new Color(r2, g2, b2, a2));
        drawRoundedRect(x, y, width, height, radius, new Color(r3, g3, b3, a3));
        drawRoundedRect(x, y, width, height, radius, new Color(r4, g4, b4, a4));

        GL11.glShadeModel(GL11.GL_FLAT);
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    public void drawHueBox(int x, int y, int width, int height) {
        for (int i = 0; i < width; i++) {
            float ratio = (float) i / (float) width;
            int color = Color.HSBtoRGB(ratio, 1.0f, 1.0f);
            Gui.drawRect(x + i, y, x + i + 1, y + height, color);
        }
    }

    public void drawRoundedHueBox(double x, double y, double width, double height, double radius) {
        drawRoundedHueBox(x, y, width, height, radius, false);
    }

    public void drawRoundedHueBox(double x, double y, double width, double height, double radius, boolean vertical) {
        if (vertical) {
            for (int i = 0; i < (int) height; i++) {
                float ratio = (float) i / (float) height;
                int color = Color.HSBtoRGB(ratio, 1.0f, 1.0f);
                renderRect(x, y + i, width, 1.0, new Color(color));
            }
        } else {
            for (int i = 0; i < (int) width; i++) {
                float ratio = (float) i / (float) width;
                int color = Color.HSBtoRGB(ratio, 1.0f, 1.0f);
                renderRect(x + i, y, 1.0, height, new Color(color));
            }
        }
    }

    public void drawItemStackWithText(ItemStack stack, double x, double y) {
        drawItemStackWithText(stack, x, y, null);
    }

    public void drawItemStackWithText(ItemStack stack, double x, double y, String text) {
        if (stack == null)
            return;
        RenderItem itemRender = mc.getRenderItem();

        RenderHelper.enableGUIStandardItemLighting();
        itemRender.zLevel = -145f;
        itemRender.renderItemAndEffectIntoGUI(stack, (int) x, (int) y);
        itemRender.renderItemOverlayIntoGUI(mc.fontRendererObj, stack, (int) x, (int) y, text);
        itemRender.zLevel = 0f;
        RenderHelper.disableStandardItemLighting();
    }

    public void highlight(Slot slot, Color color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0, 0.0, -10.0);
        GlStateManager.disableBlend();
        GlStateManager.disableLighting();
        Gui.drawRect(
                slot.xDisplayPosition,
                slot.yDisplayPosition,
                slot.xDisplayPosition + 16,
                slot.yDisplayPosition + 16,
                color.getRGB());
        GlStateManager.enableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    public void drawPlayerOnScreen(double x, double y, float partialTicks) {
        drawPlayerOnScreen(x, y, partialTicks, 1.0);
    }

    public void drawPlayerOnScreen(double x, double y, float partialTicks, double scale) {
        net.minecraft.entity.EntityLivingBase ent = mc.thePlayer;

        GlStateManager.enableColorMaterial();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 50.0);
        GlStateManager.scale(-scale, scale, scale);
        GlStateManager.rotate(180.0f, 0.0f, 0.0f, 1.0f);
        RenderHelper.enableStandardItemLighting();

        net.minecraft.client.renderer.entity.RenderManager renderManager = mc.getRenderManager();
        renderManager.setRenderShadow(false);

        renderManager.renderEntityWithPosYaw(ent, 0.0, 0.0, 0.0, ent.rotationYaw, partialTicks);
        renderManager.setRenderShadow(true);

        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    public void drawHoveringText(List<String> textLines, int x, int y) {
        drawHoveringText(textLines, x, y, getSr().getScaledWidth(), getSr().getScaledHeight(), -1, true);
    }

    public void drawHoveringText(List<String> textLines, int x, int y, int screenWidth, int screenHeight,
            int maxTextWidth, boolean themed) {
        if (textLines.isEmpty())
            return;
        GlStateManager.pushMatrix();
        GlStateManager.disableRescaleNormal();
        GlStateManager.translate(0.0, 0.0, 300.0);

        int tooltipTextWidth = 0;
        for (String line : textLines) {
            int lineWidth = FontUtil.INSTANCE.getStringWidth(line);
            if (lineWidth > tooltipTextWidth) {
                tooltipTextWidth = lineWidth;
            }
        }

        boolean needsWrap = false;
        int titleLinesCount = 1;
        int tooltipX = x + 12;

        if (tooltipX + tooltipTextWidth + 4 > screenWidth) {
            tooltipX = x - 16 - tooltipTextWidth;
            if (tooltipX < 4) {
                if (x > screenWidth / 2) {
                    tooltipTextWidth = x - 12 - 8;
                } else {
                    tooltipTextWidth = screenWidth - 16 - x;
                }
                needsWrap = true;
            }
        }

        if (maxTextWidth > 0 && tooltipTextWidth > maxTextWidth) {
            tooltipTextWidth = maxTextWidth;
            needsWrap = true;
        }

        List<String> finalTextLines;
        if (needsWrap) {
            int wrappedTooltipWidth = 0;
            List<String> wrappedTextLines = new ArrayList<>();

            for (int i = 0; i < textLines.size(); i++) {
                String textLine = textLines.get(i);
                List<String> wrappedLine = mc.fontRendererObj.listFormattedStringToWidth(textLine, tooltipTextWidth);
                if (i == 0) {
                    titleLinesCount = wrappedLine.size();
                }

                for (String line : wrappedLine) {
                    int lineWidth = FontUtil.INSTANCE.getStringWidth(line);
                    if (lineWidth > wrappedTooltipWidth) {
                        wrappedTooltipWidth = lineWidth;
                    }
                    wrappedTextLines.add(line);
                }
            }

            tooltipTextWidth = wrappedTooltipWidth;

            if (x > screenWidth / 2) {
                tooltipX = x - 16 - tooltipTextWidth;
            } else {
                tooltipX = x + 12;
            }

            finalTextLines = wrappedTextLines;
        } else {
            finalTextLines = textLines;
        }

        int tooltipY = y - 12;
        int tooltipHeight = 8;

        if (finalTextLines.size() > 1) {
            tooltipHeight += (finalTextLines.size() - 1) * 10;
            if (finalTextLines.size() > titleLinesCount) {
                tooltipHeight += 2;
            }
        }

        if (tooltipY + tooltipHeight + 6 > screenHeight) {
            tooltipY = screenHeight - tooltipHeight - 6;
        }

        if (!themed) {
            int backgroundColor = 0xF0100010;
            drawGradientRect(0, tooltipX - 3, tooltipY - 4, tooltipX + tooltipTextWidth + 3, tooltipY - 3,
                    backgroundColor, backgroundColor);
            drawGradientRect(0, tooltipX - 3, tooltipY + tooltipHeight + 3, tooltipX + tooltipTextWidth + 3,
                    tooltipY + tooltipHeight + 4, backgroundColor, backgroundColor);
            drawGradientRect(0, tooltipX - 3, tooltipY - 3, tooltipX + tooltipTextWidth + 3,
                    tooltipY + tooltipHeight + 3, backgroundColor, backgroundColor);
            drawGradientRect(0, tooltipX - 4, tooltipY - 3, tooltipX - 3, tooltipY + tooltipHeight + 3, backgroundColor,
                    backgroundColor);
            drawGradientRect(0, tooltipX + tooltipTextWidth + 3, tooltipY - 3, tooltipX + tooltipTextWidth + 4,
                    tooltipY + tooltipHeight + 3, backgroundColor, backgroundColor);

            int borderColorStart = 0x505000FF;
            int borderColorEnd = (borderColorStart & 0xFEFEFE) >> 1 | (borderColorStart & 0xFF000000);
            drawGradientRect(0, tooltipX - 3, tooltipY - 3 + 1, tooltipX - 3 + 1, tooltipY + tooltipHeight + 3 - 1,
                    borderColorStart, borderColorEnd);
            drawGradientRect(0, tooltipX + tooltipTextWidth + 2, tooltipY - 3 + 1, tooltipX + tooltipTextWidth + 3,
                    tooltipY + tooltipHeight + 3 - 1, borderColorStart, borderColorEnd);
            drawGradientRect(0, tooltipX - 3, tooltipY - 3, tooltipX + tooltipTextWidth + 3, tooltipY - 3 + 1,
                    borderColorStart, borderColorStart);
            drawGradientRect(0, tooltipX - 3, tooltipY + tooltipHeight + 2, tooltipX + tooltipTextWidth + 3,
                    tooltipY + tooltipHeight + 3, borderColorEnd, borderColorEnd);
        } else {
            Color buttonColor = ColorUtil.INSTANCE.getButtonColor();
            Color withAlpha = new Color(buttonColor.getRed(), buttonColor.getGreen(), buttonColor.getBlue(),
                    (int) (0.94f * 255));
            drawRoundedBorderedRect(tooltipX - 3.0, tooltipY - 3.0, tooltipTextWidth + 6.0, tooltipHeight + 6.0, 3.0,
                    1.0, withAlpha, ColorUtil.INSTANCE.getClickGUIColor());
        }

        for (int lineNumber = 0; lineNumber < finalTextLines.size(); lineNumber++) {
            String line = finalTextLines.get(lineNumber);
            FontUtil.INSTANCE.drawStringWithShadow(line, (double) tooltipX, (double) tooltipY, -1);

            if (lineNumber + 1 == titleLinesCount) {
                tooltipY += 2;
            }

            tooltipY += 10;
        }

        GlStateManager.enableRescaleNormal();
        GlStateManager.popMatrix();
    }

    /** Delegates to {@link #getSr()}. */
    public static ScaledResolution sr() {
        return INSTANCE.getSr();
    }

    /** Delegates to {@link #getScale()}. */
    public static double guiScale() {
        return INSTANCE.getScale();
    }

    /** Delegates to {@link #renderRect(double, double, double, double, Color)}. */
    public static void rect(double x, double y, double w, double h, Color color) {
        INSTANCE.renderRect(x, y, w, h, color);
    }

    /**
     * Delegates to
     * {@link #renderRectBorder(double, double, double, double, double, Color)}.
     */
    public static void rectBorder(double x, double y, double w, double h, double thickness, Color color) {
        INSTANCE.renderRectBorder(x, y, w, h, thickness, color);
    }

    /**
     * Delegates to
     * {@link #drawBorderedRect(double, double, double, double, double, Color, Color)}.
     */
    public static void borderedRect(double x, double y, double w, double h, double thickness, Color c1, Color c2) {
        INSTANCE.drawBorderedRect(x, y, w, h, thickness, c1, c2);
    }

    /**
     * Delegates to
     * {@link #drawRoundedRect(double, double, double, double, double, Color)}.
     */
    public static void roundedRect(double x, double y, double w, double h, double radius, Color color) {
        INSTANCE.drawRoundedRect(x, y, w, h, radius, color);
    }

    /**
     * Delegates to
     * {@link #drawRoundedOutline(double, double, double, double, double, double, Color)}.
     */
    public static void roundedOutline(double x, double y, double w, double h, double radius, double thickness,
            Color color) {
        INSTANCE.drawRoundedOutline(x, y, w, h, radius, thickness, color);
    }

    /**
     * Delegates to
     * {@link #drawRoundedBorderedRect(double, double, double, double, double, double, Color, Color)}.
     */
    public static void roundedBorderedRect(double x, double y, double w, double h, double radius, double thickness,
            Color c1, Color c2) {
        INSTANCE.drawRoundedBorderedRect(x, y, w, h, radius, thickness, c1, c2);
    }

    /** Delegates to {@link #scissor(double, double, double, double)}. */
    public static Scissor beginScissor(double x, double y, double w, double h) {
        return INSTANCE.scissor(x, y, w, h);
    }

    /** Delegates to {@link #resetScissor(Scissor)}. */
    public static void endScissor(Scissor s) {
        INSTANCE.resetScissor(s);
    }

    /** Delegates to {@link #setUpScissor(int, int, int, int)}. */
    public static void setupScissor(int x, int y, int w, int h) {
        INSTANCE.setUpScissor(x, y, w, h);
    }

    /** Delegates to {@link #setUpScissorAbsolute(int, int, int, int)}. */
    public static void setupScissorAbsolute(int left, int top, int right, int bottom) {
        INSTANCE.setUpScissorAbsolute(left, top, right, bottom);
    }

    /** Delegates to instance {@link #endScissor()}. */
    public static void disableScissor() {
        INSTANCE.endScissor();
    }

    /** Delegates to {@link #drawSBBox(double, double, double, double, int)}. */
    public static void sbBox(double x, double y, double w, double h, int topRight) {
        INSTANCE.drawSBBox(x, y, w, h, topRight);
    }

    /**
     * Delegates to
     * {@link #drawSBBox(double, double, double, double, int, int, int, int)}.
     */
    public static void sbBox(double x, double y, double w, double h, int topRight, int topLeft, int botRight,
            int botLeft) {
        INSTANCE.drawSBBox(x, y, w, h, topRight, topLeft, botRight, botLeft);
    }

    /** Delegates to {@link #drawHueBox(int, int, int, int)}. */
    public static void hueBox(int x, int y, int w, int h) {
        INSTANCE.drawHueBox(x, y, w, h);
    }

    /**
     * Delegates to
     * {@link #drawRoundedHueBox(double, double, double, double, double)}.
     */
    public static void roundedHueBox(double x, double y, double w, double h, double radius) {
        INSTANCE.drawRoundedHueBox(x, y, w, h, radius);
    }

    /**
     * Delegates to
     * {@link #drawTexturedRect(ResourceLocation, double, double, double, double)}.
     */
    public static void texturedRect(ResourceLocation resource, double x, double y, double w, double h) {
        INSTANCE.drawTexturedRect(resource, x, y, w, h);
    }

    /** Delegates to {@link #drawItemStackWithText(ItemStack, double, double)}. */
    public static void itemStack(ItemStack stack, double x, double y) {
        INSTANCE.drawItemStackWithText(stack, x, y);
    }

    /**
     * Delegates to
     * {@link #drawItemStackWithText(ItemStack, double, double, String)}.
     */
    public static void itemStack(ItemStack stack, double x, double y, String text) {
        INSTANCE.drawItemStackWithText(stack, x, y, text);
    }

    /** Delegates to {@link #highlight(Slot, Color)}. */
    public static void highlightSlot(Slot slot, Color color) {
        INSTANCE.highlight(slot, color);
    }

    /** Delegates to {@link #drawHoveringText(List, int, int)}. */
    public static void hoveringText(List<String> textLines, int x, int y) {
        INSTANCE.drawHoveringText(textLines, x, y);
    }

    /** Delegates to {@link #drawPlayerOnScreen(double, double, float)}. */
    public static void playerOnScreen(double x, double y, float partialTicks) {
        INSTANCE.drawPlayerOnScreen(x, y, partialTicks);
    }
}
