package coffee.axle.suim.feature.world.blockin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;

/**
 * HUD overlay renderer for AutoBlockIn progress display.
 */
public final class BlockInRenderer {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private BlockInRenderer() {
    }

    /**
     * Render the blocking progress percentage on screen.
     *
     * @param progress 0.0–1.0 completion fraction
     */
    public static void renderProgress(float progress) {
        if (mc.fontRendererObj == null)
            return;

        String text = String.format("Blocking: %.0f%%", progress * 100.0f);

        GL11.glPushMatrix();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        ScaledResolution sr = new ScaledResolution(mc);
        int width = mc.fontRendererObj.getStringWidth(text);
        Color color = getProgressColor(progress);

        mc.fontRendererObj.drawString(
                text,
                sr.getScaledWidth() / 2.0f - width / 2.0f,
                sr.getScaledHeight() / 5.0f * 2.0f,
                color.getRGB() & 0xFFFFFF | 0xBF000000,
                true);

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GL11.glPopMatrix();
    }

    /**
     * Progress color: red → yellow → green.
     */
    private static Color getProgressColor(float progress) {
        if (progress <= 0.33f)
            return new Color(255, 85, 85);
        if (progress <= 0.66f)
            return new Color(255, 255, 85);
        return new Color(85, 255, 85);
    }
}
