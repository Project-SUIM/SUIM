package coffee.axle.suim.eventbus;

import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.util.MyauLogger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Registers custom event handlers with Myau's internal event bus ({@code myau.H2}).
 *
 * <p>
 * <strong>Why this exists:</strong> Myau 260313 introduced SuChen anti-leak protection
 * on many module classes (including AimAssist {@code myau.HJ} and FastPlace {@code myau.H7}).
 * Anti-leak native hash verification in {@code <clinit>} detects ANY bytecode modification,
 * making SpongePowered Mixin injection impossible on those classes.
 * </p>
 *
 * <p>
 * <strong>Strategy:</strong> Instead of modifying anti-leak classes via Mixin, we register
 * our own event handlers on Myau's event bus. The event bus dispatches handlers via
 * {@code Method.invoke()}, so our handlers run alongside Myau's handlers without
 * modifying any protected bytecode.
 * </p>
 *
 * <p>
 * <strong>Registration method:</strong> Direct map manipulation — we access the internal
 * handler map {@code H2.f} and insert our own {@code Hf} (handler wrapper) entries.
 * This bypasses the native {@code H2.X(Object)} registration method entirely,
 * avoiding any potential native-code class verification.
 * </p>
 *
 * <p>
 * <strong>Event bus internals (260313):</strong><br>
 * - {@code myau.H2} — EventBus class (anti-leak protected)<br>
 * - {@code myau.H2.f} — {@code Map<Class<? extends J>, List<Hf>>} — handler map<br>
 * - {@code myau.Hf} — handler wrapper: (Object handler, Method method, int priority)<br>
 * - {@code myau.Ho} — {@code @Target(METHOD) @Retention(RUNTIME)} annotation, {@code int v() default 0}<br>
 * - {@code myau.J} — base Event class<br>
 * - Dispatch: {@code H2.C(J event)} iterates list in order, calls {@code method.invoke(handler, event)}<br>
 * - No sorting during dispatch — insertion order determines execution order<br>
 * </p>
 *
 * @author axlecoffee
 * @see AimAssistHook
 * @see FastPlaceHook
 */
public final class MyauEventBusHook {

    private static boolean initialized = false;
    private static Object eventBusInstance;

    // Reflection caches
    private static Field eventBusField;      // Zd.A
    private static Field handlerMapField;    // H2.f
    private static Constructor<?> hfConstructor; // Hf(Object, Method, int)

    private MyauEventBusHook() {}

    /**
     * Attempts to initialize the hook by locating Myau's event bus instance.
     * Call this repeatedly (e.g., on each Forge client tick) until it returns {@code true}.
     *
     * @return {@code true} if the event bus is ready and handlers can be registered
     */
    public static boolean tryInit() {
        if (initialized) return true;
        try {
            if (eventBusField == null) {
                Class<?> clientClass = Class.forName(MyauMappings.CLASS_MAIN);
                eventBusField = clientClass.getDeclaredField(MyauMappings.FIELD_EVENT_BUS);
                eventBusField.setAccessible(true);
            }

            eventBusInstance = eventBusField.get(null);
            if (eventBusInstance == null) return false; // Myau not initialized yet

            if (handlerMapField == null) {
                Class<?> eventBusClass = Class.forName(MyauMappings.CLASS_EVENT_BUS);
                handlerMapField = eventBusClass.getDeclaredField("f");
                handlerMapField.setAccessible(true);
            }

            if (hfConstructor == null) {
                Class<?> hfClass = Class.forName("myau.Hf");
                hfConstructor = hfClass.getDeclaredConstructor(Object.class, Method.class, int.class);
                hfConstructor.setAccessible(true);
            }

            initialized = true;
            MyauLogger.log("EventBusHook", "Myau event bus located, ready for handler registration");
            return true;
        } catch (Exception e) {
            MyauLogger.error("EventBusHook:init", e);
            return false;
        }
    }

