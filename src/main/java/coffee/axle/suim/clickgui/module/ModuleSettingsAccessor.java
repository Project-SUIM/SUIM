package coffee.axle.suim.clickgui.module;

import coffee.axle.suim.clickgui.settings.ModuleSetting;
import coffee.axle.suim.clickgui.settings.impl.*;
import coffee.axle.suim.hooks.ModulePropertyManager;
import coffee.axle.suim.hooks.MyauModuleManager;
import coffee.axle.suim.hooks.PropertyIntrospector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Scans a Myau module's properties and builds typed ModuleSetting instances.
 * This is the bi-directional abstraction layer between the GUI and Myau.
 */
public class ModuleSettingsAccessor {

    private final MyauModuleManager mgr;
    private final ModulePropertyManager propMgr;
    private final PropertyIntrospector introspector;

    public ModuleSettingsAccessor(
            MyauModuleManager mgr,
            ModulePropertyManager propMgr) {
        this.mgr = mgr;
        this.propMgr = propMgr;
        this.introspector = new PropertyIntrospector(mgr);
    }

    /**
     * Scans all properties of a Myau module and creates ModuleSetting wrappers.
     *
     * @param myauModule the module object from Myau
     * @return list of settings for display in the GUI
     */
    public List<ModuleSetting<?>> buildSettings(Object myauModule) {
        List<ModuleSetting<?>> settings = new ArrayList<>();
        try {
            settings.add(new ModuleKeyBindSetting(
                    "Bind", "Module keybind",
                    myauModule, mgr));

            List<Object> props = mgr.getAllProperties(myauModule);
            for (Object prop : props) {
                try {
                    String propName = mgr.getPropertyName(prop);
                    String propType = mgr.getPropertyType(prop);
                    ModuleSetting<?> setting = createSetting(
                            propName, propType, prop);
                    if (setting != null) {
                        settings.add(setting);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return settings;
    }

    private ModuleSetting<?> createSetting(
            String name, String type, Object prop) {
        switch (type) {
            case "boolean":
                return new BooleanModuleSetting(
                        formatName(name), "", prop, mgr, propMgr);
            case "float":
                return new NumberModuleSetting(
                        formatName(name), "", prop, mgr, propMgr,
                        introspector.getMin(prop, type),
                        introspector.getMax(prop, type),
                        introspector.getStep(prop, type),
                        false, "");
            case "integer":
                return new NumberModuleSetting(
                        formatName(name), "", prop, mgr, propMgr,
                        introspector.getMin(prop, type),
                        introspector.getMax(prop, type),
                        introspector.getStep(prop, type),
                        true, "");
            case "string":
                return new StringModuleSetting(
                        formatName(name), "", prop, mgr, propMgr,
                        0, "");
            case "enum":
                String[] values = mgr.getEnumPropertyValues(prop);
                if (values.length > 0) {
                    return new EnumModuleSetting(
                            formatName(name), "", prop, mgr, propMgr,
                            Arrays.asList(values));
                }
                return null;
            case "color":
                return new ColorModuleSetting(
                        formatName(name), "", prop, mgr, propMgr,
                        false, false);
            default:
                return null;
        }
    }

    private String formatName(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return rawName;
        }
        return rawName.replace("-", " ")
                .substring(0, 1).toUpperCase()
                + rawName.replace("-", " ").substring(1);
    }
}
