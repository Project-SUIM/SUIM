package coffee.axle.suim.clickgui.mode.cga;

import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.clickgui.ClickGuiCloseHandler;
import coffee.axle.suim.clickgui.ClickGuiConfig;
import coffee.axle.suim.clickgui.util.MiscElementHelper;
import coffee.axle.suim.clickgui.util.FontUtil;
import coffee.axle.suim.clickgui.util.MouseUtils;
import coffee.axle.suim.clickgui.misc.elements.impl.MiscElementTextField;
import coffee.axle.suim.clickgui.render.HUDRenderUtils;
import coffee.axle.suim.ProjectSUIM;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Main ClickGUI screen. Renders a category sidebar with a search
 * bar and a content area showing the selected category's modules.
 * Port of the Kotlin ClickGUI.kt (clickguinew variant).
 */
public class ClickGuiScreen extends SuimScreen {

    private static ClickGuiScreen instance;

    private static final double GUI_WIDTH = 355.0;
    private static final double GUI_HEIGHT = 260.0;
    private static final double CATEGORY_WIDTH = 85.0;

    private double guiX;
    private double guiY;

    private final List<CategoryPanel> panels = new ArrayList<>();
    private CategoryPanel selectedPanel;
    private MiscElementTextField searchBar;

    public ClickGuiScreen() {
        super(true);
        rebuildPanels();
    }

    private void rebuildPanels() {
        panels.clear();
        for (GuiCategory category : GuiCategory.values()) {
            panels.add(new CategoryPanel(category, this));
        }
        if (panels.size() > 1) {
            selectedPanel = panels.get(1); // RENDER
        } else if (!panels.isEmpty()) {
            selectedPanel = panels.get(0);
        }
    }

    @Override
    protected void onInit() {
        rebuildPanels();
        guiX = getX(GUI_WIDTH);
        guiY = getY(GUI_HEIGHT);

        searchBar = MiscElementHelper.createTextField(
                guiX + CATEGORY_WIDTH + 5.0, guiY,
                GUI_WIDTH - CATEGORY_WIDTH - 5.0, 20.0,
                0, "Search...", "");
        searchBar.setThickness(2.0);
        searchBar.setColour(CgaTheme.getBackground());

        for (CategoryPanel panel : panels) {
            panel.setX(guiX + CATEGORY_WIDTH + 10.0);
            panel.setY(guiY + 30.0);
        }
    }

    @Override
    protected void draw() {
        Color accent = CgaTheme.getAccent();
        Color bg = CgaTheme.getBackground();
        Color outline = CgaTheme.getOutline();
        int mx = MouseUtils.INSTANCE.getMouseX();
        int my = MouseUtils.INSTANCE.getMouseY();

        if (ClickGuiConfig.isShowUsageInfo()) {
            renderUsage(accent);
        }

        // Outer frame
        HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                guiX - 5.0, guiY - 5.0,
                GUI_WIDTH + 10.0, GUI_HEIGHT + 10.0,
                3.0, 2.0, bg, accent);

