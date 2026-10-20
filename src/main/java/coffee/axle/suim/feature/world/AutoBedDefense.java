package coffee.axle.suim.feature.world;

import coffee.axle.suim.events.PostMotionEvent;
import coffee.axle.suim.events.PreMotionEvent;
import coffee.axle.suim.events.PrePlayerInputEvent;
import coffee.axle.suim.events.SendPacketEvent;
import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// AutoDef port. PRE phase: step rotation toward the placement aim. POST phase:
// validate the aim and place, same tick. timers ticked once per phase.
// candidates are raycast-validated before use.
public class AutoBedDefense extends Feature {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double REACH_SQ = 20.25;  // 4.5^2

    private static final String[] FACE_NAMES = {"DOWN", "UP", "SOUTH", "NORTH", "WEST", "EAST"};
    private static final int[] FACE_DX = {0, 0, 0, 0, 1, -1};
    private static final int[] FACE_DY = {1, -1, 0, 0, 0, 0};
    private static final int[] FACE_DZ = {0, 0, -1, 1, 0, 0};

    private Object moduleInstance;
    private Object defenseProperty;
    private Object onlyTopBedsProperty;
    private Object ticksPerBlockProperty;
    private Object delayAfterSwapProperty;
    private Object delayAfterAimProperty;
    private Object sneakHoldTicksProperty;
    private Object fovProperty;
    private Object rotSpeedProperty;
    private Object rotToleranceProperty;

    private int idx;
    private int workIdx;
    private boolean started;
    private boolean sneaked;
    private String lockedDir = "";
    private BlockPos origin;
    @SuppressWarnings("unused")
    private BlockPos renderTarget;
    private float serverYaw;
    private float serverPitch;
    private int swapTicks;
    private int aimTicks;
    private int placeCooldown;
    private int sneakHoldRemaining;
    private List<DefData.Ent> defense;
    private int curDefIdx = -1;
    private final Map<String, Integer> invCache = new HashMap<>();

    // placement tracking: the validated ray, kept fresh across ticks
    private BlockPos placeSupport;
    private EnumFacing placeFacing;
    private BlockPos placeTarget;
    private Vec3 placeHit;
    private float placeYaw;
    private float placePitch;
    private int flagIgnoreTicks;

    @Override
    public String getName() {
        return "AutoBedDefense";
    }

    @Override
    public GuiCategory getGuiCategory() {
        return GuiCategory.WORLD;
    }

    @Override
    public boolean initialize() {
        try {
            moduleInstance = createModule();
            creator.injectModule(moduleInstance, AutoBedDefense.class);

            defenseProperty = creator.createEnumProperty("defense", 0, DefData.NAMES);
            onlyTopBedsProperty = creator.createBooleanProperty("only-top-beds", true);
            ticksPerBlockProperty = creator.createIntegerProperty("ticks-per-block", 0, 0, 20);
            delayAfterSwapProperty = creator.createIntegerProperty("delay-after-swap", 0, 0, 10);
            delayAfterAimProperty = creator.createIntegerProperty("delay-after-aim", 0, 0, 10);
            sneakHoldTicksProperty = creator.createIntegerProperty("sneak-hold-ticks", 5, 0, 20);
            fovProperty = creator.createIntegerProperty("fov", 180, 0, 180);
            rotSpeedProperty = creator.createIntegerProperty("rot-speed", 12, 3, 90);
            rotToleranceProperty = creator.createIntegerProperty("rot-tolerance", 25, 1, 45);

            creator.registerProperties(moduleInstance, defenseProperty, onlyTopBedsProperty,
                    ticksPerBlockProperty, delayAfterSwapProperty, delayAfterAimProperty,
                    sneakHoldTicksProperty, fovProperty, rotSpeedProperty, rotToleranceProperty);

            manager.reloadModuleCommand();
            MinecraftForge.EVENT_BUS.register(this);

            manager.registerModuleCallbacks(moduleInstance, this::onEnable, this::onDisable);
            return true;
        } catch (Exception e) {
            MyauLogger.error("AutoBedDefense:init", e);
            return false;
        }
    }

