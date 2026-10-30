package coffee.axle.suim.feature.combat;

import coffee.axle.suim.events.PostMotionEvent;
import coffee.axle.suim.events.PreMotionEvent;
import coffee.axle.suim.events.PrePlayerInputEvent;
import coffee.axle.suim.events.SendPacketEvent;
import coffee.axle.suim.eventbus.DisplaceHook;
import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.property.Setting;
import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.rotation.RotationState;
import coffee.axle.suim.util.BlinkManager;
import coffee.axle.suim.util.HudUtils;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.PacketUtils;
import coffee.axle.suim.util.Utils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.BlockPos;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Method;
import java.util.Random;
import static coffee.axle.suim.ProjectSUIM.mc;


public class Displace extends Feature {
    // hurt time is 3 btw

    public enum Mode {
        LEFT, RIGHT, RANDOM, VOID, SUIM
    }

    public enum Render {
        NONE, ARROW
    }

    // suim scan: 24 rays every 15 deg, steps of 0.25 out to 4 blocks
    private static final int SUIM_RAYS = 24;
    private static final double SUIM_STEP = 0.25;
    private static final double SUIM_MAX_DIST = 4.0;
    // wall = solid block anywhere in the 2-tall column at the step position
    // void = no solid block in the 10 blocks below the step position
    private static final int SUIM_VOID_DEPTH = 10;

    public static Displace instance;

    @Setting(color = true) private int renderColor = HudUtils.getInstance().getHudColor().getRGB();
    @Setting(min = 10, max = 180) private float angle = 100.0f;
    @Setting(min = 0.2, max = 8.0) private float cooldown = 0.5f;
    @Setting(min = 0.5, max = 3.0) private float arrowSize = 1.0f;
    @Setting private Mode mode = Mode.SUIM;
    @Setting private Render render = Render.ARROW;
    @Setting(name = "killaura") public boolean killAura = true;
    // its like verity but for debugging ....im so fucking brainrotted kms,,,
    @Setting private boolean hideRotations = false;
    @Setting private boolean onlyKnockbackItems = true;
    @Setting private boolean overrideAttacks = true;
    @Setting private boolean playersOnly = false;
    // todo: LINE and BOX modes
    @Setting private boolean manual = true;
    @Setting public boolean autoClicker = true;
    @Setting private boolean debuggity = false;
    private Method applyMovementState;

    private final Random random = new Random();

    // fields incase u needa know #beintheknow #didyouconsent
    public boolean rotate;
    public boolean queued;
    public boolean holding;
    public long lastAt;
    private boolean fromAimAssist;
    private int holdTicks;
    @SuppressWarnings("unused")
    private boolean fromAutoClicker;

    private float flickOffset;
    private float flickYaw, flickPitch;
    private boolean flicking;
    private boolean awaitingRestore;
    private int side = -1;
    private int lastSide = -1;
    private EntityLivingBase lastTarget;

    private int blinkTicks;
    private boolean arrow;
    private long arrowAt;
    private double arrowX, arrowY, arrowZ;
    private String arrowChar = ">";

    public Displace() {
        super("Displace", GuiCategory.COMBAT);
        instance = this;
    }

    @Override
    protected void init() throws Exception {
        applyMovementState = manager.getCachedClass(MyauMappings.CLASS_ROTATION_STATE).getMethod(
                MyauMappings.METHOD_ROTATION_STATE_APPLY,
                boolean.class, float.class, float.class, float.class, int.class);

        RotationState.getInstance().register();
    }

    @SubscribeEvent
    public void onMouse(MouseEvent e) {
        if (e.button != 0 || !e.buttonstate || !Utils.nullCheck()) {
            return;
        }
        if (!isEnabled()) {
            return;
        }

        if (DisplaceHook.isACActive()) {
            return;
        }
        if (!manual) {
            return;
        }
        Entity entity = null;
        boolean aim = false;

        if (mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            entity = mc.objectMouseOver.entityHit;
        }
        if (entity == null) {
            return;
        }
        if (canDisplace(entity, true, true)) {
            e.setCanceled(true);
            trigger(aim ? 1 : 0);
        } else if (overrideAttacks
                && canDisplace(entity, false, false)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPreMotion(PreMotionEvent e) {
        flicking = false;
        if (!isEnabled() || !Utils.nullCheck()) {
            return;
        }
        if (blinkTicks > 0 && --blinkTicks == 0) {
            stopBlink();
        }
        if (holdTicks > 0) {
            holding = --holdTicks > 0;
        }
        if (rotate) {
            startBlink();
            boolean wasDown = mc.gameSettings.keyBindAttack.isKeyDown();
            clickAttack();
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), wasDown);
            fromAutoClicker = false;
            flickOffset = offset();
            flickYaw = mc.thePlayer.rotationYaw + flickOffset;
            flickPitch = mc.thePlayer.rotationPitch;
            try {
                applyMovementState.invoke(null, true, flickYaw, flickPitch, flickYaw, -1);
                flicking = true;
            } catch (Exception ex) {
                MyauLogger.error("Displace:movement", ex);
            }
        }
    }

