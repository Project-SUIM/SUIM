package coffee.axle.suim.feature.misc;

import coffee.axle.suim.feature.Feature;

import coffee.axle.suim.hooks.MyauWorld;
import coffee.axle.suim.util.MyauLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class TestCommand extends Feature {

    @Override
    public String getName() {
        return "Command:Test";
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");
            ArrayList<String> commandNames = new ArrayList<>(Arrays.asList("test"));
            creator.registerCommand(commandNames, this::handleCommand);
            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;
        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    private void handleCommand(ArrayList<String> args) {
        if (args.size() > 1) {
            String arg = args.get(1);
            manager.sendMessage("  &eYou passed: &f" + arg);
            manager.sendMessage("");
        }
        manager.sendMessage("§dMyau Injection Template");
        manager.sendMessage("§aMade by @axle.coffee on discord §ehttps://github.com/axlecoffee");
        manager.sendMessage("");
        manager.sendMessage("§6Modules and Origins:");

        LinkedHashMap<Class<?>, Object> modules = manager.getAllModules();
        for (Map.Entry<Class<?>, Object> entry : modules.entrySet()) {
            String moduleName = manager.getModuleName(entry.getValue());
            String origin = MyauWorld.getOrigin(entry.getValue());
            manager.sendMessage("  §7" + moduleName + " §8-> §e" + origin);
        }
    }
}





