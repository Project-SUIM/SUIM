package coffee.axle.suim.clickgui.mode.flopper;

import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.clickgui.ClickGuiCloseHandler;
import coffee.axle.suim.clickgui.ClickGuiConfig;
import coffee.axle.suim.clickgui.element.GuiElement;
import coffee.axle.suim.clickgui.module.GuiModule;
import coffee.axle.suim.clickgui.module.GuiModuleManager;
import coffee.axle.suim.clickgui.settings.ModuleSetting;
import coffee.axle.suim.clickgui.settings.SettingType;
import coffee.axle.suim.clickgui.settings.impl.*;
import coffee.axle.suim.clickgui.element.impl.*;
import coffee.axle.suim.clickgui.mode.cga.ModuleButton;
import coffee.axle.suim.clickgui.mode.cga.CategoryPanel;
import coffee.axle.suim.clickgui.util.ColorUtil;
import coffee.axle.suim.clickgui.util.FontUtil;
import coffee.axle.suim.clickgui.util.KeybindListenerUtil;
import coffee.axle.suim.clickgui.render.HUDRenderUtils;
import coffee.axle.suim.ProjectSUIM;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel-based ClickGUI (Flopper mode). Renders draggable category panels
 * with module buttons and per-module setting elements.
 * Port of the Kotlin clickgui/ClickGUI.kt (old-style panel CGA).
 */
public class PanelClickGuiScreen extends GuiScreen {

    private static final double CLICK_GUI_SCALE = 2.0;
    private static final int PANEL_WIDTH = 120;
    private static final int PANEL_HEIGHT = 15;

    private static PanelClickGuiScreen instance;

    private final List<DraggablePanel> panels = new ArrayList<>();
    private double scale;
    private long openedTime;

    public PanelClickGuiScreen() {
        setupPanels();
    }

    private void setupPanels() {
        panels.clear();
        int px = 10;
        int py = 10;
        for (GuiCategory category : GuiCategory.values()) {
            DraggablePanel panel = new DraggablePanel(category, this, px, py);
            panels.add(panel);
            px += PANEL_WIDTH + 10;
        }
    }

