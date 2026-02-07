package coffee.axle.suim.hooks;

import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static coffee.axle.suim.hooks.MyauMappings.*;

/**
 * MyauHook reflection based ASM thingy
 * Ported from meow's ClientHook
 * 
 * @author axle.coffee
 * @author maybsomeday
 */
public class MyauHook {
    private static MyauHook instance;
    private static final Minecraft mc = Minecraft.getMinecraft();

    // Cached managers from Myau
    private Object moduleManager;
    private Object commandManager;
    private LinkedHashMap<Class<?>, Object> modulesMap;
    private Class<?> commandBaseClass;

    // Cached methods
    private Method moduleGetNameMethod;
    private Method propertyGetValueMethod;
    private Method propertyGetNameMethod;
    private Method commandRunMethod;

    // Reflection caches
    private final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();
    private final Map<String, Field> fieldCache = new ConcurrentHashMap<>();
    private final Map<String, Object> moduleCache = new ConcurrentHashMap<>();
    private final Map<String, Object> propertyCache = new ConcurrentHashMap<>();

    // Command handling
    private static final Map<String, CommandHandler> commandHandlers = new ConcurrentHashMap<>();
    private static int commandIdCounter = 0;

    // Module callbacks
    private final Map<String, Runnable> moduleEnableCallbacks = new ConcurrentHashMap<>();
    private final Map<String, Runnable> moduleDisableCallbacks = new ConcurrentHashMap<>();

    // Client info
    private String clientNamePrefix;
    private String cachedUsername = null;

