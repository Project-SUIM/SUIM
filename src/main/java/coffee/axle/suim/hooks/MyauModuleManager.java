package coffee.axle.suim.hooks;

import coffee.axle.suim.util.MyauLogger;
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
            e.printStackTrace();
        }
        return null;
    }

    public Object getPropertyValue(Object property) throws Exception {
        return hook.getPropertyGetValueMethod().invoke(property);
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
            e.printStackTrace();
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

    public void clearCaches() {
        this.moduleCache.clear();
        this.propertyCache.clear();
    }
}