    private void onEnable() {
        curDefIdx = -1;  // force loadDefense to pick up the preset
        loadDefense();
        if (mc.thePlayer != null) {
            serverYaw = mc.thePlayer.rotationYaw;
            serverPitch = mc.thePlayer.rotationPitch;
        }
    }

    private void onDisable() {
        if (sneaked) {
            setSneak(false);
        }
        clearPlace();
        resetState();
    }

    private void loadDefense() {
        int defIdx = properties.getInt(defenseProperty, 0);
        if (defIdx != curDefIdx) {
            curDefIdx = defIdx;
            defense = DefData.get(defIdx);
            idx = 0;
            started = false;
            origin = null;
            clearPlace();
        }
    }

    private void resetState() {
        idx = 0;
        workIdx = 0;
        renderTarget = null;
        started = false;
        sneaked = false;
        lockedDir = "";
        aimTicks = properties.getInt(delayAfterAimProperty, 0);
        placeCooldown = 0;
        invCache.clear();
        origin = null;
        clearPlace();
        flagIgnoreTicks = 5;
    }

    private void clearPlace() {
        placeSupport = null;
        placeFacing = null;
        placeTarget = null;
        placeHit = null;
        placeYaw = 0.0f;
        placePitch = 0.0f;
    }

    // the outgoing C03 IS the server rotation (no silent packet rewriting)
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSendPacket(SendPacketEvent e) {
        if (!manager.isModuleEnabled(moduleInstance)) return;
        if (!(e.getPacket() instanceof C03PacketPlayer)) return;
        C03PacketPlayer packet = (C03PacketPlayer) e.getPacket();
        if (packet.getRotating()) {
            serverYaw = packet.getYaw();
            serverPitch = packet.getPitch();
        }
    }

    // PRE phase: pick the work block, step rotation toward the placement aim
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onPreMotion(PreMotionEvent e) {
        if (!manager.isModuleEnabled(moduleInstance)) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.currentScreen != null) return;

        loadDefense();

        if (!started) {
            BlockPos bed = findBed(8);
            if (bed == null) {
                clearPlace();
                return;
            }
            origin = bed;
            lockedDir = bedDir(bed);
            started = true;
        }

        skipFilled();
        if (idx >= defense.size()) {
            disableSelf();
            return;
        }

        setupTarget();
        if (swapTicks > 0) swapTicks--;