    public String getModuleName(Object module) {
        if (module == null || moduleGetNameMethod == null)
            return "unknown";
        try {
            return (String) moduleGetNameMethod.invoke(module);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private MyauHook() {
    }

    public static MyauHook getInstance() {
        if (instance == null) {
            synchronized (MyauHook.class) {
                if (instance == null) {
                    instance = new MyauHook();
                }
            }
        }
        return instance;
    }

    public boolean initialize() {
        try {
            MyauLogger.log("HOOK_INIT");

            Class<?> mainClass = getCachedClass(CLASS_MAIN);
            Field mmField = getCachedField(mainClass, FIELD_MODULE_MANAGER);
            this.moduleManager = mmField.get(null);

            if (this.moduleManager == null) {
                MyauLogger.log("HOOK_NOT_READY");
                return false;
            }

            Field modulesField = getCachedField(this.moduleManager.getClass(), FIELD_MODULES_MAP);
            this.modulesMap = (LinkedHashMap<Class<?>, Object>) modulesField.get(this.moduleManager);

            if (this.modulesMap == null || this.modulesMap.isEmpty()) {
                return false;
            }

            cacheCommonMethods();
            cacheCommandManager();

            MyauLogger.logMore("HOOK_SUCCESS", this.modulesMap.size() + " modules");
            return true;

        } catch (Exception e) {
            MyauLogger.error("HOOK_FAIL", e);
            return false;
        }
    }

    private void cacheCommonMethods() throws Exception {
        Object firstModule = this.modulesMap.values().iterator().next();
        this.moduleGetNameMethod = firstModule.getClass().getMethod(METHOD_GET_NAME);

        for (Field f : firstModule.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            Object prop = f.get(firstModule);
            if (prop != null) {
                try {
                    this.propertyGetNameMethod = prop.getClass().getMethod(METHOD_PROPERTY_GET_NAME);
                    this.propertyGetValueMethod = prop.getClass().getMethod(METHOD_PROPERTY_GET_VALUE);
                    break;
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void cacheCommandManager() throws Exception {
        Class<?> mainClass = getCachedClass(CLASS_MAIN);
        Field cmField = mainClass.getDeclaredField(FIELD_COMMAND_MANAGER);
        cmField.setAccessible(true);
        this.commandManager = cmField.get(null);

        this.clientNamePrefix = getClientName();

        Field commandsField = this.commandManager.getClass().getDeclaredField(FIELD_COMMANDS_LIST);
        commandsField.setAccessible(true);
        ArrayList<?> commands = (ArrayList<?>) commandsField.get(this.commandManager);

        if (!commands.isEmpty()) {
            Object firstCommand = commands.get(0);
            this.commandBaseClass = firstCommand.getClass().getSuperclass();
            this.commandRunMethod = this.commandBaseClass.getDeclaredMethod(METHOD_COMMAND_RUN, ArrayList.class,
                    long.class);
        } else {
            throw new Exception("No commands found");
        }
    }

    public Object getPropertyValue(Object property) throws Exception {
        return propertyGetValueMethod.invoke(property);
    }

    public String getPropertyName(Object property) throws Exception {
        return (String) propertyGetNameMethod.invoke(property);
    }

    public Object createModule(String moduleName) throws Exception {
        String className = GENERATED_PACKAGE + moduleName + "_" + System.currentTimeMillis();
        byte[] classBytes = generateModuleClass(className);

        ClassLoader loader = new FastClassLoader(getClass().getClassLoader(), className, classBytes);
        Class<?> generatedClass = loader.loadClass(className);
        Constructor<?> constructor = generatedClass.getDeclaredConstructor(
                String.class, boolean.class, int.class, long.class);
        constructor.setAccessible(true);

        Object instance = constructor.newInstance(moduleName, false, 0, 0L);

        Field nameField = findFieldInHierarchy(instance.getClass(), FIELD_MODULE_NAME);
        if (nameField != null) {
            nameField.set(instance, moduleName);
        }

        moduleCache.put(moduleName, instance);
        return instance;
    }

    public void injectModule(Object module, Class<?> dummyClass) {
        modulesMap.put(dummyClass, module);
    }

    public boolean injectProperty(Object module, Object property) {
        return injectPropertyAfter(module, property, null);
    }

    public boolean injectPropertyAfter(Object module, Object property, String afterPropertyName) {
        try {
            try {
                Class<?> moduleBaseClass = Class.forName(CLASS_MODULE_BASE);
                Method setOwnerMethod = property.getClass().getMethod(METHOD_PROPERTY_SET_OWNER, moduleBaseClass);
                setOwnerMethod.invoke(property, module);
            } catch (Exception ignored) {
            }

            Class<?> myauClass = Class.forName(CLASS_MAIN);
            Field pmField = myauClass.getDeclaredField(FIELD_PROPERTY_MANAGER);
            pmField.setAccessible(true);
            Object pm = pmField.get(null);

            if (pm != null) {
                Field mapField = pm.getClass().getDeclaredField(FIELD_PROPERTY_MAP);
                mapField.setAccessible(true);

                @SuppressWarnings("unchecked")
                Map<Class<?>, ArrayList<Object>> map = (Map<Class<?>, ArrayList<Object>>) mapField.get(pm);

                ArrayList<Object> props = map.computeIfAbsent(
                        module.getClass(),
                        k -> new ArrayList<>());

                int insertIndex = props.size();

                if (afterPropertyName != null) {
                    for (int i = 0; i < props.size(); i++) {
                        try {
                            String propName = (String) propertyGetNameMethod.invoke(props.get(i));
                            if (propName.equals(afterPropertyName)) {
                                insertIndex = i + 1;
                                break;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }

                props.add(insertIndex, property);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void registerProperties(Object module, Object... properties) throws Exception {
        if (properties.length == 0)
            return;

        Class<?> moduleBaseClass = getCachedClass(CLASS_MODULE_BASE);

        Method setOwnerMethod = null;
        for (Object prop : properties) {
            if (setOwnerMethod == null) {
                setOwnerMethod = prop.getClass().getMethod(METHOD_PROPERTY_SET_OWNER, moduleBaseClass);
            }
            setOwnerMethod.invoke(prop, module);
        }

        Class<?> myauClass = getCachedClass(CLASS_MAIN);
        Field pmField = getCachedField(myauClass, FIELD_PROPERTY_MANAGER);
        Object pm = pmField.get(null);

        if (pm != null) {
            Field mapField = getCachedField(pm.getClass(), FIELD_PROPERTY_MAP);

            @SuppressWarnings("unchecked")
            LinkedHashMap<Class<?>, ArrayList<Object>> propMap = (LinkedHashMap<Class<?>, ArrayList<Object>>) mapField
                    .get(pm);

            Class<?> moduleClass = module.getClass();
            ArrayList<Object> props = propMap.computeIfAbsent(moduleClass, k -> new ArrayList<>());

            Collections.addAll(props, properties);
            for (Object prop : properties) {
                try {
                    String propName = (String) propertyGetNameMethod.invoke(prop);
                    propertyCache.put(module.hashCode() + "." + propName, prop);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public boolean registerPropertiesToModule(Object module, Object... properties) {
        try {
            registerProperties(module, properties);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Object createFloatProperty(String name, float defaultValue, float min, float max) throws Exception {
        Class<?> floatPropertyClass = getCachedClass(CLASS_FLOAT_PROPERTY);

        Constructor<?> constructor = null;
        for (Constructor<?> c : floatPropertyClass.getDeclaredConstructors()) {
            Class<?>[] params = c.getParameterTypes();
            if (params.length == 5 &&
                    params[0] == long.class &&
                    params[1] == String.class &&
                    params[2] == Float.class &&
                    params[3] == Float.class &&
                    params[4] == Float.class) {
                constructor = c;
                break;
            }
        }

        if (constructor == null) {
            throw new NoSuchMethodException("Could not find FloatProperty constructor");
        }

        constructor.setAccessible(true);
        return constructor.newInstance(0L, name, Float.valueOf(defaultValue), Float.valueOf(min), Float.valueOf(max));
    }

    public Object createStringProperty(String name, String defaultValue) throws Exception {
        Class<?> stringPropertyClass = getCachedClass(CLASS_STRING_PROPERTY);

        Constructor<?> constructor = null;
        for (Constructor<?> c : stringPropertyClass.getDeclaredConstructors()) {
            Class<?>[] params = c.getParameterTypes();
            if (params.length == 2 && params[0] == String.class && params[1] == String.class) {
                constructor = c;
                break;
            }
            if (params.length == 3) {
                if (params[0] == String.class && params[1] == String.class) {
                    constructor = c;
                    break;
                }
                if (params[0] == long.class && params[1] == String.class && params[2] == String.class) {
                    constructor = c;
                    break;
                }
            }
        }

        if (constructor == null) {
            throw new NoSuchMethodException("Could not find StringProperty constructor");
        }

        constructor.setAccessible(true);
        Class<?>[] params = constructor.getParameterTypes();

        if (params.length == 2) {
            return constructor.newInstance(name, defaultValue);
        } else if (params.length == 3) {
            if (params[0] == long.class) {
                return constructor.newInstance(0L, name, defaultValue);
            } else {
                return constructor.newInstance(name, defaultValue, 0L);
            }
        }

        throw new NoSuchMethodException("Unexpected StringProperty constructor signature");
    }

    public Object createBooleanProperty(String name, boolean defaultValue) throws Exception {
        Class<?> booleanPropertyClass = getCachedClass(CLASS_BOOLEAN_PROPERTY);
        Constructor<?> constructor = booleanPropertyClass.getDeclaredConstructor(
                String.class, Boolean.class, long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(name, defaultValue, 0L);
    }

    public Object createIntegerProperty(String name, int defaultValue, int min, int max) throws Exception {
        Class<?> intPropertyClass = getCachedClass(CLASS_INTEGER_PROPERTY);

        Constructor<?> targetCtor = null;
        int[] intParamIdx = null;

        for (Constructor<?> ctor : intPropertyClass.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length < 4)
                continue;
            if (params[0] != String.class)
                continue;

            List<Integer> intIndices = new ArrayList<>();
            for (int i = 0; i < params.length; i++) {
                if (params[i] == Integer.class || params[i] == int.class) {
                    intIndices.add(i);
                }
            }

            if (intIndices.size() >= 3) {
                targetCtor = ctor;
                intParamIdx = new int[] { intIndices.get(0), intIndices.get(1), intIndices.get(2) };
                break;
            }
        }

        if (targetCtor == null) {
            throw new NoSuchMethodException("No suitable IntProperty constructor found");
        }

        targetCtor.setAccessible(true);
        Class<?>[] params = targetCtor.getParameterTypes();
        Object[] args = new Object[params.length];

        args[0] = name;
        args[intParamIdx[0]] = Integer.valueOf(defaultValue);
        args[intParamIdx[1]] = Integer.valueOf(min);
        args[intParamIdx[2]] = Integer.valueOf(max);

        for (int i = 1; i < params.length; i++) {
            if (args[i] != null)
                continue;
            Class<?> t = params[i];
            if (t == boolean.class)
                args[i] = false;
            else if (t == int.class)
                args[i] = 0;
            else if (t == long.class)
                args[i] = 0L;
            else
                args[i] = null;
        }

        return targetCtor.newInstance(args);
    }

    public Object createEnumProperty(String name, int defaultValue, String[] values) throws Exception {
        Class<?> enumPropertyClass = getCachedClass(CLASS_ENUM_PROPERTY);
        Constructor<?> constructor = enumPropertyClass.getDeclaredConstructor(
                String.class, long.class, Integer.class, String[].class);
        constructor.setAccessible(true);
        return constructor.newInstance(name, 0L, defaultValue, values);
    }

    // ==================== Module Query API ====================

    public Object findModule(String name) {
        Object cached = moduleCache.get(name);
        if (cached != null) {
            return cached;
        }

        for (Object module : modulesMap.values()) {
            try {
                String moduleName = (String) moduleGetNameMethod.invoke(module);
                if (moduleName.equals(name)) {
                    moduleCache.put(name, module);
                    return module;
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    public Object findProperty(Object module, String propertyName) {
        String key = module.hashCode() + "." + propertyName;

        Object cached = propertyCache.get(key);
        if (cached != null) {
            return cached;
        }

        try {
            for (Field f : module.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object prop = f.get(module);
                if (prop == null)
                    continue;

                try {
                    String name = (String) propertyGetNameMethod.invoke(prop);
                    if (name.equals(propertyName)) {
                        propertyCache.put(key, prop);
                        return prop;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public boolean isModuleEnabled(Object module) {
        try {
            Field enabledField = findFieldInHierarchy(module.getClass(), FIELD_MODULE_ENABLED);
            if (enabledField != null) {
                enabledField.setAccessible(true);
                return enabledField.getBoolean(module);
            }
        } catch (Exception e) {
        }
        return false;
    }

    public boolean isModuleEnabled(String moduleName) {
        Object module = findModule(moduleName);
        if (module == null)
            return false;
        return isModuleEnabled(module);
    }

    public void setModuleEnabled(Object module, boolean enabled) throws Exception {
        Method setEnabledMethod = module.getClass().getMethod(METHOD_SET_ENABLED, short.class, boolean.class,
                short.class, int.class);
        setEnabledMethod.invoke(module, (short) 0, enabled, (short) 1, 0);
    }

    public void registerCommand(ArrayList<String> commandNames, CommandHandler handler) throws Exception {
        if (commandManager == null) {
            cacheCommandManager();
        }

        String handlerId = "cmd_" + commandIdCounter++;
        commandHandlers.put(handlerId, handler);

        String className = GENERATED_PACKAGE + "Command_" + commandNames.get(0) + "_" + System.currentTimeMillis();
        byte[] classBytes = generateCommandClass(className, handlerId);

        ClassLoader loader = new FastClassLoader(getClass().getClassLoader(), className, classBytes);
        Class<?> generatedClass = loader.loadClass(className);

        Constructor<?> constructor = generatedClass.getDeclaredConstructor(ArrayList.class, String.class);
        constructor.setAccessible(true);
        Object commandInstance = constructor.newInstance(commandNames, handlerId);

        Field commandsField = getCachedField(commandManager.getClass(), FIELD_COMMANDS_LIST);
        ArrayList<Object> commands = (ArrayList<Object>) commandsField.get(commandManager);
        commands.add(commandInstance);
    }

    public void reloadModuleCommand() throws Exception {
        Class<?> myauClass = getCachedClass(CLASS_MAIN);

        Field cmField = getCachedField(myauClass, FIELD_COMMAND_MANAGER);
        Object cm = cmField.get(null);

        Field commandsField = getCachedField(cm.getClass(), FIELD_COMMANDS_LIST);
        ArrayList<Object> commands = (ArrayList<Object>) commandsField.get(cm);

        Object moduleCommand = findModuleCommand(commands);

        if (moduleCommand == null) {
            throw new Exception("ModuleCommand not found");
        }

        Field mmField = getCachedField(myauClass, FIELD_MODULE_MANAGER);
        Object mm = mmField.get(null);

        Field modulesField = getCachedField(mm.getClass(), FIELD_MODULES_MAP);
        LinkedHashMap<?, ?> modules = (LinkedHashMap<?, ?>) modulesField.get(mm);

        Field namesField = moduleCommand.getClass().getField(FIELD_COMMAND_NAMES);
        ArrayList<String> names = (ArrayList<String>) namesField.get(moduleCommand);

        names.clear();
        names.ensureCapacity(modules.size());

        for (Object module : modules.values()) {
            String moduleName = (String) moduleGetNameMethod.invoke(module);
            names.add(moduleName);
        }

        MyauLogger.log("Reloaded ModuleCommand with " + names.size() + " modules");
    }

    private Object findModuleCommand(ArrayList<Object> commands) {
        try {
            Class<?> moduleCommandClass = getCachedClass(CLASS_MODULE_COMMAND);
            for (Object cmd : commands) {
                if (moduleCommandClass.isInstance(cmd)) {
                    return cmd;
                }
            }
        } catch (ClassNotFoundException ignored) {
        }

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

    public void registerEventHandler(String eventClassName, EventHandler handler, byte priority) throws Exception {
        String className = GENERATED_PACKAGE + "EventHandler_" + System.currentTimeMillis() + "_" + Math.random();

        byte[] classBytes = generateEventHandlerClass(className, eventClassName, priority);

        ClassLoader loader = new FastClassLoader(getClass().getClassLoader(), className, classBytes);
        Class<?> handlerClass = loader.loadClass(className);

        Constructor<?> constructor = handlerClass.getDeclaredConstructor(EventHandler.class);
        constructor.setAccessible(true);
        Object handlerInstance = constructor.newInstance(handler);

        Class<?> eventBusClass = getCachedClass(CLASS_EVENT_BUS);
        Method registerMethod = eventBusClass.getMethod(METHOD_EVENT_REGISTER, Object.class);
        registerMethod.invoke(null, handlerInstance);
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
                mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(formatted));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getClientName() throws Exception {
        Class<?> myauClass = getCachedClass(CLASS_MAIN);
        Field clientNameField = getCachedField(myauClass, FIELD_CLIENT_NAME);
        return (String) clientNameField.get(null);
    }

    public void setClientNamePrefix(String rawPrefix) {
        this.clientNamePrefix = rawPrefix;
    }

    public void setClientName(String name) throws Exception {
        Class<?> myauClass = getCachedClass(CLASS_MAIN);
        Field clientNameField = getCachedField(myauClass, FIELD_CLIENT_NAME);
        clientNameField.set(null, name);
        this.clientNamePrefix = name;
    }

    public String getClientVersion() {
        try {
            Class<?> myauClass = getCachedClass(CLASS_MAIN);
            InputStreamReader reader = new InputStreamReader(
                    myauClass.getResourceAsStream("/mcmod.info"),
                    java.nio.charset.StandardCharsets.UTF_8);

            com.google.gson.JsonArray arr = new com.google.gson.JsonParser().parse(reader).getAsJsonArray();
            com.google.gson.JsonObject modInfo = arr.get(0).getAsJsonObject();
            String version = modInfo.get("version").getAsString();

            reader.close();
            return version;

        } catch (Exception e) {
            MyauLogger.error("Failed to read version from mcmod.info", e);
            return "Unknown";
        }
    }

    public int getModuleCount() {
        return modulesMap != null ? modulesMap.size() : 0;
    }

    public int getCommandCount() {
        try {
            if (commandManager == null) {
                cacheCommandManager();
            }

            Field commandsField = getCachedField(commandManager.getClass(), FIELD_COMMANDS_LIST);
            ArrayList<?> commands = (ArrayList<?>) commandsField.get(commandManager);
            return commands.size();

        } catch (Exception e) {
            MyauLogger.error("Failed to get command count", e);
            return 0;
        }
    }

    public String getUsername() {
        if (cachedUsername != null) {
            return cachedUsername;
        }

        try {
            File configFile = new File(System.getProperty("user.home"), "myau.ini");
            if (configFile.exists()) {
                List<String> lines = Files.readAllLines(configFile.toPath());
                for (String line : lines) {
                    if (line.trim().toLowerCase().startsWith("username")) {
                        String[] parts = line.split("=", 2);
                        if (parts.length > 1) {
                            cachedUsername = parts[1].trim();
                            return cachedUsername;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        cachedUsername = "Unknown";
        return cachedUsername;
    }

    public int getConfigCount() {
        try {
            java.io.File configDir = new java.io.File(mc.mcDataDir, "config/myau");

            if (!configDir.exists() || !configDir.isDirectory()) {
                return 0;
            }

            java.io.File[] jsonFiles = configDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
            return jsonFiles != null ? jsonFiles.length : 0;

        } catch (Exception e) {
            MyauLogger.error("Failed to count configs", e);
            return 0;
        }
    }

    public int getModuleKeybind(Object module) {
        try {
            Field keyField = findFieldInHierarchy(module.getClass(), FIELD_MODULE_KEYBIND);
            if (keyField != null) {
                return keyField.getInt(module);
            }
        } catch (Exception e) {
            MyauLogger.error("Failed to get module keybind", e);
        }
        return -1;
    }

    public boolean setModuleKeybind(Object module, int keyCode) {
        try {
            Field keyField = findFieldInHierarchy(module.getClass(), FIELD_MODULE_KEYBIND);
            if (keyField != null) {
                keyField.setInt(module, keyCode);
                return true;
            }
        } catch (Exception e) {
            MyauLogger.error("Failed to set module keybind", e);
        }
        return false;
    }

    public void clearCaches() {
        moduleCache.clear();
        propertyCache.clear();
        fieldCache.clear();
    }

    public Class<?> getCachedClass(String className) throws ClassNotFoundException {
        return this.classCache.computeIfAbsent(className, name -> {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public Field getCachedField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        String key = clazz.getName() + "." + fieldName;
        Field field = this.fieldCache.get(key);
        if (field == null) {
            field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            this.fieldCache.put(key, field);
        }
        return field;
    }

    public Field findFieldInHierarchy(Class<?> clazz, String fieldName) {
        String key = clazz.getName() + ".hierarchy." + fieldName;
        Field field = this.fieldCache.get(key);
        if (field != null) {
            return field;
        }

        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            try {
                field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                this.fieldCache.put(key, field);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    public Method findMethodInHierarchy(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        for (Class<?> currentClass = clazz; currentClass != null; currentClass = currentClass.getSuperclass()) {
            try {
                Method method = currentClass.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    public byte[] generateModuleClass(String className) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        String internalName = className.replace('.', '/');
        String moduleBase = CLASS_MODULE_BASE.replace('.', '/');

        cw.visit(52, 1, internalName, null, moduleBase, null);

        // Constructor
        MethodVisitor mv = cw.visitMethod(1, "<init>", SIG_MODULE_CONSTRUCTOR, null, null);
        mv.visitCode();
        mv.visitVarInsn(25, 0);
        mv.visitVarInsn(25, 1);
        mv.visitVarInsn(21, 2);
        mv.visitVarInsn(21, 3);
        mv.visitVarInsn(22, 4);
        mv.visitMethodInsn(183, moduleBase, "<init>", SIG_MODULE_CONSTRUCTOR, false);
        mv.visitInsn(177);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // onEnable
        mv = cw.visitMethod(1, METHOD_ON_ENABLE, SIG_ON_ENABLE, null, null);
        mv.visitCode();
        mv.visitVarInsn(25, 0);
        mv.visitVarInsn(21, 1);
        mv.visitVarInsn(21, 2);
        mv.visitVarInsn(21, 3);
        mv.visitMethodInsn(183, moduleBase, METHOD_ON_ENABLE, SIG_ON_ENABLE, false);
        mv.visitVarInsn(25, 0);
        mv.visitMethodInsn(184, "coffee/axle/suim/hooks/MyauHook", "triggerOnEnable",
                "(Ljava/lang/Object;)V", false);
        mv.visitInsn(177);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // onDisable
        mv = cw.visitMethod(1, METHOD_ON_DISABLE, SIG_ON_DISABLE, null, null);
        mv.visitCode();
        mv.visitVarInsn(25, 0);
        mv.visitVarInsn(22, 1);
        mv.visitMethodInsn(183, moduleBase, METHOD_ON_DISABLE, SIG_ON_DISABLE, false);
        mv.visitVarInsn(25, 0);
        mv.visitMethodInsn(184, "coffee/axle/suim/hooks/MyauHook", "triggerOnDisable",
                "(Ljava/lang/Object;)V", false);
        mv.visitInsn(177);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    public byte[] generateCommandClass(String className, String handlerId) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        String internalName = className.replace('.', '/');
        String superClassName = this.commandBaseClass.getName().replace('.', '/');

        cw.visit(52, 1, internalName, null, superClassName, null);
        cw.visitField(18, "handlerId", "Ljava/lang/String;", null, null);

        // Constructor
        MethodVisitor mv = cw.visitMethod(1, "<init>", "(Ljava/util/ArrayList;Ljava/lang/String;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(25, 0);
        mv.visitVarInsn(25, 1);
        mv.visitMethodInsn(183, superClassName, "<init>", SIG_COMMAND_CONSTRUCTOR, false);
        mv.visitVarInsn(25, 0);
        mv.visitVarInsn(25, 2);
        mv.visitFieldInsn(181, internalName, "handlerId", "Ljava/lang/String;");
        mv.visitInsn(177);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // Run method
        mv = cw.visitMethod(1, METHOD_COMMAND_RUN, SIG_COMMAND_RUN, "(Ljava/util/ArrayList<Ljava/lang/String;>;J)V",
                null);
        mv.visitCode();
        mv.visitVarInsn(25, 0);
        mv.visitFieldInsn(180, internalName, "handlerId", "Ljava/lang/String;");
        mv.visitVarInsn(25, 1);
        mv.visitMethodInsn(184, "coffee/axle/suim/hooks/MyauHook", "invokeHandler",
                "(Ljava/lang/String;Ljava/util/ArrayList;)V", false);
        mv.visitInsn(177);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    public byte[] generateEventHandlerClass(String className, String eventClass, byte priority) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        String internalName = className.replace('.', '/');
        String eventInternalName = eventClass.replace('.', '/');
        String annotationDesc = "L" + CLASS_EVENT_ANNOTATION.replace('.', '/') + ";";

        cw.visit(52, 1, internalName, null, "java/lang/Object", null);
        cw.visitField(18, "handler", "Lcoffee/axle/suim/hooks/MyauHook$EventHandler;", null, null);

        // Constructor
        MethodVisitor mv = cw.visitMethod(1, "<init>",
                "(Lcoffee/axle/suim/hooks/MyauHook$EventHandler;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(25, 0);
        mv.visitMethodInsn(183, "java/lang/Object", "<init>", "()V", false);
        mv.visitVarInsn(25, 0);
        mv.visitVarInsn(25, 1);
        mv.visitFieldInsn(181, internalName, "handler",
                "Lcoffee/axle/suim/hooks/MyauHook$EventHandler;");
        mv.visitInsn(177);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // Event handler method
        mv = cw.visitMethod(1, "onEvent", "(L" + eventInternalName + ";)V", null, null);
        AnnotationVisitor av = mv.visitAnnotation(annotationDesc, true);
        av.visit("value", priority);
        av.visitEnd();
        mv.visitCode();
        mv.visitVarInsn(25, 0);
        mv.visitFieldInsn(180, internalName, "handler",
                "Lcoffee/axle/suim/hooks/MyauHook$EventHandler;");
        mv.visitVarInsn(25, 1);
        mv.visitMethodInsn(185, "coffee/axle/suim/hooks/MyauHook$EventHandler", "handleEvent",
                "(Ljava/lang/Object;)V", true);
        mv.visitInsn(177);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    public static void invokeHandler(String handlerId, ArrayList<String> args) {
        CommandHandler handler = commandHandlers.get(handlerId);
        if (handler != null) {
            try {
                handler.handle(args);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void registerCommandHandler(String handlerId, CommandHandler handler) {
        commandHandlers.put(handlerId, handler);
    }

    public String nextCommandId() {
        return "cmd_" + commandIdCounter++;
    }

    public void registerModuleCallbacks(Object moduleInstance, Runnable onEnable, Runnable onDisable) {
        String key = moduleInstance.getClass().getName() + "@" + System.identityHashCode(moduleInstance);
        if (onEnable != null)
            this.moduleEnableCallbacks.put(key, onEnable);
        if (onDisable != null)
            this.moduleDisableCallbacks.put(key, onDisable);
    }

    public static void triggerOnEnable(Object moduleInstance) {
        String key = moduleInstance.getClass().getName() + "@" + System.identityHashCode(moduleInstance);
        Runnable callback = getInstance().moduleEnableCallbacks.get(key);
        if (callback != null) {
            try {
                callback.run();
            } catch (Exception e) {
                MyauLogger.error("CALLBACK_FAIL", e);
            }
        }
    }

    public static void triggerOnDisable(Object moduleInstance) {
        String key = moduleInstance.getClass().getName() + "@" + System.identityHashCode(moduleInstance);
        Runnable callback = getInstance().moduleDisableCallbacks.get(key);
        if (callback != null) {
            try {
                callback.run();
            } catch (Exception e) {
                MyauLogger.error("CALLBACK_FAIL", e);
            }
        }
    }

    public Object getModuleManager() {
        return this.moduleManager;
    }

    public Object getCommandManager() {
        return this.commandManager;
    }

    public LinkedHashMap<Class<?>, Object> getModulesMap() {
        return this.modulesMap;
    }

    public Class<?> getCommandBaseClass() {
        return this.commandBaseClass;
    }

    public Method getModuleGetNameMethod() {
        return this.moduleGetNameMethod;
    }

    public Method getPropertyGetValueMethod() {
        return this.propertyGetValueMethod;
    }

    public Method getPropertyGetNameMethod() {
        return this.propertyGetNameMethod;
    }

    public interface CommandHandler {
        void handle(ArrayList<String> args);
    }

    public interface EventHandler {
        void handleEvent(Object event);
    }

    public static class FastClassLoader extends ClassLoader {
        private final String targetClassName;
        private final byte[] classBytes;

        public FastClassLoader(ClassLoader parent, String className, byte[] bytes) {
            super(parent);
            this.targetClassName = className;
            this.classBytes = bytes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(this.targetClassName)) {
                return defineClass(name, this.classBytes, 0, this.classBytes.length);
            }
            return super.findClass(name);
        }
    }
}
