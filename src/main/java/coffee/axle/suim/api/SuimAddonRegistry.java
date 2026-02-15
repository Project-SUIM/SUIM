package coffee.axle.suim.api;

import coffee.axle.suim.util.MyauLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central registry for external SUIM addons.
 * Addons register themselves here during Forge init; SUIM picks them
 * up when it initializes.
 */
public class SuimAddonRegistry {

    private static final List<SuimAddon> addons = new CopyOnWriteArrayList<>();

    /**
     * Register an addon. Call this during your mod's FMLInitializationEvent.
     * SUIM will pick it up when it initializes.
     */
    public static void register(SuimAddon addon) {
        if (addon == null) {
            MyauLogger.info("Attempted to register null addon");
            return;
        }
        for (SuimAddon existing : addons) {
            if (existing.getAddonId().equals(addon.getAddonId())) {
                MyauLogger.info("Addon already registered: " + addon.getAddonId());
                return;
            }
        }
        addons.add(addon);
        MyauLogger.info("Addon registered: " + addon.getAddonId()
                + " (" + addon.getDisplayName() + ")");
    }

    /**
     * Unregister an addon by its ID.
     */
    public static void unregister(String addonId) {
        addons.removeIf(a -> a.getAddonId().equals(addonId));
    }

    /**
     * Get all registered addons (unmodifiable snapshot).
     */
    public static List<SuimAddon> getAddons() {
        return Collections.unmodifiableList(new ArrayList<>(addons));
    }

    /**
     * Find an addon by its ID.
     */
    public static SuimAddon getAddon(String addonId) {
        for (SuimAddon addon : addons) {
            if (addon.getAddonId().equals(addonId)) {
                return addon;
            }
        }
        return null;
    }

    /**
     * Whether any addons are registered.
     */
    public static boolean hasAddons() {
        return !addons.isEmpty();
    }

    /**
     * Clear all registered addons. Used during shutdown.
     */
    public static void clear() {
        addons.clear();
    }
}
