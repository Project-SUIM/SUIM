package coffee.axle.suim.clickgui.mode.b3;

import coffee.axle.suim.clickgui.ClickGuiMode;

public class B3ClickGuiMode implements ClickGuiMode {
    @Override
    public String getId() {
        return "B3";
    }

    @Override
    public void open() {
        B3ClickGuiScreen.open();
    }

    @Override
    public boolean supportsDesign() {
        return false;
    }
}
