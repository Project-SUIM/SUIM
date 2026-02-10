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
import coffee.axle.suim.util.HudUtils;
import coffee.axle.suim.util.MyauLogger;

import net.minecraft.client.Minecraft;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ClickGUI feature — registers as a Myau module and manages
 * all GUI appearance/behavior settings via Myau properties.
 *
 * Opens/closes via: .t clickgui, .bind clickgui KEY, escape to close.
 * Settings accessible via: .clickgui
 */
public class ClickGui extends Feature {

    private Object moduleInstance;
    private boolean closingGui = false;

    private Object blurProp;
    private Object guiModeProp;
    private Object designProp;
    private Object customFontProp;
    private Object clientNameProp;
    private Object prefixStyleProp;
    private Object customPrefixProp;
    private Object colorProp;
    private Object showUsageInfoProp;
    private Object autosaveProp;

    @Override
    public String getName() {
        return "ClickGUI";
    }

    @Override
    public GuiCategory getGuiCategory() {
        return GuiCategory.SETTINGS;
    }

    @Override
    public boolean initialize() {
        try {
            moduleInstance = createModule();
            creator.injectModule(moduleInstance, ClickGui.class);

            ClickGuiModeRegistry.registerDefaults();

            blurProp = creator.createBooleanProperty("blur", false);
            customFontProp = creator.createBooleanProperty(
                    "custom-font", false);
            showUsageInfoProp = creator.createBooleanProperty(
                    "show-usage-info", true);

            String[] guiModes = ClickGuiModeRegistry.getModeIds();
            guiModeProp = creator.createEnumProperty(
                    "click-gui", 0, guiModes);

            String[] designs = { "JellyLike", "New" };
            designProp = creator.createEnumProperty("design", 1, designs);

            clientNameProp = creator.createStringProperty(
                    "name", "Project SUIM");

            String[] prefixStyles = { "Long", "Short", "Custom" };
            prefixStyleProp = creator.createEnumProperty(
                    "prefix-style", 0, prefixStyles);

            customPrefixProp = creator.createStringProperty("custom-prefix",
                    "\u00A70\u00A7l[\u00A74\u00A7lProject SUIM\u00A70\u00A7l]\u00A7r");

            HudUtils.getInstance().initialize();
            colorProp = creator.createStringProperty(
                    "color", toHex(getDefaultHudColor()));

            autosaveProp = creator.createEnumProperty(
                    "autosave", 0,
                    new String[] { "GUI", "GLOBAL", "NONE" });

            creator.registerProperties(moduleInstance,
                    blurProp, customFontProp,
                    showUsageInfoProp,
                    guiModeProp, designProp,
                    clientNameProp, prefixStyleProp, customPrefixProp,
                    colorProp, autosaveProp);

            ArrayList<String> cmdNames = new ArrayList<>();
            cmdNames.add("gui");
            creator.registerCommand(cmdNames, this::handleGuiCommand);

            manager.registerModuleCallbacks(
                    moduleInstance,
                    this::onMyauEnable,
                    this::onMyauDisable);

            manager.reloadModuleCommand();

            MyauLogger.info("ClickGui initialized");
            return true;
        } catch (Exception e) {
            MyauLogger.error("ClickGui:init", e);
            return false;
        }
    }

