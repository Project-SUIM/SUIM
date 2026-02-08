package coffee.axle.suim.hooks;

import coffee.axle.suim.util.MyauLogger;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
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

    // Cached managers from Myau
    private Object moduleManager;
    private Object commandManager;
    private LinkedHashMap<Class<?>, Object> modulesMap;
    private Class<?> commandBaseClass;

    // Cached methods
    private Method moduleGetNameMethod;
    private Method propertyGetValueMethod;
    private Method propertyGetNameMethod;

    @SuppressWarnings("unused")
    private Method commandRunMethod;

    // Reflection caches
    private final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();
    private final Map<String, Field> fieldCache = new ConcurrentHashMap<>();

    // Command handling
    private static final Map<String, CommandHandler> commandHandlers = new ConcurrentHashMap<>();
    private static int commandIdCounter = 0;

    // Module callbacks
    private final Map<String, Runnable> moduleEnableCallbacks = new ConcurrentHashMap<>();
    private final Map<String, Runnable> moduleDisableCallbacks = new ConcurrentHashMap<>();

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

    @SuppressWarnings("unchecked")
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

    public void clearCaches() {
        classCache.clear();
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

        cw.visit(V1_8, ACC_PUBLIC, internalName, null, moduleBase, null);

        // Constructor
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", SIG_MODULE_CONSTRUCTOR, null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitVarInsn(ILOAD, 3);
        mv.visitVarInsn(LLOAD, 4);
        mv.visitMethodInsn(INVOKESPECIAL, moduleBase, "<init>", SIG_MODULE_CONSTRUCTOR, false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // onEnable
        mv = cw.visitMethod(ACC_PUBLIC, METHOD_ON_ENABLE, SIG_ON_ENABLE, null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ILOAD, 1);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitVarInsn(ILOAD, 3);
        mv.visitMethodInsn(INVOKESPECIAL, moduleBase, METHOD_ON_ENABLE, SIG_ON_ENABLE, false);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESTATIC, "coffee/axle/suim/hooks/MyauHook", "triggerOnEnable",
                "(Ljava/lang/Object;)V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // onDisable
        mv = cw.visitMethod(ACC_PUBLIC, METHOD_ON_DISABLE, SIG_ON_DISABLE, null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(LLOAD, 1);
        mv.visitMethodInsn(INVOKESPECIAL, moduleBase, METHOD_ON_DISABLE, SIG_ON_DISABLE, false);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESTATIC, "coffee/axle/suim/hooks/MyauHook", "triggerOnDisable",
                "(Ljava/lang/Object;)V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    public byte[] generateCommandClass(String className, String handlerId) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        String internalName = className.replace('.', '/');
        String superClassName = this.commandBaseClass.getName().replace('.', '/');

        cw.visit(V1_8, ACC_PUBLIC, internalName, null, superClassName, null);
        cw.visitField(ACC_PRIVATE | ACC_FINAL, "handlerId", "Ljava/lang/String;", null, null);

        // Constructor
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(Ljava/util/ArrayList;Ljava/lang/String;)V", null,
                null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKESPECIAL, superClassName, "<init>", SIG_COMMAND_CONSTRUCTOR, false);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitFieldInsn(PUTFIELD, internalName, "handlerId", "Ljava/lang/String;");
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // Run method
        mv = cw.visitMethod(ACC_PUBLIC, METHOD_COMMAND_RUN, SIG_COMMAND_RUN,
                "(Ljava/util/ArrayList<Ljava/lang/String;>;J)V",
                null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalName, "handlerId", "Ljava/lang/String;");
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKESTATIC, "coffee/axle/suim/hooks/MyauHook", "invokeHandler",
                "(Ljava/lang/String;Ljava/util/ArrayList;)V", false);
        mv.visitInsn(RETURN);
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

        cw.visit(V1_8, ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        cw.visitField(ACC_PRIVATE | ACC_FINAL, "handler", "Lcoffee/axle/suim/hooks/MyauHook$EventHandler;", null, null);

        // Constructor
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>",
                "(Lcoffee/axle/suim/hooks/MyauHook$EventHandler;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitFieldInsn(PUTFIELD, internalName, "handler",
                "Lcoffee/axle/suim/hooks/MyauHook$EventHandler;");
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // Event handler method
        mv = cw.visitMethod(ACC_PUBLIC, "onEvent", "(L" + eventInternalName + ";)V", null, null);
        AnnotationVisitor av = mv.visitAnnotation(annotationDesc, true);
        av.visit("value", priority);
        av.visitEnd();
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalName, "handler",
                "Lcoffee/axle/suim/hooks/MyauHook$EventHandler;");
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEINTERFACE, "coffee/axle/suim/hooks/MyauHook$EventHandler", "handleEvent",
                "(Ljava/lang/Object;)V", true);
        mv.visitInsn(RETURN);
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
                MyauLogger.error("HANDLER_FAIL", e);
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
