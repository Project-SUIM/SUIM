package coffee.axle.suim.events;

import net.minecraft.network.Packet;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * SendPacketEvent - Fired when sending a packet to server
 * 
 * @author maybsomeday (meow) - ported to d'Myau
 */
@Cancelable
public class SendPacketEvent extends Event {
    private Packet<?> packet;

    public SendPacketEvent(Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> getPacket() {
        return packet;
    }
}
