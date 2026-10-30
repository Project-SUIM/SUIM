package coffee.axle.suim.hooks;

import coffee.axle.suim.events.HypixelLocationEvent;
import coffee.axle.suim.util.MyauLogger;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.minecraftforge.common.MinecraftForge;

/**
 * Registers for the hypixel location event packet and reposts it on the forge
 * bus. Everything that needs to know the game/map reads the event instead of
 * parsing the sidebar.
 */
public final class HypixelModApiHook {
    private static HypixelModApiHook instance;

    private boolean registered;

    private HypixelModApiHook() {}

    public static HypixelModApiHook getInstance() {
        if (instance == null) {
            instance = new HypixelModApiHook();
        }
        return instance;
    }

    public void register() {
        if (registered) return;
        try {
            // createHandler alone is not enough: the server only sends the
            // packet after a subscription, and it needs to be subscribed before
            // the player connects or the hello reply comes too early
            HypixelModAPI.getInstance().subscribeToEventPacket(ClientboundLocationPacket.class);
            HypixelModAPI.getInstance().createHandler(ClientboundLocationPacket.class,
                    HypixelModApiHook::post);
            registered = true;
        } catch (Throwable t) {
            // no hypixel mod api on the classpath (dev, or the tweaker bailed)
            MyauLogger.error("HypixelModApiHook:register", new Exception(t));
        }
    }

    private static void post(ClientboundLocationPacket packet) {
        String gameType = packet.getServerType().map(t -> t.name()).orElse(null);
        MinecraftForge.EVENT_BUS.post(new HypixelLocationEvent(
                gameType,
                packet.getLobbyName().orElse(null),
                packet.getMode().orElse(null),
                packet.getMap().orElse(null)));
    }
}