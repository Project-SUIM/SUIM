package coffee.axle.suim.feature;

/**
 * Categories for GUI module panels.
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
