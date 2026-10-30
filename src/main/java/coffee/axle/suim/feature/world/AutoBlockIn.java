package coffee.axle.suim.feature.world;

import coffee.axle.suim.events.PreMotionEvent;
import coffee.axle.suim.events.SendPacketEvent;
import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.feature.world.blockin.BlockInUtils;
import coffee.axle.suim.feature.world.blockin.BlockInUtils.AimPoint;
import coffee.axle.suim.property.Setting;
import coffee.axle.suim.rotation.RotationState;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.PacketUtils;
import coffee.axle.suim.util.Utils;
import net.minecraft.block.Block;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import static coffee.axle.suim.ProjectSUIM.mc;

/**
 * Places blocks on the faces around you while a bed is being broken.
 * <p>
 * The aim runs once per player tick, before the walking packet is built, and
 * silent aim rewrites the outgoing C03 instead of the camera. With silent aim
 * off the camera gets lastYaw/lastPitch from the client tick.
 */
public class AutoBlockIn extends Feature {


    @Setting(min = 0, max = 20) private static int delay = 1;
    @Setting private static boolean disableInCreative = true;
    @Setting private static boolean disableObsidian = true;
    @Setting private static boolean disableWhenComplete = false;
    @Setting private static boolean onlyWhileMiddleClicking = true;
    @Setting private static boolean prioritizeStrongerBlocks = false;
    @Setting private static boolean showPercentage = true;
    @Setting private static boolean silentAim = true;
    @Setting private static boolean silentSwing = false;
    @Setting private static boolean skipNearBed = true;

    private static final class Node {
        final BlockPos pos;
        final int score;
        @SuppressWarnings("unused")
        final int cost;

        Node(BlockPos pos, int score, int cost) {
            this.pos = pos;
            this.score = score;
            this.cost = cost;
        }
    }

    public static boolean active;
    public static String percentText;
    private static int percentColor;
    private static float percentHalfWidth;
    private static float enclosed;
    private static long startedAt, stoppedAt;
    private static boolean middleHeld;
    private static boolean waitRelease;
    private static int previousSlot = -1, blockSlot = -1;
    private static BlockPos against;
    private static EnumFacing face;
    private static BlockPos targetPos;
    private static BlockPos aimedAgainst;
    private static EnumFacing aimedFace;
    private static List<BlockPos> path;
    private static final Deque<BlockPos> retry = new ArrayDeque<BlockPos>();
    private static final Set<BlockPos> retrySet = new HashSet<BlockPos>();
    private static final Set<BlockPos> failed = new HashSet<BlockPos>();
    private static EnumFacing[] facings;
    @SuppressWarnings("unused")
    private static EnumFacing startFacing;
    private static final EnumFacing[] ORDER = {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST, EnumFacing.UP, EnumFacing.DOWN};
    private static final EnumFacing[] ORDER_UP = {EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST, EnumFacing.DOWN};
    private static final BlockPos.MutableBlockPos MUTABLE = new BlockPos.MutableBlockPos();
    private static float aimYaw, aimPitch;
    private static Float fromYaw, fromPitch;
    private static Float lastYaw, lastPitch;
    private static Float refYaw;
    private static int steps, step;

    // silent rotation state
    private boolean rotating;
    private boolean wasRotating;
    private float serverYaw, serverPitch;

    // per-tick interaction flags, reset on every C03
    private boolean sentUseEntity;
    private boolean sentAnyDigging;

    public AutoBlockIn() {
        super("AutoBlockIn", GuiCategory.WORLD);
    }

    @Override
    protected void init() throws Exception {
        RotationState.getInstance().register();
    }

    @Override
    protected void onEnable() {
        MyauLogger.log(getName(), "MODULE_ENABLED");
    }

    @Override
    protected void onDisable() {
        reset();
        waitRelease = false;
        rotating = false;
        MyauLogger.log(getName(), "MODULE_DISABLED");
    }


    private static float clamp01(float f) {
        return MathHelper.clamp_float(f, 0.0f, 1.0f);
    }

    // runs from EntityPlayerSP.onUpdate, before movement and before the
    // walking packet. PreMotionEvent (PlayerTick START) is the same point.
    @SubscribeEvent
    public void onPreMotion(PreMotionEvent e) {
        if (!isEnabled()) {
            rotating = false;
            return;
        }
        float[] rotations = getRotations();
        if (rotations == null) {
            rotating = false;
        } else {
            rotating = true;
            serverYaw = rotations[0];
            serverPitch = rotations[1];
        }
    }

