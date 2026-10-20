package coffee.axle.suim.clickgui.mode.b3;

import coffee.axle.suim.clickgui.ClickGuiCloseHandler;
import coffee.axle.suim.clickgui.ClickGuiConfig;
import coffee.axle.suim.clickgui.module.GuiModule;
import coffee.axle.suim.clickgui.module.GuiModuleManager;
import coffee.axle.suim.clickgui.render.HUDRenderUtils;
import coffee.axle.suim.clickgui.settings.ModuleSetting;
import coffee.axle.suim.clickgui.settings.SettingType;
import coffee.axle.suim.clickgui.settings.impl.BooleanModuleSetting;
import coffee.axle.suim.clickgui.settings.impl.ColorModuleSetting;
import coffee.axle.suim.clickgui.settings.impl.EnumModuleSetting;
import coffee.axle.suim.clickgui.settings.impl.NumberModuleSetting;
import coffee.axle.suim.feature.GuiCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel ClickGUI in the Raven B3 style. Port of the B4 ClickGUI script
 * (jarstore/raven-scripts/ClickGUI.java) onto SUIM's GuiModule model.
 * Accent color inherits the HUD color like AimAssist's show-target.
 */
public class B3ClickGuiScreen extends GuiScreen {

    private static final int PANEL_WIDTH = 132;
    private static final int HEADER_HEIGHT = 18;
    private static final int MODULE_HEIGHT = 18;
    private static final int SETTING_HEIGHT = 16;

    private static final String[] MOVEMENT_KEYS =
            {"forward", "back", "left", "right", "jump", "sneak", "sprint"};

    private static B3ClickGuiScreen instance;

    // in-memory layout so panels keep position while the game runs
    private static final Map<String, float[]> LAYOUT = new HashMap<>();
    private static final Map<String, Boolean> expanded = new HashMap<>();
    private static final List<Panel> panels = new ArrayList<>();
    private static final List<Row> rows = new ArrayList<>();

    private static Panel dragging;
    private static Row sliding;
    private static GuiModule bindingModule;
    private static ModuleSetting<?> bindingSetting;
    private static String query = "";
    private static boolean searching;
    private static boolean movementKeysUpdated;
    private int screenWidth, screenHeight;
    private final Minecraft mc = Minecraft.getMinecraft();

    public static void open() {
        // deferred like the other modes - opening mid-input-processing gets the
        // screen closed again by myau's own input handling
        if (instance != null) {
            coffee.axle.suim.ProjectSUIM.setDisplay(instance);
            return;
        }
        coffee.axle.suim.ProjectSUIM.setDisplay(new B3ClickGuiScreen());
    }

    private static class Panel {
        final GuiCategory category;
        float x, y, scroll, targetScroll, contentHeight, viewport;
        boolean open = true;

        Panel(GuiCategory category, float x, float y) {
            this.category = category;
            this.x = x;
            this.y = y;
        }

        String name() { return category.getDisplayName(); }
    }

    private static class Row {
        final Panel panel;
        final GuiModule module;
        final ModuleSetting<?> setting;
        final String kind;
        float y, height;
        double min, max, step;
        boolean inline;
        int valueColor = 0xFFE3E3E3;
        final List<String> labelLines = new ArrayList<>();
        final List<String> valueLines = new ArrayList<>();

        Row(Panel panel, GuiModule module, ModuleSetting<?> setting,
            String kind, float y, float height) {
            this.panel = panel;
            this.module = module;
            this.setting = setting;
            this.kind = kind;
            this.y = y;
            this.height = height;
        }
    }

    public B3ClickGuiScreen() {
        instance = this;
        buildPanels();
    }

    private void buildPanels() {
        panels.clear();
        ScaledResolution sr = new ScaledResolution(mc);
        screenWidth = sr.getScaledWidth();
        screenHeight = sr.getScaledHeight();

        GuiCategory[] cats = GuiCategory.values();
        int columns = Math.max(1, Math.min(cats.length,
                (screenWidth - 16) / (PANEL_WIDTH + 10)));
        int bands = (cats.length + columns - 1) / columns;
        int bandHeight = Math.max(60, (screenHeight - 62) / bands);

        for (int i = 0; i < cats.length; i++) {
            float x = 10 + (i % columns) * (PANEL_WIDTH + 10);
            float y = 32 + (i / columns) * bandHeight;
            float[] saved = LAYOUT.get(cats[i].name());
            Panel p = new Panel(cats[i], saved != null ? saved[0] : x,
                    saved != null ? saved[1] : y);
            if (saved != null && saved.length > 2) p.open = saved[2] != 0f;
            panels.add(p);
        }
        clampPanels();
    }

