package coffee.axle.suim.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks and renders aim dots at entity hit points.
 * Dots persist for a configurable duration then expire.
 *
 * @author axle.coffee
 */
public class AimDotTracker {
    private static final long DOT_LIFETIME_MS = 3000;
    private static final float DOT_SIZE = 6.0f;

    private static final CopyOnWriteArrayList<AimDot> dots = new CopyOnWriteArrayList<>();

    private static final class AimDot {
        final double x, y, z;
        final long createdAt;

        AimDot(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > DOT_LIFETIME_MS;
        }
    }

    /**
     * Records a hit dot by raycasting from the player's eye along their
     * look vector onto the target entity's bounding box.
     */
    public static void recordHit(Entity target) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || target == null)
            return;

        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 lookVec = mc.thePlayer.getLookVec();
        float reach = 6.0f;
        Vec3 endPos = eyePos.addVector(
                lookVec.xCoord * reach,
                lookVec.yCoord * reach,
                lookVec.zCoord * reach);

        float border = target.getCollisionBorderSize();
        AxisAlignedBB box = target.getEntityBoundingBox().expand(border, border, border);

        MovingObjectPosition hit = box.calculateIntercept(eyePos, endPos);
        if (hit != null) {
            dots.add(new AimDot(hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord));
        } else {
            double cx = (box.minX + box.maxX) / 2.0;
            double cy = (box.minY + box.maxY) / 2.0;
            double cz = (box.minZ + box.maxZ) / 2.0;
            dots.add(new AimDot(cx, cy, cz));
        }
    }

    /** Renders all active dots and prunes expired ones. */
    public static void renderDots(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || dots.isEmpty())
            return;

        dots.removeIf(AimDot::isExpired);

        if (dots.isEmpty())
            return;

        double viewX = mc.getRenderManager().viewerPosX;
        double viewY = mc.getRenderManager().viewerPosY;
        double viewZ = mc.getRenderManager().viewerPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.disableBlend();

        GL11.glPointSize(DOT_SIZE);
        GL11.glEnable(GL11.GL_POINT_SMOOTH);
        GL11.glHint(GL11.GL_POINT_SMOOTH_HINT, GL11.GL_NICEST);

        GL11.glBegin(GL11.GL_POINTS);
        GL11.glColor4f(0.0f, 1.0f, 0.0f, 1.0f);

        for (AimDot dot : dots) {
            GL11.glVertex3d(
                    dot.x - viewX,
                    dot.y - viewY,
                    dot.z - viewZ);
        }

        GL11.glEnd();

        GL11.glDisable(GL11.GL_POINT_SMOOTH);
        GL11.glPointSize(1.0f);

        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    public static void clear() {
        dots.clear();
    }

    public static boolean hasDots() {
        return !dots.isEmpty();
    }
}





