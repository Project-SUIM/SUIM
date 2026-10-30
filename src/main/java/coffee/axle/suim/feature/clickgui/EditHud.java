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

    public EditHud() {
        super("EditHud", GuiCategory.SETTINGS);
    }

    @Override
    protected void init() throws Exception {
        ArrayList<String> cmdNames = new ArrayList<>();
        cmdNames.add("edithud");
        creator.registerCommand(cmdNames, this::handleCommand);

        MyauLogger.info("EditHud initialized");
    }

    @Override
    protected void onEnable() {
        manager.sendMessage(
                "&7EditHud is a stub \u2014 full implementation pending");
    }

    private void handleCommand(ArrayList<String> args) {
        manager.sendMessage(
                "&7EditHud is not yet implemented (requires heavy reflection)");
    }
}