    @Override
    public GuiModule buildGuiModule(
            MyauModuleManager mgr,
            ModulePropertyManager propMgr) {
        List<ModuleSetting<?>> settings = new ArrayList<>();

        settings.add(new BooleanModuleSetting(
                "Blur", "Toggles background blur",
                blurProp, mgr, propMgr));

        settings.add(new BooleanModuleSetting(
                "Custom Font", "Use custom font rendering",
                customFontProp, mgr, propMgr));

        settings.add(new BooleanModuleSetting(
                "Usage Information", "Show GUI usage overlay",
                showUsageInfoProp, mgr, propMgr,
                SettingVisibility.ADVANCED_ONLY));

        settings.add(new EnumModuleSetting(
                "ClickGui", "GUI style",
                guiModeProp, mgr, propMgr,
                Arrays.asList(ClickGuiModeRegistry.getModeIds())));

        settings.add(new EnumModuleSetting(
                "Design", "Design theme",
                designProp, mgr, propMgr,
                Arrays.asList("JellyLike", "New"))
                .withVisibilityCondition(this::isDesignSupported));

        settings.add(new StringModuleSetting(
                "Name", "Client name shown in GUI",
                clientNameProp, mgr, propMgr, 15, "Client Name"));

        settings.add(new EnumModuleSetting(
                "Prefix Style", "Chat prefix style",
                prefixStyleProp, mgr, propMgr,
                Arrays.asList("Long", "Short", "Custom")));

        settings.add(new StringModuleSetting(
                "Custom Prefix", "Custom chat prefix",
                customPrefixProp, mgr, propMgr, 40, "Prefix")
                .withVisibilityCondition(() -> getPrefixStyleIndex() == 2));

        settings.add(new ColorModuleSetting(
                "Color", "GUI accent color",
                colorProp, mgr, propMgr, false, false));

        settings.add(new EnumModuleSetting(
                "Autosave", "Config autosave behavior",
                autosaveProp, mgr, propMgr,
                Arrays.asList("GUI", "GLOBAL", "NONE")));

        return new GuiModule(getName(), getGuiCategory(), this,
                moduleInstance, mgr, settings);
    }

    private void handleGuiCommand(ArrayList<String> args) {
        if (args == null || args.size() <= 1) {
            openClickGui();
            return;
        }
        manager.sendMessage(
                "&7Use .clickgui <setting> <value> to change settings");
    }

    private void onMyauEnable() {
        openClickGui();
    }

    private void onMyauDisable() {
        if (!closingGui) {
            closeGuiScreen();
        }
    }

    private void closeGuiScreen() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof coffee.axle.suim.clickgui.mode.cga.ClickGuiScreen
                || mc.currentScreen instanceof PanelClickGuiScreen) {
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

    @Override
    public Object getModuleInstance() {
        return moduleInstance;
    }

    public boolean isBlurEnabled() {
        return properties.getBoolean(blurProp, false);
    }

    public boolean isCustomFontEnabled() {
        return properties.getBoolean(customFontProp, false);
    }

    public boolean isShowUsageInfo() {
        return properties.getBoolean(showUsageInfoProp, true);
    }

    public int getGuiModeIndex() {
        return properties.getInt(guiModeProp, 0);
    }

    public int getDesignIndex() {
        return properties.getInt(designProp, 1);
    }

    public String getClientName() {
        return properties.getString(clientNameProp, "Project SUIM");
    }

    public int getPrefixStyleIndex() {
        return properties.getInt(prefixStyleProp, 0);
    }

    public String getCustomPrefix() {
        return properties.getString(customPrefixProp,
                "\u00A70\u00A7l[\u00A74\u00A7lProject SUIM\u00A70\u00A7l]\u00A7r");
    }

    public Color getGuiColor() {
        String hex = properties.getString(colorProp, toHex(getDefaultHudColor()));
        return parseHex(hex);
    }

    public void setGuiColor(Color color) {
        String hex = String.format("%02X%02X%02X%02X",
                color.getRed(), color.getGreen(), color.getBlue(),
                color.getAlpha());
        try {
            manager.setPropertyValue(colorProp, hex);
        } catch (Exception ignored) {
        }
    }

    public ClickGuiAutosaveMode getAutosaveMode() {
        int idx = properties.getInt(autosaveProp, 0);
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
            if (moduleInstance != null && !manager.isModuleEnabled(moduleInstance)) {
                manager.setModuleEnabled(moduleInstance, true);
            }
        } catch (Exception ignored) {
        }
    }

    private void disableModuleIfOpen() {
        try {
            if (moduleInstance != null && manager.isModuleEnabled(moduleInstance)) {
                manager.setModuleEnabled(moduleInstance, false);
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
