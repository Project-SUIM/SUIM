package coffee.axle.suim.eventbus;

import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;
import static coffee.axle.suim.ProjectSUIM.mc;

/**
 * Event-bus replacement for MixinFastPlace.
 *
 * <p>
 * <strong>Why not Mixin?</strong> In Myau 260729, {@code myau.JQ} (FastPlace) has SuChen
 * anti-leak protection, blocking all bytecode modification.
 * </p>
 *
 * <p>
 * <strong>How it works:</strong><br>
 * 1. Registers a handler on {@code myau.kC} (HitBlockEvent) BEFORE FastPlace's handler<br>
 * 2. If the player is holding obsidian, temporarily disables FastPlace by setting
 *    {@code V.a} (enabled flag) to {@code false} via reflection<br>
 * 3. Registers a second handler AFTER FastPlace to restore the enabled state<br>
 * 4. FastPlace's handler calls {@code X()} (isEnabled) at the start and skips if disabled<br>
 * </p>
 *
 * @author axlecoffee
 */
public class FastPlaceHook {

    private static boolean loggedInit = false;

    // Reflection caches
    private static Field enabledField;  // V.a (boolean)
    private static boolean reflectionFailed = false;

    // FastPlace instance from event bus
    private static Object fastPlaceInstance;
    private static boolean instanceSearchDone = false;

    // Track disabled state to restore
    private boolean wasEnabled = false;
    private boolean didDisable = false;

    /**
     * Registers this handler on Myau's event bus.
     * Must be called after {@link MyauEventBusHook#tryInit()} returns {@code true}.
     *
     * @return {@code true} if both pre and post handlers registered successfully
     */
    public static boolean register() {
        FastPlaceHook hook = new FastPlaceHook();
        boolean pre = MyauEventBusHook.registerBefore(
                MyauMappings.CLASS_HIT_BLOCK_EVENT, // myau.kC
                hook,
                "onTickPre",
                Object.class,
                0
        );
        boolean post = MyauEventBusHook.registerAfter(
                MyauMappings.CLASS_HIT_BLOCK_EVENT, // myau.kC
                hook,
                "onTickPost",
                Object.class,
                0
        );
        return pre && post;
    }

    /**
     * PRE handler - runs BEFORE FastPlace's handler.
     * Disables FastPlace if the player is holding obsidian.
     */
    public void onTickPre(Object event) {
        try {
            if (!loggedInit) {
                loggedInit = true;
                MyauLogger.log("FastPlace:Hook", "event-bus handler active");
            }

            didDisable = false;
            if (reflectionFailed) return;

            Object fp = getFastPlaceInstance();
            if (fp == null) return;

            ensureReflection();
            if (reflectionFailed) return;

            wasEnabled = enabledField.getBoolean(fp);
            if (!wasEnabled) return;

            // Skip placement while holding obsidian
            if (shouldBlock()) {
                enabledField.setBoolean(fp, false);
                didDisable = true;
            }
        } catch (Exception e) {
            MyauLogger.error("FastPlace:Hook pre", e);
        }
    }

    /**
     * POST handler - runs AFTER FastPlace's handler.
     * Restores FastPlace's enabled state if it was temporarily disabled.
     */
    public void onTickPost(Object event) {
        try {
            if (didDisable) {
                Object fp = getFastPlaceInstance();
                if (fp != null) {
                    enabledField.setBoolean(fp, true);
                }
                didDisable = false;
            }
        } catch (Exception e) {
            MyauLogger.error("FastPlace:Hook post", e);
        }
    }

    private static boolean shouldBlock() {
        if (mc.thePlayer == null) return false;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) return false;
        return held.getItem() instanceof ItemBlock
                && ((ItemBlock) held.getItem()).getBlock() == Blocks.obsidian;
    }

    private static Object getFastPlaceInstance() {
        if (fastPlaceInstance != null) return fastPlaceInstance;
        if (instanceSearchDone) return null;
        instanceSearchDone = true;
        fastPlaceInstance = MyauEventBusHook.findModuleInstance(
                MyauMappings.CLASS_HIT_BLOCK_EVENT,
                MyauMappings.CLASS_FAST_PLACE
        );
        if (fastPlaceInstance == null) {
            MyauLogger.log("FastPlace:Hook", "WARNING: could not find FastPlace instance in event bus");
        }
        return fastPlaceInstance;
    }

    private static void ensureReflection() {
        if (reflectionFailed || enabledField != null) return;
        try {
            Class<?> moduleBase = Class.forName(MyauMappings.CLASS_MODULE_BASE);
            enabledField = moduleBase.getDeclaredField(MyauMappings.FIELD_MODULE_ENABLED);
            enabledField.setAccessible(true);
        } catch (Exception e) {
            reflectionFailed = true;
            MyauLogger.error("FastPlace:Hook reflection", e);
        }
    }
}
