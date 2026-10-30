package coffee.axle.suim.util;

import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;

import java.util.ArrayList;
import java.util.List;
import static coffee.axle.suim.ProjectSUIM.mc;

/**
 * Storing some packets
 * do not delete this
 */
@SuppressWarnings({ "unused", "rawtypes" })
public class PacketUtils {

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

    // same C03 with the rotation swapped, keeping whatever position fields the
    // original carried
    public static Packet<?> withRotation(C03PacketPlayer orig, float yaw, float pitch) {
        if (orig instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
            C03PacketPlayer.C06PacketPlayerPosLook pos = (C03PacketPlayer.C06PacketPlayerPosLook) orig;
            return new C03PacketPlayer.C06PacketPlayerPosLook(
                    pos.getPositionX(), pos.getPositionY(), pos.getPositionZ(),
                    yaw, pitch, orig.isOnGround());
        }
        if (orig instanceof C03PacketPlayer.C04PacketPlayerPosition) {
            C03PacketPlayer.C04PacketPlayerPosition pos = (C03PacketPlayer.C04PacketPlayerPosition) orig;
            return new C03PacketPlayer.C06PacketPlayerPosLook(
                    pos.getPositionX(), pos.getPositionY(), pos.getPositionZ(),
                    yaw, pitch, orig.isOnGround());
        }
        return new C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, orig.isOnGround());
    }
}





