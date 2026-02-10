package coffee.axle.suim.events;

import net.minecraft.network.Packet;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * Event fired when sending packet without triggering normal events
 * 
 * @author maybsomeday (meow) - ported to d'Myau
 */
@Cancelable
public class NoEventPacketEvent extends Event {
    private Packet<?> packet;

    public NoEventPacketEvent(Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> getPacket() {
        return packet;
    }
}





