package coffee.axle.suim.feature.world;

import coffee.axle.suim.events.HypixelLocationEvent;
import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.util.HypixelUtils;
import coffee.axle.suim.util.MyauLogger;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Adds a bedwars-only setting to the native myau bed modules. When the
 * setting is on and we are not in a bedwars game the module keeps its enabled
 * state but does nothing unless a bedwars game is running.
 * The actual gating happens in MixinModule (myau.V.isEnabled).
 */
public class BedwarsOnly extends Feature {

    private static final String[] GATED_MODULES = {
            "BedNuker", "BedTracker", "AntiFireball", "BedESP"
    };

    private static final Map<Object, Object> gated = new HashMap<Object, Object>();
    private static Method propertyGetValue;
    private static volatile boolean active;
    private static volatile boolean cachedInGame;
    private static boolean wasInGame;
    private static long checkedAt;

    public BedwarsOnly() {
        super("BedwarsOnly");
    }

    @Override
    protected void init() throws Exception {
        propertyGetValue = manager.getPropertyGetValueMethod();
        if (propertyGetValue == null) {
            MyauLogger.log(getName(), "PROPERTY_METHOD_NULL");
            return;
        }

        for (String name : GATED_MODULES) {
            Object target = manager.findModule(name);
            if (target == null) {
                MyauLogger.log(getName(), name + " module not found");
                continue;
            }
            Object prop = creator.createBooleanProperty("bedwars-only", false);
            creator.registerProperties(target, prop);
            gated.put(target, prop);
        }

        active = !gated.isEmpty();
        MyauLogger.log(getName(), "gated " + gated.size() + " modules with bedwars-only");
    }

    // called from MixinModule on every myau.V.isEnabled, keep it cheap
    public static boolean isSuppressed(Object module) {
        if (!active) return false;
        Object prop = gated.get(module);
        if (prop == null) return false;
        if (!getBool(prop, false)) return false;
        return !inGameCached();
    }

    // the location packet lands before the first tick of a game, so turn the
    // modules on here rather than waiting for the sidebar to fill in
    @SubscribeEvent
    public void onLocation(HypixelLocationEvent e) {
        if (!active) return;
        if (!HypixelUtils.inBedWarsGame()) return;
        enableAll();
    }

    // when a game starts, flip the gated modules on so you dont have to
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        boolean inGame = inGameCached();
        if (inGame == wasInGame) return;
        wasInGame = inGame;
        if (!inGame) return;
        enableAll();
    }
    private void enableAll() {
        for (Map.Entry<Object, Object> entry : gated.entrySet()) {
            if (!getBool(entry.getValue(), false)) continue;
            try {
                manager.setModuleEnabled(entry.getKey(), true);
            } catch (Exception ex) {
                MyauLogger.error("BedwarsOnly:enable", ex);
            }
        }
    }

    private static boolean getBool(Object prop, boolean fallback) {
        try {
            Object v = propertyGetValue.invoke(prop);
            return v instanceof Boolean ? (Boolean) v : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    // the sidebar scan allocates, so cache it for 250ms
    private static boolean inGameCached() {
        long now = System.currentTimeMillis();
        if (now - checkedAt > 250L) {
            cachedInGame = HypixelUtils.inBedWarsGame();
            checkedAt = now;
        }
        return cachedInGame;
    }
}
