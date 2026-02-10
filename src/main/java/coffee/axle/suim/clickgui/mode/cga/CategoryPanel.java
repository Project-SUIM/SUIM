package coffee.axle.suim.clickgui.mode.cga;

import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.clickgui.module.GuiModule;
import coffee.axle.suim.clickgui.module.GuiModuleManager;
import coffee.axle.suim.ui.animations.impl.LinearAnimation;
import coffee.axle.suim.clickgui.util.MouseUtils;
import net.minecraft.client.renderer.GlStateManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Per-category panel in the ClickGUI. Contains a scrollable
 * list of module buttons. Port of Kotlin Window.kt.
 */
public class CategoryPanel {

    public static final int SCROLL_DISTANCE = 25;

    private final GuiCategory category;
    private final ClickGuiScreen clickGui;
    private final List<ModuleButton> moduleButtons = new ArrayList<>();

    private double x;
    private double y;
    private double width;
    private double height;

    private double scrollTarget;
    private double scrollOffset;
    private final LinearAnimation scrollAnimation = new LinearAnimation(200);

    public CategoryPanel(
            GuiCategory category, ClickGuiScreen clickGui) {
        this.category = category;
        this.clickGui = clickGui;
        this.width = clickGui.getGuiWidth()
                - clickGui.getCategoryWidth() - 15.0;
        this.height = clickGui.getGuiHeight() - 25.0;

        GuiModuleManager mgr = GuiModuleManager.getInstance();
        if (mgr != null) {
            List<GuiModule> modules = mgr.getModulesByCategory(category);
            for (GuiModule module : modules) {
                moduleButtons.add(
                        new ModuleButton(module, this));
            }
        }
    }

    /**
     * Minimal constructor for stub/proxy panels used by PanelClickGuiScreen.
     * Does not populate module buttons or access the ClickGuiScreen.
     */
    protected CategoryPanel(GuiCategory category, double width, double height) {
        this.category = category;
        this.clickGui = null;
        this.width = width;
        this.height = height;
    }

    public boolean isSelected() {
        return clickGui.getSelectedPanel() == this;
    }

    public boolean isInModule() {
        return moduleButtons.stream()
                .anyMatch(ModuleButton::isExtended);
    }

    public void draw() {
        if (!isSelected())
            return;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0);

        scrollOffset = scrollAnimation.get(scrollOffset, scrollTarget, false).doubleValue();

        List<ModuleButton> filtered = getFilteredButtons();

        double drawY = scrollOffset;
        for (ModuleButton btn : filtered) {
            btn.setX(0.0);
            btn.setY(drawY);
            drawY += btn.draw();
        }

        GlStateManager.popMatrix();
    }

    public boolean mouseClicked(int mouseButton) {
        if (isHovered()) {
            for (ModuleButton btn : getFilteredButtons()) {
                if (btn.mouseClicked(mouseButton))
                    return true;
            }
        }
        return false;
    }

    public void mouseReleased(int state) {
        if (isSelected()) {
            for (ModuleButton btn : getFilteredButtons()) {
                btn.mouseReleased(state);
            }
        }
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        if (isSelected()) {
            for (ModuleButton btn : getFilteredButtons()) {
                if (btn.keyTyped(typedChar, keyCode))
                    return true;
            }
        }
        return false;
    }

    public void mouseClickMove(
            int mouseButton, long timeSinceLastClick) {
        if (isSelected()) {
            for (ModuleButton btn : getFilteredButtons()) {
                btn.mouseClickMove(
                        mouseButton, timeSinceLastClick);
            }
        }
    }

    public void onGuiClosed() {
        for (ModuleButton btn : getFilteredButtons()) {
            btn.onGuiClosed();
        }
    }

    public boolean scroll(int amount) {
        if (isInModule() || !isHovered())
            return false;
        double h = getFilteredButtons().size() * 25.0 + 5.0;
        if (h < height)
            return false;
        scrollTarget = Math.max(
                -h + height,
                Math.min(0.0,
                        scrollTarget + amount
                                * SCROLL_DISTANCE));
        scrollAnimation.start(true);
        return true;
    }

    public boolean isHovered() {
        if (!isSelected())
            return false;
        int mx = MouseUtils.INSTANCE.getMouseX();
        int my = MouseUtils.INSTANCE.getMouseY();
        return mx >= x && mx <= x + width
                && my >= y && my <= y + height;
    }

    private List<ModuleButton> getFilteredButtons() {
        String search = clickGui.getSearchText();
        List<ModuleButton> source;
        if (search == null || search.isEmpty()) {
            source = new ArrayList<>(moduleButtons);
        } else {
            source = moduleButtons.stream()
                    .filter(btn -> btn.getModule().getName()
                            .toLowerCase()
                            .contains(search.toLowerCase()))
                    .collect(Collectors.toList());
        }
        Collections.reverse(source);
        return source;
    }

    // Accessors
    public GuiCategory getCategory() {
        return category;
    }

    public List<ModuleButton> getModuleButtons() {
        return moduleButtons;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }
}






