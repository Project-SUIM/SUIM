package coffee.axle.suim.clickgui.module;

import coffee.axle.suim.feature.GuiCategory;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps Myau module names to GUI categories.
 * Used by the reverse module hook to assign categories
 * to auto-discovered Myau modules.
 *
 * Add mappings here when new Myau modules need to appear
 * in a specific GUI category.
 */
public final class MyauModuleCategoryMapper {

    private static final Map<String, GuiCategory> CATEGORY_MAP = new HashMap<>();

    static {
        // Combat
        map("KillAura", GuiCategory.COMBAT);
        map("AimAssist", GuiCategory.COMBAT);
        map("AutoClicker", GuiCategory.COMBAT);
        map("Reach", GuiCategory.COMBAT);
        map("Velocity", GuiCategory.COMBAT);
        map("AntiBot", GuiCategory.COMBAT);
        map("Criticals", GuiCategory.COMBAT);

        // Render
        map("ESP", GuiCategory.RENDER);
        map("BedESP", GuiCategory.RENDER);
        map("BedTracker", GuiCategory.RENDER);
        map("Chams", GuiCategory.RENDER);
        map("NameTags", GuiCategory.RENDER);
        map("Tracers", GuiCategory.RENDER);
        map("HUD", GuiCategory.RENDER);
        map("ClickGUI", GuiCategory.SETTINGS);
        map("Fullbright", GuiCategory.RENDER);
        map("Animations", GuiCategory.RENDER);
        map("ItemPhysics", GuiCategory.RENDER);
        map("TimeChanger", GuiCategory.RENDER);
        map("ViewModel", GuiCategory.RENDER);
        map("XRay", GuiCategory.RENDER);
        map("Xray", GuiCategory.RENDER);

        // Player
        map("Sprint", GuiCategory.PLAYER);
        map("AutoSprint", GuiCategory.PLAYER);
        map("NoFall", GuiCategory.PLAYER);
        map("InventoryManager", GuiCategory.PLAYER);
        map("InvManager", GuiCategory.PLAYER);
        map("ChestStealer", GuiCategory.PLAYER);
        map("Scaffold", GuiCategory.PLAYER);
        map("NoSlow", GuiCategory.PLAYER);
        map("AutoArmor", GuiCategory.PLAYER);
        map("AutoTool", GuiCategory.PLAYER);

        // World
        map("Disabler", GuiCategory.WORLD);
        map("Timer", GuiCategory.WORLD);
        map("FastPlace", GuiCategory.WORLD);
        map("Eagle", GuiCategory.WORLD);
        map("Teams", GuiCategory.WORLD);

        // Exploit
        map("Blink", GuiCategory.EXPLOIT);
        map("Freeze", GuiCategory.EXPLOIT);
        map("Phase", GuiCategory.EXPLOIT);
        map("Fly", GuiCategory.EXPLOIT);
        map("Speed", GuiCategory.EXPLOIT);
        map("LongJump", GuiCategory.EXPLOIT);

        // Misc
        map("AutoGG", GuiCategory.MISC);
        map("AutoText", GuiCategory.MISC);
        map("Spammer", GuiCategory.MISC);
        map("AntiStaff", GuiCategory.MISC);
    }

    private MyauModuleCategoryMapper() {
    }

    private static void map(String moduleName, GuiCategory category) {
        CATEGORY_MAP.put(moduleName.toLowerCase(), category);
    }

    /**
     * Gets the GUI category for a Myau module name.
     * Case-insensitive matching with fallback to MISC.
     */
    public static GuiCategory getCategory(String moduleName) {
        if (moduleName == null) {
            return GuiCategory.MISC;
        }
        GuiCategory cat = CATEGORY_MAP.get(moduleName.toLowerCase());
        return cat != null ? cat : GuiCategory.MISC;
    }

    /**
     * Checks whether a module name has a known category mapping.
     */
    public static boolean hasMapping(String moduleName) {
        return moduleName != null
                && CATEGORY_MAP.containsKey(moduleName.toLowerCase());
    }
}
