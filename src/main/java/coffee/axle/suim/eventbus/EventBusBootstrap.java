package coffee.axle.suim.eventbus;

import coffee.axle.suim.util.MyauLogger;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Bootstraps the Myau event-bus hook system.
 *
 * <p>
 * On each client tick, checks whether Myau's event bus is initialized. Once
 * ready, registers all SUIM event-bus handlers and unregisters itself (one-time).
 * </p>
 *
 * @author axlecoffee
 */
public class EventBusBootstrap {

    private boolean hooksDone = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (hooksDone) return;
        if (event.phase != TickEvent.Phase.START) return;

        if (!MyauEventBusHook.tryInit()) return;

        boolean aaOk = AimAssistHook.register();
        boolean fpOk = FastPlaceHook.register();

        MyauLogger.log("EventBusBootstrap",
                "Handlers registered - AimAssist: " + (aaOk ? "OK" : "FAIL")
                        + ", FastPlace: " + (fpOk ? "OK" : "FAIL"));

        hooksDone = true;
        MinecraftForge.EVENT_BUS.unregister(this);
    }
}
