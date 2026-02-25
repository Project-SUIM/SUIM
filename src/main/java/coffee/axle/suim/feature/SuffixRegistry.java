package coffee.axle.suim.feature;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Global registry mapping Myau module instances to suffix suppliers.
 * <p>
 * {@link MixinModuleBase} hooks into {@code Module.getSuffix()} and checks
 * this registry. Features register via {@link Feature#setSuffix} helpers.
 *
 * @author axle.coffee
 */
public final class SuffixRegistry {

    private static final Map<Object, Supplier<String[]>> registry = new ConcurrentHashMap<>();

    private SuffixRegistry() {
    }

    /**
     * Register a dynamic suffix supplier for a module instance.
     * The supplier is called each frame when the HUD renders the arraylist.
     *
     * @param moduleInstance the Myau module object (from createModule or
     *                       findModule)
     * @param supplier       returns the suffix string array (e.g.
     *                       {@code () -> new String[]{"vsplit"}})
     */
    public static void register(Object moduleInstance, Supplier<String[]> supplier) {
        if (moduleInstance != null && supplier != null) {
            registry.put(moduleInstance, supplier);
        }
    }

    /**
     * Register a static (constant) suffix for a module instance.
     *
     * @param moduleInstance the Myau module object
     * @param suffix         one or more suffix strings displayed in the HUD
     *                       arraylist
     */
    public static void registerStatic(Object moduleInstance, String... suffix) {
        if (moduleInstance != null && suffix != null) {
            // Capture a snapshot — no allocation per frame
            final String[] copy = suffix.clone();
            registry.put(moduleInstance, () -> copy);
        }
    }

    /**
     * Unregister any suffix for a module instance.
     */
    public static void unregister(Object moduleInstance) {
        if (moduleInstance != null) {
            registry.remove(moduleInstance);
        }
    }

    /**
     * Look up the suffix for a module instance.
     *
     * @return the suffix array, or null if no suffix is registered
     */
    public static String[] getSuffix(Object moduleInstance) {
        Supplier<String[]> supplier = registry.get(moduleInstance);
        if (supplier == null) {
            return null;
        }
        return supplier.get();
    }
}
