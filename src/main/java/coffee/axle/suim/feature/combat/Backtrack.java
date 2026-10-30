package coffee.axle.suim.feature.combat;

import coffee.axle.suim.clickgui.mode.cga.ClickGuiScreen;
import coffee.axle.suim.clickgui.mode.flopper.PanelClickGuiScreen;
import coffee.axle.suim.eventbus.AimAssistHook;
import coffee.axle.suim.eventbus.DisplaceHook;
import coffee.axle.suim.events.ReceivePacketEvent;
import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.property.Setting;
import coffee.axle.suim.feature.movement.Stasis;
import coffee.axle.suim.rotation.RotationMath;
import coffee.axle.suim.util.HudUtils;
import coffee.axle.suim.util.HypixelUtils;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.PacketUtils;
import coffee.axle.suim.util.Utils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.ThreadQuickExitException;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.network.play.server.S2APacketParticles;
import net.minecraft.network.play.server.S3BPacketScoreboardObjective;
import net.minecraft.network.play.server.S3CPacketUpdateScore;
import net.minecraft.network.play.server.S3DPacketDisplayScoreboard;
import net.minecraft.network.play.server.S3EPacketTeams;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static coffee.axle.suim.ProjectSUIM.mc;

/**
 * Holds inbound packets while the target walks out of reach so the server
 * still thinks they're in range when you swing.
 */
public class Backtrack extends Feature {

    public enum Show {
        NONE, DEFAULT, HUD
    }

    // below this hurt time the target is still hittable
    private static final int HITTABLE_HURT_TIME = 3;

    @Setting(min = 50, max = 1500) private int delay = 150;
    @Setting(min = 0, max = 8) private float distanceMin = 2.7f;
    @Setting(min = 2, max = 8) private float distanceMax = 5.0f;
    @Setting(min = 0, max = 8) private float cooldown = 0.0f;
    @Setting(min = 20, max = 360) private float fov = 360.0f;
    @Setting private Show showPosition = Show.NONE;
    @Setting private boolean adventure = true;
    @Setting private boolean disableChase = false;
    @Setting private boolean flushDamage = true;
    @Setting private boolean ignoreTeam = true;
    @Setting private boolean onlyHittable = false;
    @Setting private boolean onlyMovement = false;
    @Setting private boolean weaponOnly = false;

    private final TreeMap<Integer, List<Packet<?>>> held = new TreeMap<Integer, List<Packet<?>>>();
    private final Object lock = new Object();
    private int tick;
    private volatile int delayTicks;

    private volatile boolean holding;
    private volatile boolean ready;
    private volatile EntityLivingBase target;
    private volatile Vec3 serverPos;
    private volatile Packet<?> processing;
    private volatile long lastFlush;

    // last two server positions of the target, interpolated for the box
    private Vec3 previousPosition;
    private Vec3 currentPosition;

    private double minRange, maxRange, maxRangeSq, scanRangeSq;

    public Backtrack() {
        super("Backtrack", GuiCategory.COMBAT);
    }

    @Override
    protected void init() throws Exception {
        setSuffixDynamic(() -> new String[] { delay + "ms" });
    }

    @Override
    protected void onDisable() {
        flush();
    }

    private boolean canStart() {
        // stasis and knockback delay both move the player server side, holding
        // packets during either would stack
        if (Stasis.frozen || KnockbackDelay.active) {
            return false;
        }
        // cooldown only kicks in once the user actually changed it
        if (!isNotDefault()) {
            return true;
        }
        float c = cooldown;
        return Math.abs(System.currentTimeMillis() - lastFlush) > c * 1000.0f - 20.0f;
    }

    private boolean isNotDefault() {
        return setting("cooldown") == null || setting("cooldown").isDefault();
    }

    private void start() {
        delayTicks = Math.max(1, delay / 50);
        updateServerPos();
        holding = true;
    }

    @SubscribeEvent
    public void onUpdate(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        if (!isEnabled()) {
            return;
        }
        if (!Utils.nullCheck()) {
            clearHeld();
            return;
        }
        updateRanges();

        EntityLivingBase found = findTarget();
        if (holding) {
            if (found != target || !targetValid()) {
                flush();
            } else {
                releaseDue();
            }
        } else {
            if (found != null) {
                target = found;
                updateServerPos();
            }
            ready = target != null && targetValid();
        }

        if (target != null) {
            Vec3 pos = serverPos;
            previousPosition = currentPosition == null ? pos : currentPosition;
            currentPosition = pos;
        }
    }

