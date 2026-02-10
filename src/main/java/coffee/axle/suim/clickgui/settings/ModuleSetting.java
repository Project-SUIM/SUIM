package coffee.axle.suim.clickgui.settings;

import java.util.function.Supplier;

/**
 * Abstract setting that backs onto a Myau property for bi-directional access.
 * Subclasses wrap specific Myau property types (boolean, float, int, string,
 * enum).
 *
 * @param <T> the value type of this setting
 */
public abstract class ModuleSetting<T> {

    private final String name;
    private final String description;
    private final SettingType type;
    private final SettingVisibility visibility;
    private Supplier<Boolean> visibilityCondition;

    protected ModuleSetting(
            String name,
            String description,
            SettingType type,
            SettingVisibility visibility) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.visibility = visibility;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public SettingType getType() {
        return type;
    }

    public SettingVisibility getVisibility() {
        return visibility;
    }

    public abstract T getValue();

    public abstract void setValue(T value);

    public abstract T getDefault();

    public boolean shouldBeVisible() {
        if (visibilityCondition != null) {
            return visibilityCondition.get();
        }
        return true;
    }

    public ModuleSetting<T> withVisibilityCondition(Supplier<Boolean> condition) {
        this.visibilityCondition = condition;
        return this;
    }
}





