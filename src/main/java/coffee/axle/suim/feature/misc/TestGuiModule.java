package coffee.axle.suim.feature.misc;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.property.Setting;

import java.util.ArrayList;

/**
 * GUI test module - verifies the click GUI can render and control
 * a SUIM-created Myau module with properties.
 */
public class TestGuiModule extends Feature {

    public enum ExampleMode {
        Alpha, Beta, Gamma
    }

    @Setting(min = 0, max = 10) private float exampleSlider = 5.0f;
    @Setting private ExampleMode exampleMode = ExampleMode.Alpha;
    @Setting private boolean exampleToggle = true;

    public TestGuiModule() {
        super("TestGuiModule", GuiCategory.MISC);
    }

    @Override
    protected void init() throws Exception {
        ArrayList<String> cmdNames = new ArrayList<>();
        cmdNames.add("testgui");
        creator.registerCommand(cmdNames, this::handleCommand);
    }

    @Override
    protected void onEnable() {
        manager.sendMessage("&aTestGuiModule enabled");
    }

    @Override
    protected void onDisable() {
        manager.sendMessage("&cTestGuiModule disabled");
    }

    private void handleCommand(ArrayList<String> args) {
        manager.sendMessage("&7TestGuiModule: toggle=" + exampleToggle
                + " slider=" + exampleSlider + " mode=" + exampleMode);
    }
}





