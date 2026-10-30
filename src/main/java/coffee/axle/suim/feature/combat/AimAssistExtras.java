package coffee.axle.suim.feature.combat;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.property.Setting;
import coffee.axle.suim.rotation.AimAssistRotation;
import coffee.axle.suim.eventbus.AimAssistHook;

/**
 * Injects aim modes tthat are in my personal opinion useful $$$$
 * {@link AimAssistHook}
 */
public class AimAssistExtras extends Feature {

    // static, aim assist's own code is pulled in from hooks that have no feature instance
    @Setting(after = "allow-tools")
    private static AimAssistRotation.AimMode aimMode = AimAssistRotation.AimMode.DEFAULT;

    @Setting(min = 0.05, max = 1.5) private static float hitboxBounds = 0.95f;
    @Setting(min = 0, max = 1) private static float headBias = 0.3f;
    @Setting(name = "contraction-min", min = 0.01, max = 0.15) private static float contractionMin = 0.03f;
    @Setting(name = "contraction-max", min = 0.10, max = 0.30) private static float contractionMax = 0.22f;
    @Setting private static boolean extrapolation = false;
    @Setting private static boolean pingComp = false;

    private static AimAssistExtras instance;

    public AimAssistExtras() {
        super("AimAssistExtras");
        instance = this;
    }

    @Override
    protected String targetModule() {
        return "AimAssist";
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    public static int getAimModeOrdinal() {
        return aimMode.ordinal();
    }

    public static float getHitboxBounds() {
        return hitboxBounds;
    }

    public static float getHeadBias() {
        return headBias;
    }

    public static boolean isExtrapolationEnabled() {
        return extrapolation;
    }

    public static boolean isPingCompEnabled() {
        return pingComp;
    }

    public static float getContractionMin() {
        return contractionMin;
    }

    public static float getContractionMax() {
        return contractionMax;
    }
}
