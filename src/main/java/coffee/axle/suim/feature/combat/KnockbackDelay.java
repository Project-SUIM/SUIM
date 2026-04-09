package coffee.axle.suim.feature.combat;

import coffee.axle.suim.events.ReceivePacketEvent;
import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.PacketUtils;
import coffee.axle.suim.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
@SuppressWarnings({ "unchecked", "unused" })
public class KnockbackDelay extends Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private Object moduleInstance;
    private Object distanceProperty;
    private Object chanceProperty;
    private Object maxDelayProperty;
    private Object inAirProperty;
    private Object onlyGroundProperty;

    private final Queue<BufferedPacket> bufferedPackets = new ConcurrentLinkedQueue<>();
    private boolean active = false;

    @Override
    public String getName() {
        return "KnockbackDelay";
    }

    @Override
    public GuiCategory getGuiCategory() {
        return GuiCategory.COMBAT;
    }

    @Override
    public boolean initialize() {
        try {
            moduleInstance = createModule();
            creator.injectModule(moduleInstance, KnockbackDelay.class);

            distanceProperty = creator.createFloatProperty("distance", 6.0f, 3.0f, 12.0f);
            chanceProperty = creator.createIntegerProperty("chance", 100, 0, 100);
            maxDelayProperty = creator.createIntegerProperty("max-delay-ms", 200, 50, 1000);
            inAirProperty = creator.createBooleanProperty("in-air", true);
            onlyGroundProperty = creator.createBooleanProperty("only-ground", false);

            creator.registerProperties(moduleInstance, distanceProperty, chanceProperty,
                    maxDelayProperty, inAirProperty, onlyGroundProperty);
            manager.reloadModuleCommand();

            MinecraftForge.EVENT_BUS.register(this);

            manager.registerModuleCallbacks(moduleInstance, this::onEnable, this::onDisable);

            return true;
        } catch (Exception e) {
            MyauLogger.error("KnockbackDelay:init", e);
            return false;
        }
    }

    private void onEnable() {
        active = false;
        bufferedPackets.clear();
    }

    private void onDisable() {
        flushAll();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onReceivePacket(ReceivePacketEvent e) {
        if (!manager.isModuleEnabled(moduleInstance)) return;
        if (!Utils.nullCheck()) return;

        if (e.getPacket() instanceof S08PacketPlayerPosLook) {
            flushAll();
            return;
        }

        if (!(e.getPacket() instanceof S12PacketEntityVelocity)) return;

        S12PacketEntityVelocity vel = (S12PacketEntityVelocity) e.getPacket();
        if (vel.getEntityID() != mc.thePlayer.getEntityId()) return;

        if (!passesConditions()) return;

        int chance = properties.getInt(chanceProperty, 100);
        if (chance < 100 && Math.random() * 100.0 >= chance) return;

        if (active) return;

        active = true;
        bufferedPackets.add(new BufferedPacket(e.getPacket(), System.currentTimeMillis()));
        e.setCanceled(true);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        if (!manager.isModuleEnabled(moduleInstance)) return;
        if (!Utils.nullCheck() || mc.thePlayer.isDead) {
            flushAll();
            return;
        }

        if (!active || bufferedPackets.isEmpty()) return;

        if (!passesConditions()) {
            flushAll();
            return;
        }

        int maxDelay = properties.getInt(maxDelayProperty, 200);
        long now = System.currentTimeMillis();
        boolean flushed = false;

        while (!bufferedPackets.isEmpty()) {
            BufferedPacket bp = bufferedPackets.peek();
            if (now - bp.timestamp >= maxDelay) {
                bufferedPackets.poll();
                processPacket(bp.packet);
                flushed = true;
            } else {
                break;
            }
        }

        if (bufferedPackets.isEmpty()) {
            active = false;
        }
    }

    private boolean passesConditions() {
        double dist = properties.getFloat(distanceProperty, 6.0f);
        double distSq = dist * dist;
        boolean foundTarget = false;

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || player.isDead || player.deathTime != 0) continue;
            if (mc.thePlayer.getDistanceSqToEntity(player) <= distSq) {
                foundTarget = true;
                break;
            }
        }
        if (!foundTarget) return false;

        boolean inAir = properties.getBoolean(inAirProperty, true);
        if (inAir && mc.thePlayer.onGround) return false;

        boolean onlyGround = properties.getBoolean(onlyGroundProperty, false);
        if (onlyGround && !mc.thePlayer.onGround) return false;

        return true;
    }

    @SuppressWarnings({"rawtypes"})
    private void processPacket(Packet<?> packet) {
        try {
            Packet raw = (Packet) packet;
            PacketUtils.skipReceiveEvent.add(raw);
            raw.processPacket(mc.getNetHandler());
        } catch (Exception ex) {
            MyauLogger.error("KnockbackDelay:process", ex);
        }
    }

    private void flushAll() {
        while (!bufferedPackets.isEmpty()) {
            BufferedPacket bp = bufferedPackets.poll();
            processPacket(bp.packet);
        }
        active = false;
    }

    private static class BufferedPacket {
        final Packet<?> packet;
        final long timestamp;

        BufferedPacket(Packet<?> packet, long timestamp) {
            this.packet = packet;
            this.timestamp = timestamp;
        }
    }
}
