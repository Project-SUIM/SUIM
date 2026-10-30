package coffee.axle.suim.feature.combat;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.property.Setting;
import coffee.axle.suim.rotation.BowAimbotRotation;
import coffee.axle.suim.rotation.RotationMath;
import coffee.axle.suim.rotation.RotationState;
import coffee.axle.suim.util.HypixelUtils;
import coffee.axle.suim.util.Utils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBow;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import java.util.HashSet;
import java.util.Set;
import static coffee.axle.suim.ProjectSUIM.mc;

public class BowAimbot extends Feature {

    public enum AimMode {
        YAW, PITCH, BOTH
    }

    public enum Priority {
        DISTANCE, ANGLE, HEALTH, FOV
    }

    private static final int TRAJECTORY_TICKS = 40;

    private static final double ARROW_DRAG = 0.99;

    @Setting(min = 1, max = 200) private int range = 50;
    @Setting(min = 0, max = 360) private int fov = 50;
    @Setting(min = 0, max = 1) private float smoothing = 0.2f;
    @Setting(name = "horizontal-speed", min = 1, max = 10) private float horizontalSpeed = 7.5f;
    @Setting(name = "vertical-speed", min = 1, max = 10) private float verticalSpeed = 7.5f;
    @Setting(name = "predict-ms", min = 0, max = 2000) private int predictMs = 1000;
    @Setting(name = "aim-mode") private AimMode aimMode = AimMode.BOTH;
    @Setting(name = "mode") private Priority priority = Priority.DISTANCE;
    @Setting private boolean reachable = true;

    private BowAimbotRotation rotation;

    private final Set<EntityPlayer> skipped = new HashSet<EntityPlayer>();

    private double pointX, pointY, pointZ;
    private double spawnX, spawnY, spawnZ;

    public BowAimbot() {
        super("BowAimbot", GuiCategory.COMBAT);
    }

    @Override
    protected void init() throws Exception {
        RotationState.getInstance().register();
        rotation = new BowAimbotRotation(RotationState.getInstance());
    }

    // how far ahead we are allowed to simulate the target, 0 means aim at where
    // they are right now
    private int maxTicks() {
        int ms = predictMs;
        if (ms <= 0) {
            return 1;
        }
        return Math.min(TRAJECTORY_TICKS, Math.max(1, ms / 50));
    }

    private void setSpawn() {
        double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
        spawnX = mc.thePlayer.posX - Math.cos(yaw) * 0.16;
        spawnY = mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - 0.1;
        spawnZ = mc.thePlayer.posZ - Math.sin(yaw) * 0.16;
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!isEnabled() || !Utils.nullCheck()) {
            return;
        }
        if (mc.currentScreen != null
                || mc.thePlayer.getHeldItem() == null
                || !(mc.thePlayer.getHeldItem().getItem() instanceof ItemBow)
                || !Utils.isKeyDown(mc.gameSettings.keyBindUseItem.getKeyCode())) {
            return;
        }
        EntityPlayer target = findTarget();
        float[] angles = null;
        while (target != null) {
            setSpawn();
            angles = solveArc(predict(target, maxTicks()));
            if (angles != null || !reachable) {
                break;
            }
            skipped.add(target);
            target = findTarget();
        }
        skipped.clear();
        if (angles == null) {
            return;
        }

        float[] next = rotation.aim(angles[0], angles[1],
                smoothing,
                horizontalSpeed,
                verticalSpeed);

