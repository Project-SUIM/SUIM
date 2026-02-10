package coffee.axle.suim.clickgui.mode.cga;

import coffee.axle.suim.clickgui.ClickGuiMode;

public class CgaClickGuiMode implements ClickGuiMode {
    @Override
    public String getId() {
        return "Cga";
    }

    @Override
    public void open() {
        ClickGuiScreen.open();
    }

    @Override
    public boolean supportsDesign() {
        return false;
    }
}
