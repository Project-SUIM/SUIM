package coffee.axle.suim.feature;

/**
 * Categories for GUI module panels.
 */
public enum GuiCategory {
    // order decides B3 panel layout - settings/scripts sit on the second
    // row of panels, settings below world
    WORLD,
    COMBAT,
    RENDER,
    MISC,
    EXPLOIT,
    PLAYER,
    SETTINGS,
    SCRIPTS;

    public String getDisplayName() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
