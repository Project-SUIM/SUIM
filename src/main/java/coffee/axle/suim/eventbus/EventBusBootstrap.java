package coffee.axle.suim.eventbus;

import coffee.axle.suim.util.MyauLogger;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Bootstraps the Myau event-bus hook system.
 *
 * <p>
 * Register this handler with Forge's event bus during mod initialization:
 * <pre>{@code MinecraftForge.EVENT_BUS.register(new EventBusBootstrap());}</pre>
 * </p>
 *
 * <p>
 * On each client tick, it checks whether Myau's event bus is initialized.
 * Once ready, it registers all SUIM event-bus handlers and unregisters itself
 * from Forge's event bus (one-time setup).
 * </p>
 *
 * <p>
 * <strong>Anti-leak class summary (260313):</strong><br>
 * The following 31 classes have anti-leak protection and CANNOT be targeted by Mixins:<br>
 * {@code H2, H5, H7, HD, HH, HI, HJ, HN, HO, HU, HZ, Hn, Hr, Ht, Hu, Hv, Hx,
 *  Z, Z1, Z6, ZK, ZL, ZM, ZY, Zd, Zg, Zi, Zo}<br>
 * <br>
 * Safe classes (no anti-leak — Mixins OK):<br>
 * {@code HT, Hf, Hd, Ho, J, V, b, ZU, m} and all other classes not listed above.
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

        // Myau is initialized — register all event-bus handlers
        boolean aaOk = AimAssistHook.register();
        boolean fpOk = FastPlaceHook.register();

        MyauLogger.log("EventBusBootstrap",
                "Handlers registered — AimAssist: " + (aaOk ? "OK" : "FAIL")
                        + ", FastPlace: " + (fpOk ? "OK" : "FAIL"));

        hooksDone = true;
        // Unregister self — no longer needed
        MinecraftForge.EVENT_BUS.unregister(this);
    }
}
