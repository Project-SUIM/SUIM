package coffee.axle.suim.feature.world;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.util.BedwarsDetector;
import coffee.axle.suim.util.MyauLogger;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Adds kol's bedwars-only setting to the native myau bed modules. When the
 * setting is on and we are not in a bedwars game the module keeps its enabled
 * state but does nothing, matching kol's "!bedwars-only || inBedWarsGame".
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

    @Override
    public String getName() {
        return "BedwarsOnly";
    }

    @Override
    public boolean initialize() {
        try {
            propertyGetValue = manager.getPropertyGetValueMethod();
            if (propertyGetValue == null) {
                MyauLogger.log(getName(), "PROPERTY_METHOD_NULL");
                return false;
            }

            for (String name : GATED_MODULES) {
                Object module = manager.findModule(name);
                if (module == null) {
                    MyauLogger.log(getName(), name + " module not found");
                    continue;
                }
                Object prop = creator.createBooleanProperty("bedwars-only", false);
                creator.registerProperties(module, prop);
                gated.put(module, prop);
            }

            active = !gated.isEmpty();
            MyauLogger.log(getName(), "gated " + gated.size() + " modules with bedwars-only");
            if (active) {
                MinecraftForge.EVENT_BUS.register(this);
            }
            return active;
        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    // called from MixinModule on every myau.V.isEnabled, keep it cheap
    public static boolean isSuppressed(Object module) {
        if (!active) return false;
        Object prop = gated.get(module);
        if (prop == null) return false;
        if (!getBool(prop, false)) return false;
        return !inGameCached();
    }

    // when a game starts, flip the gated modules on so you dont have to
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        boolean inGame = inGameCached();
        if (inGame == wasInGame) return;
        wasInGame = inGame;
        if (!inGame) return;
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
            cachedInGame = BedwarsDetector.inBedWarsGame();
            checkedAt = now;
        }
        return cachedInGame;
    }
}
