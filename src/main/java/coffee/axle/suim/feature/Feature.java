package coffee.axle.suim.feature;

import coffee.axle.suim.clickgui.module.GuiModule;
import coffee.axle.suim.hooks.ModulePropertyManager;
import coffee.axle.suim.hooks.MyauModuleCreator;
import coffee.axle.suim.hooks.MyauModuleManager;
import coffee.axle.suim.property.Property;
import coffee.axle.suim.property.Setting;
import coffee.axle.suim.util.MyauLogger;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Base class for all SUIM features also known as "modules" depending on the client (looking at Raven)
 *
 * A feature is a name, a category when it shows up in the click GUI, and its
 * settings as plain fields:
 *
 * <pre>{@code
 * public class MyFeature extends Feature {
 *     // keep these sorted
 *     // order is how it will render in commands and clickGUI FYI
 *     @Setting(min = 1, max = 200) private int range = 50;
 *     @Setting private boolean toggle = false;
 *     
 *
 *     public MyFeature() {
 *         // MyFeature is the name of your class and your feature
 *         // it will become .MyFeature (module command)
 *         // GuiCategory is where this feature will appear in the click GUI 
 *         //(optional, SUIM created, not myau as myau doesnt have one)
 *         super("MyFeature", GuiCategory.MISC);
 *     }
 * }
 * }</pre>
 *
 * The field holds the value and is read like any other field. The name is
 * the field name kebab-cased, {@link Setting#name()} only if you want different names.
 * Settings created at runtime (a list built from a file) use {@link Property}
 * directly inside {@link #init()}.
 *
 * Features that patch a myau module return its name from {@link #targetModule()}
 * and get their settings injected into it. Settings placed between existing
 * ones say so with {@link Setting#after()}.
 */
public abstract class Feature {

    /** myau could not be created, found or injected into */
    public static class ModuleSetupException extends Exception {
        public ModuleSetupException(String message) {
            super(message);
        }

        public ModuleSetupException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** a symbol we reflect into myau by name is gone, the mappings are stale, incorrect, or vehemently fucked */
    public static class MappingException extends Exception {
        public MappingException(String message) {
            super(message);
        }

        public MappingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    protected MyauModuleCreator creator;
    protected MyauModuleManager manager;
    protected ModulePropertyManager properties;

    private final String name;
    private final GuiCategory category;
    private final Map<String, Bind> settings = new LinkedHashMap<String, Bind>();
    /** the myau module this feature owns or patches */
    protected Object module;

    private static final class Bind {
        final Field field;
        final Property<?> property;

        Bind(Field field, Property<?> property) {
            this.field = field;
            this.property = property;
        }
    }

    protected Feature(String name, GuiCategory category) {
        this.name = name;
        this.category = category;
    }

    /** patches and commands have no module of their own in the clickgui */
    protected Feature(String name) {
        this(name, null);
    }

    public void inject(MyauModuleCreator creator, MyauModuleManager manager, ModulePropertyManager properties) {
        this.creator = creator;
        this.manager = manager;
        this.properties = properties;
    }

    public String getName() {
        return name;
    }

    public GuiCategory getGuiCategory() {
        return category;
    }

    public boolean isStandaloneModule() {
        return category != null;
    }

    /**
     * Origin tag for MyauWorld tracking. Override for non-suim, e.g. in the case ur script-loading or smth
     */
    protected String getOrigin() {
        return "suim";
    }

    /** name of the myau module this feature patches, null to get one of our own */
    protected String targetModule() {
        return null;
    }

    /** feature body, only needed for settings built at runtime or foreign reflection */
    protected void init() throws Exception {
    }

    /** Wired to the module's enable toggle. */
    protected void onEnable() {
    }

    /** Wired to the module's disable toggle. */
    protected void onDisable() {
    }

    public final boolean initialize() {
        try {
            if (targetModule() != null) {
                module = findModule(targetModule());
            } else if (isStandaloneModule()) {
                module = creator.createModule(getName(), getOrigin());
                creator.injectModule(module, getClass());
            }
        } catch (Exception e) {
            MyauLogger.error(getName() + ":module", e);
            return false;
        }

        bindSettings();

        try {
            init();
        } catch (Exception e) {
            MyauLogger.error(getName() + ":init", e);
            return false;
        }

        MinecraftForge.EVENT_BUS.register(this);
        if (isStandaloneModule()) {
            manager.registerModuleCallbacks(module, this::onEnable, this::onDisable);
        }
        return true;
    }

    private void bindSettings() {
        for (Class<?> type = getClass(); type != null && type != Feature.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                Setting setting = field.getAnnotation(Setting.class);
                if (setting == null) continue;
                bind(field, setting);
            }
        }
    }

    private void bind(Field field, Setting setting) {
        String name = setting.name().isEmpty() ? sterilizer(field.getName()) : setting.name();
        try {
            field.setAccessible(true);
            Property<?> property = propertyFor(field, setting, name);
            if (setting.existing()) {
                Object existing = manager.findProperty(module, name);
                if (existing == null) {
                    MyauLogger.log(getName(), "setting \"" + name + "\" not found on " + targetModule());
                    return;
                }
                property.bindTo(existing, manager, properties);
            } else {
                property.place(creator, manager, properties, module,
                        setting.after().isEmpty() ? null : setting.after());
            }
            field.set(this, property.get());
            settings.put(name, new Bind(field, property));
        } catch (Exception e) {
            MyauLogger.error(getName() + ":mapping", e);
        }
    }

    private Property<?> propertyFor(Field field, Setting setting, String name) throws Exception {
        Object initial = field.get(this);
        Class<?> type = field.getType();
        if (type == boolean.class) return Property.bool(name, (Boolean) initial);
        if (type == String.class) return Property.text(name, (String) initial);
        if (type == float.class) return Property.real(name, (Float) initial, (float) setting.min(), (float) setting.max());
        if (type == int.class) {
            if (setting.color()) return Property.color(name, (Integer) initial);
            if (setting.names().length > 0) return Property.mode(name, (Integer) initial, setting.names());
            return Property.integer(name, (Integer) initial, (int) setting.min(), (int) setting.max());
        }
        if (type.isEnum()) {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            Property<?> property = setting.names().length == 0
                    ? Property.mode(name, (Enum) initial)
                    : Property.mode(name, (Enum) initial, setting.names());
            return property;
        }
        throw new MappingException("setting \"" + name + "\" has unsupported type " + type.getSimpleName());
    }

    // myau writes values from its own clickgui and commands, so the field is a
    // mirror that catches up every tick. Writes from feature code go through
    // set() so they land immediately.
    private void syncSettings() {
        List<String> stale = null;
        for (Map.Entry<String, Bind> entry : settings.entrySet()) {
            try {
                Bind bind = entry.getValue();
                Object value = bind.property.get();
                if (!value.equals(bind.field.get(this))) {
                    bind.field.set(this, value);
                }
            } catch (Exception e) {
                MyauLogger.error(getName() + ":mapping", e);
                if (stale == null) stale = new java.util.ArrayList<String>();
                stale.add(entry.getKey());
            }
        }
        if (stale != null) {
            for (String name : stale) settings.remove(name);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public final void onSettingTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START && !settings.isEmpty()) {
            syncSettings();
        }
    }

    // --- reading and writing settings ---

    private Bind lookup(String name) {
        Bind bind = settings.get(name);
        return bind != null ? bind : settings.get(sterilizer(name));
    }

    /** writes a bound setting right away */
    protected final void set(String name, Object value) {
        Bind bind = lookup(name);
        if (bind == null) return;
        assign(bind.property, value);
        writeField(bind, value);
    }

    /** same, but flags the config dirty for values the user chose */
    protected final void setSaved(String name, Object value) {
        Bind bind = lookup(name);
        if (bind == null) return;
        bind.property.setAndSave(cast(value));
        writeField(bind, value);
    }

    private void writeField(Bind bind, Object value) {
        try {
            bind.field.set(this, value);
        } catch (Exception e) {
            MyauLogger.error(getName() + ":mapping", e);
        }
    }

    /** the myau property behind a bound setting, for the rare direct call */
    protected final Property<?> setting(String name) {
        Bind bind = lookup(name);
        return bind == null ? null : bind.property;
    }

    /** a property myau owns on the patched module */
    protected final Object findProperty(String name) {
        return manager.findProperty(module, name);
    }

    /** enabled state of the module this feature owns or patches */
    public boolean isEnabled() {
        return module != null && manager.isModuleEnabled(module);
    }

    public Object getModuleInstance() {
        return module;
    }

    @SuppressWarnings("unchecked")
    private static <T> void assign(Property<T> property, Object value) {
        property.set((T) value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }

    /**
     * You should **NEVER** have spaces in your field names, this is a text-based client and myau processes args via [0] [1] [2] with space as split<p>you will fuck up the client if you bypass this
     */
    private static String sterilizer(String field) {
        // last resort
        if (field.contains(" ")) {
            field = field.replace(" ", "-");
            MyauLogger.error("[SOS] WE NEED HELP HERE THERES EVIL SPACES FIX AT ONCE HERP HERP HERP " + field, null);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('-');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    protected final Object findModule(String name) throws ModuleSetupException {
        Object module = manager.findModule(name);
        if (module == null) {
            throw new ModuleSetupException("module \"" + name + "\" is not in myau");
        }
        return module;
    }

    protected final Field field(Class<?> type, String name) throws MappingException {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new MappingException("field " + type.getSimpleName() + "." + name + " is gone from myau", e);
        }
    }

    protected final Method method(Class<?> type, String name, Class<?>... params) throws MappingException {
        try {
            Method method = type.getDeclaredMethod(name, params);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new MappingException("method " + type.getSimpleName() + "." + name + " is gone from myau", e);
        }
    }

    public void disable() throws Exception {
        if (module != null) {
            SuffixRegistry.unregister(module);
        }
    }

    protected void setSuffix(String... suffix) {
        if (module != null) {
            SuffixRegistry.registerStatic(module, suffix);
        }
    }

    protected void setSuffixDynamic(Supplier<String[]> supplier) {
        if (module != null) {
            SuffixRegistry.register(module, supplier);
        }
    }

    protected void clearSuffix() {
        if (module != null) {
            SuffixRegistry.unregister(module);
        }
    }

    public GuiModule buildGuiModule(MyauModuleManager mgr,
            ModulePropertyManager propMgr) {
        return null;
    }
}
