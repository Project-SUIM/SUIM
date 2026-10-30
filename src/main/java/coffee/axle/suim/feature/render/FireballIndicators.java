package coffee.axle.suim.feature.render;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.property.Setting;
import coffee.axle.suim.util.HudUtils;
import coffee.axle.suim.util.Utils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static coffee.axle.suim.ProjectSUIM.mc;

/**
 * FireballIndicators
 * My friend suggested this one icl, its somewhat useful honestly
 */
public class FireballIndicators extends Feature {


    public enum ColorMode {
        NONE, DEFAULT, HUD
    }

    private static final int MAX_FIREBALLS = 10;
    private static final int MAX_STEPS = 200;
    private static final long FADE_MS = 500;

    private static final long STUCK_MS = 2000;

    @Setting(after = "arrows") private ColorMode lineColor = ColorMode.DEFAULT;

    @Setting(name = "draw-line") private boolean drawLine = true;
    @Setting(name = "line-width", min = 0.1f, max = 3.0f) private float lineWidth = 0.9f;
    @Setting(name = "impact-color") private ColorMode impactColor = ColorMode.DEFAULT;
    @Setting(name = "draw-impact") private boolean drawImpact = true;
    @Setting(name = "impact-opacity", min = 0, max = 100) private int impactOpacity = 40;
    @Setting(name = "impact-outline") private boolean impactOutline = true;
    @Setting(name = "border-color") private ColorMode borderColor = ColorMode.NONE;
    @Setting(name = "render-distance", min = 0, max = 256) private int renderDistance = 64;

    private final Map<EntityFireball, Trail> trails = new LinkedHashMap<>();

    private static class Trail {
        final List<Vec3> points = new ArrayList<>();
        BlockPos impact;
        long lastMoveAt = System.currentTimeMillis();
        long fadeStart;
        long builtTick = -1;
        double lastX, lastY, lastZ;
    }

    public FireballIndicators() {
        super("Indicators:Fireball");
    }

    @Override
    protected String targetModule() {
        return "Indicators";
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        try {
            if (!manager.isModuleEnabled(module)) {
                trails.clear();
                return;
            }
            if (mc.thePlayer == null || mc.theWorld == null) {
                trails.clear();
                return;
            }

            updateTrails(event.partialTicks);

            Color lineTint = colorFor(this.lineColor.ordinal());
            Color impactTint = colorFor(this.impactColor.ordinal());
            Color borderTint = colorFor(this.borderColor.ordinal());

            float width = this.lineWidth;
            int opacityPct = this.impactOpacity;
            boolean wantLine = this.drawLine;
            boolean wantImpact = this.drawImpact;
            boolean wantOutline = this.impactOutline;

            for (Trail trail : trails.values()) {
                float alpha = fadeAlpha(trail);
                if (alpha <= 0.0f) continue;

                if (wantLine && lineTint != null && trail.points.size() > 1) {
                    drawLine(trail.points, lineTint, alpha, width);
                }

                if (wantImpact && impactTint != null && trail.impact != null) {
                    BlockPos p = trail.impact;
                    AxisAlignedBB box = new AxisAlignedBB(p.getX(), p.getY(), p.getZ(),
                            p.getX() + 1, p.getY() + 1, p.getZ() + 1)
                            .offset(-mc.getRenderManager().viewerPosX,
                                    -mc.getRenderManager().viewerPosY,
                                    -mc.getRenderManager().viewerPosZ);
                    float a = opacityPct / 100.0f * alpha;
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    GlStateManager.disableTexture2D();
                    GlStateManager.disableCull();
                    GlStateManager.disableDepth();
                    GL11.glColor4f(impactTint.getRed() / 255.0f,
                            impactTint.getGreen() / 255.0f,
                            impactTint.getBlue() / 255.0f, a);
                    fillBox(box);
                    GlStateManager.enableDepth();
                    GlStateManager.enableTexture2D();
                    GlStateManager.enableCull();
                    GlStateManager.disableBlend();

                    if (wantOutline && borderTint != null) {
                        GlStateManager.enableBlend();
                        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                        GlStateManager.disableTexture2D();
                        GlStateManager.disableDepth();
                        GL11.glLineWidth(1.5f);
                        GL11.glEnable(GL11.GL_LINE_SMOOTH);
                        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
                        RenderGlobal.drawOutlinedBoundingBox(box,
                                borderTint.getRed(), borderTint.getGreen(),
                                borderTint.getBlue(), (int) (255 * alpha));
                        GL11.glDisable(GL11.GL_LINE_SMOOTH);
                        GlStateManager.enableDepth();
                        GlStateManager.enableTexture2D();
                        GlStateManager.disableBlend();
                    }
                }
            }

        } catch (Exception ignored) {
        }
    }

    private void updateTrails(float partialTicks) {
        long now = System.currentTimeMillis();
        long tick = mc.theWorld.getTotalWorldTime();
        int cullDistance = renderDistance;
        double maxDistSq = (double) cullDistance * (double) cullDistance;

        // cull dead / far / stuck / finished fireballs
        Iterator<Map.Entry<EntityFireball, Trail>> it = trails.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<EntityFireball, Trail> entry = it.next();
            EntityFireball fb = entry.getKey();
            Trail trail = entry.getValue();

            boolean fading = trail.fadeStart != 0;
            boolean gone = fb.isDead || !mc.theWorld.loadedEntityList.contains(fb);
            boolean far = mc.thePlayer.getDistanceSq(fb.posX, fb.posY, fb.posZ) > maxDistSq;
            boolean stuck = now - trail.lastMoveAt > STUCK_MS;
            boolean passed = trail.impact != null
                    && sq(fb.posX - (trail.impact.getX() + 0.5)) + sq(fb.posY - (trail.impact.getY() + 0.5))
                    + sq(fb.posZ - (trail.impact.getZ() + 0.5)) < 0.5 * 0.5;

            if (!fading && (gone || stuck || far || passed || (fb.motionX == 0 && fb.motionY == 0 && fb.motionZ == 0))) {
                trail.fadeStart = now;
            }
            if (fading && now - trail.fadeStart > FADE_MS) {
                it.remove();
            } else if (gone && fading) {
                it.remove();
            }
        }

