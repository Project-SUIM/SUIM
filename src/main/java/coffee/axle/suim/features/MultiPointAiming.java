package coffee.axle.suim.features;

import coffee.axle.suim.hooks.MyauHook;
import coffee.axle.suim.util.MyauLogger;

import java.lang.reflect.Method;

/**
 * Injects a "dynamic" boolean property into AimAssist for
 * multi-point vertical targeting.
 *
 * @author axle.coffee
 * @see <a href=
 *      "https://github.com/axlecoffee/CoffeeClient/blob/main/src/main/java/io/github/moulberry/notenoughupdates/coffeeclient/module/modules/AimAssistModule.java">CoffeeClient
 *      AimAssistModule</a>
 */
public class MultiPointAiming implements Feature {
    private final MyauHook hook = MyauHook.getInstance();

    private static Object aimAssistModule;

    private static Object aimAssistDynamicProp;

    private static Method propertyGetValue;

    private static volatile boolean featureActive = false;

    @Override
    public String getName() {
        return "MultiPointAiming";
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");

            propertyGetValue = hook.getPropertyGetValueMethod();
            if (propertyGetValue == null) {
                MyauLogger.log(getName(), "PROPERTY_METHOD_NULL");
                return false;
            }

            aimAssistModule = hook.findModule("AimAssist");
            if (aimAssistModule != null) {
                aimAssistDynamicProp = hook.createBooleanProperty("multi-point", true);
                if (!hook.injectPropertyAfter(aimAssistModule, aimAssistDynamicProp, "allow-tools")) {
                    hook.registerPropertiesToModule(aimAssistModule, aimAssistDynamicProp);
                }
                MyauLogger.log(getName(), "AimAssist dynamic property injected");
            } else {
                MyauLogger.log(getName(), "AimAssist module not found");
            }

            featureActive = true;
            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;

        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    /** Whether dynamic multi-point is enabled for AimAssist. */
    public static boolean isAimAssistDynamic() {
        if (!featureActive)
            return false;
        return getBooleanPropValue(aimAssistDynamicProp, false);
    }

    private static boolean getBooleanPropValue(Object prop, boolean defaultVal) {
        try {
            if (prop == null || propertyGetValue == null)
                return defaultVal;
            Object val = propertyGetValue.invoke(prop);
            return val instanceof Boolean ? (Boolean) val : defaultVal;
        } catch (Exception e) {
            return defaultVal;
        }
    }

    @Override
    public void disable() {
        featureActive = false;
        aimAssistModule = null;
        aimAssistDynamicProp = null;
    }
}
