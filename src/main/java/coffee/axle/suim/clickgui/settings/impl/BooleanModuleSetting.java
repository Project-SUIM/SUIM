package coffee.axle.suim.clickgui.settings.impl;

import coffee.axle.suim.clickgui.settings.ModuleSetting;
import coffee.axle.suim.clickgui.settings.SettingType;
import coffee.axle.suim.clickgui.settings.SettingVisibility;
import coffee.axle.suim.hooks.ModulePropertyManager;
import coffee.axle.suim.hooks.MyauModuleManager;

/**
 * Boolean setting backed by a Myau boolean property.
 */
public class BooleanModuleSetting extends ModuleSetting<Boolean> {

    private final Object myauProp;
    private final MyauModuleManager mgr;
    private final ModulePropertyManager propMgr;
    private final boolean defaultValue;

    public BooleanModuleSetting(
            String name,
            String description,
            Object myauProp,
            MyauModuleManager mgr,
            ModulePropertyManager propMgr) {
        this(name, description, myauProp, mgr, propMgr, SettingVisibility.VISIBLE);
    }

    public BooleanModuleSetting(
            String name,
            String description,
            Object myauProp,
            MyauModuleManager mgr,
            ModulePropertyManager propMgr,
            SettingVisibility visibility) {
        super(name, description, SettingType.BOOLEAN, visibility);
        this.myauProp = myauProp;
        this.mgr = mgr;
        this.propMgr = propMgr;
        this.defaultValue = propMgr.getBoolean(myauProp, false);
    }

    @Override
    public Boolean getValue() {
        return propMgr.getBoolean(myauProp, defaultValue);
    }

    @Override
    public void setValue(Boolean value) {
        try {
            mgr.setPropertyValue(myauProp, value);
        } catch (Exception ignored) {
        }
    }

    @Override
    public Boolean getDefault() {
        return defaultValue;
    }

    public void toggle() {
        setValue(!getValue());
    }
}





