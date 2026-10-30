package coffee.axle.suim.feature.clickgui;

import coffee.axle.suim.clickgui.ClickGuiAutosaveMode;
import coffee.axle.suim.clickgui.ClickGuiMode;
import coffee.axle.suim.clickgui.ClickGuiModeRegistry;
import coffee.axle.suim.clickgui.mode.flopper.PanelClickGuiScreen;
import coffee.axle.suim.clickgui.module.GuiModule;
import coffee.axle.suim.clickgui.settings.ModuleSetting;
import coffee.axle.suim.clickgui.settings.SettingVisibility;
import coffee.axle.suim.clickgui.settings.impl.*;
import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.hooks.ModulePropertyManager;
import coffee.axle.suim.hooks.MyauModuleManager;
import coffee.axle.suim.property.Property;
import coffee.axle.suim.property.Setting;
import coffee.axle.suim.util.HudUtils;
import coffee.axle.suim.util.MyauLogger;

import net.minecraft.client.Minecraft;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ClickGUI feature - registers as a Myau module and manages
 * all GUI appearance/behavior settings via Myau properties.
 *
 * Opens/closes via: .t clickgui, .bind clickgui KEY, escape to close.
 * Settings accessible via: .clickgui
 */
public class ClickGui extends Feature {

    private boolean closingGui = false;

    @Setting(name = "name") private String clientName = "Project SUIM";
    @Setting private String customPrefix = "\u00A70\u00A7l[\u00A74\u00A7lProject SUIM\u00A70\u00A7l]\u00A7r";
    @Setting private String color = toHex(getDefaultHudColor());
    @Setting private boolean blur = false;
    @Setting private boolean customFont = false;
    @Setting private boolean showUsageInfo = true;

    // option lists come from the registries, so these stay runtime properties
    private Property<Integer> guiModeProp;
    private Property<Integer> designProp;
    private Property<Integer> prefixStyleProp;
    private Property<Integer> autosaveProp;

    public ClickGui() {
        super("ClickGUI", GuiCategory.SETTINGS);
    }

    @Override
    protected void init() throws Exception {
        ClickGuiModeRegistry.registerDefaults();

        guiModeProp = Property.integer("click-gui", 0, 0, ClickGuiModeRegistry.getModeIds().length - 1)
                .place(creator, manager, properties, getModuleInstance(), null);
        designProp = Property.integer("design", 1, 0, 1)
                .place(creator, manager, properties, getModuleInstance(), null);

        prefixStyleProp = Property.integer("prefix-style", 0, 0, 2)
                .place(creator, manager, properties, getModuleInstance(), null);
        autosaveProp = Property.integer("autosave", 0, 0, 2)
                .place(creator, manager, properties, getModuleInstance(), null);

        ArrayList<String> cmdNames = new ArrayList<>();
        cmdNames.add("gui");
        creator.registerCommand(cmdNames, this::handleGuiCommand);

        HudUtils.getInstance().initialize();
        MyauLogger.info("ClickGui initialized");
    }

    @Override
    protected void onEnable() {
        openClickGui();
    }

    @Override
    protected void onDisable() {
        if (!closingGui) {
            closeGuiScreen();
        }
    }

    @Override
    public GuiModule buildGuiModule(
            MyauModuleManager mgr,
            ModulePropertyManager propMgr) {
        List<ModuleSetting<?>> settings = new ArrayList<>();

        settings.add(new BooleanModuleSetting(
                "Blur", "Toggles background blur",
                setting("blur").handle(), mgr, propMgr));;

        settings.add(new BooleanModuleSetting(
                "Custom Font", "Use custom font rendering",
                setting("custom-font").handle(), mgr, propMgr));;

        settings.add(new BooleanModuleSetting(
                "Usage Information", "Show GUI usage overlay",
                setting("show-usage-info").handle(), mgr, propMgr,
                SettingVisibility.ADVANCED_ONLY));

        settings.add(new EnumModuleSetting(
                "ClickGui", "GUI style",
                guiModeProp.handle(), mgr, propMgr,
                Arrays.asList(ClickGuiModeRegistry.getModeIds())));

        settings.add(new EnumModuleSetting(
                "Design", "Design theme",
                designProp.handle(), mgr, propMgr,
                Arrays.asList("JellyLike", "New"))
                .withVisibilityCondition(this::isDesignSupported));

        settings.add(new StringModuleSetting(
                "Name", "Client name shown in GUI",
                setting("name").handle(), mgr, propMgr, 15, "Client Name"));;

        settings.add(new EnumModuleSetting(
                "Prefix Style", "Chat prefix style",
                prefixStyleProp.handle(), mgr, propMgr,
                Arrays.asList("Long", "Short", "Custom")));

        settings.add(new StringModuleSetting(
                "Custom Prefix", "Custom chat prefix",
                setting("custom-prefix").handle(), mgr, propMgr, 40, "Prefix")
                .withVisibilityCondition(() -> getPrefixStyleIndex() == 2));

        settings.add(new ColorModuleSetting(
                "Color", "GUI accent color",
                setting("color").handle(), mgr, propMgr, false, false));;

        settings.add(new EnumModuleSetting(
                "Autosave", "Config autosave behavior",
                autosaveProp.handle(), mgr, propMgr,
                Arrays.asList("GUI", "GLOBAL", "NONE")));

        return new GuiModule(getName(), getGuiCategory(), this,
                getModuleInstance(), mgr, settings);
    }