        // Category sidebar
        HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                guiX, guiY,
                CATEGORY_WIDTH, GUI_HEIGHT,
                3.0, 2.0, bg, accent);

        // Content area
        HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                guiX + CATEGORY_WIDTH + 5.0, guiY + 25.0,
                GUI_WIDTH - CATEGORY_WIDTH - 5.0,
                GUI_HEIGHT - 25.0,
                3.0, 2.0, bg, accent);

        // Title
        String clientName = ClickGuiConfig.getClientName();
        double titleW = FontUtil.INSTANCE.getStringWidth(clientName);
        FontUtil.INSTANCE.drawStringWithShadow(
                clientName,
                guiX + CATEGORY_WIDTH / 2.0 - titleW / 2.0,
                guiY + 6.0,
                CgaTheme.getTextColor());

        // Search bar
        searchBar.setOutlineColour(outline);
        searchBar.setOutlineHoverColour(accent);
        searchBar.render(mx, my);

        // Scissor content
        HUDRenderUtils.Scissor scissor = (HUDRenderUtils.Scissor) HUDRenderUtils.INSTANCE.scissor(
                guiX, guiY + 26.0,
                GUI_WIDTH, GUI_HEIGHT - 27.0);

        double categoryOffset = 25.0;
        String search = getSearchText();

        for (CategoryPanel panel : panels) {
            double offset = panel.getCategory() == GuiCategory.SETTINGS
                    ? GUI_HEIGHT - 20.0
                    : categoryOffset;

            // Filter empty categories by search
            if (!search.isEmpty()) {
                boolean hasMatch = panel.getModuleButtons()
                        .stream()
                        .anyMatch(btn -> btn.getModule()
                                .getName().toLowerCase()
                                .contains(search.toLowerCase()));
                if (!hasMatch)
                    continue;
            }

            // Draw panel content
            panel.draw();

            // Category button
            double btnX = guiX + 5.0;
            double btnY = guiY + offset + 2.0;
            double btnW = CATEGORY_WIDTH - 9.0;
            double btnH = 14.0;

            if (selectedPanel == panel) {
                HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                        btnX, btnY, btnW, btnH,
                        3.0, 1.0, outline, outline);
            }

            double fh = FontUtil.INSTANCE.getFontHeight();
            FontUtil.INSTANCE.drawString(
                    panel.getCategory().getDisplayName(),
                    btnX + 12.0,
                    btnY + btnH / 2.0 - fh / 2.0,
                    CgaTheme.getTextColor());

            if (panel.getCategory() != GuiCategory.SETTINGS) {
                categoryOffset += 14.0;
            }
        }

        HUDRenderUtils.INSTANCE.resetScissor(
                (HUDRenderUtils.Scissor) scissor);
    }

    @Override
    protected void onScroll(int amount) {
        for (int i = panels.size() - 1; i >= 0; i--) {
            if (panels.get(i).scroll(amount))
                return;
        }
        if (selectedPanel != null) {
            for (ModuleButton btn : selectedPanel.getModuleButtons()) {
                if (btn.scroll(amount))
                    return;
            }
        }
    }

    @Override
    protected void onMouseClick(int mouseButton) {
        int mx = MouseUtils.INSTANCE.getMouseX();
        int my = MouseUtils.INSTANCE.getMouseY();
        searchBar.mouseClicked(mx, my, mouseButton);

        // Category button clicks
        double categoryOffset = 25.0;
        for (CategoryPanel panel : panels) {
            double offset = panel.getCategory() == GuiCategory.SETTINGS
                    ? GUI_HEIGHT - 20.0
                    : categoryOffset;
            double btnX = guiX + 5.0;
            double btnY = guiY + offset + 2.0;
            double btnW = CATEGORY_WIDTH - 9.0;
            double btnH = 14.0;

            if (mx >= btnX && mx <= btnX + btnW
                    && my >= btnY && my <= btnY + btnH) {
                if (selectedPanel != null) {
                    for (ModuleButton btn : selectedPanel.getModuleButtons()) {
                        btn.setExtended(false);
                    }
                }
                selectedPanel = panel;
                return;
            }

            if (panel.getCategory() != GuiCategory.SETTINGS) {
                categoryOffset += 14.0;
            }
        }

        // Panel clicks
        for (int i = panels.size() - 1; i >= 0; i--) {
            if (panels.get(i).mouseClicked(mouseButton))
                return;
        }
    }

    @Override
    protected void onMouseRelease(int state) {
        for (int i = panels.size() - 1; i >= 0; i--) {
            panels.get(i).mouseReleased(state);
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode)
            throws java.io.IOException {
        if (searchBar.keyTyped(typedChar, keyCode))
            return;
        for (int i = panels.size() - 1; i >= 0; i--) {
            if (panels.get(i).keyTyped(typedChar, keyCode))
                return;
        }
        if (keyCode == Keyboard.KEY_F && isCtrlKeyDown()) {
            searchBar.setFocused(true);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void onMouseClickMove(
            int mouseButton, long timeSinceLastClick) {
        int mx = MouseUtils.INSTANCE.getMouseX();
        int my = MouseUtils.INSTANCE.getMouseY();
        for (int i = panels.size() - 1; i >= 0; i--) {
            panels.get(i).mouseClickMove(
                    mouseButton, timeSinceLastClick);
        }
        searchBar.mouseClickMove(
                mx, my, mouseButton, timeSinceLastClick);
    }

    @Override
    public void onGuiClosed() {
        for (int i = panels.size() - 1; i >= 0; i--) {
            panels.get(i).onGuiClosed();
        }
        searchBar.setFocused(false);
        ClickGuiCloseHandler.onClose();
    }

    private void renderUsage(Color accent) {
        String[] lines = {
                "GUI Usage:",
                "Left click Module Buttons to toggle the Module.",
                "Right click Module Buttons to open settings.",
                "Middle click or Shift click to change key bind.",
                "Disable this Overlay in ClickGUI settings.",
                "You can change GUI style in settings."
        };

        double lineH = FontUtil.INSTANCE.getFontHeight(1.3);
        for (int i = 0; i < lines.length; i++) {
            FontUtil.INSTANCE.drawString(
                    lines[i], 10.0, 10.0 + lineH * i,
                    accent.getRGB(), false, 1.3, false);
        }
    }

    // Accessors for child components
    public String getSearchText() {
        return searchBar != null ? searchBar.getText() : "";
    }

    public CategoryPanel getSelectedPanel() {
        return selectedPanel;
    }

    public double getGuiWidth() {
        return GUI_WIDTH;
    }

    public double getGuiHeight() {
        return GUI_HEIGHT;
    }

    public double getCategoryWidth() {
        return CATEGORY_WIDTH;
    }

    /**
     * Open the ClickGUI screen.
     * Called from ClickGui when the module is toggled.
     */
    public static void open() {
        if (instance == null) {
            instance = new ClickGuiScreen();
        }
        ProjectSUIM.setDisplay(instance);
    }

    public static void invalidate() {
        instance = null;
    }
}
