package coffee.axle.suim.hooks;

public final class PropertyUtils {

    private static final MyauHook hook = MyauHook.getInstance();

    private PropertyUtils() {
    }

    public static boolean getBoolean(
            Object property, boolean defaultValue) {
        try {
            return (Boolean) hook.getPropertyValue(property);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static float getFloat(
            Object property, float defaultValue) {
        try {
            return (Float) hook.getPropertyValue(property);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static int getInt(
            Object property, int defaultValue) {
        try {
            return (Integer) hook.getPropertyValue(property);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static String getString(
            Object property, String defaultValue) {
        try {
            return (String) hook.getPropertyValue(property);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
