package coffee.axle.suim.clickgui.module;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.hooks.ModulePropertyManager;
import coffee.axle.suim.hooks.MyauModuleManager;
import coffee.axle.suim.util.MyauLogger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registry of all GuiModules for the ClickGUI.
 * Builds modules from SUIM Features, organized by category.
 */
public class GuiModuleManager {

    private static GuiModuleManager instance;

    private final MyauModuleManager mgr;
    private final ModulePropertyManager propMgr;
    private final List<GuiModule> modules = new ArrayList<>();

    public GuiModuleManager(
            MyauModuleManager mgr,
            ModulePropertyManager propMgr) {
        this.mgr = mgr;
        this.propMgr = propMgr;
        instance = this;
    }

    public static GuiModuleManager getInstance() {
        return instance;
    }

    /**
     * Builds GuiModules from a list of features.
     * Only standalone features (those with a GuiCategory) are included.
     */
    public void populate(List<Feature> features) {
        modules.clear();
        for (Feature feature : features) {
            if (!feature.isStandaloneModule())
                continue;

            Object myauModule = feature.getModuleInstance();
            if (myauModule == null)
                continue;

            GuiCategory category = feature.getGuiCategory();
            if (category == null)
                continue;

            try {
                GuiModule module = feature.buildGuiModule(mgr, propMgr);
                if (module != null) {
                    modules.add(module);
                } else {
                    modules.add(new GuiModule(
                            feature, category, myauModule, mgr, propMgr));
                }
            } catch (Exception e) {
                MyauLogger.error("GuiModuleManager:populate(" +
                        feature.getName() + ")", e);
            }
        }
        MyauLogger.info("GuiModuleManager: populated with " +
                modules.size() + " modules");
    }

    /**
     * Discovers ALL Myau modules and creates GuiModules for any
     * not already registered by a SUIM Feature.
     * Call this AFTER {@link #populate(List)} so standalone features
     * get priority.
     */
    public void populateFromMyau() {
        Set<String> covered = new HashSet<>();
        for (GuiModule existing : modules) {
            covered.add(existing.getName().toLowerCase());
        }

        LinkedHashMap<Class<?>, Object> allModules = mgr.getAllModules();
        int added = 0;

        for (Map.Entry<Class<?>, Object> entry : allModules.entrySet()) {
            Object myauModule = entry.getValue();
            try {
                String name = mgr.getModuleName(myauModule);
                if (name == null || covered.contains(name.toLowerCase())) {
                    continue;
                }

                GuiCategory category = MyauModuleCategoryMapper.getCategory(name);

                GuiModule guiModule = new GuiModule(
                        name, category, myauModule, mgr, propMgr);
                modules.add(guiModule);
                covered.add(name.toLowerCase());
                added++;
            } catch (Exception e) {
                MyauLogger.error(
                        "GuiModuleManager:populateFromMyau", e);
            }
        }

        MyauLogger.info("GuiModuleManager: added " + added +
                " Myau modules (total: " + modules.size() + ")");
    }

    public List<GuiModule> getModules() {
        return modules;
    }

    public List<GuiModule> getModulesByCategory(GuiCategory category) {
        return modules.stream()
                .filter(m -> m.getCategory() == category)
                .collect(Collectors.toList());
    }

    public GuiModule getModuleByName(String name) {
        for (GuiModule module : modules) {
            if (module.getName().equalsIgnoreCase(name)) {
                return module;
            }
        }
        return null;
    }

    public MyauModuleManager getManager() {
        return mgr;
    }

    public ModulePropertyManager getPropertyManager() {
        return propMgr;
    }
}
