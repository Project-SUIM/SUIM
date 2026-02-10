package coffee.axle.suim.config;

import coffee.axle.suim.ProjectSUIM;
import coffee.axle.suim.clickgui.misc.inventorybuttons.InventoryButton;
import coffee.axle.suim.clickgui.util.ConfigSystem;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the list of inventory buttons, including load/save/defaults.
 */
public final class InventoryButtonsConfig {

    public static final InventoryButtonsConfig INSTANCE = new InventoryButtonsConfig();

    private static final File configFile = new File(ProjectSUIM.getConfigPath(), "inventory_buttons.json");

    private static final List<InventoryButton> allButtons = new ArrayList<>();

    private InventoryButtonsConfig() {
    }

    public static List<InventoryButton> getAllButtons() {
        return allButtons;
    }

    /**
     * Loads inventory buttons from the config file.
     * If no buttons are loaded, initializes defaults.
     */
    public static void load() {
        allButtons.clear();
        List<InventoryButton> loaded = ConfigSystem.loadConfig(
                configFile,
                new TypeToken<List<InventoryButton>>() {
                }.getType());
        if (loaded != null) {
            allButtons.addAll(loaded);
        }
        if (allButtons.isEmpty()) {
            initDefaults();
        }
    }

    /**
     * Saves the current inventory buttons to the config file.
     */
    public static void save() {
        if (configFile.getParentFile() != null) {
            configFile.getParentFile().mkdirs();
        }
        ConfigSystem.saveConfig(configFile, allButtons);
    }

    /**
     * Initializes the default button grid around the inventory.
     */
    private static void initDefaults() {
        // Top row
        int[][] topRow = {
                { -19, -1 }, { -1, -19 }, { 17, -19 }, { 35, -19 }, { 53, -19 },
                { 71, -19 }, { 89, -19 }, { 107, -19 }, { 125, -19 }, { 143, -19 }, { 161, -19 }
        };
        // Right column
        int[][] rightCol = {
                { 177, -1 }, { 177, 17 }, { 177, 35 }, { 177, 53 }, { 177, 71 },
                { 177, 89 }, { 177, 107 }, { 177, 125 }, { 177, 143 }
        };
        // Left column
        int[][] leftCol = {
                { -19, 17 }, { -19, 35 }, { -19, 53 }, { -19, 71 }, { -19, 89 },
                { -19, 107 }, { -19, 125 }, { -19, 143 }
        };

        for (int[] pos : topRow) {
            allButtons.add(new InventoryButton(pos[0], pos[1]));
        }
        for (int[] pos : rightCol) {
            allButtons.add(new InventoryButton(pos[0], pos[1]));
        }
        for (int[] pos : leftCol) {
            allButtons.add(new InventoryButton(pos[0], pos[1]));
        }

        // Equipment slots (read-only display)
        allButtons.add(new InventoryButton(7, 7, "", "barrier", true));
        allButtons.add(new InventoryButton(7, 25, "", "barrier", true));
        allButtons.add(new InventoryButton(7, 43, "", "barrier", true));
        allButtons.add(new InventoryButton(7, 61, "", "barrier", true));
    }
}





