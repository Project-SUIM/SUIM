package coffee.axle.suim.clickgui.module;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.clickgui.settings.ModuleSetting;
import coffee.axle.suim.hooks.ModulePropertyManager;
import coffee.axle.suim.hooks.MyauModuleManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a module in the ClickGUI.
 * Wraps a SUIM Feature and its Myau module for display and interaction.
 */
public class GuiModule {

    private final String name;
    private final GuiCategory category;
    private final Feature feature;
    private final Object myauModule;
    private final MyauModuleManager mgr;
    private final List<ModuleSetting<?>> settings;

    public GuiModule(
            Feature feature,
            GuiCategory category,
            Object myauModule,
            MyauModuleManager mgr,
            ModulePropertyManager propMgr) {
        this.name = feature.getName();
        this.category = category;
        this.feature = feature;
        this.myauModule = myauModule;
        this.mgr = mgr;

        ModuleSettingsAccessor accessor = new ModuleSettingsAccessor(mgr, propMgr);
        this.settings = accessor.buildSettings(myauModule);
    }

    /**
     * Constructor for modules with pre-built settings
     * (e.g. ClickGui with custom color/keybind settings).
     */
    public GuiModule(
            String name,
            GuiCategory category,
            Feature feature,
            Object myauModule,
            MyauModuleManager mgr,
            List<ModuleSetting<?>> settings) {
        this.name = name;
        this.category = category;
        this.feature = feature;
        this.myauModule = myauModule;
        this.mgr = mgr;
        this.settings = settings != null ? settings : new ArrayList<>();
    }

    /**
     * Constructor for Myau-only modules (no SUIM Feature).
     * Used by the reverse module hook to wrap existing Myau modules.
     */
    public GuiModule(
            String name,
            GuiCategory category,
            Object myauModule,
            MyauModuleManager mgr,
            ModulePropertyManager propMgr) {
        this.name = name;
        this.category = category;
        this.feature = null;
        this.myauModule = myauModule;
        this.mgr = mgr;

        ModuleSettingsAccessor accessor = new ModuleSettingsAccessor(mgr, propMgr);
        this.settings = accessor.buildSettings(myauModule);
    }

    public String getName() {
        return name;
    }

    public GuiCategory getCategory() {
        return category;
    }

    public Feature getFeature() {
        return feature;
    }

    public Object getMyauModule() {
        return myauModule;
    }

    public List<ModuleSetting<?>> getSettings() {
        return settings;
    }

    public boolean isEnabled() {
        if (myauModule == null)
            return false;
        try {
            return mgr.isModuleEnabled(myauModule);
        } catch (Exception e) {
            return false;
        }
    }

    public void toggle() {
        if (myauModule == null)
            return;
        mgr.toggleModuleViaCommand(myauModule);
    }

    public void setEnabled(boolean enabled) {
        if (myauModule == null)
            return;
        try {
            mgr.setModuleEnabled(myauModule, enabled);
        } catch (Exception ignored) {
        }
    }

    public int getKeybind() {
        if (myauModule == null)
            return 0;
        return mgr.getModuleKeybind(myauModule);
    }

    public void setKeybind(int keyCode) {
        if (myauModule == null)
            return;
        mgr.setModuleKeybind(myauModule, keyCode);
    }
}
