package coffee.axle.suim.features;

import coffee.axle.suim.hooks.MyauHook;
import coffee.axle.suim.util.MyauLogger;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Displays d'Myau info
 */
public class DMyauCommand implements Feature {
    private final MyauHook hook = MyauHook.getInstance();

    @Override
    public String getName() {
        return "Command:DMyau";
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");

            ArrayList<String> commandNames = new ArrayList<>(Arrays.asList("dmyau", "myau"));
            hook.registerCommand(commandNames, this::handleCommand);

            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;

        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    private void handleCommand(ArrayList<String> args) {
        hook.sendMessage("&d&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        hook.sendMessage("&d&l    d'Myau &f&l@axle.coffee");
        hook.sendMessage("&d&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        hook.sendMessage("");

        hook.sendMessage("&c&l Combat");
        hook.sendMessage("  &7• &fAim-Assist: &eShow-Target");
        hook.sendMessage("  &7• &fKillAura: &eFilter-Armor &7& &eDisable-On-Death");
        hook.sendMessage("  &7• &fAutoClicker: &eInventory-Fill & Require-Press");
        hook.sendMessage("  &7• &fHitSelect");
        hook.sendMessage("  &7• &fSword NoSlow");
        hook.sendMessage("");

        hook.sendMessage("&b&l Player");
        hook.sendMessage("  &7• &fEagle: &eAuto-Swap");
        hook.sendMessage("  &7• &fFreelook");
        hook.sendMessage("  &7• &fFreeze");
        hook.sendMessage("  &7• &fBufferVelo");
        hook.sendMessage("  &7• &fInvManager: &eDrop-Tools &7& &eDrop-Trash-Except");
        hook.sendMessage("  &7• &fFastPlace: &eSkip-Obsidian &7& &eSkip-Interactable");
        hook.sendMessage("");

        hook.sendMessage("&a&l Render");
        hook.sendMessage("  &7• &fBedESP: &eColor Mode (Team)");
        hook.sendMessage("  &7• &fBedPlates");
        hook.sendMessage("  &7• &fSkullESP");
        hook.sendMessage("  &7• &fXray: &eSpawner-Nametags");
        hook.sendMessage("");

        hook.sendMessage("&6&l Client");
        hook.sendMessage("  &7• &f.friend &7& &f.enemy &7- &eMultiple players support");
        hook.sendMessage("  &7• &f.client &7- &eClient Info &7& &eChange client name");
        hook.sendMessage("  &7• &f.find");
        hook.sendMessage("  &7• &f.status");
        hook.sendMessage("");

        hook.sendMessage("&d&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}
