package coffee.axle.suim.events;

import net.minecraft.network.Packet;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * ReceivePacketEvent - Fired when receiving a packet from server
 * 
 * @author maybsomeday (meow) - ported to d'Myau
 */
@Cancelable
public class ReceivePacketEvent extends Event {
    private Packet<?> packet;

    public ReceivePacketEvent(Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> getPacket() {
        return packet;
    }
}
