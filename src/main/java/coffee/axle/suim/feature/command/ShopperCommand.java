package coffee.axle.suim.feature.command;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.exploit.Shopper;
import coffee.axle.suim.util.MyauLogger;

import java.util.ArrayList;

public class ShopperCommand extends Feature {

    @Override
    public String getName() {
        return "Command:Shopper";
    }

    @Override
    public boolean initialize() {
        try {
            ArrayList<String> names = new ArrayList<>();
            names.add("shopperconfig");
            names.add("sh");
            creator.registerCommand(names, this::handle);
            return true;
        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    private void handle(ArrayList<String> args) {
        // args.get(0) is the command name itself; subcommand starts at 1
        String sub = args.size() > 1 ? args.get(1).toLowerCase() : "";

        switch (sub) {
            case "record": {
                if (Shopper.isRecording()) {
                    int count = Shopper.stopRecordingAndGetCount();
                    manager.sendMessage("&fShopper: &crecording stopped&7, &f" + count + " &7items");
                } else {
                    Shopper.startRecording();
                    manager.sendMessage("&fShopper: &arecording&7 - click through your shop route");
                }
                return;
            }
            case "save": {
                if (args.size() < 3) {
                    manager.sendMessage("&fShopper: &7usage: .sh save <name>");
                    return;
                }
                String name = args.get(2);
                manager.sendMessage(Shopper.save(name)
                        ? "&fShopper: &asaved &b" + name
                        : "&fShopper: &cnothing recorded");
                return;
            }
            case "load": {
                if (args.size() < 3) {
                    manager.sendMessage("&fShopper: &7usage: .sh load <name>");
                    return;
                }
                String name = args.get(2);
                manager.sendMessage(Shopper.load(name)
                        ? "&fShopper: &aloaded &b" + name
                        : "&fShopper: &cno config named &b" + name);
                return;
            }
            case "list": {
                ArrayList<String> names = new ArrayList<>(Shopper.configNames());
                if (names.isEmpty()) {
                    manager.sendMessage("&fShopper configs: &7none");
                    return;
                }
                StringBuilder sb = new StringBuilder("&fShopper configs: ");
                for (int i = 0; i < names.size(); i++) {
                    if (i > 0)
                        sb.append("&7, ");
                    sb.append("&b").append(names.get(i));
                }
                manager.sendMessage(sb.toString());
                return;
            }
            default:
                manager.sendMessage("&fShopper: &7usage: .sh <record|save|load|list>");
        }
    }
}
