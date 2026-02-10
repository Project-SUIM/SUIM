package coffee.axle.suim.clickgui.settings.impl;

import coffee.axle.suim.clickgui.settings.ModuleSetting;
import coffee.axle.suim.clickgui.settings.SettingType;
import coffee.axle.suim.clickgui.settings.SettingVisibility;
import coffee.axle.suim.hooks.MyauModuleManager;

public class ModuleKeyBindSetting extends ModuleSetting<Integer> {
    private final Object myauModule;
    private final MyauModuleManager mgr;
    private final int defaultValue;

    public ModuleKeyBindSetting(
            String name,
            String description,
            Object myauModule,
            MyauModuleManager mgr) {
        this(name, description, myauModule, mgr, SettingVisibility.VISIBLE);
    }

    public ModuleKeyBindSetting(
            String name,
            String description,
            Object myauModule,
            MyauModuleManager mgr,
            SettingVisibility visibility) {
        super(name, description, SettingType.KEY_BIND, visibility);
        this.myauModule = myauModule;
        this.mgr = mgr;
        this.defaultValue = myauModule != null ? mgr.getModuleKeybind(myauModule) : 0;
    }

    @Override
    public Integer getValue() {
        if (myauModule == null) {
            return defaultValue;
        }
        return mgr.getModuleKeybind(myauModule);
    }

    @Override
    public void setValue(Integer value) {
        if (myauModule == null) {
            return;
        }
        mgr.setModuleKeybind(myauModule, value);
    }

    @Override
    public Integer getDefault() {
        return defaultValue;
    }
}