    /**
     * Registers a handler method on a specific event type.
     * The handler will be appended at the END of the handler list (runs after existing handlers).
     *
     * @param eventClassName fully qualified event class name (e.g., "myau.b" for TickEventPost)
     * @param handler        the object whose method will be invoked
     * @param methodName     the handler method name on the handler object
     * @param paramType      the parameter type the method accepts (use {@code Object.class}
     *                       to accept any event; the dispatch uses reflection so this is safe)
     * @param priority       handler priority (0 = default, 100 = RotationManager priority)
     * @return {@code true} if registration succeeded
     */
    @SuppressWarnings("unchecked")
    public static boolean registerAfter(String eventClassName, Object handler,
                                        String methodName, Class<?> paramType, int priority) {
        return register(eventClassName, handler, methodName, paramType, priority, false);
    }

    /**
     * Registers a handler method that runs BEFORE existing handlers for this event type.
     * Inserts at position 0 in the handler list.
     *
     * @see #registerAfter
     */
    @SuppressWarnings("unchecked")
    public static boolean registerBefore(String eventClassName, Object handler,
                                         String methodName, Class<?> paramType, int priority) {
        return register(eventClassName, handler, methodName, paramType, priority, true);
    }

    @SuppressWarnings("unchecked")
    private static boolean register(String eventClassName, Object handler,
                                    String methodName, Class<?> paramType,
                                    int priority, boolean insertFirst) {
        if (!initialized) {
            MyauLogger.log("EventBusHook", "Cannot register — not initialized");
            return false;
        }
        try {
            Class<?> eventClass = Class.forName(eventClassName);
            Method handlerMethod = handler.getClass().getMethod(methodName, paramType);
            handlerMethod.setAccessible(true);

            Object hfWrapper = hfConstructor.newInstance(handler, handlerMethod, priority);

            Map<Object, List<Object>> handlerMap =
                    (Map<Object, List<Object>>) handlerMapField.get(eventBusInstance);

            List<Object> handlers = handlerMap.get(eventClass);
            if (handlers == null) {
                handlers = new ArrayList<>();
                handlerMap.put(eventClass, handlers);
            }

            if (insertFirst) {
                handlers.add(0, hfWrapper);
            } else {
                handlers.add(hfWrapper);
            }

            MyauLogger.log("EventBusHook",
                    "Registered " + handler.getClass().getSimpleName() + "." + methodName
                            + " on " + eventClassName + " (" + (insertFirst ? "BEFORE" : "AFTER") + ")");
            return true;
        } catch (Exception e) {
            MyauLogger.error("EventBusHook:register " + methodName, e);
            return false;
        }
    }

    /**
     * Finds a specific module instance from the event bus handler list.
     * Useful for accessing module fields via reflection (e.g., reading AimAssist properties).
     *
     * @param eventClassName the event class name this module handles
     * @param moduleClassName the module class name to find (e.g., "myau.HJ")
     * @return the module instance, or null if not found
     */
    @SuppressWarnings("unchecked")
    public static Object findModuleInstance(String eventClassName, String moduleClassName) {
        if (!initialized) return null;
        try {
            Class<?> eventClass = Class.forName(eventClassName);
            Map<Object, List<Object>> handlerMap =
                    (Map<Object, List<Object>>) handlerMapField.get(eventBusInstance);
            List<Object> handlers = handlerMap.get(eventClass);
            if (handlers == null) return null;

            // Hf.h() returns the handler object
            Method hfGetHandler = Class.forName("myau.Hf").getMethod("h");
            for (Object hf : handlers) {
                Object handlerObj = hfGetHandler.invoke(hf);
                if (handlerObj.getClass().getName().equals(moduleClassName)) {
                    return handlerObj;
                }
            }
        } catch (Exception e) {
            MyauLogger.error("EventBusHook:findModule " + moduleClassName, e);
        }
        return null;
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
