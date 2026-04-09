package coffee.axle.suim.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.client.C01PacketEncryptionResponse;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BlinkManager {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final BlinkManager INSTANCE = new BlinkManager();

    public enum BlinkModule {
        NONE,
        SHOP_BLINK,
        VELOCITY_BUFFER,
        GENERIC
    }

    private final Queue<Packet<?>> blinkedPackets = new ConcurrentLinkedQueue<>();
    private volatile boolean blinking = false;
    private BlinkModule blinkModule = BlinkModule.NONE;

    public static BlinkManager getInstance() {
        return INSTANCE;
    }

    public boolean tryBlink(Packet<?> packet) {
        if (!blinking) return false;
        if (packet instanceof C00PacketKeepAlive || packet instanceof C01PacketChatMessage) {
            return false;
        }
        if (blinkedPackets.isEmpty() && packet instanceof C0FPacketConfirmTransaction) {
            return false;
        }
        blinkedPackets.offer(packet);
        return true;
    }

    public boolean setBlinkState(boolean state, BlinkModule module) {
        if (module == BlinkModule.NONE) return false;

        if (state) {
            if (blinking && blinkModule != module) return false;
            this.blinkModule = module;
            this.blinking = true;
        } else {
            if (blinkModule != module) return false;
            this.blinking = false;
            flush();
            this.blinkModule = BlinkModule.NONE;
        }
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void flush() {
        if (mc.getNetHandler() == null || blinkedPackets.isEmpty()) return;
        for (Packet<?> packet : blinkedPackets) {
            PacketUtils.sendPacketNoEvent((Packet) packet);
        }
        blinkedPackets.clear();
    }

    public void forceStop() {
        blinking = false;
        flush();
        blinkModule = BlinkModule.NONE;
    }

    public BlinkModule getBlinkingModule() {
        return blinkModule;
    }

    public long countMovement() {
        return blinkedPackets.stream()
                .filter(p -> p instanceof net.minecraft.network.play.client.C03PacketPlayer)
                .count();
    }

    public boolean isBlinking() {
        return blinking;
    }

    public boolean isBlinking(BlinkModule module) {
        return blinking && blinkModule == module;
    }

    public void onLoginPacket(Packet<?> packet) {
        if (packet instanceof C00Handshake
                || packet instanceof C00PacketLoginStart
                || packet instanceof C00PacketServerQuery
                || packet instanceof C01PacketPing
                || packet instanceof C01PacketEncryptionResponse) {
            forceStop();
        }
    }

    public void onPlayerDeath() {
        forceStop();
    }
}
