package coffee.axle.suim.property;

import coffee.axle.suim.hooks.ModulePropertyManager;
import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.hooks.MyauModuleCreator;
import coffee.axle.suim.hooks.MyauModuleManager;
import coffee.axle.suim.util.MyauLogger;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * A setting backed by a myau property. Every type lives in this file.
 *
 * <p>Features normally never touch this class, declare a plain field with
 * {@link Setting} and read it like any other field. The factories are for the
 * odd cases: a mode list built from a file, one toggle shared across four
 * modules, or wrapping a property myau already owns, other hellish stuff like that et cetra</p>
 */
public abstract class Property<T> {

    private final String name;
    private final T def;

    private ModulePropertyManager props;
    private MyauModuleManager manager;
    private Object handle;

    Property(String name, T def) {
        this.name = name;
        this.def = def;
    }

    public String name() {
        return name;
    }

    public T def() {
        return def;
    }

    /** the raw myau property, for the rare call that needs it directly */
    public Object handle() {
        return handle;
    }

    public T get() {
        return handle == null ? def : cast(readValue());
    }

    /** writes the value, myau handles rest, subject to break if myau config logic changes? */
    public void set(T value) {
        if (handle != null) {
            writeValue(value);
        }
    }

    /** writes the value and saves the config, can RACE if improperly used <p> make sure you disable x first before calling, else you will save with things enabled (e.g. causing perm-on clickguis) */
    public void setAndSave(T value) {
        if (handle == null) return;
        try {
            manager.setPropertyValue(handle, value);
        } catch (Exception e) {
            writeValue(value);
            MyauLogger.error("property:" + name, e);
        }
    }

    /** true until the user actually changes the value */
    public boolean isDefault() {
        if (handle == null) return true;
        try {
            Object current = field(handle, MyauMappings.FIELD_VALUE_CURRENT).get(handle);
            Object original = field(handle, MyauMappings.FIELD_VALUE_DEFAULT).get(handle);
            return original == null ? current == null : original.equals(current);
        } catch (Exception e) {
            return true;
        }
    }

    protected abstract Object readValue();

    protected abstract void writeValue(Object value);

    @SuppressWarnings("unchecked")
    private static <V> V cast(Object value) {
        return (V) value;
    }

    /** creates this property on the given myau module, {@code after} appends it uppon
     * behind another setting by name, null appends at the end */
    public Property<T> place(MyauModuleCreator creator, MyauModuleManager manager, ModulePropertyManager props,
            Object module, String after) throws Exception {
        this.manager = manager;
        this.props = props;
        this.handle = create(creator);
        if (after == null) {
            creator.registerProperties(module, handle);
        } else if (!creator.injectPropertyAfter(module, handle, after)) {
            creator.registerProperties(module, handle);
        }
        return this;
    }

    /** wraps a property myau already owns */
    public Property<T> bindTo(Object existing, MyauModuleManager manager, ModulePropertyManager props) {
        this.manager = manager;
        this.props = props;
        this.handle = existing;
        return this;
    }

    protected abstract Object create(MyauModuleCreator creator) throws Exception;

    protected Object readMyau() {
        try {
            return props.getPropertyValue(handle);
        } catch (Exception e) {
            return null;
        }
    }

    protected void writeMyau(Object value) {
        try {
            field(handle, MyauMappings.FIELD_VALUE_CURRENT).set(handle, value);
        } catch (Exception e) {
            MyauLogger.error("property:" + name, e);
        }
    }

    // myau keeps the value fields on the base class of the concrete property
    private static final Map<Class<?>, Field> valueFields = new HashMap<>();

    private static Field field(Object property, String name) throws Exception {
        Class<?> type = property.getClass();
        Field cached = valueFields.get(type);
        if (cached == null || !cached.getName().equals(name)) {
            cached = findField(type, name);
            cached.setAccessible(true);
            valueFields.put(type, cached);
        }
        return cached;
    }

