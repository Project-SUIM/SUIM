package coffee.axle.suim.feature.misc;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.property.Setting;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import static coffee.axle.suim.ProjectSUIM.mc;

public class DarkMode extends Feature {


    @Setting(min = 0, max = 100) private float opacity = 20.0f;

    public DarkMode() {
        super("DarkMode", GuiCategory.MISC);
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text e) {
        if (!isEnabled() || mc.theWorld == null || mc.thePlayer == null) return;

        if (opacity <= 0.0f) return;

        ScaledResolution sr = new ScaledResolution(mc);
        int color = ((int) (opacity / 100.0f * 255.0f)) << 24;

        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0f, 0.0f, 400.0f);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Gui.drawRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(), color);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