        if (aimMode != AimMode.PITCH) {
            float before = mc.thePlayer.rotationYaw;
            mc.thePlayer.rotationYaw = next[0];
            mc.thePlayer.prevRotationYaw += mc.thePlayer.rotationYaw - before;
        }
        if (aimMode != AimMode.YAW) {
            float before = mc.thePlayer.rotationPitch;
            mc.thePlayer.rotationPitch = next[1];
            mc.thePlayer.prevRotationPitch += mc.thePlayer.rotationPitch - before;
        }
    }

    private EntityPlayer findTarget() {
        EntityPlayer best = null;
        double bestScore = Double.MAX_VALUE;
        double halfFov = fov / 2.0;
        double r = range;
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.deathTime != 0) continue;
            if (skipped.contains(p)) continue;
            if (HypixelUtils.isBot(p) || HypixelUtils.isFriend(p) || HypixelUtils.isTeammate(p)) continue;
            if (mc.thePlayer.getDistanceSqToEntity(p) > r * r) continue;
            double dist = mc.thePlayer.getDistanceToEntity(p);
            Vec3 center = Utils.boxCenter(p.getEntityBoundingBox()).addVector(0.0, 0.3, 0.0);
            float[] aim = angy(center, mc.thePlayer);
            float angleDiff = Math.abs(MathHelper.wrapAngleTo180_float(aim[0] - mc.thePlayer.rotationYaw));
            if (angleDiff > halfFov) continue;
            double score;
            if (priority == Priority.FOV) {
                double dp = MathHelper.wrapAngleTo180_float(aim[1] - mc.thePlayer.rotationPitch);
                score = Math.sqrt(angleDiff * angleDiff + dp * dp);
            } else if (priority == Priority.ANGLE) {
                score = angleDiff;
            } else if (priority == Priority.HEALTH) {
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

    // solve the exact flight time. vanilla integrates move-then-drag, so the velocity after k ticks is v0*d^k - g*(1-d^k)/(1-d), and summing that over k gives the reachable displacement for n ticks. required speed falls as n grows up to the flat arc then rises again for the lob, so thefirst crossing is the flat one
    private float[] solveArc(Vec3[] path) {
        // charge only draws at 20 ticks per second, a half drawn bow is slow
        float charge = mc.thePlayer.getItemInUseDuration() / 20.0f;
        charge = MathHelper.clamp_float(charge, 0.0f, 1.0f);
        charge = (charge * charge + charge * 2.0f) / 3.0f;
        double speed = charge * 3.0;
        if (speed < 0.1) {
            return null;
        }
        double limit = Math.min(TRAJECTORY_TICKS, path.length - 1);
        double prevTicks = 1.0;
        double prevError = speedErr(path, prevTicks, speed);

        if (prevError <= 0.0) {
            return aimAt(path, prevTicks);
        }
        for (double n = prevTicks + 1.0; n <= limit; n += 1.0) {
            double error = speedErr(path, n, speed);
            if (error <= 0.0) {
                // bracket found, bisect down to the real crossing
                double lo = prevTicks;
                double hi = n;
                for (int i = 0; i < 8; i++) {
                    double mid = (lo + hi) * 0.5;
                    if (speedErr(path, mid, speed) > 0.0) {
                        lo = mid;
                    } else {
                        hi = mid;
                    }
                }
                return aimAt(path, hi);
            }
            prevTicks = n;
            prevError = error;
        }
        return null;
    }

    private double speedErr(Vec3[] path, double ticks, double speed) {
        atPoint(path, ticks);
        double s = sumDrag(ticks);
        if (s <= 0.0) {
            return Double.MAX_VALUE;
        }
        double vx = pointX / s;
        double vy = (pointY + grav(ticks)) / s;
        double vz = pointZ / s;
        return Math.sqrt(vx * vx + vy * vy + vz * vz) - speed;
    }

    private float[] aimAt(Vec3[] path, double ticks) {
        atPoint(path, ticks);
        double s = sumDrag(ticks);
        double vx = pointX / s;
        double vy = (pointY + grav(ticks)) / s;
        double vz = pointZ / s;
        if (!herpable(vx, vy, vz, (int) Math.ceil(ticks))) {
            return null;
        }
        double flat = Math.sqrt(vx * vx + vz * vz);
        float rawYaw = (float) (Math.atan2(vz, vx) * 180.0 / Math.PI) - 90.0f;
        float yaw = mc.thePlayer.rotationYaw
                + MathHelper.wrapAngleTo180_float(rawYaw - mc.thePlayer.rotationYaw);
        float pitch = (float) (-Math.atan2(vy, flat) * 180.0 / Math.PI);
        return new float[] { yaw, pitch };
    }

    // interpolated target offset from the arrow spawn, written to pointX/Y/Z
    private void atPoint(Vec3[] path, double ticks) {
        double t = MathHelper.clamp_double(ticks, 0.0, path.length - 1);
        int i = (int) t;
        double f = t - i;
        Vec3 a = path[i];
        Vec3 b = i + 1 < path.length ? path[i + 1] : a;
        pointX = a.xCoord + (b.xCoord - a.xCoord) * f - spawnX;
        pointY = a.yCoord + (b.yCoord - a.yCoord) * f - spawnY;
        pointZ = a.zCoord + (b.zCoord - a.zCoord) * f - spawnZ;
    }

    // sum of drag^k for k in [0, n), the horizontal displacement per unit v0
    private static double sumDrag(double n) {
        return n <= 0.0 ? 0.0 : (1.0 - Math.pow(ARROW_DRAG, n)) / (1.0 - ARROW_DRAG);
    }

    // extra launch speed needed to climb against n ticks of gravity
    private static double grav(double n) {
        return 0.05 / (1.0 - ARROW_DRAG) * (n - sumDrag(n));
    }

    private boolean herpable(double vx, double vy, double vz, int ticks) {
        double x = spawnX;
        double y = spawnY;
        double z = spawnZ;
        for (int i = 0; i < ticks; i++) {
            if (mc.theWorld.rayTraceBlocks(new Vec3(x, y, z),
                    new Vec3(x + vx, y + vy, z + vz), false, true, false) != null) {
                return false;
            }
            x += vx;
            y += vy;
            z += vz;
            vx *= ARROW_DRAG;
            vy = vy * ARROW_DRAG - 0.05;
            vz *= ARROW_DRAG;
        }
        return true;
    }

    private Vec3[] predict(EntityPlayer target, int ticks) {
        Vec3[] points = new Vec3[ticks + 1];
        AxisAlignedBB box = target.getEntityBoundingBox();
        double motionX = target.posX - target.lastTickPosX;
        double motionY = (target.posY - target.lastTickPosY - 0.08) * 0.98f;
        double motionZ = target.posZ - target.lastTickPosZ;
        boolean ground = target.onGround;
        float forward = 0.0f, strafe = 0.0f;
        if (target != mc.thePlayer) {
            // infer forward/strafe from last motion, deadzone 0.1
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
        points[0] = Utils.boxCenter(box).addVector(0.0, 0.3, 0.0);
        if (speedLen > 5.0) {
            // blinked or teleported, extrapolating that would aim nonsense
            return points;
        }
        int steps = Math.min(ticks, TRAJECTORY_TICKS);
        boolean inWater = mc.theWorld.isMaterialInBB(box.expand(-0.1, -0.4, -0.1), net.minecraft.block.material.Material.water);
        boolean inLava = mc.theWorld.isMaterialInBB(box.expand(-0.1, -0.4, -0.1), net.minecraft.block.material.Material.lava);
        for (int n = 0; n < steps; n++) {
            boolean flying = target.capabilities.isFlying && target.ridingEntity == null;
            double fall = motionY;
            step(box, target, forward, strafe, flying ? false : ground, inWater, inLava, motionHolder);
            box = motionHolder.box;
            motionX = motionHolder.x;
            motionY = flying ? fall * 0.6 : motionHolder.y;
            motionZ = motionHolder.z;
            ground = motionHolder.ground;
            points[n + 1] = Utils.boxCenter(box).addVector(0.0, 0.3, 0.0);
        }
        return points;
    }

    // the sim needs mutable per-tick state, keep it in one reusable cell
    private static final class Motion {
        AxisAlignedBB box;
        double x, y, z;
        boolean ground, horizontal, vertical;
    }

    private final Motion motionHolder = new Motion();

    private void step(AxisAlignedBB box, EntityPlayer target, float forward, float strafe,
            boolean ground, boolean inWater, boolean inLava, Motion out) {
        if (inWater || inLava) {
            stupidFluid(box, target, forward, strafe, ground, out);
            return;
        }
        double motionY = out.y;
        float slip = slip(box, ground);
        float friction = ground ? target.getAIMoveSpeed()
                * (0.16277136f / (slip * slip * slip))
                : target.jumpMovementFactor;
        move(out, forward, strafe, friction, target);
        // second pass, the source called it twice per tick
        move(out, forward, strafe, friction, target);
        motionY = out.y;
        if (target.isOnLadder() && out.horizontal) {
            motionY = 0.2;
        }
        motionY -= 0.08;
        motionY *= 0.98f;
        double motionX = out.x * slip;
        double motionZ = out.z * slip;
        out.box = colilision(box, target, motionX, motionY, motionZ, out);
        out.x = motionX;
        out.y = motionY;
        out.z = motionZ;
    }

    private float slip(AxisAlignedBB box, boolean ground) {
        if (!ground) {
            return 0.91f;
        }
        Vec3 center = Utils.boxCenter(box);
        return mc.theWorld.getBlockState(new net.minecraft.util.BlockPos(
                MathHelper.floor_double(center.xCoord),
                MathHelper.floor_double(box.minY) - 1,
                MathHelper.floor_double(center.zCoord)))
                .getBlock().slipperiness * 0.91f;
    }

    private void stupidFluid(AxisAlignedBB box, EntityPlayer target, float forward, float strafe,
            boolean ground, Motion out) {
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
        move(out, forward, strafe, accel, target);
        out.box = colilision(box, target, out.x, out.y, out.z, out);
        // collide zeroes the blocked axes, the fluid drag applies after that
        double y = out.y;
        out.x *= drag;
        out.y = y * 0.8f - 0.02;
        out.z *= drag;
        if (minY != out.box.minY) {
            out.y = y;
        }
    }

    // rotate the wanted move into world space, scale to the tick friction,
    // add it to the running motion
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

    // vanilla moveEntity flow: web drag, sneak edge clamp, y/x/z collision
    // order and step, zero motion on collision
    private AxisAlignedBB colilision(AxisAlignedBB box, EntityPlayer target,
            double x, double y, double z, Motion out) {
        @SuppressWarnings("unused")
        Vec3 center = Utils.boxCenter(box);
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

    private static float[] angy(Vec3 to, EntityPlayer from) {
        Vec3 eyes = from.getPositionEyes(1.0f);
        float[] rot = RotationMath.toRotationsFromDelta(
                to.xCoord - eyes.xCoord, to.yCoord - eyes.yCoord, to.zCoord - eyes.zCoord);
        return new float[] {
                from.rotationYaw + MathHelper.wrapAngleTo180_float(rot[0] - from.rotationYaw),
                from.rotationPitch + MathHelper.wrapAngleTo180_float(rot[1] - from.rotationPitch)
        };
    }
}
