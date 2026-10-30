package coffee.axle.suim.feature.command;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.util.MyauLogger;

import java.util.ArrayList;
import java.util.Arrays;

public class SuimCommand extends Feature {

    @Override
    public String getName() {
        return "Command:Suim";
    }

    @Override
    public boolean initialize() {
        try {
            ArrayList<String> commandNames = new ArrayList<>(Arrays.asList("suim", "myau"));
            creator.registerCommand(commandNames, this::handleCommand);

            return true;
        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    private void handleCommand(ArrayList<String> args) {
        manager.sendMessage("&d&m--------------------------------");
        manager.sendMessage("&d&l    SUIM &f&l@axle.coffee");
        manager.sendMessage("&d&m--------------------------------");
        manager.sendMessage("");

        manager.sendMessage("&c&lCombat");
        manager.sendMessage("  &7• &fAim-Assist: &eShow-Target");
        manager.sendMessage("  &7• &fKillAura: &eFilter-Armor &7& &eDisable-On-Death");
        manager.sendMessage("  &7• &fAutoClicker: &eInventory-Fill &7& &eRequire-Press");
        manager.sendMessage("");

        manager.sendMessage("&b&lPlayer");
        manager.sendMessage("  &7• &fEagle: &eAuto-Swap");
        manager.sendMessage("  &7• &fFreelook");
        manager.sendMessage("  &7• &fFreeze");
        manager.sendMessage("  &7• &fBufferVelo");
        manager.sendMessage("  &7• &fInvManager: &eDrop-Tools &7& &eDrop-Trash-Except");
        manager.sendMessage("  &7• &fFastPlace: &eSkip-Obsidian &7& &eSkip-Interactable");
        manager.sendMessage("");

        manager.sendMessage("&a&lRender");
        manager.sendMessage("  &7• &fBedESP: &eColor Mode (Team)");
        manager.sendMessage("  &7• &fBedPlates");
        manager.sendMessage("  &7• &fSkullESP");
        manager.sendMessage("  &7• &fItemESP: &eName-Tags");
        manager.sendMessage("  &7• &fChestESP: &ePer-Container Visuals");
        manager.sendMessage("  &7• &fXray: &eSpawner-Nametags &7& &eDragon-Egg");
        manager.sendMessage("");

        manager.sendMessage("&6&lClient");
        manager.sendMessage("  &7• &f.friend &7& &f.enemy &7- &eMultiple players support");
        manager.sendMessage("  &7• &f.client &7- &eClient info &7& &echange client name");
        manager.sendMessage("  &7• &f.find");
        manager.sendMessage("  &7• &f.status");
        manager.sendMessage("");

        manager.sendMessage("&d&m--------------------------------");
    }
}