    @Override
    public void initGui() {
        openedTime = System.currentTimeMillis();
        ScaledResolution sr = new ScaledResolution(mc);
        scale = CLICK_GUI_SCALE / sr.getScaleFactor();

        if (ClickGuiConfig.isBlurEnabled()) {
            try {
                if (mc.getRenderViewEntity() instanceof EntityPlayer) {
                    mc.entityRenderer.stopUseShader();
                    mc.entityRenderer.loadShader(
                            new ResourceLocation("shaders/post/blur.json"));
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ScaledResolution sr = new ScaledResolution(mc);
        int prevScale = mc.gameSettings.guiScale;
        scale = CLICK_GUI_SCALE / sr.getScaleFactor();
        mc.gameSettings.guiScale = 2;
        GL11.glScaled(scale, scale, scale);

        int sx = getScaledMouseX();
        int sy = getScaledMouseY();

        for (DraggablePanel panel : panels) {
            panel.drawScreen(sx, sy, partialTicks);
        }

        if (ClickGuiConfig.isShowUsageInfo()) {
            renderUsageInfo();
        }

        super.drawScreen(sx, sy, partialTicks);
        mc.gameSettings.guiScale = prevScale;
    }

    private void renderUsageInfo() {
        ScaledResolution sr = new ScaledResolution(mc);
        String[] lines = {
                "GUI Usage:",
                "Left click Module Buttons to toggle the Module.",
                "Right click Module Buttons to extend Settings.",
                "Drag panel headers to rearrange.",
                "Right click headers to collapse/expand.",
                "Disable this Overlay in ClickGUI settings."
        };

        GL11.glPushMatrix();
        GL11.glTranslated(sr.getScaledWidth() * 0.1, sr.getScaledHeight() * 0.7, 0.0);
        GL11.glScaled(1.5, 1.5, 1.5);
        int color = ColorUtil.INSTANCE.getClickGUIColor().getRGB();
        int fh = FontUtil.INSTANCE.getFontHeight();
        for (int i = 0; i < lines.length; i++) {
            FontUtil.INSTANCE.drawString(lines[i], 0.0, fh * i, color);
        }
        GL11.glPopMatrix();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int sx = getScaledMouseX();
        int sy = getScaledMouseY();

        int scroll = Mouse.getEventDWheel();
        if (scroll != 0) {
            int amount = scroll > 0 ? 1 : -1;
            if (isShiftKeyDown())
                amount *= 7;
            for (int i = panels.size() - 1; i >= 0; i--) {
                if (panels.get(i).scroll(amount, sx, sy))
                    return;
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int sx = getScaledMouseX();
        int sy = getScaledMouseY();

        for (int i = panels.size() - 1; i >= 0; i--) {
            if (panels.get(i).mouseClicked(sx, sy, mouseButton))
                return;
        }
        super.mouseClicked(sx, sy, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        int sx = getScaledMouseX();
        int sy = getScaledMouseY();

        for (int i = panels.size() - 1; i >= 0; i--) {
            panels.get(i).mouseReleased(sx, sy, state);
        }
        super.mouseReleased(sx, sy, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        for (int i = panels.size() - 1; i >= 0; i--) {
            if (panels.get(i).keyTyped(typedChar, keyCode))
                return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        for (DraggablePanel panel : panels) {
            panel.onGuiClosed();
        }
        ClickGuiCloseHandler.onClose();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private int getScaledMouseX() {
        ScaledResolution sr = new ScaledResolution(mc);
        double currentScale = CLICK_GUI_SCALE / sr.getScaleFactor();
        return (int) (Mouse.getX()
                / (sr.getScaleFactor() * currentScale));
    }

    private int getScaledMouseY() {
        ScaledResolution sr = new ScaledResolution(mc);
        double currentScale = CLICK_GUI_SCALE / sr.getScaleFactor();
        return (int) (((mc.displayHeight - Mouse.getY())
                / sr.getScaleFactor()) / currentScale);
    }

    int getPanelWidth() {
        return PANEL_WIDTH;
    }

    int getPanelHeight() {
        return PANEL_HEIGHT;
    }

    public static void open() {
        if (instance == null) {
            instance = new PanelClickGuiScreen();
        }
        ProjectSUIM.setDisplay(instance);
    }

    public static void invalidate() {
        instance = null;
    }

    // Inner: DraggablePanel — per-category draggable panel
    static class DraggablePanel {
        private final GuiCategory category;
        private final PanelClickGuiScreen gui;
        private final String title;
        private final List<PanelModuleButton> moduleButtons = new ArrayList<>();

        private final int width;
        private final int height;
        int x;
        int y;
        boolean extended = true;
        boolean dragging;
        private int dragOffsetX;
        private int dragOffsetY;
        private int scrollOffset;
        private int length;

        private static final int SCROLL_DISTANCE = 11;

        DraggablePanel(GuiCategory category, PanelClickGuiScreen gui, int x, int y) {
            this.category = category;
            this.gui = gui;
            this.title = category.getDisplayName();
            this.width = gui.getPanelWidth();
            this.height = gui.getPanelHeight();
            this.x = x;
            this.y = y;

            GuiModuleManager mgr = GuiModuleManager.getInstance();
            if (mgr != null) {
                for (GuiModule module : mgr.getModulesByCategory(category)) {
                    moduleButtons.add(new PanelModuleButton(module, this));
                }
            }
        }

        void drawScreen(int mouseX, int mouseY, float partialTicks) {
            if (dragging) {
                x = dragOffsetX + mouseX;
                y = dragOffsetY + mouseY;
            }

            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, 0.0f);

            HUDRenderUtils.INSTANCE.setUpScissorAbsolute(
                    x - 2, y + height, x + width + 1, y + height + 4000);

            int startY = height;
            if (extended && !moduleButtons.isEmpty()) {
                startY -= scrollOffset;
                for (PanelModuleButton btn : moduleButtons) {
                    btn.y = startY;
                    startY += btn.drawScreen(mouseX, mouseY, partialTicks);
                }
                length = startY + 5;
            }

            HUDRenderUtils.INSTANCE.endScissor();

            Gui.drawRect(0, 0, width, height, ColorUtil.dropDownColor);
            Gui.drawRect(0, startY, width, startY + 5, ColorUtil.dropDownColor);

            if (ClickGuiConfig.isDesign("New")) {
                Gui.drawRect(0, 0, 2, height,
                        ColorUtil.INSTANCE.getOutlineColor().getRGB());
                Gui.drawRect(0, startY, 2, startY + 5,
                        ColorUtil.INSTANCE.getOutlineColor().getRGB());
                FontUtil.INSTANCE.drawStringWithShadow(
                        title, 4.0,
                        height / 2.0 - FontUtil.INSTANCE.getFontHeight() / 2.0);
            } else if (ClickGuiConfig.isDesign("JellyLike")) {
                Gui.drawRect(4, 2, 5, height - 2, ColorUtil.jellyPanelColor);
                Gui.drawRect(width - 4, 2, width - 5, height - 2, ColorUtil.jellyPanelColor);
                FontUtil.INSTANCE.drawTotalCenteredStringWithShadow(
                        title, width / 2.0, height / 2.0);
            }

            GlStateManager.popMatrix();
        }

        boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
            if (isHovered(mouseX, mouseY)) {
                if (mouseButton == 0) {
                    dragOffsetX = x - mouseX;
                    dragOffsetY = y - mouseY;
                    dragging = true;
                    return true;
                } else if (mouseButton == 1) {
                    extended = !extended;
                    return true;
                }
            } else if (isMouseOverExtended(mouseX, mouseY)) {
                for (int i = moduleButtons.size() - 1; i >= 0; i--) {
                    if (moduleButtons.get(i).mouseClicked(mouseX, mouseY, mouseButton))
                        return true;
                }
            }
            return false;
        }

        void mouseReleased(int mouseX, int mouseY, int state) {
            if (state == 0) {
                dragging = false;
            }
            if (extended) {
                for (int i = moduleButtons.size() - 1; i >= 0; i--) {
                    moduleButtons.get(i).mouseReleased(mouseX, mouseY, state);
                }
            }
        }

        boolean keyTyped(char typedChar, int keyCode) {
            if (extended) {
                for (int i = moduleButtons.size() - 1; i >= 0; i--) {
                    if (moduleButtons.get(i).keyTyped(typedChar, keyCode))
                        return true;
                }
            }
            return false;
        }

        boolean scroll(int amount, int mouseX, int mouseY) {
            if (!extended)
                return false;
            if (!isMouseOverExtended(mouseX, mouseY))
                return false;
            int diff = Math.min(-amount * SCROLL_DISTANCE, length - height - 16);
            int realDiff = Math.max(scrollOffset + diff, 0) - scrollOffset;
            length -= realDiff;
            scrollOffset += realDiff;
            return true;
        }

        void onGuiClosed() {
            for (PanelModuleButton btn : moduleButtons) {
                btn.onGuiClosed();
            }
        }

        private boolean isHovered(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + width
                    && mouseY >= y && mouseY <= y + height;
        }

        private boolean isMouseOverExtended(int mouseX, int mouseY) {
            if (!extended)
                return false;
            return mouseX >= x && mouseX <= x + width
                    && mouseY >= y && mouseY <= y + length;
        }
    }

    // Inner: PanelModuleButton — module toggle + settings elements
    static class PanelModuleButton {
        private final GuiModule module;
        private final DraggablePanel panel;
        private final KeybindListenerUtil keybindUtil;
        private final List<GuiElement> menuElements = new ArrayList<>();

        int x;
        int y;
        final int width;
        final int height;
        boolean extended;

        PanelModuleButton(GuiModule module, DraggablePanel panel) {
            this.module = module;
            this.panel = panel;
            this.width = panel.width;
            this.height = Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT + 2;
            this.keybindUtil = new KeybindListenerUtil(module);
            buildElements();
        }

        private void buildElements() {
            menuElements.clear();
            for (ModuleSetting<?> setting : module.getSettings()) {
                if (!setting.shouldBeVisible())
                    continue;
                GuiElement element = createElementFor(setting);
                if (element != null) {
                    menuElements.add(element);
                }
            }
        }

        private GuiElement createElementFor(ModuleSetting<?> setting) {
            ModuleButton stub = createStub();
            switch (setting.getType()) {
                case BOOLEAN:
                    return new BooleanElement(stub, (BooleanModuleSetting) setting);
                case NUMBER:
                    return new SliderElement(stub, (NumberModuleSetting) setting);
                case ENUM:
                    return new SelectorElement(stub, (EnumModuleSetting) setting);
                case STRING:
                    return new TextFieldElement(stub, (StringModuleSetting) setting);
                case COLOR:
                    return new ColorElement(stub, (ColorModuleSetting) setting);
                case ACTION:
                    return new ActionElement(stub, (ActionModuleSetting) setting);
                case KEY_BIND:
                    return new KeyBindElement(stub, (ModuleSetting<Integer>) setting);
                case HUD:
                    return new HudElement(stub, (BooleanModuleSetting) setting);
                default:
                    return null;
            }
        }

        private ModuleButton stubInstance;

        private ModuleButton createStub() {
            if (stubInstance == null) {
                stubInstance = new PanelModuleButtonStub(module, panel, this);
            }
            return stubInstance;
        }

        int drawScreen(int mouseX, int mouseY, float partialTicks) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, 0.0f);

            Gui.drawRect(0, 0, width, height + 1, ColorUtil.moduleButtonColor);

            if (ClickGuiConfig.isDesign("New")) {
                Gui.drawRect(0, 0, 2, height + 1,
                        ColorUtil.INSTANCE.getOutlineColor().getRGB());
            }

            if (module.isEnabled()) {
                Gui.drawRect(0, 0, width, height + 1,
                        ColorUtil.INSTANCE.getOutlineColor().getRGB());
            }

            if (isButtonHovered(mouseX, mouseY)) {
                if (module.isEnabled()) {
                    Gui.drawRect(0, 0, width, height + 1, 0x55111111);
                } else {
                    Gui.drawRect(0, 0, width, height + 1, ColorUtil.INSTANCE.getHoverColor());
                }
            }

            FontUtil.INSTANCE.drawTotalCenteredStringWithShadow(
                    module.getName(), width / 2.0, 1 + height / 2.0);

            String keyDisplay = keybindUtil.getKeyDisplayName();
            if (!"NONE".equals(keyDisplay)) {
                FontUtil.INSTANCE.drawStringWithShadow(
                        "[" + keyDisplay + "]",
                        width - FontUtil.INSTANCE.getStringWidth(
                                "[" + keyDisplay + "]") - 2.0,
                        1 + height / 2.0 - FontUtil.INSTANCE.getFontHeight() / 2.0);
            }

            int offs = height + 1;
            if (extended && !menuElements.isEmpty()) {
                for (GuiElement element : menuElements) {
                    element.setY(offs);
                    element.update();
                    offs += (int) element.draw();
                }
            }

            GlStateManager.popMatrix();
            return offs;
        }

        boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
            if (keybindUtil.handleMouseClick(mouseButton)) {
                return true;
            }

            if (isButtonHovered(mouseX, mouseY)) {
                if (keybindUtil.shouldStartListening(mouseButton, GuiScreen.isShiftKeyDown())) {
                    return true;
                }

                if (mouseButton == 0) {
                    module.toggle();
                    return true;
                } else if (mouseButton == 1) {
                    if (!menuElements.isEmpty()) {
                        extended = !extended;
                        if (!extended) {
                            for (GuiElement e : menuElements) {
                                e.setListening(false);
                            }
                        }
                    }
                    return true;
                }
            } else if (isMouseUnderButton(mouseX, mouseY)) {
                for (int i = menuElements.size() - 1; i >= 0; i--) {
                    if (menuElements.get(i).mouseClicked(mouseButton)) {
                        buildElements();
                        return true;
                    }
                }
            }
            return false;
        }

        void mouseReleased(int mouseX, int mouseY, int state) {
            if (extended) {
                for (int i = menuElements.size() - 1; i >= 0; i--) {
                    menuElements.get(i).mouseReleased(state);
                }
            }
        }

        boolean keyTyped(char typedChar, int keyCode) {
            if (keybindUtil.handleKeyTyped(keyCode)) {
                return true;
            }
            if (extended) {
                for (int i = menuElements.size() - 1; i >= 0; i--) {
                    if (menuElements.get(i).keyTyped(typedChar, keyCode))
                        return true;
                }
            }
            return false;
        }

        void onGuiClosed() {
            for (GuiElement e : menuElements) {
                e.onGuiClosed();
            }
        }

        private boolean isButtonHovered(int mouseX, int mouseY) {
            int xa = x + panel.x;
            int ya = y + panel.y;
            return mouseX >= xa && mouseX <= xa + width
                    && mouseY >= ya && mouseY <= ya + height;
        }

        private boolean isMouseUnderButton(int mouseX, int mouseY) {
            if (!extended)
                return false;
            int xa = x + panel.x;
            int ya = y + panel.y;
            return mouseX >= xa && mouseX <= xa + width
                    && mouseY > ya + height;
        }
    }

    // Inner: PanelModuleButtonStub — bridges panel elements to GuiElement's parent
    static class PanelModuleButtonStub extends ModuleButton {
        private final DraggablePanel draggablePanel;
        private final PanelModuleButton parentButton;

        PanelModuleButtonStub(GuiModule module, DraggablePanel panel, PanelModuleButton parentButton) {
            super(module, new StubCategoryPanel(panel, parentButton));
            this.draggablePanel = panel;
            this.parentButton = parentButton;
        }

        @Override
        public double getXAbsolute() {
            return getX() + draggablePanel.x;
        }

        @Override
        public double getYAbsolute() {
            return getY() + draggablePanel.y + parentButton.y;
        }
    }

    // Inner: StubCategoryPanel — minimal CategoryPanel for panel-mode
    static class StubCategoryPanel extends CategoryPanel {
        private final DraggablePanel draggablePanel;
        private final PanelModuleButton parentButton;

        StubCategoryPanel(DraggablePanel panel, PanelModuleButton parentButton) {
            super(panel.category, panel.width, 4000.0);
            this.draggablePanel = panel;
            this.parentButton = parentButton;
        }

        @Override
        public double getX() {
            return draggablePanel.x;
        }

        @Override
        public double getY() {
            return draggablePanel.y + parentButton.y;
        }

        @Override
        public boolean isHovered() {
            return true;
        }
    }
}
