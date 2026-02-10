package coffee.axle.suim.hooks;

import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.feature.clickgui.ClickGui;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static coffee.axle.suim.hooks.MyauMappings.*;

/**
 * Manages existing Myau modules
 */
public class MyauModuleManager {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private final MyauHook hook;
    private final Map<String, Object> moduleCache = new ConcurrentHashMap<>();
    private final Map<String, Object> propertyCache = new ConcurrentHashMap<>();

    private String cachedUsername = null;

    public MyauModuleManager(MyauHook hook) {
        this.hook = hook;
    }

    public Object findModule(String name) {
        Object cached = this.moduleCache.get(name);
        if (cached != null) {
            return cached;
        }

        Method getNameMethod = hook.getModuleGetNameMethod();
        for (Object module : hook.getModulesMap().values()) {
            try {
                String moduleName = (String) getNameMethod.invoke(module);
                if (moduleName.equals(name)) {
                    this.moduleCache.put(name, module);
                    return module;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public String getModuleName(Object module) {
        try {
            return (String) hook.getModuleGetNameMethod().invoke(module);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    public LinkedHashMap<Class<?>, Object> getAllModules() {
        return hook.getModulesMap();
    }

    public boolean isModuleEnabled(Object module) {
        try {
            Field enabledField = hook.findFieldInHierarchy(module.getClass(), FIELD_MODULE_ENABLED);
            if (enabledField != null) {
                return enabledField.getBoolean(module);
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public boolean isModuleEnabled(String moduleName) {
        Object module = findModule(moduleName);
        return module != null && isModuleEnabled(module);
    }

    public void setModuleEnabled(Object module, boolean enabled) throws Exception {
        Method setEnabled = module.getClass().getMethod(METHOD_SET_ENABLED,
                short.class, boolean.class, short.class, int.class);
        setEnabled.invoke(module, (short) 0, enabled, (short) 1, 0);
    }

    public void toggleModule(Object module) throws Exception {
        setModuleEnabled(module, !isModuleEnabled(module));
    }

    /**
     * Toggles a module through Myau's command handler so the
     * standard "[Myau] Module (ON/OFF)" notification is displayed.
     * Falls back to direct toggle if the command path fails.
     */
    public void toggleModuleViaCommand(Object module) {
        try {
            String name = getModuleName(module);
            ArrayList<String> args = new ArrayList<>();
            args.add(name);
            runCommandByName(name, args);
        } catch (Exception e) {
            try {
                toggleModule(module);
            } catch (Exception fallback) {
                MyauLogger.error("toggleModuleViaCommand", fallback);
            }
        }
    }

    public void toggleModule(String moduleName) throws Exception {
        Object module = findModule(moduleName);
        if (module != null) {
            toggleModule(module);
        }
    }

    public int getModuleKeybind(Object module) {
        try {
            Field keyField = hook.findFieldInHierarchy(module.getClass(), FIELD_MODULE_KEYBIND);
            if (keyField != null) {
                return keyField.getInt(module);
            }
        } catch (Exception e) {
            MyauLogger.error("HOOK_FAIL", e);
        }
        return -1;
    }

    public boolean setModuleKeybind(Object module, int keyCode) {
        try {
            Field keyField = hook.findFieldInHierarchy(module.getClass(), FIELD_MODULE_KEYBIND);
            if (keyField != null) {
                keyField.setInt(module, keyCode);
                notifyConfigChanged();
                return true;
            }
        } catch (Exception e) {
            MyauLogger.error("HOOK_FAIL", e);
        }
        return false;
    }

    public Object findProperty(Object module, String propertyName) {
        String key = module.hashCode() + "." + propertyName;
        Object cached = this.propertyCache.get(key);
        if (cached != null) {
            return cached;
        }

        Method getNameMethod = hook.getPropertyGetNameMethod();
        try {
            for (Field f : module.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object prop = f.get(module);
                if (prop != null) {
                    try {
                        String name = (String) getNameMethod.invoke(prop);
                        if (name.equals(propertyName)) {
                            this.propertyCache.put(key, prop);
                            return prop;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            MyauLogger.error("Manager:findProperty", e);
        }
        return null;
    }

    public Object getPropertyValue(Object property) throws Exception {
        return hook.getPropertyGetValueMethod().invoke(property);
    }

    /**
     * Sets the value on a Myau property object.
     * Finds the value field by matching against the field name constant from
     * mappings.
     */
    public void setPropertyValue(Object property, Object newValue) throws Exception {
        Field valueField = hook.findFieldInHierarchy(property.getClass(), FIELD_VALUE_CURRENT);
        if (valueField != null) {
            valueField.set(property, newValue);
            notifyConfigChanged();
            return;
        }
        // Fallback: find a non-static, non-final field matching the value type
        Object currentValue = getPropertyValue(property);
        if (currentValue == null) {
            throw new Exception("Cannot set property value: current value is null");
        }
        Class<?> clazz = property.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                    continue;
                if (java.lang.reflect.Modifier.isFinal(field.getModifiers()))
                    continue;
                field.setAccessible(true);
                try {
                    Object fieldValue = field.get(property);
                    if (fieldValue != null && fieldValue.equals(currentValue)) {
                        field.set(property, newValue);
                        notifyConfigChanged();
                        return;
                    }
                } catch (Exception ignored) {
                }
            }
            clazz = clazz.getSuperclass();
        }
        throw new Exception("Could not find value field for property type " + property.getClass().getName());
    }

    private void notifyConfigChanged() {
        ClickGui clickGui = ClickGui.getInstance();
        if (clickGui != null) {
            clickGui.onSettingChanged();
        }
    }

    /**
     * Gets all Myau property objects from a module by scanning its declared fields.
     */
    public List<Object> getAllProperties(Object module) {
        List<Object> props = new ArrayList<>();
        Method getNameMethod = hook.getPropertyGetNameMethod();
        try {
            for (Field f : module.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object prop = f.get(module);
                if (prop != null) {
                    try {
                        getNameMethod.invoke(prop);
                        props.add(prop);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            MyauLogger.error("Manager:getAllProperties", e);
        }
        return props;
    }

    /**
     * Gets the type of a Myau property (boolean, float, integer, string, enum,
     * color).
     */
    public String getPropertyType(Object property) {
        String className = property.getClass().getName();
        if (className.equals(CLASS_BOOLEAN_PROPERTY))
            return "boolean";
        if (className.equals(CLASS_FLOAT_PROPERTY))
            return "float";
        if (className.equals(CLASS_INTEGER_PROPERTY) || className.equals(CLASS_INT_VALUE))
            return "integer";
        if (className.equals(CLASS_STRING_PROPERTY))
            return "string";
        if (className.equals(CLASS_ENUM_PROPERTY))
            return "enum";
        if (className.equals(CLASS_COLOR_PROPERTY))
            return "color";
        return "unknown";
    }

    /**
     * Gets the enum values array from an enum property.
     */
    public String[] getEnumPropertyValues(Object enumProperty) {
        try {
            Field valuesField = hook.findFieldInHierarchy(enumProperty.getClass(), FIELD_ENUM_VALUES_ARRAY);
            if (valuesField != null) {
                Object arr = valuesField.get(enumProperty);
                if (arr instanceof String[])
                    return (String[]) arr;
            }
        } catch (Exception ignored) {
        }
        return new String[0];
    }

    /**
     * Gets the minimum value from a ranged Myau property.
     * Supports RangedValue (Integer), FloatValue (Float), and IntValue (Integer).
     *
     * @return the min value as Number, or null if not a ranged type
     */
    public Number getPropertyMin(Object property) {
        String type = getPropertyType(property);
        try {
            switch (type) {
                case "integer": {
                    String className = property.getClass().getName();
                    String fieldName;
                    if (className.equals(CLASS_INTEGER_PROPERTY)) {
                        fieldName = MyauMappings.FIELD_RANGED_MIN;
                    } else if (className.equals(CLASS_INT_VALUE)) {
                        fieldName = MyauMappings.FIELD_INT_MIN;
                    } else {
                        return null;
                    }
                    Field f = hook.findFieldInHierarchy(property.getClass(), fieldName);
                    return f != null ? (Number) f.get(property) : null;
                }
                case "float": {
                    Field f = hook.findFieldInHierarchy(property.getClass(), MyauMappings.FIELD_FLOAT_MIN);
                    return f != null ? (Number) f.get(property) : null;
                }
                default:
                    return null;
            }
        } catch (Exception e) {
            MyauLogger.error("getPropertyMin", e);
            return null;
        }
    }

    /**
     * Gets the maximum value from a ranged Myau property.
     * Supports RangedValue (Integer), FloatValue (Float), and IntValue (Integer).
     *
     * @return the max value as Number, or null if not a ranged type
     */
    public Number getPropertyMax(Object property) {
        String type = getPropertyType(property);
        try {
            switch (type) {
                case "integer": {
                    String className = property.getClass().getName();
                    String fieldName;
                    if (className.equals(CLASS_INTEGER_PROPERTY)) {
                        fieldName = MyauMappings.FIELD_RANGED_MAX;
                    } else if (className.equals(CLASS_INT_VALUE)) {
                        fieldName = MyauMappings.FIELD_INT_MAX;
                    } else {
                        return null;
                    }
                    Field f = hook.findFieldInHierarchy(property.getClass(), fieldName);
                    return f != null ? (Number) f.get(property) : null;
                }
                case "float": {
                    Field f = hook.findFieldInHierarchy(property.getClass(), MyauMappings.FIELD_FLOAT_MAX);
                    return f != null ? (Number) f.get(property) : null;
                }
                default:
                    return null;
            }
        } catch (Exception e) {
            MyauLogger.error("getPropertyMax", e);
            return null;
        }
    }

    /**
     * Checks whether a Myau property is currently visible
     * (its BooleanSupplier visibility condition returns true).
     */
    public boolean isPropertyVisible(Object property) {
        try {
            Field visField = hook.findFieldInHierarchy(property.getClass(), MyauMappings.FIELD_VALUE_VISIBILITY);
            if (visField != null) {
                Object supplier = visField.get(property);
                if (supplier instanceof java.util.function.BooleanSupplier) {
                    return ((java.util.function.BooleanSupplier) supplier).getAsBoolean();
                }
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    public String getPropertyName(Object property) throws Exception {
        return (String) hook.getPropertyGetNameMethod().invoke(property);
    }

    public String getClientName() throws Exception {
        Class<?> mainClass = hook.getCachedClass(CLASS_MAIN);
        Field nameField = hook.getCachedField(mainClass, FIELD_CLIENT_NAME);
        return (String) nameField.get(null);
    }

    public String getClientVersion() {
        try {
            Class<?> mainClass = hook.getCachedClass(CLASS_MAIN);
            InputStreamReader reader = new InputStreamReader(
                    mainClass.getResourceAsStream("/mcmod.info"), StandardCharsets.UTF_8);
            JsonArray arr = new JsonParser().parse(reader).getAsJsonArray();
            JsonObject modInfo = arr.get(0).getAsJsonObject();
            String version = modInfo.get("version").getAsString();
            reader.close();
            return version;
        } catch (Exception e) {
            return "Unknown";
        }
    }

    public String getUsername() {
        if (this.cachedUsername != null) {
            return this.cachedUsername;
        }

        try {
            Class<?> mainClass = hook.getCachedClass(CLASS_MAIN);
            for (Field f : mainClass.getDeclaredFields()) {
                if (f.getType() == String.class) {
                    f.setAccessible(true);
                    Object val = f.get(null);
                    if (val != null && val.toString().length() > 0 && val.toString().length() <= 16) {
                        this.cachedUsername = val.toString();
                        return this.cachedUsername;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        this.cachedUsername = "Unknown";
        return this.cachedUsername;
    }

    public int getModuleCount() {
        LinkedHashMap<Class<?>, Object> modules = hook.getModulesMap();
        return modules != null ? modules.size() : 0;
    }

    public int getCommandCount() {
        try {
            Field commandsField = hook.getCachedField(hook.getCommandManager().getClass(), FIELD_COMMANDS_LIST);
            ArrayList<?> commands = (ArrayList<?>) commandsField.get(hook.getCommandManager());
            return commands.size();
        } catch (Exception e) {
            return 0;
        }
    }

    public int getConfigCount() {
        try {
            File configDir = new File(mc.mcDataDir, "config/myau");
            if (configDir.exists() && configDir.isDirectory()) {
                String[] files = configDir.list((dir, name) -> name.endsWith(".json"));
                return files != null ? files.length : 0;
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    public void sendMessage(String message) {
        try {
            String prefix;
            try {
                prefix = getClientName();
            } catch (Exception e) {
                prefix = "§7[§cMyau§7]§r ";
            }

            String formatted = (prefix + message).replaceAll("&([0-9a-fk-or])", "§$1");
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText(formatted));
            }
        } catch (Exception e) {
            MyauLogger.error("Manager:sendMessage", e);
        }
    }

    public void reloadModuleCommand() throws Exception {
        Class<?> mainClass = hook.getCachedClass(CLASS_MAIN);

        Field cmField = hook.getCachedField(mainClass, FIELD_COMMAND_MANAGER);
        Object cm = cmField.get(null);

        // meowww
        Field commandsField = hook.getCachedField(cm.getClass(), FIELD_COMMANDS_LIST);
        @SuppressWarnings("unchecked")
        ArrayList<Object> commands = (ArrayList<Object>) commandsField.get(cm);

        Object moduleCommand = findModuleCommand(commands);
        if (moduleCommand == null) {
            throw new Exception("ModuleCommand not found");
        }

        Field mmField = hook.getCachedField(mainClass, FIELD_MODULE_MANAGER);
        Object mm = mmField.get(null);
        Field modulesField = hook.getCachedField(mm.getClass(), FIELD_MODULES_MAP);
        LinkedHashMap<?, ?> modules = (LinkedHashMap<?, ?>) modulesField.get(mm);

        Field namesField = moduleCommand.getClass().getField(FIELD_COMMAND_NAMES);
        @SuppressWarnings("unchecked")
        ArrayList<String> names = (ArrayList<String>) namesField.get(moduleCommand);
        names.clear();
        names.ensureCapacity(modules.size());

        Method getNameMethod = hook.getModuleGetNameMethod();
        for (Object module : modules.values()) {
            String moduleName = (String) getNameMethod.invoke(module);
            names.add(moduleName);
        }

        MyauLogger.info("Reloaded ModuleCommand with " + names.size() + " modules");
    }

    public void saveCurrentConfig() throws Exception {
        ArrayList<String> args = new ArrayList<>();
        args.add("config");
        args.add("save");
        runCommandByName("config", args);
    }

    public void runCommandByName(String commandName, ArrayList<String> args)
            throws Exception {
        Object cm = hook.getCommandManager();
        if (cm == null) {
            throw new Exception("Command manager not available");
        }
        Field commandsField = hook.getCachedField(
                cm.getClass(), FIELD_COMMANDS_LIST);
        @SuppressWarnings("unchecked")
        ArrayList<Object> commands = (ArrayList<Object>) commandsField.get(cm);
        Method runMethod = hook.getCommandRunMethod();
        if (runMethod == null) {
            throw new Exception("Command run method not available");
        }

        for (Object cmd : commands) {
            try {
                Field namesField = cmd.getClass().getField(FIELD_COMMAND_NAMES);
                @SuppressWarnings("unchecked")
                ArrayList<String> names = (ArrayList<String>) namesField.get(cmd);
                if (names != null && names.contains(commandName)) {
                    runMethod.invoke(cmd, args, System.currentTimeMillis());
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        throw new Exception("Command not found: " + commandName);
    }

    private Object findModuleCommand(ArrayList<Object> commands) {
        try {
            Class<?> moduleCommandClass = hook.getCachedClass(CLASS_MODULE_COMMAND);
            for (Object cmd : commands) {
                if (moduleCommandClass.isInstance(cmd)) {
                    return cmd;
                }
            }
        } catch (ClassNotFoundException ignored) {
        }

        // some horrid fallback
        for (Object cmd : commands) {
            try {
                Field namesField = cmd.getClass().getField(FIELD_COMMAND_NAMES);
                Object namesObj = namesField.get(cmd);
                if (namesObj instanceof ArrayList) {
                    ArrayList<?> names = (ArrayList<?>) namesObj;
                    if (names.contains("AimAssist")) {
                        return cmd;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public void setClientName(String name) throws Exception {
        Class<?> mainClass = hook.getCachedClass(CLASS_MAIN);
        Field nameField = hook.getCachedField(mainClass, FIELD_CLIENT_NAME);
        nameField.set(null, name);
    }

    public void registerModuleCallbacks(Object moduleInstance, Runnable onEnable, Runnable onDisable) {
        hook.registerModuleCallbacks(moduleInstance, onEnable, onDisable);
    }

    public Field findFieldInHierarchy(Class<?> clazz, String fieldName) {
        return hook.findFieldInHierarchy(clazz, fieldName);
    }

    public Class<?> getCachedClass(String className) throws ClassNotFoundException {
        return hook.getCachedClass(className);
    }

    public Field getCachedField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        return hook.getCachedField(clazz, fieldName);
    }

    public Method getModuleGetNameMethod() {
        return hook.getModuleGetNameMethod();
    }

    public Method getPropertyGetValueMethod() {
        return hook.getPropertyGetValueMethod();
    }

    public Method getPropertyGetNameMethod() {
        return hook.getPropertyGetNameMethod();
    }

    public LinkedHashMap<Class<?>, Object> getModulesMap() {
        return hook.getModulesMap();
    }

    public void clearCaches() {
        this.moduleCache.clear();
        this.propertyCache.clear();
    }
}
