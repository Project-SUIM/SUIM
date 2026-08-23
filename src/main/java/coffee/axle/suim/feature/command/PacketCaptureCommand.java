package coffee.axle.suim.feature.command;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.PacketCapture;

import java.util.ArrayList;
import java.util.Arrays;

public class PacketCaptureCommand extends Feature {

    @Override
    public String getName() {
        return "Command:PacketCapture";
    }

    @Override
    public boolean initialize() {
        try {
            creator.registerCommand(new ArrayList<>(Arrays.asList("packetcapture", "pcap")), this::handleCommand);
            PacketCapture.getInstance().register();
            return true;
        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    private void handleCommand(ArrayList<String> args) {
        String sub = args.size() > 1 ? args.get(1).toLowerCase() : "help";
        PacketCapture cap = PacketCapture.getInstance();
        switch (sub) {
            case "start":
                if (cap.isCapturing()) {
                    manager.sendMessage("&cAlready capturing");
                    return;
                }
                cap.start();
                manager.sendMessage("&aCapturing - .packetcapture end to stop");
                break;
            case "end":
            case "stop":
                if (!cap.isCapturing()) {
                    manager.sendMessage("&cNot capturing");
                    return;
                }
                cap.stop();
                manager.sendMessage("&a" + cap.getEntryCount() + " packets copied to clipboard");
                if (cap.getLastOutputPath() != null)
                    manager.sendMessage("&7File: &f" + cap.getLastOutputPath());
                break;
            case "target":
                if (args.size() < 3) {
                    manager.sendMessage("&cUsage: .packetcapture target <ign>");
                    return;
                }
                cap.setTarget(args.get(2));
                manager.sendMessage("&aTarget: " + args.get(2));
                break;
            default:
                manager.sendMessage("&e.packetcapture start | end | target <ign>");
        }
    }
}