        // pick up new fireballs, cap at 10
        for (Object obj : mc.theWorld.loadedEntityList) {
            if (!(obj instanceof EntityFireball)) continue;
            EntityFireball fb = (EntityFireball) obj;
            if (trails.containsKey(fb)) continue;
            if (trails.size() >= MAX_FIREBALLS) break;
            trails.put(fb, new Trail());
        }

        for (Map.Entry<EntityFireball, Trail> entry : trails.entrySet()) {
            EntityFireball fb = entry.getKey();
            Trail trail = entry.getValue();
            if (tick == trail.builtTick) continue;
            trail.builtTick = tick;
            if (fb.lastTickPosX == fb.posX && fb.lastTickPosY == fb.posY && fb.lastTickPosZ == fb.posZ
                    && !trail.points.isEmpty()) {
                continue;
            }
            if (fb.posX != trail.lastX || fb.posY != trail.lastY || fb.posZ != trail.lastZ) {
                trail.lastMoveAt = now;
                trail.lastX = fb.posX;
                trail.lastY = fb.posY;
                trail.lastZ = fb.posZ;
            }
            simulate(fb, trail, partialTicks, maxDistSq);
        }
    }

    private void simulate(EntityFireball fb, Trail trail, float partialTicks, double maxDistSq) {
        Vec3 origin = Utils.interpolate(fb, partialTicks);
        double x = origin.xCoord;
        double y = origin.yCoord;
        double z = origin.zCoord;
        double mx = fb.motionX, my = fb.motionY, mz = fb.motionZ;
        double ax = fb.accelerationX, ay = fb.accelerationY, az = fb.accelerationZ;

        trail.points.clear();
        trail.impact = null;

        for (int i = 0; i < MAX_STEPS; i++) {
            float f = fb.isInWater() ? 0.8F : 0.95F;
            mx += ax;
            my += ay;
            mz += az;
            mx *= f;
            my *= f;
            mz *= f;

            Vec3 from = new Vec3(x, y, z);
            Vec3 to = new Vec3(x + mx, y + my, z + mz);
            MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(from, to, false, true, false);

            x += mx;
            y += my;
            z += mz;
            trail.points.add(new Vec3(x, y, z));

            if (hit != null) {
                trail.impact = hit.getBlockPos();
                break;
            }
            if (mc.thePlayer.getDistanceSq(x, y, z) > maxDistSq) {
                break;
            }
        }
    }

    private float fadeAlpha(Trail trail) {
        if (trail.fadeStart == 0) return 1.0f;
        float l = (System.currentTimeMillis() - trail.fadeStart) / (float) FADE_MS;
        return Math.max(0.0f, 1.0f - l);
    }

    private Color colorFor(int mode) {
        if (mode == ColorMode.NONE.ordinal()) return null;
        if (mode == ColorMode.HUD.ordinal()) return HudUtils.getInstance().getHudColor(new Color(0xFFFFFF));
        return new Color(0xFFFFFF);
    }

    private static double sq(double d) {
        return d * d;
    }

    private void fillBox(AxisAlignedBB box) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(box.minX, box.minY, box.minZ);
        GL11.glVertex3d(box.maxX, box.minY, box.minZ);
        GL11.glVertex3d(box.maxX, box.minY, box.maxZ);
        GL11.glVertex3d(box.minX, box.minY, box.maxZ);

        GL11.glVertex3d(box.minX, box.maxY, box.minZ);
        GL11.glVertex3d(box.minX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.minZ);

        GL11.glVertex3d(box.minX, box.minY, box.minZ);
        GL11.glVertex3d(box.minX, box.maxY, box.minZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.minZ);
        GL11.glVertex3d(box.maxX, box.minY, box.minZ);

        GL11.glVertex3d(box.minX, box.minY, box.maxZ);
        GL11.glVertex3d(box.maxX, box.minY, box.maxZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.minX, box.maxY, box.maxZ);

        GL11.glVertex3d(box.minX, box.minY, box.minZ);
        GL11.glVertex3d(box.minX, box.minY, box.maxZ);
        GL11.glVertex3d(box.minX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.minX, box.maxY, box.minZ);

        GL11.glVertex3d(box.maxX, box.minY, box.minZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.minZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.maxX, box.minY, box.maxZ);
        GL11.glEnd();
    }

    private void drawLine(List<Vec3> points, Color color, float alpha, float width) {
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GL11.glLineWidth(width);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glColor4f(color.getRed() / 255.0f, color.getGreen() / 255.0f,
                color.getBlue() / 255.0f, alpha);

        GL11.glBegin(GL11.GL_LINE_STRIP);
        double vx = mc.getRenderManager().viewerPosX;
        double vy = mc.getRenderManager().viewerPosY;
        double vz = mc.getRenderManager().viewerPosZ;
        for (Vec3 point : points) {
            GL11.glVertex3d(point.xCoord - vx, point.yCoord - vy, point.zCoord - vz);
        }
        GL11.glEnd();

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
