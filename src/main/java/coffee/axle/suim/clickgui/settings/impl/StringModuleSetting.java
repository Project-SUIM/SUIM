package coffee.axle.suim.clickgui.settings.impl;

import coffee.axle.suim.clickgui.settings.ModuleSetting;
import coffee.axle.suim.clickgui.settings.SettingType;
import coffee.axle.suim.clickgui.settings.SettingVisibility;
import coffee.axle.suim.hooks.ModulePropertyManager;
import coffee.axle.suim.hooks.MyauModuleManager;

/**
 * String setting backed by a Myau string property.
 */
public class StringModuleSetting extends ModuleSetting<String> {

    private final Object myauProp;
    private final MyauModuleManager mgr;
    private final ModulePropertyManager propMgr;
    private final String defaultValue;
    private final int maxLength;
    private final String placeholder;

    public StringModuleSetting(
            String name,
            String description,
            Object myauProp,
            MyauModuleManager mgr,
            ModulePropertyManager propMgr,
            int maxLength,
            String placeholder) {
        this(name, description, myauProp, mgr, propMgr, maxLength, placeholder,
                SettingVisibility.VISIBLE);
    }

    public StringModuleSetting(
            String name,
            String description,
            Object myauProp,
            MyauModuleManager mgr,
            ModulePropertyManager propMgr,
            int maxLength,
            String placeholder,
            SettingVisibility visibility) {
        super(name, description, SettingType.STRING, visibility);
        this.myauProp = myauProp;
        this.mgr = mgr;
        this.propMgr = propMgr;
        this.maxLength = maxLength;
        this.placeholder = placeholder;
        this.defaultValue = propMgr.getString(myauProp, "");
    }

    @Override
    public String getValue() {
        return propMgr.getString(myauProp, defaultValue);
    }

    @Override
    public void setValue(String value) {
        try {
            String clamped = (maxLength > 0 && value.length() > maxLength)
                    ? value.substring(0, maxLength)
                    : value;
            mgr.setPropertyValue(myauProp, clamped);
        } catch (Exception ignored) {
        }
    }

    @Override
    public String getDefault() {
        return defaultValue;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public String getPlaceholder() {
        return placeholder;
    }
}