    private void clampPanels() {
        for (Panel panel : panels) {
            panel.x = clamp(panel.x, 2, Math.max(2, screenWidth - PANEL_WIDTH - 2));
            panel.y = clamp(panel.y, 30,
                    Math.max(30, screenHeight - HEADER_HEIGHT - 25));
        }
    }

    private void saveLayout() {
        for (Panel panel : panels) {
            LAYOUT.put(panel.category.name(),
                    new float[] {panel.x, panel.y, panel.open ? 1f : 0f});
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean inside(float x, float y, float left, float top,
                                  float width, float height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private int accent() {
        return HudColorHelper.rgb();
    }

    @Override
    public void initGui() {
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
    public void updateScreen() {
        updateMovementKeys();
    }

    private boolean physicalKey(String name) {
        KeyBinding bind = mc.gameSettings.keyBindForward;
        for (KeyBinding kb : new KeyBinding[] {
                mc.gameSettings.keyBindForward, mc.gameSettings.keyBindBack,
                mc.gameSettings.keyBindLeft, mc.gameSettings.keyBindRight,
                mc.gameSettings.keyBindJump, mc.gameSettings.keyBindSneak,
                mc.gameSettings.keyBindSprint}) {
            if (kb.getKeyDescription().equals(name)) {
                bind = kb;
                break;
            }
        }
        int code = bind.getKeyCode();
        if (code > 0) return Keyboard.isKeyDown(code);
        if (code < 0) return Mouse.isButtonDown(code + 100);
        return false;
    }

    private void updateMovementKeys() {
        boolean moving = !searching && bindingModule == null && bindingSetting == null;
        for (String key : MOVEMENT_KEYS) {
            KeyBinding kb = keyFor(key);
            if (kb == null) continue;
            boolean down = moving && physicalKey(key);
            if ("sprint".equals(key) && moving) {
                down = down || mc.gameSettings.keyBindSprint.isKeyDown()
                        || isSprintModuleEnabled();
            }
            KeyBinding.setKeyBindState(kb.getKeyCode(), down);
        }
        movementKeysUpdated = true;
    }

    private boolean isSprintModuleEnabled() {
        GuiModuleManager mgr = GuiModuleManager.getInstance();
        if (mgr == null) return false;
        for (GuiModule module : mgr.getModules()) {
            if ("Sprint".equalsIgnoreCase(module.getName())) {
                return module.isEnabled();
            }
        }
        return false;
    }

    private KeyBinding keyFor(String name) {
        for (KeyBinding kb : new KeyBinding[] {
                mc.gameSettings.keyBindForward, mc.gameSettings.keyBindBack,
                mc.gameSettings.keyBindLeft, mc.gameSettings.keyBindRight,
                mc.gameSettings.keyBindJump, mc.gameSettings.keyBindSneak,
                mc.gameSettings.keyBindSprint}) {
            if (kb.getKeyDescription().equals(name)) return kb;
        }
        return null;
    }

    private void restoreMovementKeys() {
        if (!movementKeysUpdated) return;
        for (String key : MOVEMENT_KEYS) {
            KeyBinding kb = keyFor(key);
            if (kb == null || kb.getKeyCode() <= 0) continue;
            KeyBinding.setKeyBindState(kb.getKeyCode(), physicalKey(key));
        }
        movementKeysUpdated = false;
    }

    private void clearInteraction() {
        dragging = null;
        sliding = null;
        bindingModule = null;
        bindingSetting = null;
        searching = false;
    }

    private void closeGui() {
        saveLayout();
        clearInteraction();
        restoreMovementKeys();
        mc.displayGuiScreen(null);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ScaledResolution sr = new ScaledResolution(mc);
        screenWidth = sr.getScaledWidth();
        screenHeight = sr.getScaledHeight();

        if (dragging != null) {
            if (Mouse.isButtonDown(0)) {
                dragging.x = mouseX - grabX;
                dragging.y = mouseY - grabY;
            } else {
                dragging = null;
                saveLayout();
            }
        }
        if (sliding != null) {
            updateSlider(mouseX);
            if (!Mouse.isButtonDown(0)) sliding = null;
        }
        clampPanels();
        layoutRows();

        Gui.drawRect(0, 0, screenWidth, screenHeight, 0x64000000);
        Gui.drawRect(8, 7, Math.min(screenWidth - 8, 184), 23, 0xB8000000);
        String search = query.isEmpty() && !searching
                ? "Search modules"
                : query + (searching && System.currentTimeMillis() % 1000 < 500 ? "_" : "");
        label(fit(search, 164), 12, 11,
                searching ? 0xFFFFFFFF : 0xFFBBBBBB);

        for (Panel panel : panels) {
            drawPanel(panel, mouseX, mouseY);
        }

        if (bindingModule != null || bindingSetting != null) {
            String moduleName = bindingModule != null ? bindingModule.getName() : "";
            String prompt = "Bind " + moduleName
                    + ": press a key or mouse button; ESC cancels, DEL clears";
            Gui.drawRect(0, screenHeight - 35, screenWidth, screenHeight, 0xD0000000);
            label(fit(prompt, screenWidth - 16), 8, screenHeight - 29, 0xFFFFFFFF);
        }
    }

    private float grabX, grabY;

    private void label(String text, float x, float y, int color) {
        mc.fontRendererObj.drawStringWithShadow(text, Math.round(x),
                Math.round(y), color);
    }

    private String fit(String text, float available) {
        if (mc.fontRendererObj.getStringWidth(text) <= available) return text;
        while (text.length() > 0 && mc.fontRendererObj
                .getStringWidth(text + "..") > available) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "..";
    }

    private List<String> wrap(String text, float available) {
        List<String> lines = new ArrayList<>();
        while (!text.isEmpty()) {
            int end = text.length();
            while (end > 1 && mc.fontRendererObj
                    .getStringWidth(text.substring(0, end)) > available) end--;
            if (end < text.length()) {
                int space = text.lastIndexOf(' ', end);
                if (space > 0) end = space;
            }
            lines.add(text.substring(0, end));
            text = text.substring(end).trim();
        }
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    private void layoutRows() {
        rows.clear();
        GuiModuleManager mgr = GuiModuleManager.getInstance();
        if (mgr == null) return;

        for (Panel panel : panels) {
            float offset = 0;
            List<Row> content = new ArrayList<>();
            for (GuiModule module : mgr.getModules()) {
                if (module.getCategory() != panel.category) continue;
                if (!matches(module.getName())) continue;

                Row moduleRow = new Row(panel, module, null, "module",
                        offset, MODULE_HEIGHT);
                prepareText(moduleRow);
                content.add(moduleRow);
                offset += moduleRow.height;

                if (!Boolean.TRUE.equals(expanded.get(module.getName()))) continue;
                for (ModuleSetting<?> setting : module.getSettings()) {
                    if (!setting.shouldBeVisible()) continue;
                    Row row = buildSettingRow(panel, module, setting, offset);
                    if (row == null) continue;
                    prepareText(row);
                    content.add(row);
                    offset += row.height;
                }
            }

            panel.contentHeight = offset;
            // combat/render hold the most modules, give them the full height
            boolean big = panel.category == GuiCategory.COMBAT
                    || panel.category == GuiCategory.RENDER;
            int limit = big ? Math.max(30, screenHeight - (int) panel.y
                    - HEADER_HEIGHT - 25) : 300;
            panel.viewport = panel.open ? Math.max(0, Math.min(Math.max(offset, 16),
                    Math.min(limit, screenHeight - (int) panel.y - HEADER_HEIGHT - 24))) : 0;
            float max = Math.max(0, offset - panel.viewport);
            panel.targetScroll = clamp(panel.targetScroll, 0, max);
            panel.scroll = clamp(panel.scroll, 0, max);
            for (Row row : content) {
                row.y += panel.y + HEADER_HEIGHT - panel.scroll;
                rows.add(row);
            }
        }
    }

    private Row buildSettingRow(Panel panel, GuiModule module,
                                ModuleSetting<?> setting, float offset) {
        SettingType type = setting.getType();
        float height = SETTING_HEIGHT;
        String kind;
        switch (type) {
            case BOOLEAN:
                kind = "boolean";
                break;
            case ENUM:
                kind = "mode";
                break;
            case NUMBER:
                kind = "number";
                height = 26;
                break;
            case COLOR:
                kind = "color";
                height = 46;
                break;
            case KEY_BIND:
                kind = "key";
                break;
            default:
                return null;
        }
        Row row = new Row(panel, module, setting, kind, offset, height);
        if ("number".equals(kind)) {
            NumberModuleSetting num = (NumberModuleSetting) setting;
            row.min = num.getMin();
            row.max = num.getMax();
            row.step = num.getIncrement();
        }
        return row;
    }

    private boolean matches(String name) {
        return query.isEmpty() || name.toLowerCase().contains(query.toLowerCase());
    }

    private void prepareText(Row row) {
        if ("module".equals(row.kind)) {
            row.labelLines.clear();
            row.labelLines.addAll(wrap(row.module.getName(), PANEL_WIDTH - 10));
            row.height = Math.max(MODULE_HEIGHT, row.labelLines.size() * 11 + 7);
            return;
        }

        String name = row.setting == null ? "" : row.setting.getName();
        String value = "";
        if ("boolean".equals(row.kind)) {
            boolean on = ((BooleanModuleSetting) row.setting).getValue();
            value = on ? "true" : "false";
            row.valueColor = on ? 0xFF55FF55 : 0xFFFF5555;
        } else if ("mode".equals(row.kind)) {
            value = ((EnumModuleSetting) row.setting).getValue();
            row.valueColor = 0xFF8CB4FF;
        } else if ("number".equals(row.kind)) {
            NumberModuleSetting num = (NumberModuleSetting) row.setting;
            value = number(num.getValue()) + ("%".equals(num.getUnit()) ? "%" : "");
            row.valueColor = 0xFF55FFFF;
        } else if ("color".equals(row.kind)) {
            value = "#" + String.format("%06X",
                    ((ColorModuleSetting) row.setting).getValue().getRGB() & 0xFFFFFF);
        } else if ("key".equals(row.kind)) {
            @SuppressWarnings("unchecked")
            ModuleSetting<Integer> key = (ModuleSetting<Integer>) row.setting;
            int code = key.getValue();
            value = code == 0 ? "NONE"
                    : code < 0 ? "BUTTON" + (code + 100) : "key " + code;
        }

        if (bindingSetting == row.setting) {
            name = "Press a key...";
            value = "";
            row.valueColor = 0xFFFFFF55;
        }

        float available = PANEL_WIDTH - 8;
        String title = name + (value.isEmpty() ? "" : ": ");
        row.inline = mc.fontRendererObj.getStringWidth(title + value) <= available;
        row.labelLines.clear();
        row.valueLines.clear();
        if (row.inline) {
            row.labelLines.add(title);
            row.valueLines.add(value);
        } else {
            row.labelLines.addAll(wrap(title.trim(), available));
            row.valueLines.addAll(wrap(value, available));
        }
        int lines = row.inline ? 1 : row.labelLines.size() + row.valueLines.size();
        row.height = Math.max(row.height, lines * 11 + 5
                + ("number".equals(row.kind) ? 10 : "color".equals(row.kind) ? 30 : 0));
    }

    private String number(double value) {
        if (value == Math.rint(value)) return String.valueOf((long) value);
        String result = String.format(java.util.Locale.ROOT, "%.2f", value);
        while (result.endsWith("0")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private void drawPanel(Panel panel, int mouseX, int mouseY) {
        if (panel.open) {
            Gui.drawRect((int) panel.x - 1, (int) panel.y + HEADER_HEIGHT,
                    (int) panel.x + PANEL_WIDTH + 1,
                    (int) (panel.y + HEADER_HEIGHT + panel.viewport + 2), 0x68000000);
        }
        Gui.drawRect((int) panel.x - 2, (int) panel.y,
                (int) panel.x + PANEL_WIDTH + 2,
                (int) panel.y + HEADER_HEIGHT, 0xD8000000);
        label(panel.name(), panel.x + 3, panel.y + 4, 0xFFFFFFFF);
        label(panel.open ? "-" : "+", panel.x + PANEL_WIDTH - 11,
                panel.y + 4, 0xFFFFFFFF);

        if (!panel.open || panel.viewport <= 0) return;
        float top = panel.y + HEADER_HEIGHT;
        float bottom = top + panel.viewport;

        HUDRenderUtils.setupScissorAbsolute((int) panel.x, (int) top,
                (int) (panel.x + PANEL_WIDTH), (int) bottom);
        try {
            if (panel.contentHeight == 0) {
                label("No matches", panel.x + 5, top + 5, 0xFF888888);
            }
            for (Row row : rows) {
                if (row.panel != panel) continue;
                if (row.y + row.height <= top || row.y >= bottom) continue;
                drawRow(row, mouseX, mouseY);
            }
        } finally {
            HUDRenderUtils.disableScissor();
        }

        if (panel.contentHeight > panel.viewport) {
            float thumb = Math.max(8,
                    panel.viewport * panel.viewport / panel.contentHeight);
            float position = panel.scroll / (panel.contentHeight - panel.viewport)
                    * (panel.viewport - thumb);
            Gui.drawRect((int) (panel.x + PANEL_WIDTH - 2), (int) (top + position),
                    (int) (panel.x + PANEL_WIDTH), (int) (top + position + thumb),
                    0x60FFFFFF);
        }
    }

    private void drawRow(Row row, int mouseX, int mouseY) {
        Panel panel = row.panel;
        float left = panel.x + 4, right = panel.x + PANEL_WIDTH - 4;
        int tint = accent();
        boolean hover = inside(mouseX, mouseY, panel.x, row.y,
                PANEL_WIDTH, row.height);
        if (hover) {
            Gui.drawRect((int) panel.x, (int) row.y,
                    (int) (panel.x + PANEL_WIDTH),
                    (int) (row.y + row.height), 0x16FFFFFF);
        }

        if ("module".equals(row.kind)) {
            for (int i = 0; i < row.labelLines.size(); i++) {
                String text = row.labelLines.get(i);
                // enabled highlight inherits the HUD color
                label(text,
                        panel.x + (PANEL_WIDTH
                                - mc.fontRendererObj.getStringWidth(text)) / 2f,
                        row.y + 4 + i * 11,
                        row.module.isEnabled() ? tint : 0xFFAAAAAA);
            }
            return;
        }

        for (int i = 0; i < row.labelLines.size(); i++) {
            label(row.labelLines.get(i), left, row.y + 3 + i * 11, 0xFFE3E3E3);
        }
        for (int i = 0; i < row.valueLines.size(); i++) {
            float x = row.inline
                    ? left + mc.fontRendererObj.getStringWidth(row.labelLines.get(0))
                    : left;
            float y = row.y + 3
                    + (row.inline ? 0 : row.labelLines.size() + i) * 11;
            label(row.valueLines.get(i), x, y, row.valueColor);
        }

        if ("number".equals(row.kind)) {
            NumberModuleSetting num = (NumberModuleSetting) row.setting;
            double value = num.getValue();
            float fraction = row.max > row.min
                    ? (float) clamp((value - row.min) / (row.max - row.min), 0, 1) : 0;
            float y = row.y + row.height - 11;
            Gui.drawRect((int) left, (int) y, (int) right, (int) y + 5, 0xFF4A4A4A);
            Gui.drawRect((int) left, (int) y,
                    (int) (left + (right - left) * fraction), (int) y + 5, tint);
        } else if ("color".equals(row.kind)) {
            ColorModuleSetting color = (ColorModuleSetting) row.setting;
            float y = row.y + row.height - 30;
            float[] hsb = currentHsb(color);
            drawGradientRect((int) left, (int) y, (int) right, (int) (y + 6),
                    Color.HSBtoRGB(hsb[0], 0, hsb[2]) | 0xFF000000,
                    Color.HSBtoRGB(hsb[0], 1, hsb[2]) | 0xFF000000);
            drawGradientRect((int) left, (int) (y + 9), (int) right,
                    (int) (y + 15), 0xFF000000,
                    Color.HSBtoRGB(hsb[0], hsb[1], 1) | 0xFF000000);
            for (int i = 0; i < 6; i++) {
                float x1 = left + (right - left) * i / 6f;
                float x2 = left + (right - left) * (i + 1) / 6f;
                drawGradientRect((int) x1, (int) (y + 18), (int) x2,
                        (int) (y + 24),
                        Color.HSBtoRGB(i / 6f, 1, 1) | 0xFF000000,
                        Color.HSBtoRGB((i + 1) / 6f, 1, 1) | 0xFF000000);
            }
            for (int i = 0; i < 3; i++) {
                float x = left + (right - left) * hsb[i];
                Gui.drawRect((int) x - 1, (int) (y - 1 + i * 9), (int) x,
                        (int) (y + 7 + i * 9), 0xFFEEEEEE);
            }
        }
    }

    private float[] currentHsb(ColorModuleSetting setting) {
        Color c = setting.getValue();
        return Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
    }

    private void updateSlider(float mouseX) {
        Row row = sliding;
        if (row == null || row.setting == null) return;
        float fraction = clamp((mouseX - row.panel.x - 4) / (PANEL_WIDTH - 8), 0, 1);
        if ("number".equals(row.kind)) {
            NumberModuleSetting num = (NumberModuleSetting) row.setting;
            double value = row.min + fraction * (row.max - row.min);
            double step = row.step > 0 ? row.step : 0.01;
            value = row.min + Math.round((value - row.min) / step) * step;
            num.setValue(clamp(value, row.min, row.max));
        } else if (row.kind.startsWith("hsb")) {
            ColorModuleSetting color = (ColorModuleSetting) row.setting;
            int component = Integer.parseInt(row.kind.substring(3));
            float[] hsb = currentHsb(color);
            hsb[component] = fraction;
            color.setValue(new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2])));
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY,
                                  int clickedMouseButton, long timeSinceLastClick) {
        // slider dragging is handled in drawScreen via sliding state
    }

    private Panel panelAt(float mouseX, float mouseY) {
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel panel = panels.get(i);
            if (inside(mouseX, mouseY, panel.x - 2, panel.y, PANEL_WIDTH + 4,
                    HEADER_HEIGHT + (panel.open ? panel.viewport + 2 : 0))) {
                return panel;
            }
        }
        return null;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button)
            throws IOException {
        if (bindingModule != null && button >= 0) {
            bindingModule.setKeybind(button - 100);
            bindingModule = null;
            return;
        }
        if (bindingSetting != null && button >= 0) {
            @SuppressWarnings("unchecked")
            ModuleSetting<Integer> key = (ModuleSetting<Integer>) bindingSetting;
            key.setValue(button - 100);
            bindingSetting = null;
            return;
        }
        if (button == 0) {
            searching = inside(mouseX, mouseY, 8, 7,
                    Math.min(screenWidth - 16, 176), 16);
        }

        Panel panel = panelAt(mouseX, mouseY);
        if (panel == null) return;
        panels.remove(panel);
        panels.add(panel);

        if (mouseY < panel.y + HEADER_HEIGHT) {
            if (button == 1 || (button == 0
                    && mouseX >= panel.x + PANEL_WIDTH - 16)) {
                panel.open = !panel.open;
                saveLayout();
            } else if (button == 0) {
                dragging = panel;
                grabX = mouseX - panel.x;
                grabY = mouseY - panel.y;
            }
            return;
        }
        if (!panel.open || mouseY >= panel.y + HEADER_HEIGHT + panel.viewport) {
            return;
        }

        for (Row row : rows) {
            if (row.panel != panel || !inside(mouseX, mouseY, panel.x, row.y,
                    PANEL_WIDTH, row.height)) continue;

            if ("module".equals(row.kind)) {
                if (button == 0) {
                    row.module.setEnabled(!row.module.isEnabled());
                } else if (button == 1) {
                    Boolean was = expanded.get(row.module.getName());
                    expanded.put(row.module.getName(), !Boolean.TRUE.equals(was));
                } else if (button == 2) {
                    bindingModule = row.module;
                }
            } else if ("boolean".equals(row.kind) && button == 0) {
                BooleanModuleSetting b = (BooleanModuleSetting) row.setting;
                b.setValue(!b.getValue());
            } else if ("mode".equals(row.kind) && (button == 0 || button == 1)) {
                EnumModuleSetting mode = (EnumModuleSetting) row.setting;
                List<String> options = mode.getOptions();
                if (options == null || options.isEmpty()) continue;
                int index = options.indexOf(mode.getValue());
                index += button == 0 ? 1 : -1;
                if (index >= options.size()) index = 0;
                if (index < 0) index = options.size() - 1;
                mode.setValue(options.get(index));
            } else if ("number".equals(row.kind) && button == 0) {
                sliding = row;
                updateSlider(mouseX);
            } else if ("color".equals(row.kind) && button == 0
                    && mouseY >= row.y + row.height - 32) {
                int component = Math.max(0, Math.min(2,
                        (int) ((mouseY - (row.y + row.height - 32)) / 9)));
                Row hsbRow = new Row(panel, row.module, row.setting,
                        "hsb" + component, row.y, row.height);
                sliding = hsbRow;
                updateSlider(mouseX);
            } else if ("key".equals(row.kind) && button == 0) {
                bindingSetting = row.setting;
            }
            return;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0) {
            sliding = null;
            if (dragging != null) {
                dragging = null;
                saveLayout();
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        ScaledResolution sr = new ScaledResolution(mc);
        int mx = Mouse.getX() * sr.getScaledWidth() / mc.displayWidth;
        int my = sr.getScaledHeight()
                - Mouse.getY() * sr.getScaledHeight() / mc.displayHeight - 1;
        Panel panel = panelAt(mx, my);
        if (panel == null || !panel.open) return;
        float max = Math.max(0, panel.contentHeight - panel.viewport);
        panel.targetScroll = clamp(panel.targetScroll + (wheel > 0 ? -24 : 24), 0, max);
        panel.scroll = panel.targetScroll;
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) throws IOException {
        if (bindingModule != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                bindingModule.setKeybind(Keyboard.KEY_NONE);
                bindingModule = null;
            } else if (keyCode == Keyboard.KEY_DELETE
                    || keyCode == Keyboard.KEY_BACK) {
                bindingModule.setKeybind(Keyboard.KEY_NONE);
                bindingModule = null;
            } else if (keyCode != 0) {
                bindingModule.setKeybind(keyCode);
                bindingModule = null;
            }
            return;
        }
        if (bindingSetting != null) {
            @SuppressWarnings("unchecked")
            ModuleSetting<Integer> key = (ModuleSetting<Integer>) bindingSetting;
            if (keyCode == Keyboard.KEY_ESCAPE
                    || keyCode == Keyboard.KEY_DELETE
                    || keyCode == Keyboard.KEY_BACK) {
                key.setValue(0);
                bindingSetting = null;
            } else if (keyCode != 0) {
                key.setValue(keyCode);
                bindingSetting = null;
            }
            return;
        }

        if (keyCode == Keyboard.KEY_F && isCtrlKeyDown()) {
            searching = true;
            return;
        }
        if (searching) {
            if (keyCode == Keyboard.KEY_BACK && !query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
            } else if (keyCode == Keyboard.KEY_RETURN) {
                searching = false;
            } else if (typedChar >= 32 && typedChar <= 126
                    && query.length() < 48) {
                query += typedChar;
            }
            for (Panel panel : panels) {
                panel.scroll = 0;
                panel.targetScroll = 0;
            }
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            closeGui();
        }
    }

    @Override
    public void onGuiClosed() {
        saveLayout();
        clearInteraction();
        restoreMovementKeys();
        ClickGuiCloseHandler.onClose();
        instance = null;
    }

    private static class HudColorHelper {
        static int rgb() {
            Color color = coffee.axle.suim.util.HudUtils.getInstance().getHudColor();
            return color.getRGB() & 0x00FFFFFF;
        }
    }
}
