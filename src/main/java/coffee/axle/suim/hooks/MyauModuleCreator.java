package coffee.axle.suim.hooks;

import coffee.axle.suim.util.MyauLogger;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import static coffee.axle.suim.hooks.MyauMappings.*;

/**
 * Creates and injects new modules, properties, commands, and event handlers
 * into Myau.
 */
public class MyauModuleCreator {
    private final MyauHook hook;

    public MyauModuleCreator(MyauHook hook) {
        this.hook = hook;
    }

    public Object createModule(String moduleName) throws Exception {
        return createModule(moduleName, null);
    }

    public Object createModule(String moduleName, String origin) throws Exception {
        String className = GENERATED_PACKAGE + moduleName + "_" + System.currentTimeMillis();
        byte[] classBytes = hook.generateModuleClass(className);

        ClassLoader loader = new MyauHook.FastClassLoader(getClass().getClassLoader(), className, classBytes);
        Class<?> generatedClass = loader.loadClass(className);

        Constructor<?> constructor = generatedClass.getDeclaredConstructor(String.class, boolean.class, int.class,
                long.class);
        constructor.setAccessible(true);
        Object instance = constructor.newInstance(moduleName, false, 0, 0L);

        Field nameField = hook.findFieldInHierarchy(instance.getClass(), FIELD_MODULE_NAME);
        if (nameField != null) {
            nameField.set(instance, moduleName);
        }

        if (origin != null && !origin.isEmpty()) {
            MyauWorld.registerOrigin(instance, origin);
        }

        return instance;
    }

    public void injectModule(Object module, Class<?> dummyClass) {
        hook.getModulesMap().put(dummyClass, module);
    }

    public Object createBooleanProperty(String name, boolean defaultValue) throws Exception {
        Class<?> propClass = hook.getCachedClass(CLASS_BOOLEAN_PROPERTY);
        Constructor<?> ctor = propClass.getDeclaredConstructor(String.class, Boolean.class, long.class);
        ctor.setAccessible(true);
        return ctor.newInstance(name, defaultValue, 0L);
    }

