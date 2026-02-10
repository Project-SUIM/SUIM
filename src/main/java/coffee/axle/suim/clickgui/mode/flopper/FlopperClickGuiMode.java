package coffee.axle.suim.clickgui.mode.flopper;

import coffee.axle.suim.clickgui.ClickGuiMode;

public class FlopperClickGuiMode implements ClickGuiMode {
    @Override
    public String getId() {
        return "Flopper";
    }

    @Override
    public void open() {
        PanelClickGuiScreen.open();
    }

    @Override
    public boolean supportsDesign() {
        return true;
    }
}
