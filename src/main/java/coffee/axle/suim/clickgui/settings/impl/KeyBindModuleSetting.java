package coffee.axle.suim.clickgui.settings.impl;

import coffee.axle.suim.clickgui.settings.ModuleSetting;
import coffee.axle.suim.clickgui.settings.SettingType;
import coffee.axle.suim.clickgui.settings.SettingVisibility;
import coffee.axle.suim.hooks.ModulePropertyManager;
import coffee.axle.suim.hooks.MyauModuleManager;

/**
 * Key bind setting backed by a Myau int property (LWJGL key code).
 */
public class KeyBindModuleSetting extends ModuleSetting<Integer> {

    private final Object myauProp;
    private final MyauModuleManager mgr;
    private final ModulePropertyManager propMgr;
    private final int defaultValue;

    public KeyBindModuleSetting(
            String name,
            String description,
            Object myauProp,
            MyauModuleManager mgr,
            ModulePropertyManager propMgr) {
        this(name, description, myauProp, mgr, propMgr,
                SettingVisibility.VISIBLE);
    }

    public KeyBindModuleSetting(
            String name,
            String description,
            Object myauProp,
            MyauModuleManager mgr,
            ModulePropertyManager propMgr,
            SettingVisibility visibility) {
        super(name, description, SettingType.KEY_BIND, visibility);
        this.myauProp = myauProp;
        this.mgr = mgr;
        this.propMgr = propMgr;
        this.defaultValue = propMgr.getInt(myauProp, 0);
    }

    @Override
    public Integer getValue() {
        return propMgr.getInt(myauProp, defaultValue);
    }

    @Override
    public void setValue(Integer value) {
        try {
            mgr.setPropertyValue(myauProp, value);
        } catch (Exception ignored) {
        }
    }

    @Override
    public Integer getDefault() {
        return defaultValue;
    }
}





