package coffee.axle.suim.feature.combat;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.rotation.BowAimRotation;
import coffee.axle.suim.rotation.RotationState;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBow;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Breeze 2.3 BowAim 1:1 port. Iterates a simulated target trajectory, solves
 * the bow projectile arc for the first step whose flat travel reaches the
 * target, ray-checks the flight path, then lerps the player's real rotations
 * toward it.
 */
public class BowAim extends Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int MODE_YAW = 0;
    private static final int MODE_PITCH = 1;
    private static final int MODE_BOTH = 2;

    private static final int PRIORITY_DISTANCE = 0;
    private static final int PRIORITY_ANGLE = 1;
    private static final int PRIORITY_HEALTH = 2;

    private static final int TRAJECTORY_TICKS = 40;

    private Object moduleInstance;
    private Object modeProperty;
    private Object priorityProperty;
    private Object rangeProperty;
    private Object fovProperty;
    private Object smoothingProperty;
    private Object horizontalSpeedProperty;
    private Object verticalSpeedProperty;
    private Object predictMsProperty;

    private BowAimRotation rotation;

    @Override
    public String getName() {
        return "BowAim";
    }

    @Override
    public GuiCategory getGuiCategory() {
        return GuiCategory.COMBAT;
    }

    @Override
    public boolean initialize() {
        try {
            moduleInstance = createModule();
            creator.injectModule(moduleInstance, BowAim.class);

            modeProperty = creator.createEnumProperty("mode", MODE_BOTH,
                    new String[] { "YAW", "PITCH", "BOTH" });
            priorityProperty = creator.createEnumProperty("priority", PRIORITY_DISTANCE,
                    new String[] { "DISTANCE", "ANGLE", "HEALTH" });
            rangeProperty = creator.createIntegerProperty("range", 50, 1, 200);
            fovProperty = creator.createIntegerProperty("fov", 50, 0, 360);
            smoothingProperty = creator.createFloatProperty("smoothing", 0.2f, 0.0f, 1.0f);
            horizontalSpeedProperty = creator.createFloatProperty("horizontal-speed", 7.5f, 1.0f, 10.0f);
            verticalSpeedProperty = creator.createFloatProperty("vertical-speed", 7.5f, 1.0f, 10.0f);
            predictMsProperty = creator.createIntegerProperty("predict-ms", 200, 0, 500);

            creator.registerProperties(moduleInstance, modeProperty, priorityProperty,
                    rangeProperty, fovProperty, smoothingProperty,
                    horizontalSpeedProperty, verticalSpeedProperty, predictMsProperty);
            manager.reloadModuleCommand();

            RotationState.getInstance().register();
            rotation = new BowAimRotation(RotationState.getInstance());

            MinecraftForge.EVENT_BUS.register(this);
            manager.registerModuleCallbacks(moduleInstance, () -> {
            }, () -> { });
            return true;
        } catch (Exception e) {
            MyauLogger.error("BowAim:init", e);
            return false;
        }
    }

    private int mode() {
        return properties.getInt(modeProperty, MODE_BOTH);
    }

    @SuppressWarnings("unused")
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!manager.isModuleEnabled(moduleInstance) || !Utils.nullCheck()) {
            return;
        }
        if (mc.currentScreen != null
                || mc.thePlayer.getHeldItem() == null
                || !(mc.thePlayer.getHeldItem().getItem() instanceof ItemBow)
                || !physicalKeyDown(mc.gameSettings.keyBindUseItem.getKeyCode())) {
            return;
        }
        EntityPlayer target = findTarget();
        if (target == null) {
            return;
        }
        Vec3[] trajectory = predictTarget(target);
        float[] angles = solveArc(trajectory);
        if (angles == null) {
            return;
        }

        // breeze lerps the real camera per frame, the myau-style step cap +
        // gcd chain in Rotation replaces its raw min/max lerp
        float[] next = rotation.aim(angles[0], angles[1],
                properties.getFloat(smoothingProperty, 0.2f),
                properties.getFloat(horizontalSpeedProperty, 7.5f),
                properties.getFloat(verticalSpeedProperty, 7.5f));

        if (mode() != MODE_PITCH) {
            float before = mc.thePlayer.rotationYaw;
            mc.thePlayer.rotationYaw = next[0];
            mc.thePlayer.prevRotationYaw += mc.thePlayer.rotationYaw - before;
        }
        if (mode() != MODE_YAW) {
            float before = mc.thePlayer.rotationPitch;
            mc.thePlayer.rotationPitch = next[1];
            mc.thePlayer.prevRotationPitch += mc.thePlayer.rotationPitch - before;
        }
    }

    private boolean physicalKeyDown(int code) {
        if (code < 0) {
            int button = 100 + code;
            return button >= 0 && button < Mouse.getButtonCount() && Mouse.isButtonDown(button);
        }
        return code < Keyboard.getKeyCount() && Keyboard.isKeyDown(code);
    }

    // breeze getDistance_2: fov gate is on the yaw-to-target angle, priority
    // picks the smallest distance/angle/health, filters are myau's own
    private EntityPlayer findTarget() {
        EntityPlayer best = null;
        double bestScore = Double.MAX_VALUE;
        double halfFov = properties.getInt(fovProperty, 50) / 2.0;
        double range = properties.getInt(rangeProperty, 50);
        int priority = properties.getInt(priorityProperty, PRIORITY_DISTANCE);
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.deathTime != 0) continue;
            if (Backtrack.isBot(p) || Backtrack.isFriend(p) || Backtrack.isTeammate(p)) continue;
            if (mc.thePlayer.getDistanceSqToEntity(p) > range * range) continue;
            double dist = mc.thePlayer.getDistanceToEntity(p);
            Vec3 center = boxCenter(p.getEntityBoundingBox()).addVector(0.0, 0.3, 0.0);
            float yawTo = yawTo(center, mc.thePlayer);
            float angleDiff = Math.abs(MathHelper.wrapAngleTo180_float(yawTo - mc.thePlayer.rotationYaw));
            if (angleDiff > halfFov) continue;
            double score;
            if (priority == PRIORITY_ANGLE) {
                score = angleDiff;
            } else if (priority == PRIORITY_HEALTH) {
                score = p.getHealth();
            } else {
                score = dist;
            }
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    private float[] solveArc(Vec3[] trajectory) {
        double step = properties.getInt(predictMsProperty, 200) / 50.0f;
        double drag = Math.log(0.99);
        float velocity = 3.0f;
        double t = 1.0;
        for (float i = (float) step; i < trajectory.length; i += 0.01f) {
            Vec3 point = trajectory[MathHelper.clamp_int(Math.round(i), 0, trajectory.length - 1)];
            double flat = mc.thePlayer.getDistance(point.xCoord, mc.thePlayer.posY, point.zCoord);
            // breeze solves the bow arc analytically, gravity 0.05 per drag tick
            double arc = Math.pow(0.99, t) - 1.0;
            float[] angles = anglesTo(point, mc.thePlayer);
            float yaw = angles[0];
            double eyeY = mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - 0.1f;
            float pitch = (float) Math.toDegrees(
                    Math.asin((5.0 - (point.yCoord - eyeY + 5.0f * t) * (drag / arc)) / velocity));
            if (Float.isInfinite(pitch) || Float.isNaN(pitch)) {
                pitch = angles[1];
            }
            double dx = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)) * velocity;
            double dz = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)) * velocity;
            double vx = -(Math.cos(Math.toDegrees(yaw)) * 0.16f);
            double vz = -(Math.sin(Math.toDegrees(yaw)) * 0.16f);
            double cx = vx + dx * (arc / drag);
            double cz = vz + dz * (arc / drag);
            double travel = Math.sqrt(cx * cx + cz * cz);
            t += 0.01;
            if (flat < travel) {
                Vec3 from = new Vec3(
                        mc.thePlayer.posX + vx,
                        mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - 0.1f,
                        mc.thePlayer.posZ + vz);
                if (!pathClear(MathHelper.clamp_int(Math.round(i), 0, trajectory.length),
                        from, dx, velocity * -Math.sin(Math.toRadians(pitch)), dz, flat)) {
                    return null;
                }
                return new float[] { yaw, pitch };
            }
        }
        return null;
    }

    // breeze rayTrace: march the arrow sim forward, blocked is only a miss if
    // the block is closer than the target
    private boolean pathClear(int ticks, Vec3 from, double dx, double dy, double dz, double targetDist) {
        Vec3 end = from.addVector(dx, dy, dz);
        Vec3 start = from;
        for (int n = 0; n <= ticks; n++) {
            MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(start, end);
            if (hit != null) {
                double fx = end.xCoord - from.xCoord;
                double fz = end.zCoord - from.zCoord;
                return targetDist < Math.sqrt(fx * fx + fz * fz);
            }
            dx *= 0.99;
            dz *= 0.99;
            dy = dy * 0.99 - 0.05;
            start = end;
            end = end.addVector(dx, dy, dz);
        }
        return true;
    }

    // breeze TargetUtil + BlockUtil10.getPredictedHitboxes: simulate the
    // target's movement 40 ticks ahead and keep the box centers
    private Vec3[] predictTarget(EntityPlayer target) {
        Vec3[] points = new Vec3[TRAJECTORY_TICKS + 1];
        AxisAlignedBB box = target.getEntityBoundingBox();
        double motionX = target.posX - target.lastTickPosX;
        double motionY = (target.posY - target.lastTickPosY - 0.08) * 0.98f;
        double motionZ = target.posZ - target.lastTickPosZ;
        boolean ground = target.onGround;
        boolean collidedHorizontally = target.isCollidedHorizontally;
        boolean collidedVertically = target.isCollidedVertically;
        float forward = 0.0f, strafe = 0.0f;
        if (target != mc.thePlayer) {
            // breeze absValue: infer forward/strafe from last motion, deadzone 0.1
            strafe = (float) (motionZ * Math.cos(Math.toRadians(target.rotationYaw))
                    - motionX * Math.sin(Math.toRadians(target.rotationYaw)));
            forward = (float) (motionX * Math.cos(Math.toRadians(target.rotationYaw))
                    + motionZ * Math.sin(Math.toRadians(target.rotationYaw)));
            strafe = Math.abs(strafe) > 0.1 ? Math.signum(strafe) : 0.0f;
            forward = Math.abs(forward) > 0.1 ? Math.signum(forward) : 0.0f;
            if (target.isSneaking()) {
                strafe *= 0.3f;
                forward *= 0.3f;
            }
        } else {
            forward = mc.thePlayer.moveForward;
            strafe = mc.thePlayer.moveStrafing;
        }
        double speedLen = Math.sqrt(motionX * motionX + motionZ * motionZ + motionY * motionY);
        points[0] = boxCenter(box).addVector(0.0, 0.3, 0.0);
        if (speedLen > 5.0) {
            for (int n = 1; n <= TRAJECTORY_TICKS; n++) {
                points[n] = boxCenter(box).addVector(0.0, 0.3, 0.0);
            }
            return points;
        }
        boolean inWater = mc.theWorld.isMaterialInBB(box.expand(-0.1, -0.4, -0.1), net.minecraft.block.material.Material.water);
        boolean inLava = mc.theWorld.isMaterialInBB(box.expand(-0.1, -0.4, -0.1), net.minecraft.block.material.Material.lava);
        for (int n = 0; n < TRAJECTORY_TICKS; n++) {
            if (target.capabilities.isFlying && target.ridingEntity == null) {
                double y = motionY;
                float jump = target.jumpMovementFactor;
                stepGround(box, target, forward, strafe, false, inWater, inLava, motionHolder);
                motionY = y * 0.6;
            } else {
                stepGround(box, target, forward, strafe, ground, inWater, inLava, motionHolder);
            }
            box = motionHolder.box;
            motionX = motionHolder.x;
            motionY = motionHolder.y;
            motionZ = motionHolder.z;
            ground = motionHolder.ground;
            collidedHorizontally = motionHolder.horizontal;
            collidedVertically = motionHolder.vertical;
            points[n + 1] = boxCenter(box).addVector(0.0, 0.3, 0.0);
        }
        return points;
    }

    // breeze's per-tick sim leaks state through statics, we hold it in a cell
    private static final class Motion {
        AxisAlignedBB box;
        double x, y, z;
        boolean ground, horizontal, vertical;
    }

    private final Motion motionHolder = new Motion();

    private void stepGround(AxisAlignedBB box, EntityPlayer target, float forward, float strafe,
            boolean ground, boolean inWater, boolean inLava, Motion out) {
        double motionX = out.x;
        double motionY = out.y;
        double motionZ = out.z;
        if (!inWater && !inLava) {
            float slipperiness = 0.91f;
            if (ground) {
                Vec3 center = boxCenter(box);
                slipperiness = mc.theWorld.getBlockState(
                        new net.minecraft.util.BlockPos(MathHelper.floor_double(center.xCoord),
                                MathHelper.floor_double(box.minY) - 1,
                                MathHelper.floor_double(center.zCoord)))
                        .getBlock().slipperiness * 0.91f;
            }
            float friction = ground ? target.getAIMoveSpeed()
                    * (0.16277136f / (slipperiness * slipperiness * slipperiness))
                    : target.jumpMovementFactor;
            move(motionHolder, forward, strafe, friction, target);
            slipperiness = 0.91f;
            if (ground) {
                Vec3 center = boxCenter(box);
                slipperiness = mc.theWorld.getBlockState(
                        new net.minecraft.util.BlockPos(MathHelper.floor_double(center.xCoord),
                                MathHelper.floor_double(box.minY) - 1,
                                MathHelper.floor_double(center.zCoord)))
                        .getBlock().slipperiness * 0.91f;
            }
            if (target.isOnLadder()) {
                motionX = MathHelper.clamp_double(motionHolder.x, -0.15, 0.15);
                motionZ = MathHelper.clamp_double(motionHolder.z, -0.15, 0.15);
                if (motionY < -0.15) {
                    motionY = -0.15;
                }
                if (target.isSneaking() && motionY < 0.0) {
                    motionY = 0.0;
                }
            }
            move(motionHolder, forward, strafe, friction, target);
            motionY = motionHolder.y;
            if (target.isOnLadder() && motionHolder.horizontal) {
                motionY = 0.2;
            }
            motionY -= 0.08;
            motionY *= 0.98f;
            motionX = motionHolder.x * slipperiness;
            motionZ = motionHolder.z * slipperiness;
            if (!inWater && !inLava) {
                // breeze returns here, the water/lava branch below is dead for us
                out.box = collide(box, target, motionX, motionY, motionZ, out);
                out.x = motionX;
                out.y = motionY;
                out.z = motionZ;
                return;
            }
        }
        double minY = box.minY;
        float drag = 0.8f;
        float accel = 0.02f;
        float depthStrider = net.minecraft.enchantment.EnchantmentHelper.getDepthStriderModifier(target);
        if (depthStrider > 3.0f) {
            depthStrider = 3.0f;
        }
        if (!ground) {
            depthStrider *= 0.5f;
        }
        if (depthStrider > 0.0f) {
            drag += (0.54600006f - drag) * depthStrider / 3.0f;
            accel += (target.getAIMoveSpeed() - accel) * depthStrider / 3.0f;
        }
        move(motionHolder, forward, strafe, accel, target);
        motionX = motionHolder.x;
        motionY = motionHolder.y;
        motionZ = motionHolder.z;
        out.box = collide(box, target, motionX, motionY, motionZ, out);
        out.x = motionHolder.x * drag;
        out.y = motionHolder.y * 0.8f - 0.02;
        out.z = motionHolder.z * drag;
        if (minY != out.box.minY) {
            out.y = motionHolder.y;
        }
    }

    // breeze calculate(): rotate the wanted move into world space, scale to
    // the tick's friction, add it to the running motion
    private void move(Motion m, float forward, float strafe, float friction, EntityPlayer target) {
        float len = forward * forward + strafe * strafe;
        if (len < 1.0E-4f) {
            return;
        }
        len = MathHelper.sqrt_float(len);
        if (len < 1.0f) {
            len = 1.0f;
        }
        float scale = friction / len;
        forward *= scale;
        strafe *= scale;
        float sin = MathHelper.sin(target.rotationYaw * (float) Math.PI / 180.0f);
        float cos = MathHelper.cos(target.rotationYaw * (float) Math.PI / 180.0f);
        m.x += forward * cos - strafe * sin;
        m.z += strafe * cos + forward * sin;
    }

    // breeze getHitbox_2: the vanilla moveEntity flow, web drag + sneak edge
    // clamp + y/x/z collision order + step, then zero motion on collision
    private AxisAlignedBB collide(AxisAlignedBB box, EntityPlayer target,
            double x, double y, double z, Motion out) {
        Vec3 center = boxCenter(box);
        if (mc.theWorld.isMaterialInBB(box.expand(-0.1f, -0.4f, -0.1f), net.minecraft.block.material.Material.web)) {
            x *= 0.25;
            y *= 0.05f;
            z *= 0.25;
            out.x = 0.0;
            out.y = 0.0;
            out.z = 0.0;
        }
        double ox = x, oy = y, oz = z;
        if (out.ground && target.isSneaking()) {
            double edge = 0.05;
            while (x != 0.0 && mc.theWorld.getCollidingBoundingBoxes(target, box.offset(x, -1.0, 0.0)).isEmpty()) {
                if (x < edge && x >= -edge) {
                    x = 0.0;
                } else if (x > 0.0) {
                    x -= edge;
                } else {
                    x += edge;
                }
                ox = x;
            }
            while (z != 0.0 && mc.theWorld.getCollidingBoundingBoxes(target, box.offset(0.0, -1.0, z)).isEmpty()) {
                if (z < edge && z >= -edge) {
                    z = 0.0;
                } else if (z > 0.0) {
                    z -= edge;
                } else {
                    z += edge;
                }
                oz = z;
            }
            while (x != 0.0 && z != 0.0 && mc.theWorld.getCollidingBoundingBoxes(target, box.offset(x, -1.0, z)).isEmpty()) {
                if (x < edge && x >= -edge) {
                    x = 0.0;
                } else if (x > 0.0) {
                    x -= edge;
                } else {
                    x += edge;
                }
                ox = x;
                if (z < edge && z >= -edge) {
                    z = 0.0;
                } else if (z > 0.0) {
                    z -= edge;
                } else {
                    z += edge;
                }
                oz = z;
            }
        }
        java.util.List<AxisAlignedBB> boxes = mc.theWorld.getCollidingBoundingBoxes(target, box.addCoord(x, y, z));
        AxisAlignedBB original = box;
        for (AxisAlignedBB bb : boxes) {
            y = bb.calculateYOffset(box, y);
        }
        box = box.offset(0.0, y, 0.0);
        boolean landed = out.ground || oy != y && oy < 0.0;
        for (AxisAlignedBB bb : boxes) {
            x = bb.calculateXOffset(box, x);
        }
        box = box.offset(x, 0.0, 0.0);
        for (AxisAlignedBB bb : boxes) {
            z = bb.calculateZOffset(box, z);
        }
        box = box.offset(0.0, 0.0, z);
        if (target.stepHeight > 0.0f && landed && (ox != x || oz != z)) {
            double sx = x, sy = y, sz = z;
            AxisAlignedBB preStep = box;
            box = original;
            y = target.stepHeight;
            boxes = mc.theWorld.getCollidingBoundingBoxes(target, box.addCoord(ox, y, oz));
            AxisAlignedBB yBox = box;
            AxisAlignedBB xzBox = yBox.addCoord(ox, 0.0, oz);
            double yy = y;
            for (AxisAlignedBB bb : boxes) {
                yy = bb.calculateYOffset(xzBox, yy);
            }
            yBox = yBox.offset(0.0, yy, 0.0);
            double xx = ox;
            for (AxisAlignedBB bb : boxes) {
                xx = bb.calculateXOffset(yBox, xx);
            }
            yBox = yBox.offset(xx, 0.0, 0.0);
            double zz = oz;
            for (AxisAlignedBB bb : boxes) {
                zz = bb.calculateZOffset(yBox, zz);
            }
            yBox = yBox.offset(0.0, 0.0, zz);
            AxisAlignedBB second = yBox;
            double sy2 = yy;
            for (AxisAlignedBB bb : boxes) {
                sy2 = bb.calculateYOffset(second, sy2);
            }
            second = second.offset(0.0, sy2, 0.0);
            double sx2 = ox;
            for (AxisAlignedBB bb : boxes) {
                sx2 = bb.calculateXOffset(second, sx2);
            }
            second = second.offset(sx2, 0.0, 0.0);
            double sz2 = oz;
            for (AxisAlignedBB bb : boxes) {
                sz2 = bb.calculateZOffset(second, sz2);
            }
            second = second.offset(0.0, 0.0, sz2);
            double firstSq = sx * sx + sz * sz;
            double secondSq = sx2 * sx2 + sz2 * sz2;
            if (firstSq > secondSq) {
                x = xx;
                z = zz;
                y = -yy;
                box = yBox;
            } else {
                x = sx2;
                z = sz2;
                y = -sy2;
                box = second;
            }
            for (AxisAlignedBB bb : boxes) {
                y = bb.calculateYOffset(box, y);
            }
            box = box.offset(0.0, y, 0.0);
            if (sx * sx + sz * sz >= x * x + z * z) {
                x = sx;
                y = sy;
                z = sz;
                box = preStep;
            }
        }
        out.horizontal = ox != x || oz != z;
        out.vertical = oy != y;
        out.ground = out.vertical && oy < 0.0;
        if (ox != x) {
            out.x = 0.0;
        }
        if (oz != z) {
            out.z = 0.0;
        }
        if (oy != y) {
            out.y = 0.0;
        }
        return box;
    }

    private static Vec3 boxCenter(AxisAlignedBB box) {
        return new Vec3(box.minX + (box.maxX - box.minX) * 0.5,
                box.minY + (box.maxY - box.minY) * 0.5,
                box.minZ + (box.maxZ - box.minZ) * 0.5);
    }

    // breeze RotationUtil7.getAnglesTo: eyes at partialTick 1.0, wrapped delta
    private static float[] anglesTo(Vec3 to, EntityPlayer from) {
        Vec3 eyes = from.getPositionEyes(1.0f);
        double dx = to.xCoord - eyes.xCoord;
        double dy = to.yCoord - eyes.yCoord;
        double dz = to.zCoord - eyes.zCoord;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, flat)));
        return new float[] {
                from.rotationYaw + MathHelper.wrapAngleTo180_float(yaw - from.rotationYaw),
                from.rotationPitch + MathHelper.wrapAngleTo180_float(pitch - from.rotationPitch)
        };
    }

    private static float yawTo(Vec3 to, EntityPlayer from) {
        Vec3 eyes = from.getPositionEyes(1.0f);
        double dx = to.xCoord - eyes.xCoord;
        double dz = to.zCoord - eyes.zCoord;
        return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
    }
}
