package coffee.axle.suim.feature.player;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.util.MyauLogger;

public class ClickOutOfContainers extends Feature {

    private static Object moduleInstance;
    private static ClickOutOfContainers instance;

    @Override
    public String getName() {
        return "ClickOutContainers";
    }

    @Override
    public GuiCategory getGuiCategory() {
        return GuiCategory.PLAYER;
    }

    @Override
    public boolean initialize() {
        try {
            moduleInstance = createModule();
            instance = this;
            creator.injectModule(moduleInstance, ClickOutOfContainers.class);
            manager.reloadModuleCommand();
            return true;
        } catch (Exception e) {
            MyauLogger.error("ClickOutContainers:init", e);
            return false;
        }
    }

    public static boolean isEnabled() {
        if (moduleInstance == null || instance == null) return false;
        try {
            return instance.manager.isModuleEnabled(moduleInstance);
        } catch (Exception e) {
            return false;
        }
    }
}
