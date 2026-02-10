package coffee.axle.suim.clickgui.settings.impl;

import coffee.axle.suim.clickgui.settings.ModuleSetting;
import coffee.axle.suim.clickgui.settings.SettingType;
import coffee.axle.suim.clickgui.settings.SettingVisibility;
import coffee.axle.suim.hooks.ModulePropertyManager;
import coffee.axle.suim.hooks.MyauModuleManager;

import java.util.List;

/**
 * Enum/selector setting backed by a Myau enum property.
 * Index-based storage in Myau, string-based API for the GUI.
 */
public class EnumModuleSetting extends ModuleSetting<String> {

    private final Object myauProp;
    private final MyauModuleManager mgr;
    private final ModulePropertyManager propMgr;
    private final List<String> options;
    private final String defaultValue;

    public EnumModuleSetting(
            String name,
            String description,
            Object myauProp,
            MyauModuleManager mgr,
            ModulePropertyManager propMgr,
            List<String> options) {
        this(name, description, myauProp, mgr, propMgr, options,
                SettingVisibility.VISIBLE);
    }

    public EnumModuleSetting(
            String name,
            String description,
            Object myauProp,
            MyauModuleManager mgr,
            ModulePropertyManager propMgr,
            List<String> options,
            SettingVisibility visibility) {
        super(name, description, SettingType.ENUM, visibility);
        this.myauProp = myauProp;
        this.mgr = mgr;
        this.propMgr = propMgr;
        this.options = options;

        int idx = propMgr.getInt(myauProp, 0);
        this.defaultValue = (idx >= 0 && idx < options.size())
                ? options.get(idx)
                : (options.isEmpty() ? "" : options.get(0));
    }

    @Override
    public String getValue() {
        try {
            int idx = propMgr.getInt(myauProp, 0);
            return (idx >= 0 && idx < options.size())
                    ? options.get(idx)
                    : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public void setValue(String value) {
        int idx = -1;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equalsIgnoreCase(value)) {
                idx = i;
                break;
            }
        }
        if (idx >= 0) {
            try {
                mgr.setPropertyValue(myauProp, idx);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public String getDefault() {
        return defaultValue;
    }

    public List<String> getOptions() {
        return options;
    }

    public int getIndex() {
        String current = getValue();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equalsIgnoreCase(current)) {
                return i;
            }
        }
        return 0;
    }

    public void setIndex(int index) {
        int clamped;
        if (index >= options.size()) {
            clamped = 0;
        } else if (index < 0) {
            clamped = options.size() - 1;
        } else {
            clamped = index;
        }
        setValue(options.get(clamped));
    }

    public void cycle() {
        setIndex(getIndex() + 1);
    }

    public boolean isSelected(String option) {
        return getValue().equalsIgnoreCase(option);
    }
}