        if (placeTarget != null && placeSupport != null) {
            float[] stepped = RotStep.step(e.getYaw(), e.getPitch(), placeYaw, placePitch, effSpeed());
            e.setYaw(stepped[0]);
            e.setPitch(stepped[1]);
        }
    }

    // move fix: re-project input onto the spoofed yaw frame for this tick
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onPreInput(PrePlayerInputEvent e) {
        if (!manager.isModuleEnabled(moduleInstance)) return;
        if (mc.thePlayer == null) return;
        if (e.getForward() == 0.0f && e.getStrafe() == 0.0f) return;
        float delta = (float) Math.toRadians(mc.thePlayer.rotationYaw - serverYaw);
        float cos = MathHelper.cos(delta);
        float sin = MathHelper.sin(delta);
        float fwd = e.getForward();
        float str = e.getStrafe();
        e.setForward(fwd * cos + str * sin);
        e.setStrafe(str * cos - fwd * sin);
    }

    // POST phase: validate the aim and place, same tick the rotation was set
    @SubscribeEvent
    public void onPostMotion(PostMotionEvent e) {
        if (!manager.isModuleEnabled(moduleInstance)) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.currentScreen != null) return;

        tickSneak();
        tryPlace();
        if (flagIgnoreTicks > 0) flagIgnoreTicks--;
    }

    private void skipFilled() {
        while (idx < defense.size()) {
            BlockPos t = targetAt(idx);
            if (mc.theWorld.isAirBlock(t)) break;
            idx++;
        }
    }

    private BlockPos targetAt(int i) {
        DefData.Ent ent = defense.get(i);
        int[] off = rot(ent.x, ent.y, ent.z);
        return origin.add(off[0], off[1], off[2]);
    }

    private void setupTarget() {
        workIdx = pickWork();
        if (workIdx < 0) return;
        DefData.Ent ent = defense.get(workIdx);
        BlockPos target = targetAt(workIdx);
        renderTarget = target;
        placeTarget = target;

        if (playerBlocks(target)) {
            clearPlace();
            placeTarget = target;
            renderTarget = target;
            return;
        }

        int want = slotFor(ent.block);
        if (want == -1) {
            disableSelf();
            return;
        }
        if (mc.thePlayer.inventory.currentItem != want) {
            mc.thePlayer.inventory.currentItem = want;
            swapTicks = properties.getInt(delayAfterSwapProperty, 0);
        }

        trackPlacement(target);
        bedSneak();
    }

    private int pickWork() {
        skipFilled();
        if (idx >= defense.size() || origin == null) return -1;
        if (!playerBlocks(targetAt(idx))) return idx;
        for (int i = idx + 1; i < defense.size(); i++) {
            BlockPos t = targetAt(i);
            if (!mc.theWorld.isAirBlock(t) || playerBlocks(t)) continue;
            return i;
        }
        return idx;
    }

    private boolean playerBlocks(BlockPos pos) {
        if (mc.thePlayer == null || pos == null) return false;
        AxisAlignedBB box = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
        return mc.thePlayer.getEntityBoundingBox().intersectsWith(box);
    }

    private void bedSneak() {
        if (placeSupport == null || sneaked) return;
        if (blockName(placeSupport).contains("bed")) {
            setSneak(true);
            sneaked = true;
            sneakHoldRemaining = properties.getInt(sneakHoldTicksProperty, 5);
        }
    }

    private void tickSneak() {
        if (!sneaked) return;
        if (sneakHoldRemaining > 0) {
            sneakHoldRemaining--;
        } else {
            setSneak(false);
            sneaked = false;
        }
    }

    // keep the validated ray fresh; rescan when stale
    private void trackPlacement(BlockPos target) {
        placeTarget = target;
        if (!stale(target)) {
            if (!hitStillValid(target) && !refreshHit(target)) {
                placeHit = facePoint(placeSupport, placeFacing);
            }
            aimForPlace();
            return;
        }
        if (findPlace(target)) return;
        // nothing clickable: aim at target center so the next scan starts close
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        double dx = target.getX() + 0.5 - eye.xCoord;
        double dy = target.getY() + 0.5 - eye.yCoord;
        double dz = target.getZ() + 0.5 - eye.zCoord;
        placeYaw = unwrap((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f, placeYaw);
        placePitch = MathHelper.clamp_float(
                (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), -90.0f, 90.0f);
    }

    private boolean stale(BlockPos target) {
        if (placeSupport == null || placeFacing == null || placeTarget == null) return true;
        if (!placeTarget.equals(target)) return true;
        if (mc.theWorld.isAirBlock(placeSupport)) return true;
        return !placeSupport.offset(placeFacing).equals(target);
    }

    private boolean hitStillValid(BlockPos target) {
        if (placeHit == null || placeSupport == null || placeFacing == null) return false;
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        float[] r = rots(eye, placeHit.xCoord, placeHit.yCoord, placeHit.zCoord);
        float yaw = unwrap(r[0], placeYaw);
        MovingObjectPosition ray = raycast(yaw, r[1], 4.5);
        if (ray == null || ray.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return false;
        if (!ray.getBlockPos().equals(placeSupport) || ray.sideHit != placeFacing) return false;
        return ray.getBlockPos().offset(ray.sideHit).equals(target);
    }

    // rescan the current face grid for a still-valid hit point
    private boolean refreshHit(BlockPos target) {
        if (placeSupport == null || placeFacing == null) return false;
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        EnumFacing face = placeFacing;
        BlockPos support = placeSupport;
        double inset = 0.05, step = 0.2;
        int grid = (int) Math.round(1.0 / step);
        double bestDist = Double.MAX_VALUE;
        Vec3 bestHit = null;
        float bestYaw = placeYaw, bestPitch = placePitch;
        for (int rr = 0; rr <= grid; rr++) {
            for (int cc = 0; cc <= grid; cc++) {
                double[] p = facePointAt(support, face, cc * step, rr * step, inset);
                if (p == null) continue;
                double dx = p[0] - eye.xCoord, dy = p[1] - eye.yCoord, dz = p[2] - eye.zCoord;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq > REACH_SQ) continue;
                float[] r = rots(eye, p[0], p[1], p[2]);
                float yaw = unwrap(r[0], placeYaw);
                MovingObjectPosition ray = raycast(yaw, r[1], 4.5);
                if (ray == null || ray.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                        || !ray.getBlockPos().equals(support) || ray.sideHit != face
                        || !ray.getBlockPos().offset(ray.sideHit).equals(target)) continue;
                if (distSq < bestDist) {
                    bestDist = distSq;
                    bestHit = ray.hitVec;
                    bestYaw = yaw;
                    bestPitch = r[1];
                }
            }
        }
        if (bestHit == null) return false;
        placeHit = bestHit;
        placeYaw = bestYaw;
        placePitch = bestPitch;
        return true;
    }

    private void aimForPlace() {
        if (placeHit == null) return;
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        float[] r = rots(eye, placeHit.xCoord, placeHit.yCoord, placeHit.zCoord);
        float newYaw = unwrap(r[0], placeYaw);
        float newPitch = MathHelper.clamp_float(r[1], -90.0f, 90.0f);
        // skip wild re-aims (ray flipped to the other side of the block)
        if (placeSupport != null && Math.abs(MathHelper.wrapAngleTo180_float(newYaw - placeYaw)) > 90.0f
                && Math.abs(newPitch - placePitch) > 45.0f) return;
        placeYaw = newYaw;
        placePitch = newPitch;
    }

    // full 6-face grid scan: raycast-validated candidates only
    private boolean findPlace(BlockPos target) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        int fov = properties.getInt(fovProperty, 180);
        float maxYaw = fov;
        float maxPitch = Math.min(fov, 90.0f);
        float curYaw = normYaw(serverYaw);
        float curPit = serverPitch;
        boolean onlyTop = properties.getBoolean(onlyTopBedsProperty, true);

        double inset = 0.05, step = 0.2;
        double insetTop = 1.0 - inset - 0.001;
        double insetBot = inset + 0.001;
        int grid = (int) Math.round(1.0 / step);

        List<double[]> cands = new ArrayList<>();
        for (int fi = 0; fi < 6; fi++) {
            BlockPos support = target.add(FACE_DX[fi], FACE_DY[fi], FACE_DZ[fi]);
            if (mc.theWorld.isAirBlock(support)) continue;
            String supName = blockName(support);
            if (onlyTop && supName.contains("bed") && !"UP".equals(FACE_NAMES[fi])) continue;

            for (int rr = 0; rr <= grid; rr++) {
                boolean ltr = (rr & 1) == 0;
                double v = rr * step;
                for (int cc = 0; cc <= grid; cc++) {
                    double cu = cc * step;
                    double u = ltr ? cu : 1.0 - cu;
                    double px, py, pz;
                    if (fi < 2) {
                        px = support.getX() + u;
                        pz = support.getZ() + v;
                        py = support.getY() + (fi == 1 ? insetTop : insetBot);
                    } else if (fi < 4) {
                        px = support.getX() + u;
                        py = support.getY() + v;
                        pz = support.getZ() + (fi == 2 ? insetTop : insetBot);
                    } else {
                        pz = support.getZ() + u;
                        py = support.getY() + v;
                        px = support.getX() + (fi == 5 ? insetTop : insetBot);
                    }
                    double dx = px - eye.xCoord, dy = py - eye.yCoord, dz = pz - eye.zCoord;
                    if (dx * dx + dy * dy + dz * dz > REACH_SQ) continue;
                    if (!clearRay(eye, px, py, pz, support)) continue;

                    float[] r = rots(eye, px, py, pz);
                    if (Math.abs(wrapD(curYaw, r[0])) > maxYaw) continue;
                    if (Math.abs(r[1] - curPit) > maxPitch) continue;
                    if (Math.abs(r[1]) > 90.0f) continue;

                    float yawU = unwrap(r[0], serverYaw);
                    MovingObjectPosition ray = raycast(yawU, r[1], 4.5);
                    if (ray == null || ray.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                            || !ray.getBlockPos().equals(support)
                            || ray.sideHit != facing(fi)
                            || !ray.getBlockPos().offset(ray.sideHit).equals(target)) continue;
                    if (onlyTop && ray.sideHit != EnumFacing.UP && supName.contains("bed")) continue;

                    double cost = Math.abs(wrapD(curYaw, r[0]))
                            + Math.abs(r[1] - curPit)
                            + ("UP".equals(FACE_NAMES[fi]) ? -0.25 : 0.0);
                    cands.add(new double[]{cost, yawU, r[1],
                            ray.hitVec.xCoord, ray.hitVec.yCoord, ray.hitVec.zCoord});
                }
            }
        }
        if (cands.isEmpty()) return false;
        cands.sort(Comparator.comparingDouble(a -> a[0]));
        double[] best = cands.get(0);

        // keep the current ray if the best is a wild flip but current still lands
        if (placeSupport != null && placeHit != null
                && Math.abs(MathHelper.wrapAngleTo180_float((float) best[1] - placeYaw)) > 120.0f) {
            MovingObjectPosition keep = raycast(placeYaw, placePitch, 4.5);
            if (keep != null && keep.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                    && keep.getBlockPos().offset(keep.sideHit).equals(target)) {
                return true;
            }
        }

        placeYaw = (float) best[1];
        placePitch = (float) best[2];
        placeHit = new Vec3(best[3], best[4], best[5]);
        // recover support/face from the validated ray
        MovingObjectPosition ray = raycast(placeYaw, placePitch, 4.5);
        if (ray == null || ray.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return false;
        placeSupport = ray.getBlockPos();
        placeFacing = ray.sideHit;
        return true;
    }

    private void tryPlace() {
        if (placeTarget == null) return;
        if (playerBlocks(placeTarget)) return;
        if (swapTicks > 0 || placeCooldown > 0 || aimTicks > 0) {
            if (placeCooldown > 0) placeCooldown--;
            if (aimTicks > 0) aimTicks--;
            return;
        }

        boolean onlyTop = properties.getBoolean(onlyTopBedsProperty, true);
        BlockPos support = null;
        EnumFacing face = null;
        Vec3 hit = null;

        MovingObjectPosition ray = raycast(serverYaw, serverPitch, 4.5);
        if (ray != null && ray.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            BlockPos cand = ray.getBlockPos().offset(ray.sideHit);
            if (cand.equals(placeTarget) && !mc.theWorld.isAirBlock(ray.getBlockPos())
                    && !(onlyTop && blockName(ray.getBlockPos()).contains("bed") && ray.sideHit != EnumFacing.UP)) {
                support = ray.getBlockPos();
                face = ray.sideHit;
                hit = ray.hitVec;
            }
        }
        // tolerance fallback: aim close enough to the tracked placement
        if (support == null && placeSupport != null && placeFacing != null && placeHit != null) {
            int tol = properties.getInt(rotToleranceProperty, 25);
            float yawD = Math.abs(MathHelper.wrapAngleTo180_float(placeYaw - serverYaw));
            float pitchD = Math.abs(placePitch - serverPitch);
            if (yawD <= tol && pitchD <= tol && !mc.theWorld.isAirBlock(placeSupport)
                    && placeSupport.offset(placeFacing).equals(placeTarget)) {
                support = placeSupport;
                face = placeFacing;
                hit = placeHit;
            }
        }
        if (support == null || face == null || hit == null) return;
        if (playerBlocks(placeTarget)) return;

        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held == null) return;

        if (!mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, held, support, face, hit)) {
            return;
        }
        mc.thePlayer.swingItem();

        if (workIdx <= idx) idx = workIdx + 1;
        skipFilled();
        aimTicks = properties.getInt(delayAfterAimProperty, 0);
        placeCooldown = properties.getInt(ticksPerBlockProperty, 0);
        clearPlace();
        if (aimTicks <= 0 && placeCooldown <= 0 && swapTicks <= 0 && idx < defense.size()) {
            setupTarget();
        }
    }

    private float effSpeed() {
        int speed = properties.getInt(rotSpeedProperty, 12);
        return moving() ? Math.min(180.0f, speed * 3.0f) : speed;
    }

    private boolean moving() {
        if (mc.thePlayer == null) return false;
        double dx = mc.thePlayer.posX - mc.thePlayer.prevPosX;
        double dy = mc.thePlayer.posY - mc.thePlayer.prevPosY;
        double dz = mc.thePlayer.posZ - mc.thePlayer.prevPosZ;
        if (dx * dx + dy * dy + dz * dz > 1.0e-4) return true;
        return Math.abs(mc.thePlayer.motionX) > 0.01
                || Math.abs(mc.thePlayer.motionZ) > 0.01
                || Math.abs(mc.thePlayer.motionY) > 0.01;
    }

    // line-walk occlusion check: nothing solid between eye and hit point
    private boolean clearRay(Vec3 eye, double hx, double hy, double hz, BlockPos support) {
        double dx = hx - eye.xCoord, dy = hy - eye.yCoord, dz = hz - eye.zCoord;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, (int) Math.ceil(dist / 0.08));
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            BlockPos sample = new BlockPos(
                    eye.xCoord + dx * t, eye.yCoord + dy * t, eye.zCoord + dz * t);
            if (sample.equals(support) || mc.theWorld.isAirBlock(sample)) continue;
            return false;
        }
        return true;
    }

    private double[] facePointAt(BlockPos support, EnumFacing face, double u, double v, double inset) {
        switch (face) {
            case UP:
                return new double[]{support.getX() + u, support.getY() + 1.0 - inset, support.getZ() + v};
            case DOWN:
                return new double[]{support.getX() + u, support.getY() + inset, support.getZ() + v};
            case NORTH:
                return new double[]{support.getX() + u, support.getY() + v, support.getZ() + inset};
            case SOUTH:
                return new double[]{support.getX() + u, support.getY() + v, support.getZ() + 1.0 - inset};
            case WEST:
                return new double[]{support.getX() + inset, support.getY() + v, support.getZ() + u};
            case EAST:
                return new double[]{support.getX() + 1.0 - inset, support.getY() + v, support.getZ() + u};
            default:
                return null;
        }
    }

    private Vec3 facePoint(BlockPos support, EnumFacing face) {
        double[] p = facePointAt(support, face, 0.5, 0.5, 0.05);
        return p == null ? null : new Vec3(p[0], p[1], p[2]);
    }

    private EnumFacing facing(int fi) {
        switch (FACE_NAMES[fi]) {
            case "UP": return EnumFacing.UP;
            case "DOWN": return EnumFacing.DOWN;
            case "NORTH": return EnumFacing.NORTH;
            case "SOUTH": return EnumFacing.SOUTH;
            case "EAST": return EnumFacing.EAST;
            case "WEST": return EnumFacing.WEST;
            default: return EnumFacing.UP;
        }
    }

    private int[] rot(int x, int y, int z) {
        switch (lockedDir) {
            case "south": return new int[]{-x, y, -z};
            case "east": return new int[]{-z, y, x};
            case "west": return new int[]{z, y, -x};
            default: return new int[]{x, y, z};
        }
    }

    // closest of head/foot to the player (AutoDef parity - foot-only breaks
    // the pattern offset when standing near the head)
    private BlockPos findBed(int range) {
        BlockPos center = new BlockPos(mc.thePlayer);
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int x = center.getX() - range; x <= center.getX() + range; x++) {
            for (int y = center.getY() - range; y <= center.getY() + range; y++) {
                for (int z = center.getZ() - range; z <= center.getZ() + range; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!(mc.theWorld.getBlockState(pos).getBlock() instanceof BlockBed)) continue;
                    IBlockState state = mc.theWorld.getBlockState(pos);
                    EnumFacing facing = state.getValue(BlockBed.FACING);
                    boolean head = state.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD;
                    BlockPos foot = head ? pos.offset(facing.getOpposite()) : pos;
                    BlockPos headPos = head ? pos : pos.offset(facing);
                    BlockPos pick = distSq(headPos) > distSq(foot) ? headPos : foot;
                    double pd = distSq(pick);
                    if (pd < bestDist) {
                        bestDist = pd;
                        best = pick;
                    }
                }
            }
        }
        return best;
    }

    private double distSq(BlockPos pos) {
        double dx = pos.getX() + 0.5 - mc.thePlayer.posX;
        double dy = pos.getY() + 0.5 - mc.thePlayer.posY;
        double dz = pos.getZ() + 0.5 - mc.thePlayer.posZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private String bedDir(BlockPos bed) {
        IBlockState state = mc.theWorld.getBlockState(bed);
        if (!(state.getBlock() instanceof BlockBed)) return "";
        EnumFacing facing = state.getValue(BlockBed.FACING);
        switch (facing) {
            case SOUTH: return "north";
            case NORTH: return "south";
            case WEST: return "east";
            case EAST: return "west";
            default: return "";
        }
    }

    private int slotFor(String blockName) {
        String key = blockName.toLowerCase();
        Integer cached = invCache.get(key);
        if (cached != null && matches(mc.thePlayer.inventory.getStackInSlot(cached), key)) {
            return cached;
        }
        for (int s = 0; s < 9; s++) {
            if (matches(mc.thePlayer.inventory.getStackInSlot(s), key)) {
                invCache.put(key, s);
                return s;
            }
        }
        return -1;
    }

    private boolean matches(ItemStack stack, String blockName) {
        if (stack == null) return false;
        Item item = stack.getItem();
        switch (blockName) {
            case "wool": return item == Item.getItemFromBlock(Blocks.wool);
            case "end_stone": return item == Item.getItemFromBlock(Blocks.end_stone);
            case "stained_glass": return item == Item.getItemFromBlock(Blocks.stained_glass);
            case "planks": return item == Item.getItemFromBlock(Blocks.planks);
            default: return false;
        }
    }

    private String blockName(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        if (block == Blocks.air) return "air";
        if (block instanceof BlockBed) return "bed";
        if (block == Blocks.wool) return "wool";
        if (block == Blocks.end_stone) return "end_stone";
        if (block == Blocks.stained_glass) return "stained_glass";
        if (block == Blocks.planks) return "planks";
        return block.getLocalizedName();
    }

    private void setSneak(boolean pressed) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), pressed);
    }

    private MovingObjectPosition raycast(float yaw, float pitch, double range) {
        Vec3 start = mc.thePlayer.getPositionEyes(1.0f);
        double x = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
        double y = -Math.sin(Math.toRadians(pitch));
        double z = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
        Vec3 end = start.addVector(x * range, y * range, z * range);
        return mc.theWorld.rayTraceBlocks(start, end);
    }

    private float[] rots(Vec3 eye, double tx, double ty, double tz) {
        double dx = tx - eye.xCoord, dy = ty - eye.yCoord, dz = tz - eye.zCoord;
        double hd = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, hd));
        return new float[]{normYaw(yaw), pitch};
    }

    private static float normYaw(float yaw) {
        yaw = ((yaw % 360f) + 360f) % 360f;
        return yaw > 180f ? yaw - 360f : yaw;
    }

    private static float wrapD(float base, float target) {
        return MathHelper.wrapAngleTo180_float(target - base);
    }

    private static float unwrap(float yaw, float prev) {
        return prev + ((((yaw - prev + 180f) % 360f) + 360f) % 360f - 180f);
    }

    private void disableSelf() {
        try {
            manager.setModuleEnabled(moduleInstance, false);
        } catch (Exception e) {
            MyauLogger.error("AutoBedDefense:disable", e);
        }
    }
}
