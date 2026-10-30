package coffee.axle.suim.events;

import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;

// fired from MixinMinecraft.clickMouse HEAD, before swingItem()/attackEntity()
// run. cancelling drops the click at the input level: no C0A/C02 packets and
// no client-side attack prediction
@Cancelable
public class ClickEvent extends Event {
}
