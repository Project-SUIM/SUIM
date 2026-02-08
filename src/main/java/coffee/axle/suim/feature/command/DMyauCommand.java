package coffee.axle.suim.feature.command;

import coffee.axle.suim.feature.Feature;

import coffee.axle.suim.util.MyauLogger;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Displays d'Myau info
 */
public class DMyauCommand extends Feature {

    @Override
    public String getName() {
        return "Command:DMyau";
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");

            ArrayList<String> commandNames = new ArrayList<>(Arrays.asList("dmyau", "myau"));
            creator.registerCommand(commandNames, this::handleCommand);

            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;

        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    private void handleCommand(ArrayList<String> args) {
        manager.sendMessage("&d&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        manager.sendMessage("&d&l    d'Myau &f&l@axle.coffee");
        manager.sendMessage("&d&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        manager.sendMessage("");

        manager.sendMessage("&c&l Combat");
        manager.sendMessage("  &7• &fAim-Assist: &eShow-Target");
        manager.sendMessage("  &7• &fKillAura: &eFilter-Armor &7& &eDisable-On-Death");
        manager.sendMessage("  &7• &fAutoClicker: &eInventory-Fill & Require-Press");
        manager.sendMessage("  &7• &fHitSelect");
        manager.sendMessage("  &7• &fSword NoSlow");
        manager.sendMessage("");

        manager.sendMessage("&b&l Player");
        manager.sendMessage("  &7• &fEagle: &eAuto-Swap");
        manager.sendMessage("  &7• &fFreelook");
        manager.sendMessage("  &7• &fFreeze");
        manager.sendMessage("  &7• &fBufferVelo");
        manager.sendMessage("  &7• &fInvManager: &eDrop-Tools &7& &eDrop-Trash-Except");
        manager.sendMessage("  &7• &fFastPlace: &eSkip-Obsidian &7& &eSkip-Interactable");
        manager.sendMessage("");

        manager.sendMessage("&a&l Render");
        manager.sendMessage("  &7• &fBedESP: &eColor Mode (Team)");
        manager.sendMessage("  &7• &fBedPlates");
        manager.sendMessage("  &7• &fSkullESP");
        manager.sendMessage("  &7• &fXray: &eSpawner-Nametags");
        manager.sendMessage("");

        manager.sendMessage("&6&l Client");
        manager.sendMessage("  &7• &f.friend &7& &f.enemy &7- &eMultiple players support");
        manager.sendMessage("  &7• &f.client &7- &eClient Info &7& &eChange client name");
        manager.sendMessage("  &7• &f.find");
        manager.sendMessage("  &7• &f.status");
        manager.sendMessage("");

        manager.sendMessage("&d&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}