    private void updateRanges() {
        minRange = distanceMin;
        maxRange = distanceMax;
        double d = maxRange + 1.2;
        double d2 = maxRange + 2.5;
        maxRangeSq = d * d;
        scanRangeSq = d2 * d2;
    }

    private void updateServerPos() {
        serverPos = new Vec3(target.serverPosX / 32.0, target.serverPosY / 32.0, target.serverPosZ / 32.0);
    }

    private boolean valid(EntityLivingBase e) {
        if (onlyHittable && e.hurtTime > HITTABLE_HURT_TIME) {
            return false;
        }
        return !disableChase || !awayFromPlayer(e);
    }

    private boolean targetValid() {
        if (!Utils.nullCheck() || mc.isSingleplayer()) {
            return false;
        }
        if (target == null || mc.theWorld.getEntityByID(target.getEntityId()) == null || target.deathTime != 0) {
            return false;
        }
        if (mc.currentScreen != null
                && !(mc.currentScreen instanceof ClickGuiScreen)
                && !(mc.currentScreen instanceof PanelClickGuiScreen)) {
            return false;
        }
        if (mc.thePlayer.capabilities.allowFlying
                || adventure && !mc.thePlayer.capabilities.allowEdit) {
            return false;
        }
        return !weaponOnly || holdingWeapon();
    }

    private static boolean holdingWeapon() {
        return mc.thePlayer.getHeldItem() != null
                && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword;
    }