    public Object createIntegerProperty(String name, int defaultValue, int min, int max) throws Exception {
        Class<?> propClass = hook.getCachedClass(CLASS_INTEGER_PROPERTY);
        Constructor<?> targetCtor = null;
        int[] intParamIdx = null;

        for (Constructor<?> ctor : propClass.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length >= 4 && params[0] == String.class) {
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
        }

        if (targetCtor == null) {
            throw new NoSuchMethodException("No suitable IntProperty constructor found");
        }

        targetCtor.setAccessible(true);
        Class<?>[] params = targetCtor.getParameterTypes();
        Object[] args = new Object[params.length];
        args[0] = name;
        args[intParamIdx[0]] = defaultValue;
        args[intParamIdx[1]] = min;
        args[intParamIdx[2]] = max;

        // Fill remaining params with defaults
        for (int i = 1; i < params.length; i++) {
            if (args[i] == null) {
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
        }

        return targetCtor.newInstance(args);
    }

    public Object createFloatProperty(String name, float defaultValue, float min, float max) throws Exception {
        Class<?> propClass = hook.getCachedClass(CLASS_FLOAT_PROPERTY);
        Constructor<?> constructor = null;

        for (Constructor<?> c : propClass.getDeclaredConstructors()) {
            Class<?>[] params = c.getParameterTypes();
            if (params.length == 5
                    && params[0] == long.class
                    && params[1] == String.class
                    && params[2] == Float.class
                    && params[3] == Float.class
                    && params[4] == Float.class) {
                constructor = c;
                break;
            }
        }

        if (constructor == null) {
            throw new NoSuchMethodException("Could not find FloatProperty constructor");
        }

        constructor.setAccessible(true);
        return constructor.newInstance(0L, name, defaultValue, min, max);
    }

    public Object createStringProperty(String name, String defaultValue) throws Exception {
        Class<?> propClass = hook.getCachedClass(CLASS_STRING_PROPERTY);
        Constructor<?> constructor = null;

        for (Constructor<?> c : propClass.getDeclaredConstructors()) {
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
            return params[0] == long.class
                    ? constructor.newInstance(0L, name, defaultValue)
                    : constructor.newInstance(name, defaultValue, 0L);
        }
        throw new NoSuchMethodException("Unexpected StringProperty constructor signature");
    }

    public Object createEnumProperty(String name, int defaultValue, String[] values) throws Exception {
        Class<?> propClass = hook.getCachedClass(CLASS_ENUM_PROPERTY);
        Constructor<?> ctor = propClass.getDeclaredConstructor(String.class, long.class, Integer.class, String[].class);
        ctor.setAccessible(true);
        return ctor.newInstance(name, 0L, defaultValue, values);
    }

    public boolean injectProperty(Object module, Object property) {
        return injectPropertyAfter(module, property, null);
    }

    public boolean injectPropertyAfter(Object module, Object property, String afterPropertyName) {
        try {
            try {
                Class<?> moduleBase = hook.getCachedClass(CLASS_MODULE_BASE);
                Method setOwner = property.getClass().getMethod(METHOD_PROPERTY_SET_OWNER, moduleBase);
                setOwner.invoke(property, module);
            } catch (Exception ignored) {
            }

            Class<?> mainClass = hook.getCachedClass(CLASS_MAIN);
            Field pmField = mainClass.getDeclaredField(FIELD_PROPERTY_MANAGER);
            pmField.setAccessible(true);
            Object pm = pmField.get(null);

            if (pm != null) {
                Field mapField = pm.getClass().getDeclaredField(FIELD_PROPERTY_MAP);
                mapField.setAccessible(true);
                @SuppressWarnings("unchecked")
                LinkedHashMap<Class<?>, ArrayList<Object>> map = (LinkedHashMap<Class<?>, ArrayList<Object>>) mapField
                        .get(pm);

                ArrayList<Object> props = map.computeIfAbsent(module.getClass(), k -> new ArrayList<>());
                int insertIndex = props.size();

                if (afterPropertyName != null) {
                    Method getNameMethod = hook.getPropertyGetNameMethod();
                    for (int i = 0; i < props.size(); i++) {
                        try {
                            String propName = (String) getNameMethod.invoke(props.get(i));
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
            MyauLogger.error("Creator:injectProperty", e);
        }
        return false;
    }

    public void registerProperties(Object module, Object... properties) throws Exception {
        if (properties.length == 0)
            return;

        Class<?> moduleBase = hook.getCachedClass(CLASS_MODULE_BASE);
        Method setOwner = null;

        for (Object prop : properties) {
            if (setOwner == null) {
                setOwner = prop.getClass().getMethod(METHOD_PROPERTY_SET_OWNER, moduleBase);
            }
            setOwner.invoke(prop, module);
        }

        Class<?> mainClass = hook.getCachedClass(CLASS_MAIN);
        Field pmField = hook.getCachedField(mainClass, FIELD_PROPERTY_MANAGER);
        Object pm = pmField.get(null);

        if (pm != null) {
            Field mapField = hook.getCachedField(pm.getClass(), FIELD_PROPERTY_MAP);
            @SuppressWarnings("unchecked")
            LinkedHashMap<Class<?>, ArrayList<Object>> propMap = (LinkedHashMap<Class<?>, ArrayList<Object>>) mapField
                    .get(pm);

            ArrayList<Object> props = propMap.computeIfAbsent(module.getClass(), k -> new ArrayList<>());
            Collections.addAll(props, properties);
        }
    }

    public void registerCommand(ArrayList<String> commandNames, MyauHook.CommandHandler handler) throws Exception {
        String handlerId = hook.nextCommandId();
        hook.registerCommandHandler(handlerId, handler);

        String className = GENERATED_PACKAGE + "Command_" + commandNames.get(0) + "_" + System.currentTimeMillis();
        byte[] classBytes = hook.generateCommandClass(className, handlerId);

        ClassLoader loader = new MyauHook.FastClassLoader(getClass().getClassLoader(), className, classBytes);
        Class<?> generatedClass = loader.loadClass(className);

        Constructor<?> ctor = generatedClass.getDeclaredConstructor(ArrayList.class, String.class);
        ctor.setAccessible(true);
        Object commandInstance = ctor.newInstance(commandNames, handlerId);

        Field commandsField = hook.getCachedField(hook.getCommandManager().getClass(), FIELD_COMMANDS_LIST);
        @SuppressWarnings("unchecked")
        ArrayList<Object> commands = (ArrayList<Object>) commandsField.get(hook.getCommandManager());
        commands.add(commandInstance);
    }

    public void registerEventHandler(String eventClassName, MyauHook.EventHandler handler, byte priority)
            throws Exception {
        String className = GENERATED_PACKAGE + "EventHandler_" + System.currentTimeMillis() + "_" + Math.random();
        byte[] classBytes = hook.generateEventHandlerClass(className, eventClassName, priority);

        ClassLoader loader = new MyauHook.FastClassLoader(getClass().getClassLoader(), className, classBytes);
        Class<?> handlerClass = loader.loadClass(className);

        Constructor<?> ctor = handlerClass.getDeclaredConstructor(MyauHook.EventHandler.class);
        ctor.setAccessible(true);
        Object handlerInstance = ctor.newInstance(handler);

        Class<?> eventBusClass = hook.getCachedClass(CLASS_EVENT_BUS);
        Method registerMethod = eventBusClass.getMethod(METHOD_EVENT_REGISTER, Object.class);
        registerMethod.invoke(null, handlerInstance);
    }
}





