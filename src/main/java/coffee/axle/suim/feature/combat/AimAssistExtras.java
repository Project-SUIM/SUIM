package coffee.axle.suim.feature.combat;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.rotation.AimAssistRotation;
import coffee.axle.suim.util.MyauLogger;

import java.lang.reflect.Method;

/**
 * Injects aim-mode and hitbox-bounds properties into the evil AimAssist module.
 */
public class AimAssistExtras extends Feature {

    private static Object aimAssistModule;

    private static Object aimModeProp;
    private static Object hitboxBoundsProp;
    private static Object headBiasProp;
    private static Object extrapolationProp;
    private static Object pingCompProp;
    private static Object contractionMinProp;
    private static Object contractionMaxProp;

    private static Method propertyGetValue;

    private static volatile boolean featureActive = false;

    @Override
    public String getName() {
        return "AimAssistExtras";
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");

            propertyGetValue = manager.getPropertyGetValueMethod();
            if (propertyGetValue == null) {
                MyauLogger.log(getName(), "PROPERTY_METHOD_NULL");
                return false;
            }

            aimAssistModule = manager.findModule("AimAssist");
            if (aimAssistModule != null) {
                aimModeProp = creator.createEnumProperty("aim-mode", 0,
                        AimAssistRotation.MODE_NAMES);
                hitboxBoundsProp = creator.createFloatProperty(
                        "hitbox-bounds", 0.95f, 0.05f, 1.5f);

                if (!creator.injectPropertyAfter(aimAssistModule, aimModeProp, "allow-tools")) {
                    creator.registerProperties(aimAssistModule, aimModeProp);
                }
                creator.registerProperties(aimAssistModule, hitboxBoundsProp);

                headBiasProp = creator.createFloatProperty("head-bias", 0.3f, 0.0f, 1.0f);
                extrapolationProp = creator.createBooleanProperty("extrapolation", false);
                pingCompProp = creator.createBooleanProperty("ping-comp", false);
                contractionMinProp = creator.createFloatProperty("contraction-min", 0.03f, 0.01f, 0.15f);
                contractionMaxProp = creator.createFloatProperty("contraction-max", 0.22f, 0.10f, 0.30f);

                creator.registerProperties(aimAssistModule,
                        headBiasProp, extrapolationProp, pingCompProp,
                        contractionMinProp, contractionMaxProp);

                MyauLogger.log(getName(), "god developer axle.coffee comes with insane bypass");
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

    public static boolean isActive() {
        return featureActive;
    }

    public static int getAimModeOrdinal() {
        if (!featureActive)
            return 0;
        return getIntPropValue(aimModeProp, 0);
    }

    public static float getHitboxBounds() {
        if (!featureActive)
            return 0.95f;
        return getFloatPropValue(hitboxBoundsProp, 0.95f);
    }

    public static float getHeadBias() {
        if (!featureActive)
            return 0.3f;
        return getFloatPropValue(headBiasProp, 0.3f);
    }

    public static boolean isExtrapolationEnabled() {
        if (!featureActive)
            return false;
        return getBoolPropValue(extrapolationProp, false);
    }

    public static boolean isPingCompEnabled() {
        if (!featureActive)
            return false;
        return getBoolPropValue(pingCompProp, false);
    }

    public static float getContractionMin() {
        if (!featureActive)
            return 0.03f;
        return getFloatPropValue(contractionMinProp, 0.03f);
    }

    public static float getContractionMax() {
        if (!featureActive)
            return 0.22f;
        return getFloatPropValue(contractionMaxProp, 0.22f);
    }

    private static int getIntPropValue(Object prop, int defaultVal) {
        try {
            if (prop == null || propertyGetValue == null)
                return defaultVal;
            Object val = propertyGetValue.invoke(prop);
            return val instanceof Number ? ((Number) val).intValue() : defaultVal;
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private static float getFloatPropValue(Object prop, float defaultVal) {
        try {
            if (prop == null || propertyGetValue == null)
                return defaultVal;
            Object val = propertyGetValue.invoke(prop);
            return val instanceof Number ? ((Number) val).floatValue() : defaultVal;
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private static boolean getBoolPropValue(Object prop, boolean defaultVal) {
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
        aimModeProp = null;
        hitboxBoundsProp = null;
        headBiasProp = null;
        extrapolationProp = null;
        pingCompProp = null;
        contractionMinProp = null;
        contractionMaxProp = null;
    }
}
