package coffee.axle.suim.feature;

/**
 * Categories for GUI module panels.
 * Maps to the Kotlin Category enum used by the GUI rendering layer.
 */
public enum GuiCategory {
    COMBAT,
    RENDER,
    PLAYER,
    WORLD,
    EXPLOIT,
    MISC,
    SETTINGS;

    public String getDisplayName() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