    private EntityLivingBase findTarget() {
        // killaura target first, then aim assist, then a plain fov scan
        Entity ka = DisplaceHook.kaTarget();
        if (ka instanceof EntityLivingBase && valid((EntityLivingBase) ka)) {
            return (EntityLivingBase) ka;
        }
        EntityLivingBase aa = AimAssistHook.currentTarget();
        if (aa != null && valid(aa)) {
            return aa;
        }
        double half = fov / 2.0;
        double best = 999.0;
        EntityPlayer found = null;
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.deathTime != 0) continue;
            if (!valid(p)) continue;
            if (HypixelUtils.isBot(p)) continue;
            if (mc.thePlayer.getDistanceSqToEntity(p) > scanRangeSq) continue;
            double yaw = Math.abs(yawDifference(p));
            if (yaw > half) continue;
            if (HypixelUtils.isFriend(p)) continue;
            if (ignoreTeam && HypixelUtils.isTeammate(p)) continue;
            if (yaw >= best) continue;
            best = yaw;
            found = p;
        }
        return found;
    }

    private static double yawDifference(Entity e) {
        double dx = e.posX - mc.thePlayer.posX;
        double dz = e.posZ - mc.thePlayer.posZ;
        float yawTo = (float) (Math.atan2(dz, dx) * 57.29577951308232) - 90.0f;
        return MathHelper.wrapAngleTo180_double(yawTo - mc.thePlayer.rotationYaw);
    }

    private boolean awayFromPlayer(EntityLivingBase e) {
        if (mc.thePlayer.getDistanceSqToEntity(e) <= 1.0) {
            return false;
        }
        double dx = mc.thePlayer.posX - e.posX;
        double dz = mc.thePlayer.posZ - e.posZ;
        float yawTo = (float) (Math.atan2(dz, dx) * 57.29577951308232) - 90.0f;
        return Math.abs(MathHelper.wrapAngleTo180_double(yawTo - e.rotationYaw)) > 90.0;
    }

    private static boolean passesThrough(Packet<?> packet) {
        return packet instanceof S02PacketChat || packet instanceof S06PacketUpdateHealth
                || packet instanceof S14PacketEntity.S16PacketEntityLook
                || packet instanceof S19PacketEntityStatus || packet instanceof S22PacketMultiBlockChange
                || packet instanceof S23PacketBlockChange || packet instanceof S29PacketSoundEffect
                || packet instanceof S2APacketParticles || packet instanceof S3BPacketScoreboardObjective
                || packet instanceof S3CPacketUpdateScore || packet instanceof S3DPacketDisplayScoreboard
                || packet instanceof S3EPacketTeams;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onReceivePacket(ReceivePacketEvent e) {
        if (!isEnabled()) {
            return;
        }
        Packet<?> packet = e.getPacket();
        if (packet == processing || packet == PacketUtils.reprocessing) {
            return;
        }
        if (!Utils.nullCheck()) {
            clearHeld();
            return;
        }

        if (holding) {
            boolean flush = packet instanceof S08PacketPlayerPosLook || packet instanceof S40PacketDisconnect;
            if (!flush && flushDamage && packet instanceof S12PacketEntityVelocity
                    && ((S12PacketEntityVelocity) packet).getEntityID() == mc.thePlayer.getEntityId()) {
                flush = true;
            }
            if (flush) {
                flush();
                return;
            }
        }
        if (!ready) {
            return;
        }

        Vec3 pos = serverPos;
        double x = pos.xCoord;
        double y = pos.yCoord;
        double z = pos.zCoord;
        boolean moves = false;
        boolean teleport = false;
        if (packet instanceof S14PacketEntity.S15PacketEntityRelMove
                || packet instanceof S14PacketEntity.S17PacketEntityLookMove) {
            S14PacketEntity p = (S14PacketEntity) packet;
            Entity entity = p.getEntity(mc.theWorld);
            if (entity != null && target != null && entity.getEntityId() == target.getEntityId()) {
                moves = true;
                x += p.func_149062_c() / 32.0;
                y += p.func_149061_d() / 32.0;
                z += p.func_149064_e() / 32.0;
            }
        } else if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport p = (S18PacketEntityTeleport) packet;
            if (target != null && p.getEntityId() == target.getEntityId()) {
                teleport = true;
                moves = true;
                x = p.getX() / 32.0;
                y = p.getY() / 32.0;
                z = p.getZ() / 32.0;
            }
        }
        if (moves) {
            boolean flush = true;
            double distSq = mc.thePlayer.getDistanceSq(x, y, z);
            AxisAlignedBB box;
            AxisAlignedBB newBox;
            float[] rot;
            if (distSq <= maxRangeSq
                    && !rayHits(target, newBox = (box = target.getEntityBoundingBox())
                            .offset(x - target.posX, y - target.posY, z - target.posZ),
                            (rot = toBox(target, newBox))[0], rot[1], minRange)) {
                boolean inRange = rayHits(target, newBox, rot[0], rot[1], maxRange);
                if (inRange) {
                    AxisAlignedBB oldBox = box.offset(pos.xCoord - target.posX, pos.yCoord - target.posY,
                            pos.zCoord - target.posZ);
                    float[] oldRot = toBox(target, oldBox);
                    double newDist = rayDistanceSq(target, newBox, rot[0], rot[1], maxRange);
                    double oldDist = rayDistanceSq(target, oldBox, oldRot[0], oldRot[1], maxRange + 2.0);
                    if (oldDist >= 0.0 && newDist > oldDist && (!teleport || newDist - oldDist < 64.0)) {
                        if (!holding && canStart()) {
                            start();
                        }
                        serverPos = new Vec3(x, y, z);
                        flush = false;
                    }
                }
            }
            if (holding && flush) {
                flush();
                return;
            }
        }
        if (holding && !(onlyMovement ? !moves : passesThrough(packet))) {
            hold(packet);
            e.setCanceled(true);
        }
    }

    private void hold(Packet<?> packet) {
        synchronized (lock) {
            int n = tick + delayTicks;
            List<Packet<?>> list = held.computeIfAbsent(n, k -> new ArrayList<Packet<?>>());
            list.add(packet);
        }
    }

    private void releaseDue() {
        ArrayList<Packet<?>> list = null;
        synchronized (lock) {
            if (!held.isEmpty()) {
                Map.Entry<Integer, List<Packet<?>>> entry;
                Iterator<Map.Entry<Integer, List<Packet<?>>>> it = held.entrySet().iterator();
                while (it.hasNext() && tick >= (entry = it.next()).getKey()) {
                    List<Packet<?>> packets = entry.getValue();
                    if (!packets.isEmpty()) {
                        if (list == null) {
                            list = new ArrayList<Packet<?>>();
                        }
                        list.addAll(packets);
                        packets.clear();
                    }
                    it.remove();
                }
            }
            tick++;
        }
        process(list);
    }

    private void flush() {
        ArrayList<Packet<?>> list = null;
        synchronized (lock) {
            if (!held.isEmpty()) {
                for (List<Packet<?>> packets : held.values()) {
                    if (packets.isEmpty()) continue;
                    if (list == null) {
                        list = new ArrayList<Packet<?>>();
                    }
                    list.addAll(packets);
                    packets.clear();
                }
                held.clear();
            }
            reset();
        }
        process(list);
    }

    // world went away, dont feed dead packets into a dying net handler
    private void clearHeld() {
        synchronized (lock) {
            held.clear();
            reset();
        }
    }

    private void reset() {
        ready = false;
        holding = false;
        target = null;
        tick = 0;
        lastFlush = System.currentTimeMillis();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void process(List<Packet<?>> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (Packet<?> packet : list) {
            try {
                processing = packet;
                PacketUtils.reprocessing = packet;
                // re-fire so stasis & friends still see the released packet
                ReceivePacketEvent event = new ReceivePacketEvent(packet);
                MinecraftForge.EVENT_BUS.post(event);
                if (!event.isCanceled()) {
                    // unsafe, we dont care
                    ((Packet) packet).processPacket(mc.getNetHandler());
                }
            } catch (ThreadQuickExitException quickExit) {
            } catch (Throwable t) {
                MyauLogger.error("Backtrack:process " + packet.getClass().getSimpleName(), new Exception(t));
            } finally {
                processing = null;
                PacketUtils.reprocessing = null;
            }
        }
    }

    @SubscribeEvent
    public void onUnload(WorldEvent.Unload e) {
        if (e.world != null && e.world.isRemote && isEnabled()) {
            clearHeld();
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        Show mode = showPosition;
        if (!holding || mode == Show.NONE || target == null) {
            return;
        }
        if (previousPosition == null || currentPosition == null) {
            return;
        }
        if (!Utils.nullCheck()) {
            return;
        }
        Color color = mode == Show.HUD
                ? HudUtils.getInstance().getHudColor()
                : HypixelUtils.teamColor(target instanceof EntityPlayer ? (EntityPlayer) target : null);
        double x = Utils.lerp(currentPosition.xCoord, previousPosition.xCoord, e.partialTicks)
                - mc.getRenderManager().viewerPosX;
        double y = Utils.lerp(currentPosition.yCoord, previousPosition.yCoord, e.partialTicks)
                - mc.getRenderManager().viewerPosY;
        double z = Utils.lerp(currentPosition.zCoord, previousPosition.zCoord, e.partialTicks)
                - mc.getRenderManager().viewerPosZ;
        float border = target.getCollisionBorderSize();
        AxisAlignedBB bounds = new AxisAlignedBB(
                x - target.width / 2.0, y, z - target.width / 2.0,
                x + target.width / 2.0, y + target.height, z + target.width / 2.0)
                .expand(border, border, border);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableCull();

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(color.getRed() / 255.0f, color.getGreen() / 255.0f,
                color.getBlue() / 255.0f, 63 / 255.0f);
        // bottom
        GL11.glVertex3d(bounds.minX, bounds.minY, bounds.minZ);
        GL11.glVertex3d(bounds.maxX, bounds.minY, bounds.minZ);
        GL11.glVertex3d(bounds.maxX, bounds.minY, bounds.maxZ);
        GL11.glVertex3d(bounds.minX, bounds.minY, bounds.maxZ);
        // top
        GL11.glVertex3d(bounds.minX, bounds.maxY, bounds.minZ);
        GL11.glVertex3d(bounds.minX, bounds.maxY, bounds.maxZ);
        GL11.glVertex3d(bounds.maxX, bounds.maxY, bounds.maxZ);
        GL11.glVertex3d(bounds.maxX, bounds.maxY, bounds.minZ);
        // front
        GL11.glVertex3d(bounds.minX, bounds.minY, bounds.minZ);
        GL11.glVertex3d(bounds.minX, bounds.maxY, bounds.minZ);
        GL11.glVertex3d(bounds.maxX, bounds.maxY, bounds.minZ);
        GL11.glVertex3d(bounds.maxX, bounds.minY, bounds.minZ);
        // back
        GL11.glVertex3d(bounds.minX, bounds.minY, bounds.maxZ);
        GL11.glVertex3d(bounds.maxX, bounds.minY, bounds.maxZ);
        GL11.glVertex3d(bounds.maxX, bounds.maxY, bounds.maxZ);
        GL11.glVertex3d(bounds.minX, bounds.maxY, bounds.maxZ);
        // left
        GL11.glVertex3d(bounds.minX, bounds.minY, bounds.minZ);
        GL11.glVertex3d(bounds.minX, bounds.minY, bounds.maxZ);
        GL11.glVertex3d(bounds.minX, bounds.maxY, bounds.maxZ);
        GL11.glVertex3d(bounds.minX, bounds.maxY, bounds.minZ);
        // right
        GL11.glVertex3d(bounds.maxX, bounds.minY, bounds.minZ);
        GL11.glVertex3d(bounds.maxX, bounds.maxY, bounds.minZ);
        GL11.glVertex3d(bounds.maxX, bounds.maxY, bounds.maxZ);
        GL11.glVertex3d(bounds.maxX, bounds.minY, bounds.maxZ);
        GL11.glEnd();

        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    // toBox aims at the closest point on the box
    private static float[] toBox(Entity entity, AxisAlignedBB box) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
        float f = entity.getCollisionBorderSize();
        box = box.expand(f, f, f);
        if (box.isVecInside(eyes)) {
            return new float[] { mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch };
        }
        // blend between the 0.825 height center and the closest point to the
        // eyes, t = 0.9 matches the stock aim-closest-point setting
        double cx = (box.minX + box.maxX) / 2.0;
        double cy = box.minY + (box.maxY - box.minY) * 0.825;
        double cz = (box.minZ + box.maxZ) / 2.0;
        double m = 0.03;
        double px = MathHelper.clamp_double(eyes.xCoord, box.minX + m, box.maxX - m);
        double py = MathHelper.clamp_double(eyes.yCoord, box.minY + m, box.maxY - m);
        double pz = MathHelper.clamp_double(eyes.zCoord, box.minZ + m, box.maxZ - m);
        double t = 0.9;
        return RotationMath.toRotationsFromDelta(cx + (px - cx) * t - eyes.xCoord,
                cy + (py - cy) * t - eyes.yCoord,
                cz + (pz - cz) * t - eyes.zCoord);
    }

    private static Vec3 lookVec(float yaw, float pitch) {
        float f = MathHelper.cos(-yaw * 0.017453292f - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292f - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292f);
        float f3 = MathHelper.sin(-pitch * 0.017453292f);
        return new Vec3(f1 * f2, f3, f * f2);
    }

    private static boolean rayHits(Entity entity, AxisAlignedBB box, float yaw, float pitch, double range) {
        if (entity == null || !entity.canBeCollidedWith()) {
            return false;
        }
        Vec3 from = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 dir = lookVec(yaw, pitch);
        Vec3 to = from.addVector(dir.xCoord * range, dir.yCoord * range, dir.zCoord * range);
        float f = entity.getCollisionBorderSize();
        box = box.expand(f, f, f);
        return box.calculateIntercept(from, to) != null;
    }

    private static double rayDistanceSq(Entity entity, AxisAlignedBB box, float yaw, float pitch, double range) {
        if (entity == null || !entity.canBeCollidedWith()) {
            return -1.0;
        }
        Vec3 from = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 dir = lookVec(yaw, pitch);
        Vec3 to = from.addVector(dir.xCoord * range, dir.yCoord * range, dir.zCoord * range);
        float f = entity.getCollisionBorderSize();
        box = box.expand(f, f, f);
        net.minecraft.util.MovingObjectPosition hit = box.calculateIntercept(from, to);
        if (hit == null) {
            return -1.0;
        }
        return from.squareDistanceTo(hit.hitVec);
    }
}
