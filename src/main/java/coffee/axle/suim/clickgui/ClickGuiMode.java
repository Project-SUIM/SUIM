package coffee.axle.suim.clickgui;

public interface ClickGuiMode {
    String getId();

    void open();

    boolean supportsDesign();
}
