package coffee.axle.suim.feature.combat;

import coffee.axle.suim.clickgui.mode.cga.ClickGuiScreen;
import coffee.axle.suim.clickgui.mode.flopper.PanelClickGuiScreen;
import coffee.axle.suim.eventbus.AimAssistHook;
import coffee.axle.suim.eventbus.DisplaceHook;
import coffee.axle.suim.events.ReceivePacketEvent;
import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.feature.movement.Stasis;
import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.util.HudUtils;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.PacketUtils;
import coffee.axle.suim.util.Utils;
import net.minecraft.client.Minecraft;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * OpenB4 Backtrack port. Holds inbound packets while the target walks out of
 * your reach so the server still thinks they're in range when you swing.
 * show-position is myau LagRange's impl (ksyz style) instead of raven's ghost
 * player: interpolated box, team color or HUD color, alpha 63 fill.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class Backtrack extends Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int SHOW_NONE = 0;
    @SuppressWarnings("unused")
    private static final int SHOW_DEFAULT = 1;
    private static final int SHOW_HUD = 2;

    // raven used SettingsModule.hittableHurtTime, we just bake it
    private static final int HITTABLE_HURT_TIME = 3;

    private Object moduleInstance;
    private Object delayProperty;
    private Object distanceMinProperty;
    private Object distanceMaxProperty;
    private Object cooldownProperty;
    private Object fovProperty;
    private Object adventureProperty;
    private Object disableChaseProperty;
    private Object flushDamageProperty;
    private Object ignoreTeamProperty;
    private Object showPositionProperty;
    private Object onlyHittableProperty;
    private Object onlyMovementProperty;
    private Object weaponOnlyProperty;

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

    // lagrange style render history of the target's server position
    private Vec3 previousPosition;
    private Vec3 currentPosition;

    private double minRange, maxRange, maxRangeSq, scanRangeSq;

    // myau.kY + friend list reflection, shared with the aim assist filters
    private static Method isBotMethod;
    private static Method isTeamMethod;
    private static Method teamColorMethod;
    private static List<String> friendNames = new ArrayList<String>();
    private static boolean myauFiltersTried;

    @Override
    public String getName() {
        return "Backtrack";
    }

    @Override
    public GuiCategory getGuiCategory() {
        return GuiCategory.COMBAT;
    }

    @Override
    public boolean initialize() {
        try {
            moduleInstance = createModule();
            creator.injectModule(moduleInstance, Backtrack.class);
            delayProperty = creator.createIntegerProperty("delay", 150, 50, 1500);
            distanceMinProperty = creator.createFloatProperty("distance-min", 2.7f, 0.0f, 8.0f);
            distanceMaxProperty = creator.createFloatProperty("distance-max", 5.0f, 2.0f, 8.0f);
            cooldownProperty = creator.createFloatProperty("cooldown", 0.0f, 0.0f, 8.0f);
            fovProperty = creator.createFloatProperty("fov", 360.0f, 20.0f, 360.0f);
            adventureProperty = creator.createBooleanProperty("adventure", true);
            disableChaseProperty = creator.createBooleanProperty("disable-chase", false);
            flushDamageProperty = creator.createBooleanProperty("flush-damage", true);
            ignoreTeamProperty = creator.createBooleanProperty("ignore-team", true);
            showPositionProperty = creator.createEnumProperty("show-position", SHOW_NONE,
                    new String[] { "NONE", "DEFAULT", "HUD" });
            onlyHittableProperty = creator.createBooleanProperty("only-hittable", false);
            onlyMovementProperty = creator.createBooleanProperty("only-movement", false);
            weaponOnlyProperty = creator.createBooleanProperty("weapon-only", false);
            creator.registerProperties(moduleInstance,
                    delayProperty, distanceMinProperty, distanceMaxProperty,
                    cooldownProperty, fovProperty, adventureProperty,
                    disableChaseProperty, flushDamageProperty, ignoreTeamProperty,
                    showPositionProperty, onlyHittableProperty, onlyMovementProperty,
                    weaponOnlyProperty);
            manager.reloadModuleCommand();

            MinecraftForge.EVENT_BUS.register(this);
            manager.registerModuleCallbacks(moduleInstance, () -> {
            }, this::onDisable);
            setSuffixDynamic(() -> new String[] { properties.getInt(delayProperty, 150) + "ms" });
            return true;
        } catch (Exception e) {
            MyauLogger.error("Backtrack:init", e);
            return false;
        }
    }

    private boolean bool(Object prop, boolean def) {
        return properties.getBoolean(prop, def);
    }

    private void onDisable() {
        flush();
    }

    private boolean canStart() {
        // raven gates on their freeze + antiknockback delay, stasis and
        // knockback delay are the suim equivalents
        if (Stasis.frozen || KnockbackDelay.active) {
            return false;
        }
        // raven quirk: cooldown only applies once the user changed it from default
        if (!isNotDefault(cooldownProperty)) {
            return true;
        }
        float cooldown = properties.getFloat(cooldownProperty, 0.0f);
        return Math.abs(System.currentTimeMillis() - lastFlush) > cooldown * 1000.0f - 20.0f;
    }

    private boolean isNotDefault(Object prop) {
        try {
            Class<?> base = prop.getClass().getSuperclass();
            Field def = base.getDeclaredField(MyauMappings.FIELD_VALUE_DEFAULT);
            Field cur = base.getDeclaredField(MyauMappings.FIELD_VALUE_CURRENT);
            def.setAccessible(true);
            cur.setAccessible(true);
            Object d = def.get(prop);
            Object c = cur.get(prop);
            return d == null ? c != null : !d.equals(c);
        } catch (Exception e) {
            return false;
        }
    }

    private void start() {
        delayTicks = Math.max(1, properties.getInt(delayProperty, 150) / 50);
        updateServerPos();
        holding = true;
    }

    @SubscribeEvent
    public void onUpdate(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        if (!manager.isModuleEnabled(moduleInstance)) {
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

        // lagrange: previous becomes current, current becomes the latest server pos
        if (target != null) {
            Vec3 pos = serverPos;
            previousPosition = currentPosition == null ? pos : currentPosition;
            currentPosition = pos;
        }
    }

    private void updateRanges() {
        minRange = properties.getFloat(distanceMinProperty, 2.7f);
        maxRange = properties.getFloat(distanceMaxProperty, 5.0f);
        double d = maxRange + 1.2;
        double d2 = maxRange + 2.5;
        maxRangeSq = d * d;
        scanRangeSq = d2 * d2;
    }

    private void updateServerPos() {
        serverPos = new Vec3(target.serverPosX / 32.0, target.serverPosY / 32.0, target.serverPosZ / 32.0);
    }

    private boolean valid(EntityLivingBase e) {
        if (bool(onlyHittableProperty, false) && e.hurtTime > HITTABLE_HURT_TIME) {
            return false;
        }
        return !bool(disableChaseProperty, false) || !awayFromPlayer(e);
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
                || bool(adventureProperty, true) && !mc.thePlayer.capabilities.allowEdit) {
            return false;
        }
        return !bool(weaponOnlyProperty, false) || holdingWeapon();
    }

    private static boolean holdingWeapon() {
        // raven also counts axe/hoe/shovel but those global toggles default off
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
        double half = properties.getFloat(fovProperty, 360.0f) / 2.0;
        double best = 999.0;
        EntityPlayer found = null;
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.deathTime != 0) continue;
            if (!valid(p)) continue;
            if (isBot(p)) continue;
            if (mc.thePlayer.getDistanceSqToEntity(p) > scanRangeSq) continue;
            double yaw = Math.abs(yawDifference(p));
            if (yaw > half) continue;
            if (isFriend(p)) continue;
            if (bool(ignoreTeamProperty, true) && isTeammate(p)) continue;
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
        if (!manager.isModuleEnabled(moduleInstance)) {
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
            if (!flush && bool(flushDamageProperty, true) && packet instanceof S12PacketEntityVelocity
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
        if (holding && !(bool(onlyMovementProperty, false) ? !moves : passesThrough(packet))) {
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
                    ((Packet) packet).processPacket(mc.getNetHandler());
                }
            } catch (ThreadQuickExitException quickExit) {
                // expected: off the main thread processPacket schedules onto
                // the main thread and throws to abort, same as vanilla
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
        if (e.world != null && e.world.isRemote && manager.isModuleEnabled(moduleInstance)) {
            clearHeld();
        }
    }

    // --- myau lagrange show-position, 1:1 style ---

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        int mode = properties.getInt(showPositionProperty, SHOW_NONE);
        if (!holding || mode == SHOW_NONE || target == null) {
            return;
        }
        if (previousPosition == null || currentPosition == null) {
            return;
        }
        if (!Utils.nullCheck()) {
            return;
        }
        Color color = mode == SHOW_HUD
                ? HudUtils.getInstance().getHudColor()
                : teamColor(target instanceof EntityPlayer ? (EntityPlayer) target : null);
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

    // --- raven math helpers, toBox aims at the closest point on the box ---

    private static float[] toBox(Entity entity, AxisAlignedBB box) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
        float f = entity.getCollisionBorderSize();
        box = box.expand(f, f, f);
        if (box.isVecInside(eyes)) {
            return new float[] { mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch };
        }
        // raven aimPoint: blend between the 0.825-height center and the closest
        // point to the eyes, their aim-closest-point slider defaults to 90 so t = 0.9
        double cx = (box.minX + box.maxX) / 2.0;
        double cy = box.minY + (box.maxY - box.minY) * 0.825;
        double cz = (box.minZ + box.maxZ) / 2.0;
        double m = 0.03;
        double px = MathHelper.clamp_double(eyes.xCoord, box.minX + m, box.maxX - m);
        double py = MathHelper.clamp_double(eyes.yCoord, box.minY + m, box.maxY - m);
        double pz = MathHelper.clamp_double(eyes.zCoord, box.minZ + m, box.maxZ - m);
        double t = 0.9;
        return fromDelta(cx + (px - cx) * t - eyes.xCoord,
                cy + (py - cy) * t - eyes.yCoord,
                cz + (pz - cz) * t - eyes.zCoord);
    }

    private static float[] fromDelta(double dx, double dy, double dz) {
        double h = Math.sqrt(dx * dx + dz * dz);
        return new float[] {
                (float) (Math.atan2(dz, dx) * 57.29577951308232) - 90.0f,
                (float) (-(Math.atan2(dy, h) * 57.29577951308232)) };
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

    // --- myau filters (kY bot/team/teamColor + friend list), shared with bowaim ---

    static boolean isBot(EntityPlayer p) {
        initMyauFilters();
        try {
            return isBotMethod != null && (Boolean) isBotMethod.invoke(null, p);
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isTeammate(EntityPlayer p) {
        initMyauFilters();
        try {
            return isTeamMethod != null && (Boolean) isTeamMethod.invoke(null, p);
        } catch (Exception e) {
            return false;
        }
    }

    private static Color teamColor(EntityPlayer p) {
        initMyauFilters();
        try {
            if (teamColorMethod != null && p != null) {
                return (Color) teamColorMethod.invoke(null, p, 1.0f);
            }
        } catch (Exception ignored) {
        }
        return new Color(-1);
    }

    static boolean isFriend(EntityPlayer p) {
        initMyauFilters();
        return friendNames.contains(p.getName());
    }

    private static void initMyauFilters() {
        if (myauFiltersTried) {
            return;
        }
        myauFiltersTried = true;
        try {
            Class<?> util = Class.forName(MyauMappings.CLASS_TEAM_UTIL);
            isBotMethod = util.getMethod(MyauMappings.METHOD_TEAM_UTIL_IS_BOT, EntityPlayer.class);
            isTeamMethod = util.getMethod(MyauMappings.METHOD_TEAM_UTIL_IS_TEAM, EntityPlayer.class);
            teamColorMethod = util.getMethod("T", EntityPlayer.class, float.class);
        } catch (Exception e) {
            MyauLogger.error("Backtrack:kY", e);
        }
        try {
            Class<?> myauClass = Class.forName(MyauMappings.CLASS_MAIN);
            Field friendManagerField = myauClass.getDeclaredField(MyauMappings.FIELD_FRIEND_MANAGER);
            friendManagerField.setAccessible(true);
            Object friendManager = friendManagerField.get(null);
            Field playersField = friendManager.getClass().getSuperclass()
                    .getDeclaredField(MyauMappings.FIELD_PLAYER_LIST);
            playersField.setAccessible(true);
            friendNames = (List<String>) playersField.get(friendManager);
        } catch (Exception e) {
            MyauLogger.error("Backtrack:friends", e);
        }
    }
}