    private static Field findField(Class<?> type, String name) throws Exception {
        while (type != null && type != Object.class) {
            try {
                return type.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    public static Property<Boolean> bool(String name, boolean def) {
        return new Bool(name, def);
    }

    public static Property<Integer> integer(String name, int def, int min, int max) {
        return new Int(name, def, min, max);
    }

    public static Property<Float> real(String name, float def, float min, float max) {
        return new Real(name, def, min, max);
    }

    public static Property<String> text(String name, String def) {
        return new Text(name, def);
    }

    public static Property<Integer> color(String name, int def) {
        return new Color(name, def);
    }

    /** mode over an enum, names come from the constants */
    public static <E extends Enum<E>> Property<E> mode(String name, E def) {
        return new ModeEnum<E>(name, def, names(def.getDeclaringClass().getEnumConstants()));
    }

    /** mode over an enum with our own display names */
    public static <E extends Enum<E>> Property<E> mode(String name, E def, String... names) {
        return new ModeEnum<E>(name, def, names);
    }

    /** mode over a plain index, for option lists built at runtime */
    public static Property<Integer> mode(String name, int def, String... names) {
        return new ModeIndex(name, def, names);
    }

    static String[] names(Enum<?>[] constants) {
        String[] names = new String[constants.length];
        for (int i = 0; i < constants.length; i++) {
            names[i] = constants[i].name();
        }
        return names;
    }


    static final class Bool extends Property<Boolean> {
        Bool(String name, boolean def) {
            super(name, def);
        }

        protected Object create(MyauModuleCreator creator) throws Exception {
            return creator.createBooleanProperty(name(), def());
        }

        protected Object readValue() {
            Object value = readMyau();
            return value instanceof Boolean ? value : def();
        }

        protected void writeValue(Object value) {
            writeMyau(value);
        }
    }

    static final class Int extends Property<Integer> {
        private final int min;
        private final int max;

        Int(String name, int def, int min, int max) {
            super(name, def);
            this.min = min;
            this.max = max;
        }

        protected Object create(MyauModuleCreator creator) throws Exception {
            return creator.createIntegerProperty(name(), def(), min, max);
        }

        protected Object readValue() {
            Object value = readMyau();
            return value instanceof Number ? ((Number) value).intValue() : def();
        }

        protected void writeValue(Object value) {
            writeMyau(value instanceof Number ? ((Number) value).intValue() : value);
        }
    }

    static final class Real extends Property<Float> {
        private final float min;
        private final float max;

        Real(String name, float def, float min, float max) {
            super(name, def);
            this.min = min;
            this.max = max;
        }

        protected Object create(MyauModuleCreator creator) throws Exception {
            return creator.createFloatProperty(name(), def(), min, max);
        }

        protected Object readValue() {
            Object value = readMyau();
            return value instanceof Number ? ((Number) value).floatValue() : def();
        }

        protected void writeValue(Object value) {
            writeMyau(value instanceof Number ? ((Number) value).floatValue() : value);
        }
    }

    static final class Text extends Property<String> {
        Text(String name, String def) {
            super(name, def);
        }

        protected Object create(MyauModuleCreator creator) throws Exception {
            return creator.createStringProperty(name(), def());
        }

        protected Object readValue() {
            Object value = readMyau();
            return value instanceof String ? value : def();
        }

        protected void writeValue(Object value) {
            writeMyau(value);
        }
    }

    static final class Color extends Property<Integer> {
        Color(String name, int def) {
            super(name, def);
        }

        protected Object create(MyauModuleCreator creator) throws Exception {
            return creator.createColorProperty(name(), def());
        }

        protected Object readValue() {
            Object value = readMyau();
            return value instanceof Number ? ((Number) value).intValue() : def();
        }

        protected void writeValue(Object value) {
            writeMyau(value instanceof Number ? ((Number) value).intValue() : value);
        }
    }

    static final class ModeEnum<E extends Enum<E>> extends Property<E> {
        private final Class<E> type;
        private final String[] names;

        ModeEnum(String name, E def, String[] names) {
            super(name, def);
            this.type = def.getDeclaringClass();
            this.names = names;
        }

        protected Object create(MyauModuleCreator creator) throws Exception {
            return creator.createEnumProperty(name(), def().ordinal(), names);
        }

        protected Object readValue() {
            Object value = readMyau();
            if (!(value instanceof Number)) return def();
            int index = ((Number) value).intValue();
            E[] constants = type.getEnumConstants();
            return index >= 0 && index < constants.length ? constants[index] : def();
        }

        protected void writeValue(Object value) {
            writeMyau(value instanceof Enum ? ((Enum<?>) value).ordinal() : value);
        }
    }

    static final class ModeIndex extends Property<Integer> {
        private final String[] names;

        ModeIndex(String name, int def, String[] names) {
            super(name, def);
            this.names = names;
        }

        protected Object create(MyauModuleCreator creator) throws Exception {
            return creator.createEnumProperty(name(), def(), names);
        }

        protected Object readValue() {
            Object value = readMyau();
            return value instanceof Number ? ((Number) value).intValue() : def();
        }

        protected void writeValue(Object value) {
            writeMyau(value instanceof Number ? ((Number) value).intValue() : value);
        }
    }
}
