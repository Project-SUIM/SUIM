package coffee.axle.suim.clickgui.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;

public class StencilUtils {
    public static final StencilUtils INSTANCE = new StencilUtils();

    private final Minecraft mc = Minecraft.getMinecraft();

    private StencilUtils() {
    }

    public void dispose() {
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GlStateManager.disableAlpha();
        GlStateManager.disableBlend();
    }

    public void erase(boolean invert) {
        erase(invert, 1);
    }

    public void erase(boolean invert, int ref) {
        GL11.glStencilFunc(invert ? GL11.GL_EQUAL : GL11.GL_NOTEQUAL, ref, 65535);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.0f);
    }

    public void write(boolean renderClipLayer) {
        write(renderClipLayer, 1);
    }

    public void write(boolean renderClipLayer, int ref) {
        checkSetupFBO();
        GL11.glClearStencil(0);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilFunc(GL11.GL_ALWAYS, ref, 65535);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        if (!renderClipLayer) {
            GlStateManager.colorMask(false, false, false, false);
        }
    }

    public void write(boolean renderClipLayer, Framebuffer fb) {
        write(renderClipLayer, fb, 1);
    }

    public void write(boolean renderClipLayer, Framebuffer fb, int ref) {
        checkSetupFBO(fb);
        GL11.glClearStencil(0);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilFunc(GL11.GL_ALWAYS, ref, 65535);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        if (!renderClipLayer) {
            GlStateManager.colorMask(false, false, false, false);
        }
    }

    public void checkSetupFBO() {
        Framebuffer fbo = mc.getFramebuffer();
        if (fbo != null && fbo.depthBuffer > -1) {
            setupFBO(fbo);
            fbo.depthBuffer = -1;
        }
    }

    public void checkSetupFBO(Framebuffer fbo) {
        if (fbo != null && fbo.depthBuffer > -1) {
            setupFBO(fbo);
            fbo.depthBuffer = -1;
        }
    }

    public void setupFBO(Framebuffer fbo) {
        EXTFramebufferObject.glDeleteRenderbuffersEXT(fbo.depthBuffer);
        int stencilDepthBufferID = EXTFramebufferObject.glGenRenderbuffersEXT();
        EXTFramebufferObject.glBindRenderbufferEXT(36161, stencilDepthBufferID);
        EXTFramebufferObject.glRenderbufferStorageEXT(36161, 34041, mc.displayWidth, mc.displayHeight);
        EXTFramebufferObject.glFramebufferRenderbufferEXT(36160, 36128, 36161, stencilDepthBufferID);
        EXTFramebufferObject.glFramebufferRenderbufferEXT(36160, 36096, 36161, stencilDepthBufferID);
    }

    public void disable() {
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

    public void enable() {
        GL11.glEnable(GL11.GL_STENCIL_TEST);
    }
}