    // runs on the client tick, before the player tick. camera apply and
    // progress tracking live here.
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        if (!isEnabled()) return;
        if (!Utils.nullCheck()) return;

        if (lastYaw != null && !silentAim) {
            mc.thePlayer.rotationYaw = lastYaw;
            mc.thePlayer.rotationPitch = lastPitch;
        }
        if (!active) return;

        int x = MathHelper.floor_double(mc.thePlayer.posX);
        int y = MathHelper.floor_double(mc.thePlayer.posY);
        int z = MathHelper.floor_double(mc.thePlayer.posZ);
        enclosed = enclosure(x, y, z);
        int percent = Math.round(enclosed * 100.0f);
        percentText = percent + "%";
        percentColor = percent != 100 ? -1 : Color.green.getRGB();
        percentHalfWidth = (float) mc.fontRendererObj.getStringWidth(percentText) / 2.0f - 2.0f;
        if (percent == 100 && disableWhenComplete) {
            if (!onlyWhileMiddleClicking) {
                try {
                    manager.setModuleEnabled(getModuleInstance(), false);
                } catch (Exception ex) {
                    MyauLogger.error("AutoBlockIn:disable", ex);
                }
            } else {
                stop();
                waitRelease = true;
            }
        }
    }

    private float[] getRotations() {
        // module gate
        if (!Utils.nullCheck() || !mc.inGameHasFocus
                || !mc.thePlayer.capabilities.allowEdit
                || mc.currentScreen != null
                || !noUseOrDig()
                || disableInCreative && mc.thePlayer.capabilities.isCreativeMode) {
            if (middleHeld) {
                stop();
            } else if (active) {
                reset();
            }
            return null;
        }
        if (onlyWhileMiddleClicking && waitRelease) {
            if (Mouse.isButtonDown(2)) {
                return null;
            }
            waitRelease = false;
        }
        boolean run = !onlyWhileMiddleClicking || Mouse.isButtonDown(2);
        if (!run && middleHeld) {
            stop();
        }
        middleHeld = run;
        if (!run) {
            return null;
        }
        if (!active) {
            blockSlot = findSlot();
            if (blockSlot == -1) {
                return null;
            }
            previousSlot = mc.thePlayer.inventory.currentItem;
            mc.thePlayer.inventory.currentItem = blockSlot;
            active = true;
            startedAt = System.currentTimeMillis();
            resetSteps();
            resetAim();
            captureFacings();
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        }
        ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(mc.thePlayer.inventory.currentItem);
        if (active && !usable(itemStack)) {
            int n = findSlot();
            if (n == -1) {
                stop();
                return null;
            }
            blockSlot = n;
            mc.thePlayer.inventory.currentItem = blockSlot;
        }
        failed.clear();
        AimPoint last = null;
        for (int i = 0; i < 5; ++i) {
            if (!(hasTarget() || findTarget() || retryTarget())) {
                clearTarget();
                break;
            }
            AimPoint aim = BlockInUtils.aimAtFace(against, face, currentYaw(), currentPitch());
            if (aim == null) {
                markFailed();
                clearTarget();
                continue;
            }
            if (!canPlace(aim, itemStack)) {
                markFailed();
                clearTarget();
                continue;
            }
            aimAt(aim);
            if (steps > 0 && step < steps) {
                float t = clamp01(((float) step + 1.0f) / (float) steps);
                float startYaw = fromYaw != null ? fromYaw : (lastYaw != null ? unWrap(lastYaw, refYaw != null ? refYaw : mc.thePlayer.rotationYaw) : mc.thePlayer.rotationYaw);
                float startPitch = fromPitch != null ? fromPitch : (lastPitch != null ? lastPitch : mc.thePlayer.rotationPitch);
                float y = Utils.lerp(aimYaw, startYaw, t);
                float p = Utils.lerp(aimPitch, startPitch, t);
                ++step;
                return finish(y, p);
            }
            double reach = mc.playerController.getBlockReachDistance();
            float wrappedYaw = MathHelper.wrapAngleTo180_float(aimYaw);
            float aimP = aimPitch;
            MovingObjectPosition hit = BlockInUtils.rayTraceBlock(against, face, reach, wrappedYaw, aimP);
            if (hit != null && mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, itemStack, against, face, aim.point)) {
                BlockInUtils.swing(silentSwing, true);
                if (path != null && !path.isEmpty() && path.get(0).equals(targetPos)) {
                    path.remove(0);
                }
                clearTarget();
                resetSteps();
                float y = lastYaw != null ? lastYaw : wrappedYaw;
                if (refYaw == null) {
                    refYaw = y;
                }
                float u = unWrap(y, refYaw);
                setFrom(u, lastPitch != null ? lastPitch : aimP);
                return finish(aimYaw, aimPitch);
            }
            last = aim;
            markFailed();
            clearTarget();
        }
        if (fromYaw != null || lastYaw != null) {
            int n = delay;
            if (n > 0 && step < n) {
                float t = clamp01(((float) step + 1.0f) / (float) n);
                float startYaw = fromYaw != null ? fromYaw : (lastYaw != null ? unWrap(lastYaw, refYaw != null ? refYaw : mc.thePlayer.rotationYaw) : mc.thePlayer.rotationYaw);
                float startPitch = fromPitch != null ? fromPitch : (lastPitch != null ? lastPitch : mc.thePlayer.rotationPitch);
                float y = Utils.lerp(aimYaw, startYaw, t);
                float p = Utils.lerp(aimPitch, startPitch, t);
                ++step;
                return finish(y, p);
            }
            return finish(aimYaw, aimPitch);
        }
        return last == null ? null : finish(last.yaw, last.pitch);
    }

    private float currentYaw() {
        return rotating && wasRotating ? serverYaw : mc.thePlayer.rotationYaw;
    }

    private float currentPitch() {
        return rotating && wasRotating ? serverPitch : mc.thePlayer.rotationPitch;
    }

    // tracked in the packet hook and reset on every C03, so they are
    // effectively per-tick "did we interact" flags
    private boolean noUseOrDig() {
        return !sentUseEntity && !sentAnyDigging;
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        Packet<?> packet = e.getPacket();
        if (packet instanceof C02PacketUseEntity) {
            sentUseEntity = true;
            return;
        }
        if (packet instanceof C07PacketPlayerDigging) {
            sentAnyDigging = true;
            return;
        }
        if (!(packet instanceof C03PacketPlayer)) {
            return;
        }
        sentUseEntity = false;
        sentAnyDigging = false;

        C03PacketPlayer c03 = (C03PacketPlayer) packet;
        boolean enabled = isEnabled();

        // one snap-back packet the tick after rotation stops, so the server
        // yaw and the player yaw line up again
        if (enabled && !rotating && wasRotating) {
            wasRotating = rotating;
            if (Utils.nullCheck()) {
                float snapYaw = serverYaw - serverYaw % 360.0f + mc.thePlayer.rotationYaw % 360.0f;
                e.setCanceled(true);
                PacketUtils.sendPacketNoEvent(PacketUtils.withRotation(c03, snapYaw, mc.thePlayer.rotationPitch));
                RotationState.getInstance().setServerAngles(snapYaw, mc.thePlayer.rotationPitch);
                return;
            }
            return;
        }

        if (enabled && rotating) {
            wasRotating = rotating;
            e.setCanceled(true);
            PacketUtils.sendPacketNoEvent(PacketUtils.withRotation(c03, lastYaw, lastPitch));
            RotationState.getInstance().setServerAngles(lastYaw, lastPitch);
            mc.thePlayer.rotationYawHead = lastYaw;
            return;
        }
        wasRotating = rotating;
    }


    private void aimAt(AimPoint aim) {
        boolean changed = aimedAgainst == null || !aimedAgainst.equals(against) || aimedFace != face;
        float dYaw = Math.abs(MathHelper.wrapAngleTo180_float(aim.yaw - aimYaw));
        float dPitch = Math.abs(aim.pitch - aimPitch);
        boolean moved = dYaw > 1.0f || dPitch > 1.0f;
        if (!changed && !moved && fromYaw != null) {
            float ref = refYaw != null ? refYaw : mc.thePlayer.rotationYaw;
            aimYaw = unWrap(aim.yaw, ref);
            aimPitch = aim.pitch;
        } else {
            float y = lastYaw != null ? lastYaw : mc.thePlayer.rotationYaw;
            float p = lastPitch != null ? lastPitch : mc.thePlayer.rotationPitch;
            if (refYaw == null) {
                refYaw = y;
            }
            float u = unWrap(y, refYaw);
            float target = unWrap(aim.yaw, refYaw);
            setFrom(u, p);
            aimYaw = target;
            aimPitch = aim.pitch;
            aimedAgainst = against;
            aimedFace = face;
        }
    }

    private static boolean ownVertical(BlockPos pos, EnumFacing facing) {
        BlockPos own = BlockInUtils.playerPos();
        return pos.equals(own) && (facing == EnumFacing.UP || facing == EnumFacing.DOWN);
    }

    private static boolean retryTarget() {
        int n = retry.size();
        while (n-- > 0 && !retry.isEmpty()) {
            BlockPos pos = retry.pollFirst();
            if (pos == null) continue;
            if (skip(pos) || !BlockInUtils.isReplaceable(pos)) {
                retrySet.remove(pos);
                continue;
            }
            if (findSupport(pos)) {
                retrySet.remove(pos);
                return true;
            }
            retry.addLast(pos);
        }
        return false;
    }

    private static boolean findTarget() {
        BlockPos own = BlockInUtils.playerPos();
        BlockPos roof = own.up(2);
        if (path != null && !path.isEmpty()) {
            while (!path.isEmpty() && (retrySet.contains(path.get(0)) || BlockInUtils.isPlayerBlock(path.get(0)))) {
                path.remove(0);
            }
            BlockPos next;
            if (!path.isEmpty() && !skip(next = path.get(0)) && findSupport(next)) {
                return true;
            }
        }
        if (BlockInUtils.isReplaceable(roof) && BlockInUtils.hasSupport(roof)) {
            if (!skip(roof) && findSupport(roof)) {
                return true;
            }
        }
        List<BlockPos> list;
        BlockPos next;
        if (BlockInUtils.isReplaceable(roof) && (list = findPath(roof, own, 8, -2, 6)) != null && !list.isEmpty()) {
            path = list;
            while (!path.isEmpty() && BlockInUtils.isPlayerBlock(path.get(0))) {
                path.remove(0);
            }
            if (!path.isEmpty() && !skip(next = path.get(0)) && findSupport(next)) {
                return true;
            }
        }
        for (EnumFacing facing : facings()) {
            BlockPos below = own.down().offset(facing);
            BlockPos feet = own.offset(facing);
            BlockPos head = feet.up();
            check:
            {
                if (BlockInUtils.isReplaceable(below)) {
                    if (BlockInUtils.hasSupport(below)) {
                        if (!skip(below)) {
                            if (!findSupportUp(below)) {
                                break check;
                            }
                            return true;
                        }
                    } else {
                        list = findPath(below, own, Math.max(3, 8 / 2), -6, 0);
                        if (list != null && !list.isEmpty()) {
                            path = list;
                            while (!path.isEmpty() && BlockInUtils.isPlayerBlock(path.get(0))) {
                                path.remove(0);
                            }
                            if (!path.isEmpty() && !skip(next = path.get(0)) && findSupport(next)) {
                                return true;
                            }
                        }
                    }
                }
            }
            if (BlockInUtils.isReplaceable(feet)) {
                if (!skip(feet) && findSupportBeside(feet, own, facing)) {
                    return true;
                }
                if (!BlockInUtils.hasSupport(feet)) {
                    list = findPath(feet, own, Math.max(2, 8 / 2), -3, 1);
                    if (list != null && !list.isEmpty()) {
                        path = list;
                        while (!path.isEmpty() && BlockInUtils.isPlayerBlock(path.get(0))) {
                            path.remove(0);
                        }
                        if (!path.isEmpty() && !skip(next = path.get(0)) && findSupport(next)) {
                            return true;
                        }
                    }
                } else if (!skip(feet) && findSupport(feet, ORDER)) {
                    return true;
                }
            }
            if (!BlockInUtils.isReplaceable(head)) continue;
            if (BlockInUtils.hasSupport(head)) {
                if (skip(head) || !findSupport(head, ORDER)) continue;
                return true;
            }
            list = findPath(head, own, Math.max(2, 8 / 2), -2, 2);
            if (list == null || list.isEmpty()) continue;
            path = list;
            while (!path.isEmpty() && BlockInUtils.isPlayerBlock(path.get(0))) {
                path.remove(0);
            }
            if (path.isEmpty() || skip(next = path.get(0)) || !findSupport(next)) continue;
            return true;
        }
        return BlockInUtils.isReplaceable(roof) && BlockInUtils.hasSupport(roof) && !skip(roof) && findSupport(roof);
    }

    private static void captureFacings() {
        EnumFacing f = startFacing = mc.thePlayer.getHorizontalFacing();
        facings = new EnumFacing[]{f, f.rotateY(), f.getOpposite(), f.rotateYCCW()};
    }

    private static int findSlot() {
        int slot = -1;
        int most = 0;
        float best = -1.0f;
        for (int i = 0; i < 9; ++i) {
            ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(i);
            if (!usable(itemStack)) continue;
            Block block = BlockInUtils.blockOf(itemStack);
            int size = itemStack.stackSize;
            if (prioritizeStrongerBlocks) {
                float f = BlockInUtils.breakTicks(block, null, true);
                if (f < 0.0f) continue;
                if (!(f > best || f == best && size > most)) continue;
                best = f;
                most = size;
                slot = i;
                continue;
            }
            if (size <= most) continue;
            most = size;
            slot = i;
        }
        return slot;
    }


    private static boolean usable(ItemStack itemStack) {
        if (!BlockInUtils.isBlockItem(itemStack)) {
            return false;
        }
        Block block = BlockInUtils.blockOf(itemStack);
        if (!BlockInUtils.isPlaceable(block)) {
            return false;
        }
        if (block == Blocks.pumpkin || disableObsidian && block == Blocks.obsidian) {
            return false;
        }
        return itemStack.stackSize > 0;
    }

    private static boolean findSupportBeside(BlockPos pos, BlockPos own, EnumFacing facing) {
        if (BlockInUtils.isPlayerBlock(pos)) {
            return false;
        }
        if (pos.getY() == own.getY()) {
            if (!ownVertical(own, facing) && BlockInUtils.isSupport(BlockInUtils.getBlock(own))) {
                against = own;
                face = facing;
                targetPos = pos;
                return true;
            }
            BlockPos below = pos.down();
            if (!ownVertical(below, EnumFacing.UP) && BlockInUtils.isSupport(BlockInUtils.getBlock(below))) {
                against = below;
                face = EnumFacing.UP;
                targetPos = pos;
                return true;
            }
        }
        return findSupport(pos, ORDER);
    }

    private static boolean skip(BlockPos pos) {
        return failed.contains(pos) || BlockInUtils.isPlayerBlock(pos) || skipNearBed && BlockInUtils.bedAdjacent(pos);
    }

    private static void stop() {
        if (previousSlot >= 0 && previousSlot < 9) {
            mc.thePlayer.inventory.currentItem = previousSlot;
        }
        reset();
    }

    private static boolean canPlace(AimPoint aim, ItemStack itemStack) {
        if (!BlockInUtils.isBlockItem(itemStack)) {
            return false;
        }
        if (against == null || face == null || targetPos == null) {
            return false;
        }
        double reach = mc.playerController.getBlockReachDistance();
        MovingObjectPosition hit = BlockInUtils.rayTraceBlock(against, face, reach, aim.yaw, aim.pitch);
        return hit != null && BlockInUtils.canPlace(hit, itemStack);
    }

    private static List<BlockPos> findPath(BlockPos pos, BlockPos own, int r, int minY, int maxY) {
        HashSet<BlockPos> solids = new HashSet<BlockPos>();
        HashSet<BlockPos> open = new HashSet<BlockPos>();
        BlockInUtils.scan(solids, open, retrySet, own, r, minY, maxY);
        if (open.isEmpty()) {
            return null;
        }
        for (EnumFacing f : EnumFacing.VALUES) {
            if (solids.contains(pos.offset(f.getOpposite()))) {
                return null;
            }
        }
        ArrayList<BlockPos> starts = new ArrayList<BlockPos>();
        BlockInUtils.touching(starts, solids, open, own);
        if (starts.isEmpty()) {
            return null;
        }
        HashSet<BlockPos> goals = new HashSet<BlockPos>();
        for (EnumFacing f : EnumFacing.VALUES) {
            BlockPos p = pos.offset(f.getOpposite());
            if (open.contains(p) && !retrySet.contains(p)) {
                goals.add(p);
            }
        }
        if (goals.isEmpty()) {
            return null;
        }
        int size = open.size();
        int limit = Math.min(8000, Math.max(500, size * 2));
        PriorityQueue<Node> queue = new PriorityQueue<Node>(Comparator.comparingInt(n -> n.score));
        HashMap<BlockPos, Integer> cost = new HashMap<BlockPos, Integer>(size);
        HashMap<BlockPos, BlockPos> parent = new HashMap<BlockPos, BlockPos>(size);
        for (BlockPos p : starts) {
            int h = BlockInUtils.nearestDistance(p, goals);
            cost.put(p, 0);
            queue.add(new Node(p, h, 0));
            parent.put(p, null);
        }
        HashSet<BlockPos> closed = new HashSet<BlockPos>();
        int visited = 0;
        BlockPos found = null;
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            BlockPos p = node.pos;
            if (closed.contains(p)) continue;
            closed.add(p);
            if (goals.contains(p)) {
                found = p;
                break;
            }
            if (++visited > limit) {
                break;
            }
            int c = cost.get(p);
            for (EnumFacing f : EnumFacing.VALUES) {
                BlockPos n = p.offset(f);
                if (!open.contains(n) || closed.contains(n)) continue;
                int stepCost = 1 + (p.getY() != n.getY() ? 1 : 0);
                int nc = c + stepCost;
                Integer old = cost.get(n);
                if (old == null || nc < old) {
                    cost.put(n, nc);
                    parent.put(n, p);
                    int h = BlockInUtils.nearestDistance(n, goals);
                    queue.add(new Node(n, nc + h, nc));
                }
            }
        }
        if (found == null) {
            return null;
        }
        ArrayList<BlockPos> route = new ArrayList<BlockPos>();
        for (BlockPos p = found; p != null; p = parent.get(p)) {
            route.add(p);
        }
        Collections.reverse(route);
        ArrayList<BlockPos> out = new ArrayList<BlockPos>();
        for (BlockPos p : route) {
            if (!BlockInUtils.isSupport(BlockInUtils.getBlock(p)) && !BlockInUtils.isPlayerBlock(p)) {
                out.add(p);
            }
        }
        return out;
    }

    private static void markFailed() {
        if (targetPos != null) {
            failed.add(targetPos);
            if (path != null && !path.isEmpty() && targetPos.equals(path.get(0))) {
                if (!retrySet.contains(targetPos)) {
                    addRetry(targetPos);
                }
                path = null;
            }
            if (!retrySet.contains(targetPos)) {
                if (!BlockInUtils.isPlayerBlock(targetPos)) {
                    addRetry(targetPos);
                }
            }
        }
    }

    private static void addRetry(BlockPos pos) {
        if (retry.size() >= 3) {
            BlockPos old = retry.pollFirst();
            if (old != null) {
                retrySet.remove(old);
            }
        }
        retry.addLast(pos);
        retrySet.add(pos);
    }

    private static void resetAim() {
        fromYaw = null;
        fromPitch = null;
        aimYaw = 0.0f;
        aimPitch = 0.0f;
        aimedAgainst = null;
        aimedFace = null;
    }

    private static boolean hasTarget() {
        return against != null && face != null && targetPos != null && !BlockInUtils.isPlayerBlock(targetPos) && BlockInUtils.isReplaceable(targetPos);
    }


    private static void reset() {
        if (active) {
            active = false;
            stoppedAt = System.currentTimeMillis();
        }
        middleHeld = false;
        previousSlot = -1;
        blockSlot = -1;
        path = null;
        retry.clear();
        retrySet.clear();
        failed.clear();
        step = 0;
        resetAim();
        lastPitch = null;
        lastYaw = null;
        refYaw = null;
        clearTarget();
        startFacing = null;
        facings = null;
    }

    private static void setFrom(float yaw, float pitch) {
        fromYaw = yaw;
        fromPitch = pitch;
    }

    private static float[] finish(float yaw, float pitch) {
        float[] rot = BlockInUtils.fixSensitivity(yaw, pitch, lastYaw != null ? lastYaw : mc.thePlayer.rotationYaw, lastPitch != null ? lastPitch : mc.thePlayer.rotationPitch);
        lastYaw = rot[0];
        lastPitch = rot[1];
        if (refYaw == null) {
            refYaw = lastYaw;
        }
        refYaw = unWrap(lastYaw, refYaw);
        return new float[]{lastYaw, lastPitch};
    }

    private static void clearTarget() {
        against = null;
        face = null;
        targetPos = null;
    }

    private static void resetSteps() {
        steps = delay;
        step = 0;
    }

    private static boolean findSupportUp(BlockPos pos) {
        if (BlockInUtils.isPlayerBlock(pos)) {
            return false;
        }
        return findSupport(pos, ORDER_UP);
    }

    private static float enclosure(int x, int y, int z) {
        int n = 0;
        if (solid(x, y + 2, z)) ++n;
        if (solid(x + 1, y, z)) ++n;
        if (solid(x - 1, y, z)) ++n;
        if (solid(x, y, z + 1)) ++n;
        if (solid(x, y, z - 1)) ++n;
        if (solid(x + 1, y + 1, z)) ++n;
        if (solid(x - 1, y + 1, z)) ++n;
        if (solid(x, y + 1, z + 1)) ++n;
        if (solid(x, y + 1, z - 1)) ++n;
        return (float) n / 9.0f;
    }

    private static EnumFacing[] facings() {
        return facings != null ? facings : new EnumFacing[]{EnumFacing.EAST, EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.SOUTH};
    }

    private static float unWrap(float yaw, float ref) {
        float d = MathHelper.wrapAngleTo180_float(yaw - ref);
        return ref + d;
    }

    @SubscribeEvent
    public void onMouse(MouseEvent e) {
        if (e.buttonstate && e.button != 0 && active) {
            e.setCanceled(true);
        }
    }

    private static boolean solid(int x, int y, int z) {
        MUTABLE.set(x, y, z);
        return BlockInUtils.isSupport(BlockInUtils.getBlock(MUTABLE));
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (!showPercentage || percentText == null || e.phase != TickEvent.Phase.END) return;
        if (!Utils.nullCheck() || !mc.inGameHasFocus || mc.gameSettings.showDebugInfo || mc.currentScreen != null) return;

        long now = System.currentTimeMillis();
        int a;
        long age;
        if (!active) {
            age = now - stoppedAt;
            if (age >= 125L) {
                a = 0;
                percentText = null;
            } else {
                a = (int) ((float) 240 - (float) age / (float) 125L * (float) 240);
            }
        } else {
            age = now - startedAt;
            if (age >= 125L) {
                a = 240;
            } else {
                a = (int) ((float) age / (float) 125L * (float) 240);
            }
        }
        if (a > 0) {
            ScaledResolution sr = new ScaledResolution(mc);
            float x = (float) sr.getScaledWidth() / 2.0f - percentHalfWidth;
            float y = (float) sr.getScaledHeight() / 2.0f + 11.0f;
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            mc.fontRendererObj.drawString(percentText, x, y, withAlpha(percentColor, clampAlpha(a)), true);
            GlStateManager.disableBlend();
        }
    }

    private static int withAlpha(int rgb, int alpha) {
        return rgb & 0xFFFFFF | alpha << 24;
    }

    private static int clampAlpha(int n) {
        if (n > 255) return 255;
        if (n < 4) return 4;
        return n;
    }

    private static boolean findSupport(BlockPos pos) {
        BlockPos own = BlockInUtils.playerPos();
        if (!pos.equals(own.north()) && !pos.equals(own.south())) {
            BlockPos below = own.down();
            return !pos.equals(below.east()) && !pos.equals(below.west()) && !pos.equals(below.north()) && !pos.equals(below.south())
                    ? findSupport(pos, ORDER) : findSupportUp(pos);
        }
        return false;
    }

    private static boolean findSupport(BlockPos pos, EnumFacing[] order) {
        if (BlockInUtils.isPlayerBlock(pos)) {
            return false;
        }
        for (EnumFacing f : order) {
            BlockPos n = pos.offset(f.getOpposite());
            if (ownVertical(n, f)) continue;
            Block block = BlockInUtils.getBlock(n);
            if (BlockInUtils.isSupport(block)) {
                against = n;
                face = f;
                targetPos = pos;
                return true;
            }
        }
        return false;
    }

    @Override
    public void disable() {
        if (getModuleInstance() != null) {
            onDisable();
        }
    }
}
