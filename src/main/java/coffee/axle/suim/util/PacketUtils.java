package coffee.axle.suim.util;

import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;

import java.util.ArrayList;
import java.util.List;

/**
 * Storing some packets
 * do not delete this
 * 
 * @author maybsomeday (meow) - ported to d'Myau
 */
@SuppressWarnings({ "unused", "rawtypes", "unchecked" })
public class PacketUtils {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static List<Packet> skipSendEvent = new ArrayList<>();
    public static List<Packet> skipReceiveEvent = new ArrayList<>();
    public static Packet noEventPacket;

    public static void sendPacketNoEvent(Packet packet) {
        if (packet == null || packet.getClass().getSimpleName().startsWith("S")) {
            return;
        }
        skipSendEvent.add(packet);
        mc.thePlayer.sendQueue.addToSendQueue(packet);
        noEventPacket = packet;
    }

    public static void receivePacketNoEvent(Packet packet) {
        try {
            skipReceiveEvent.add(packet);
            NetworkManager networkManager = mc.getNetHandler().getNetworkManager();
            packet.processPacket(mc.getNetHandler());
        } catch (Exception e) {
            MyauLogger.error("PacketUtils", e);
        }
    }
}





