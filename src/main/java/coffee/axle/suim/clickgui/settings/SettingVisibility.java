package coffee.axle.suim.clickgui.settings;

public enum SettingVisibility {
    HIDDEN(0),
    CLICK_GUI_ONLY(1),
    ADVANCED_ONLY(2),
    VISIBLE(3);

    private final int ordinalValue;

    SettingVisibility(int ordinalValue) {
        this.ordinalValue = ordinalValue;
    }

    public boolean isVisibleInClickGui() {
        return (ordinalValue & 1) != 0;
    }

    public boolean isVisibleInAdvanced() {
        return (ordinalValue & 2) != 0;
    }
}