    private void handleGuiCommand(ArrayList<String> args) {
        if (args == null || args.size() <= 1) {
            openClickGui();
            return;
        }
        manager.sendMessage(
                "&7Use .clickgui <setting> <value> to change settings");
    }


    private void closeGuiScreen() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof coffee.axle.suim.clickgui.mode.cga.ClickGuiScreen
                || mc.currentScreen instanceof PanelClickGuiScreen
                || mc.currentScreen instanceof coffee.axle.suim.clickgui.mode.b3.B3ClickGuiScreen) {
            mc.displayGuiScreen(null);
        }
    }

    private void openClickGui() {
        ensureModuleEnabled();
        int modeIndex = getGuiModeIndex();
        ClickGuiMode mode = ClickGuiModeRegistry.getMode(modeIndex);
        if (mode != null) {
            mode.open();
        }
    }

    public boolean isBlurEnabled() {
        return blur;
    }

    public boolean isCustomFontEnabled() {
        return customFont;
    }

    public boolean isShowUsageInfo() {
        return showUsageInfo;
    }

    public int getGuiModeIndex() {
        return guiModeProp.get();
    }

    public int getDesignIndex() {
        return designProp.get();
    }

    public String getClientName() {
        return clientName;
    }

    public int getPrefixStyleIndex() {
        return prefixStyleProp.get();
    }

    public String getCustomPrefix() {
        return customPrefix;
    }

    public Color getGuiColor() {
        String hex = color;
        return parseHex(hex);
    }

    public void setGuiColor(Color color) {
        String hex = String.format("%02X%02X%02X%02X",
                color.getRed(), color.getGreen(), color.getBlue(),
                color.getAlpha());
        setSaved("color", hex);
    }

    public ClickGuiAutosaveMode getAutosaveMode() {
        int idx = autosaveProp.get();
        if (idx == 1) {
            return ClickGuiAutosaveMode.GLOBAL;
        }
        if (idx == 2) {
            return ClickGuiAutosaveMode.NONE;
        }
        return ClickGuiAutosaveMode.GUI;
    }

    public void onGuiClosed() {
        closingGui = true;
        try {
            handleAutosave(false);
            disableModuleIfOpen();
        } finally {
            closingGui = false;
        }
    }

    public void onSettingChanged() {
        handleAutosave(true);
    }

    private void handleAutosave(boolean immediate) {
        ClickGuiAutosaveMode mode = getAutosaveMode();
        if (mode == ClickGuiAutosaveMode.NONE) {
            return;
        }
        if (mode == ClickGuiAutosaveMode.GUI && immediate) {
            return;
        }
        try {
            manager.saveCurrentConfig();
        } catch (Exception ignored) {
        }
    }

    private void ensureModuleEnabled() {
        try {
            if (getModuleInstance() != null && !isEnabled()) {
                manager.setModuleEnabled(getModuleInstance(), true);
            }
        } catch (Exception ignored) {
        }
    }

    private void disableModuleIfOpen() {
        try {
            if (getModuleInstance() != null && isEnabled()) {
                manager.setModuleEnabled(getModuleInstance(), false);
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isDesignSupported() {
        ClickGuiMode mode = ClickGuiModeRegistry.getMode(getGuiModeIndex());
        return mode != null && mode.supportsDesign();
    }

    private static Color getDefaultHudColor() {
        return HudUtils.getInstance().getHudColor(new Color(80, 200, 220));
    }

    private static String toHex(Color color) {
        return String.format("%02X%02X%02X%02X",
                color.getRed(), color.getGreen(), color.getBlue(),
                color.getAlpha());
    }

    private static ClickGui instance;

    public static ClickGui getInstance() {
        return instance;
    }

    {
        instance = this;
    }

    private static Color parseHex(String hex) {
        try {
            if (hex == null || hex.isEmpty()) {
                return new Color(255, 137, 213);
            }
            hex = hex.replace("#", "");
            if (hex.length() == 6)
                hex = hex + "FF";
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            int a = Integer.parseInt(hex.substring(6, 8), 16);
            return new Color(r, g, b, a);
        } catch (Exception e) {
            return new Color(255, 137, 213);
        }
    }
}
