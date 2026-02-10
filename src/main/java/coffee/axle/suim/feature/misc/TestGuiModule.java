package coffee.axle.suim.feature.misc;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.util.MyauLogger;

import java.util.ArrayList;

/**
 * GUI test module — verifies the click GUI can render and control
 * a SUIM-created Myau module with properties.
 */
public class TestGuiModule extends Feature {

    private Object moduleInstance;
    private Object exampleToggle;
    private Object exampleSlider;
    private Object exampleMode;

    @Override
    public String getName() {
        return "TestGuiModule";
    }

    @Override
    public GuiCategory getGuiCategory() {
        return GuiCategory.MISC;
    }

    @Override
    public boolean initialize() {
        try {
            moduleInstance = createModule();
            creator.injectModule(moduleInstance, TestGuiModule.class);

            exampleToggle = creator.createBooleanProperty(
                    "example-toggle", true);
            exampleSlider = creator.createFloatProperty(
                    "example-slider", 5.0f, 0.0f, 10.0f);

            String[] modes = { "Alpha", "Beta", "Gamma" };
            exampleMode = creator.createEnumProperty(
                    "example-mode", 0, modes);

            creator.registerProperties(
                    moduleInstance, exampleToggle, exampleSlider, exampleMode);

            ArrayList<String> cmdNames = new ArrayList<>();
            cmdNames.add("testgui");
            creator.registerCommand(cmdNames, this::handleCommand);

            manager.registerModuleCallbacks(
                    moduleInstance,
                    () -> manager.sendMessage("&aTestGuiModule enabled"),
                    () -> manager.sendMessage("&cTestGuiModule disabled"));

            manager.reloadModuleCommand();

            MyauLogger.info("TestGuiModule initialized");
            return true;
        } catch (Exception e) {
            MyauLogger.error("TestGuiModule:init", e);
            return false;
        }
    }

    private void handleCommand(ArrayList<String> args) {
        boolean toggle = properties.getBoolean(exampleToggle, true);
        float slider = properties.getFloat(exampleSlider, 5.0f);
        String mode = "unknown";
        try {
            int idx = properties.getInt(exampleMode, 0);
            String[] modes = { "Alpha", "Beta", "Gamma" };
            if (idx >= 0 && idx < modes.length)
                mode = modes[idx];
        } catch (Exception ignored) {
        }
        manager.sendMessage("&7TestGuiModule: toggle=" + toggle
                + " slider=" + slider + " mode=" + mode);
    }
}





