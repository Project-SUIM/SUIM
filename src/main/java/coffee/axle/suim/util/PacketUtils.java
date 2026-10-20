package coffee.axle.suim.util;

import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.Packet;

import java.util.ArrayList;
import java.util.List;

/**
 * Storing some packets
 * do not delete this
 */
@SuppressWarnings({ "unused", "rawtypes" })
public class PacketUtils {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static List<Packet> skipSendEvent = new ArrayList<>();

    // packet currently being re-fired by a holder module (backtrack), other
    // holders skip it so they dont re-eat released packets
    public static Packet reprocessing;

    public static void sendPacketNoEvent(Packet packet) {
        if (packet == null || packet.getClass().getSimpleName().startsWith("S")) {
            return;
        }
        // play packets written while the connection is in login/handshake state
        // (hypixel bungee transfers, blink flush across a switch) hit the serializer
        // with no packet id -> "Can't serialize unregistered packet" kills the channel
        if (mc.thePlayer == null || !(mc.getNetHandler() instanceof NetHandlerPlayClient)) {
            return;
        }
        skipSendEvent.add(packet);
        mc.thePlayer.sendQueue.addToSendQueue(packet);
    }
}





