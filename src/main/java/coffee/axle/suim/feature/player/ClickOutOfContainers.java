package coffee.axle.suim.feature.player;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;

public class ClickOutOfContainers extends Feature {

    private static ClickOutOfContainers instance;

    public ClickOutOfContainers() {
        super("ClickOutContainers", GuiCategory.PLAYER);
    }

    @Override
    protected void init() throws Exception {
        instance = this;
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }
}
