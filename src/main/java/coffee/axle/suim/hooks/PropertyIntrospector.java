package coffee.axle.suim.hooks;

import coffee.axle.suim.util.MyauLogger;

/**
 * Reads metadata (min, max, step, visibility) from Myau property objects.
 * Uses reflection via MyauModuleManager to extract range bounds from
 * RangedValue, FloatValue, and IntValue classes.
 */
public class PropertyIntrospector {

    private static final double DEFAULT_INT_MIN = 0.0;
    private static final double DEFAULT_INT_MAX = 100.0;
    private static final double DEFAULT_FLOAT_MIN = 0.0;
    private static final double DEFAULT_FLOAT_MAX = 10.0;
    private static final double DEFAULT_INT_STEP = 1.0;
    private static final double DEFAULT_FLOAT_STEP = 0.1;

    private final MyauModuleManager mgr;

    public PropertyIntrospector(MyauModuleManager mgr) {
        this.mgr = mgr;
    }

    /**
     * Reads the minimum value for a numeric property.
     * Falls back to sensible defaults if reflection fails.
     */
    public double getMin(Object property, String type) {
        Number min = mgr.getPropertyMin(property);
        if (min != null) {
            return min.doubleValue();
        }
        return "float".equals(type) ? DEFAULT_FLOAT_MIN : DEFAULT_INT_MIN;
    }

    /**
     * Reads the maximum value for a numeric property.
     * Falls back to sensible defaults if reflection fails.
     */
    public double getMax(Object property, String type) {
        Number max = mgr.getPropertyMax(property);
        if (max != null) {
            return max.doubleValue();
        }
        return "float".equals(type) ? DEFAULT_FLOAT_MAX : DEFAULT_INT_MAX;
    }

    /**
     * Determines the step increment for a numeric property.
     * Integer types use 1.0, float types use a fraction of the range.
     */
    public double getStep(Object property, String type) {
        if ("integer".equals(type)) {
            return DEFAULT_INT_STEP;
        }

        Number min = mgr.getPropertyMin(property);
        Number max = mgr.getPropertyMax(property);
        if (min != null && max != null) {
            double range = max.doubleValue() - min.doubleValue();
            if (range <= 1.0) {
                return 0.01;
            }
            if (range <= 10.0) {
                return 0.1;
            }
            return 0.5;
        }
        return DEFAULT_FLOAT_STEP;
    }

    /**
     * Whether the property's visibility condition currently allows display.
     */
    public boolean isVisible(Object property) {
        return mgr.isPropertyVisible(property);
    }
}
