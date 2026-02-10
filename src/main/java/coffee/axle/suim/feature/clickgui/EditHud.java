package coffee.axle.suim.feature.clickgui;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.util.MyauLogger;

import java.util.ArrayList;

/**
 * Stub for EditHud as a Myau module.
 * Full HUD editing requires heavy reflection and will be implemented later.
 */
public class EditHud extends Feature {

    private Object moduleInstance;

    @Override
    public String getName() {
        return "EditHud";
    }

    @Override
    public GuiCategory getGuiCategory() {
        return GuiCategory.SETTINGS;
    }

    @Override
    public boolean initialize() {
        try {
            moduleInstance = createModule();
            creator.injectModule(moduleInstance, EditHud.class);

            manager.registerModuleCallbacks(
                    moduleInstance,
                    this::onMyauEnable,
                    this::onMyauDisable);

            ArrayList<String> cmdNames = new ArrayList<>();
            cmdNames.add("edithud");
            creator.registerCommand(cmdNames, this::handleCommand);

            manager.reloadModuleCommand();

            MyauLogger.info("EditHud initialized");
            return true;
        } catch (Exception e) {
            MyauLogger.error("EditHud:init", e);
            return false;
        }
    }

    private void onMyauEnable() {
        manager.sendMessage(
                "&7EditHud is a stub \u2014 full implementation pending");
    }

    private void onMyauDisable() {
    }

    private void handleCommand(ArrayList<String> args) {
        manager.sendMessage(
                "&7EditHud is not yet implemented (requires heavy reflection)");
    }
}
