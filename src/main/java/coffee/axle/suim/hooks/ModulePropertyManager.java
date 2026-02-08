package coffee.axle.suim.hooks;

import coffee.axle.suim.util.MyauLogger;

/**
 * Intermediary for type-safe property access on Myau modules.
 * Delegates all find/get operations to {@link MyauModuleManager}.
 */
public class ModulePropertyManager {
    private final MyauModuleManager manager;

    public ModulePropertyManager(MyauModuleManager manager) {
        this.manager = manager;
    }

    public boolean getBoolean(Object property, boolean defaultValue) {
        try {
            Object value = manager.getPropertyValue(property);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }

    public float getFloat(Object property, float defaultValue) {
        try {
            Object value = manager.getPropertyValue(property);
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }

    public int getInt(Object property, int defaultValue) {
        try {
            Object value = manager.getPropertyValue(property);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }

    public String getString(Object property, String defaultValue) {
        try {
            Object value = manager.getPropertyValue(property);
            if (value instanceof String) {
                return (String) value;
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }

    public boolean getBoolean(String moduleName, String propertyName, boolean defaultValue) {
        try {
            Object module = manager.findModule(moduleName);
            if (module == null)
                return defaultValue;
            Object property = manager.findProperty(module, propertyName);
            if (property == null)
                return defaultValue;
            return getBoolean(property, defaultValue);
        } catch (Exception e) {
            MyauLogger.error("PROPERTY_NOT_FOUND", e);
            return defaultValue;
        }
    }

    public float getFloat(String moduleName, String propertyName, float defaultValue) {
        try {
            Object module = manager.findModule(moduleName);
            if (module == null)
                return defaultValue;
            Object property = manager.findProperty(module, propertyName);
            if (property == null)
                return defaultValue;
            return getFloat(property, defaultValue);
        } catch (Exception e) {
            MyauLogger.error("PROPERTY_NOT_FOUND", e);
            return defaultValue;
        }
    }

    public int getInt(String moduleName, String propertyName, int defaultValue) {
        try {
            Object module = manager.findModule(moduleName);
            if (module == null)
                return defaultValue;
            Object property = manager.findProperty(module, propertyName);
            if (property == null)
                return defaultValue;
            return getInt(property, defaultValue);
        } catch (Exception e) {
            MyauLogger.error("PROPERTY_NOT_FOUND", e);
            return defaultValue;
        }
    }

    public String getString(String moduleName, String propertyName, String defaultValue) {
        try {
            Object module = manager.findModule(moduleName);
            if (module == null)
                return defaultValue;
            Object property = manager.findProperty(module, propertyName);
            if (property == null)
                return defaultValue;
            return getString(property, defaultValue);
        } catch (Exception e) {
            MyauLogger.error("PROPERTY_NOT_FOUND", e);
            return defaultValue;
        }
    }

    public boolean getBoolean(Object module, String propertyName, boolean defaultValue) {
        try {
            Object property = manager.findProperty(module, propertyName);
            if (property == null)
                return defaultValue;
            return getBoolean(property, defaultValue);
        } catch (Exception e) {
            MyauLogger.error("PROPERTY_NOT_FOUND", e);
            return defaultValue;
        }
    }

    public float getFloat(Object module, String propertyName, float defaultValue) {
        try {
            Object property = manager.findProperty(module, propertyName);
            if (property == null)
                return defaultValue;
            return getFloat(property, defaultValue);
        } catch (Exception e) {
            MyauLogger.error("PROPERTY_NOT_FOUND", e);
            return defaultValue;
        }
    }

    public int getInt(Object module, String propertyName, int defaultValue) {
        try {
            Object property = manager.findProperty(module, propertyName);
            if (property == null)
                return defaultValue;
            return getInt(property, defaultValue);
        } catch (Exception e) {
            MyauLogger.error("PROPERTY_NOT_FOUND", e);
            return defaultValue;
        }
    }

    public String getString(Object module, String propertyName, String defaultValue) {
        try {
            Object property = manager.findProperty(module, propertyName);
            if (property == null)
                return defaultValue;
            return getString(property, defaultValue);
        } catch (Exception e) {
            MyauLogger.error("PROPERTY_NOT_FOUND", e);
            return defaultValue;
        }
    }

    public Object findProperty(Object module, String propertyName) {
        return manager.findProperty(module, propertyName);
    }

    public Object getPropertyValue(Object property) throws Exception {
        return manager.getPropertyValue(property);
    }

    public String getPropertyName(Object property) throws Exception {
        return manager.getPropertyName(property);
    }
}
