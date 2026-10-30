package coffee.axle.suim.feature;

public enum GuiCategory {
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
