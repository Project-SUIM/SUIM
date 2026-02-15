package coffee.axle.suim.api;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.hooks.ModulePropertyManager;
import coffee.axle.suim.hooks.MyauModuleCreator;
import coffee.axle.suim.hooks.MyauModuleManager;

import java.util.List;

/**
 * Interface for external SUIM addons.
 * Implement this in your addon mod and register it via
 * {@link SuimAddonRegistry}.
 *
 * <p>
 * Addons receive full access to the SUIM hook infrastructure:
 * module creation, command registration, property management,
 * and the ClickGUI integration.
 * </p>
 */
public interface SuimAddon {

    /**
     * Unique identifier for this addon. Used in MyauWorld origin tracking.
     * Should be lowercase, alphanumeric with hyphens/underscores only.
     */
    String getAddonId();

    /**
     * Display name shown in logs and the ClickGUI.
     */
    String getDisplayName();

    /**
     * Called when SUIM is ready to initialize addons.
     * Use the provided creator, manager, and propertyManager to set up
     * your modules, commands, and properties.
     *
     * @param creator     Module/command/property factory
     * @param manager     Module state management
     * @param propManager Type-safe property access
     */
    void onInitialize(MyauModuleCreator creator, MyauModuleManager manager, ModulePropertyManager propManager);

    /**
     * Return all features this addon provides.
     * These will be injected into SUIM's feature pipeline and
     * appear in the ClickGUI if they have a GuiCategory.
     */
    List<Feature> getFeatures();

    /**
     * Called after ALL features (built-in + addon) have been initialized.
     * Use this for cross-module operations like injecting properties into
     * modules created by SUIM or other addons.
     *
     * @param creator     Module/command/property factory
     * @param manager     Module state management
     * @param propManager Type-safe property access
     */
    default void onPostInit(MyauModuleCreator creator, MyauModuleManager manager, ModulePropertyManager propManager) {
    }

    /**
     * Called when SUIM is shutting down. Clean up resources here.
     */
    default void onDisable() {
    }
}
