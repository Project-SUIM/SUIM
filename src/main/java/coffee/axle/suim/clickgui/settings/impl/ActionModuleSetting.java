package coffee.axle.suim.clickgui.settings.impl;

import coffee.axle.suim.clickgui.settings.ModuleSetting;
import coffee.axle.suim.clickgui.settings.SettingType;
import coffee.axle.suim.clickgui.settings.SettingVisibility;

/**
 * Action setting with no backing Myau property.
 * Represents a clickable button in the GUI that triggers a callback.
 */
public class ActionModuleSetting extends ModuleSetting<Runnable> {

    private final Runnable action;

    public ActionModuleSetting(String name, String description, Runnable action) {
        super(name, description, SettingType.ACTION, SettingVisibility.VISIBLE);
        this.action = action;
    }

    @Override
    public Runnable getValue() {
        return action;
    }

    @Override
    public void setValue(Runnable value) {
        // no-op; action is immutable
    }

    @Override
    public Runnable getDefault() {
        return action;
    }

    public void doAction() {
        if (action != null) {
            action.run();
        }
    }
}