    @SubscribeEvent
    public void onPostMotion(PostMotionEvent e) {
        rotate = false;
        queued = false;
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (!isEnabled())
            return;
        if (!Utils.nullCheck())
            return;
        Packet<?> packet = e.getPacket();
        if (!(packet instanceof C03PacketPlayer))
            return;
        C03PacketPlayer c03 = (C03PacketPlayer) packet;

        DisplaceHook.watchOut(packet);

        if (rotate) {
            if (fromAimAssist) {
                fromAimAssist = false;
            }
            rotate = false;
            awaitingRestore = true;
            e.setCanceled(true);
            emit(PacketUtils.withRotation(c03, flickYaw, flickPitch));
            RotationState.getInstance().setServerAngles(flickYaw, flickPitch);
            mc.thePlayer.rotationYawHead = flickYaw;
            if (debuggity) {
                MyauLogger.log("DISPLACE", String.format("flick yaw=%.1f offset=%.1f packet=%s",
                        flickYaw, flickOffset, c03.getClass().getSimpleName()));
            }
            return;
        }

        if (c03.getRotating()) {
            awaitingRestore = false;
            return;
        }

        if (awaitingRestore) {
            if (debuggity) {
                MyauLogger.log("DISPLACE", "RESTORE");
            }
            e.setCanceled(true);
            emit(PacketUtils.withRotation(c03, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch));
            RotationState.getInstance().setServerAngles(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
            awaitingRestore = false;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPreInput(PrePlayerInputEvent e) {
        if (!isEnabled() || !flicking)
            return;
        if (!Utils.nullCheck())
            return;
        float forward = e.getForward();
        float strafe = e.getStrafe();
        if (forward == 0.0f && strafe == 0.0f)
            return;
        float target = MathHelper.wrapAngleTo180_float(moveYaw(mc.thePlayer.rotationYaw, forward, strafe));
        float best = -1.0f;
        float bestForward = 0.0f, bestStrafe = 0.0f;
        for (float f = -1.0f; f <= 1.0f; f += 1.0f) {
            for (float s = -1.0f; s <= 1.0f; s += 1.0f) {
                if (f == 0.0f && s == 0.0f) continue;
                float yaw = MathHelper.wrapAngleTo180_float(moveYaw(flickYaw, f, s));
                float diff = Math.abs(MathHelper.wrapAngleTo180_float(target - yaw));
                if (best != -1.0f && !(diff < best)) continue;
                best = diff;
                bestForward = f;
                bestStrafe = s;
            }
        }
        float speed = Math.max(Math.abs(forward), Math.abs(strafe));
        e.setForward(bestForward * speed);
        e.setStrafe(bestStrafe * speed);
    }

    private static float moveYaw(float yaw, float forward, float strafe) {
        float scale = 1.0f;
        if (forward < 0.0f) {
            yaw += 180.0f;
            scale = -0.5f;
        } else if (forward > 0.0f) {
            scale = 0.5f;
        }
        if (strafe > 0.0f) yaw -= 90.0f * scale;
        else if (strafe < 0.0f) yaw += 90.0f * scale;
        return yaw;
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) {
            onDisable();
        }
    }

    @Override
    protected void onDisable() {
        DisplaceHook.reset();
        if (blinkTicks > 0) {
            blinkTicks = 0;
            stopBlink();
        }
        holding = false;
        arrow = false;
        rotate = false;
        flicking = false;
        awaitingRestore = false;
        fromAimAssist = false;
        fromAutoClicker = false;
        queued = false;
        holdTicks = 0;
        lastSide = -1;
        side = -1;
    }

    public void trigger(int source) {
        if (source == 0) {
            rotate = true;
        } else if (source == 1) {
            fromAimAssist = true;
            rotate = true;
        } else if (source == 2) {
            queued = true;
        } else if (source == 3) {
            fromAutoClicker = true;
            rotate = true;
        }
        lastAt = System.currentTimeMillis();
        if (debuggity) {
            MyauLogger.log("DISPLACE", String.format("trigger source=%d", source));
        }
    }

    public void rearm() {
        holdTicks = 3;
    }

    public void clear() {
        lastAt = 0L;
    }

    public float offsetForHook() {
        return offset();
    }

    public boolean canDisplace(Entity entity, boolean checkHurtTime, boolean checkCooldown) {
        if (!(entity instanceof EntityLivingBase)) {
            return false;
        }
        if (checkHurtTime && ((EntityLivingBase) entity).hurtTime > 3) {
            return false;
        }
        if (playersOnly && !(entity instanceof EntityPlayer)) {
            return false;
        }
        int n = EnchantmentHelper.getKnockbackModifier(mc.thePlayer);
        if (!mc.thePlayer.isSprinting() && n == 0) {
            return false;
        }
        if (onlyKnockbackItems && n == 0) {
            return false;
        }
        if (checkCooldown && Math.abs(System.currentTimeMillis() - lastAt)
                < cooldown * 1000.0 - 20.0) {
            return false;
        }
        if (mode == Mode.VOID) {
            side = lastSide = hellishVoidCheckNotRefractoringThisTemp(entity, lastSide);
            if (side == -1) {
                return false;
            }
        }
        lastTarget = (EntityLivingBase) entity;
        return true;
    }

    private float offset() {
        float f = angle;
        if (mode == Mode.SUIM) {
            f = suimOffset();
            if (f != 0.0f) {
                f += random.nextFloat() * 10.0f - 5.0f;
                if (render != Render.NONE && lastTarget != null) {
                    setArrow(lastTarget, f);
                }
            }
            return f;
        }
        if (side != -1) {
            if (side == 0) {
                f = -f;
            }
            side = -1;
        } else if (mode == Mode.LEFT || mode == Mode.RANDOM && random.nextBoolean()) {
            f = -f;
        }
        f += random.nextFloat() * 10.0f - 5.0f;
        if (render != Render.NONE && lastTarget != null) {
            setArrow(lastTarget, f);
        }
        return f;
    }

    // suim mode: 24 rays, find the best void direction by fall depth and distance.
    // returns 0 when no valid direction exists (caller treats as no-displace)
    private float suimOffset() {
        if (lastTarget == null || mc.theWorld == null || mc.thePlayer == null) {
            return 0.0f;
        }
        Entity t = lastTarget;
        int baseY = (int) Math.floor(t.posY);
        double px = t.posX;
        double pz = t.posZ;

        float bestYaw = 0.0f;
        double bestScore = -1.0;

        for (int i = 0; i < SUIM_RAYS; i++) {
            float rayYaw = i * (360.0f / SUIM_RAYS);
            double rad = Math.toRadians(rayYaw);
            double dx = -Math.sin(rad);
            double dz = Math.cos(rad);

            for (double dist = SUIM_STEP; dist <= SUIM_MAX_DIST; dist += SUIM_STEP) {
                int bx = (int) Math.floor(px + dx * dist);
                int bz = (int) Math.floor(pz + dz * dist);

                // wall check: solid block in the 2-tall column blocks this ray
                if (hasCollision(bx, baseY, bz) || hasCollision(bx, baseY + 1, bz)) {
                    break; // this ray is blocked, try next direction
                }

                // void check: how many blocks of air below
                int depth = voidDepth(bx, baseY, bz);
                if (depth >= SUIM_VOID_DEPTH) {
                    // score: deeper void wins, closer void wins
                    double score = (double) depth / (dist * dist);
                    if (score > bestScore) {
                        bestScore = score;
                        float yawDelta = MathHelper.wrapAngleTo180_float(rayYaw - mc.thePlayer.rotationYaw);
                        // clamp the flick to the configured angle, keeping the sign
                        float maxAngle = angle;
                        if (Math.abs(yawDelta) > maxAngle) {
                            yawDelta = Math.signum(yawDelta) * maxAngle;
                        }
                        bestYaw = yawDelta;
                    }
                    break; // found void on this ray, no need to look further
                }
            }
        }
        return bestYaw;
    }

    private boolean hasCollision(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        IBlockState state = mc.theWorld.getBlockState(pos);
        return state.getBlock().getCollisionBoundingBox(mc.theWorld, pos, state) != null;
    }

    private int voidDepth(int x, int y, int z) {
        for (int d = 0; d < SUIM_VOID_DEPTH; d++) {
            if (y - d < 1) {
                return SUIM_VOID_DEPTH; // hit bedrock floor, treat as full void
            }
            if (hasCollision(x, y - d, z)) {
                return d;
            }
        }
        return SUIM_VOID_DEPTH;
    }

    public void startBlink() {
        if (hideRotations) {
            blinkTicks = 2;
            BlinkManager.getInstance().setBlinkState(true, BlinkManager.BlinkModule.DISPLACE);
        }
    }

    private void stopBlink() {
        BlinkManager.getInstance().setBlinkState(false, BlinkManager.BlinkModule.DISPLACE);
    }

    private void clickAttack() {
        int code = mc.gameSettings.keyBindAttack.getKeyCode();
        KeyBinding.setKeyBindState(code, true);
        KeyBinding.onTick(code);
        KeyBinding.setKeyBindState(code, false);
    }

    private void emit(Packet<?> packet) {
        if (packet == null)
            return;
        if (BlinkManager.getInstance().isBlinking(BlinkManager.BlinkModule.DISPLACE)) {
            BlinkManager.getInstance().queueDirect(packet);
        } else {
            PacketUtils.sendPacketNoEvent(packet);
        }
    }


    private int hellishVoidCheckNotRefractoringThisTemp(Entity entity, int previous) {
        double dx = entity.posX - mc.thePlayer.posX;
        double dz = entity.posZ - mc.thePlayer.posZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001) {
            return -1;
        }
        dx /= len;
        dz /= len;
        double px = -dz;
        double pz = dx;
        double y = entity.posY;
        int minusSide = 0;
        int plusSide = 0;
        int behind = 0;
        for (int n4 : new int[] { 1, 2, 3, 4, 6, 8 }) {
            int weight = n4 < 6 ? 2 : 1;
            double bx = entity.posX + dx * (double) n4;
            double bz = entity.posZ + dz * (double) n4;
            for (int i = -1; i <= 1; ++i) {
                double x = bx + px * (double) i;
                double z = bz + pz * (double) i;
                if (mc.theWorld.rayTraceBlocks(new Vec3(x, y, z), new Vec3(x, y - 6.0, z), true) != null)
                    continue;
                behind += weight;
            }
            double mx = entity.posX - px * (double) n4;
            double mz = entity.posZ - pz * (double) n4;
            for (int i = -1; i <= 1; ++i) {
                double x = mx + dx * (double) i;
                double z = mz + dz * (double) i;
                if (mc.theWorld.rayTraceBlocks(new Vec3(x, y, z), new Vec3(x, y - 6.0, z), true) != null)
                    continue;
                minusSide += weight;
            }
            double qx = entity.posX + px * (double) n4;
            double qz = entity.posZ + pz * (double) n4;
            for (int i = -1; i <= 1; ++i) {
                double x = qx + dx * (double) i;
                double z = qz + dz * (double) i;
                if (mc.theWorld.rayTraceBlocks(new Vec3(x, y, z), new Vec3(x, y - 6.0, z), true) != null)
                    continue;
                plusSide += weight;
            }
        }
        if (minusSide == 0 && plusSide == 0) {
            return -1;
        }
        if (behind >= minusSide && behind >= plusSide) {
            return -1;
        }
        if (minusSide == plusSide && previous != -1) {
            return previous;
        }
        return minusSide > plusSide ? 0 : 1;
    }

    private void setArrow(EntityLivingBase entity, float f) {
        double d = Math.toRadians(mc.thePlayer.rotationYaw + f);
        double dirX = -Math.sin(d);
        double dirZ = Math.cos(d);
        arrowX = entity.posX + dirX * 0.8;
        arrowY = entity.posY + 1.2;
        arrowZ = entity.posZ + dirZ * 0.8;
        arrowChar = f >= 0 ? ">" : "<";
        arrowAt = System.currentTimeMillis();
        arrow = true;
        if (debuggity) {
            MyauLogger.log("DISPLACE", String.format("arrow set char=%s mode=%s pos=%.1f,%.1f,%.1f",
                    arrowChar, render, arrowX, arrowY, arrowZ));
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!arrow || render == Render.NONE) {
            return;
        }
        if (!Utils.nullCheck()) {
            return;
        }
        long l = System.currentTimeMillis() - arrowAt;
        if (l > 600L) {
            arrow = false;
            return;
        }

        double x = arrowX - mc.getRenderManager().viewerPosX;
        double y = arrowY - mc.getRenderManager().viewerPosY;
        double z = arrowZ - mc.getRenderManager().viewerPosZ;

        int color = renderColor;
        float size = arrowSize;

        // simple lerp fadeout over the last 150ms
        float alpha = 1.0f;
        if (l > 450L) {
            alpha = 1.0f - (l - 450L) / 150.0f;
            int a = (int) (255 * alpha) << 24;
            color = a | (color & 0xFFFFFF);
        }

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);

        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(180.0f - mc.getRenderManager().playerViewY, 0.0f, 1.0f, 0.0f);
        float scale = size * 0.05f;
        GlStateManager.scale(-scale, -scale, scale);
        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        int textWidth = mc.fontRendererObj.getStringWidth(arrowChar);
        mc.fontRendererObj.drawString(arrowChar, -textWidth / 2,
                -mc.fontRendererObj.FONT_HEIGHT / 2, color);

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
