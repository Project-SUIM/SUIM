package coffee.axle.suim.feature;

import coffee.axle.suim.clickgui.module.GuiModule;
import coffee.axle.suim.hooks.ModulePropertyManager;
import coffee.axle.suim.hooks.MyauModuleCreator;
import coffee.axle.suim.hooks.MyauModuleManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for all SUIM features.
 * Provides shared access to module creation, management, and property access.
 *
 * Features that create standalone Myau modules should override
 * {@link #getGuiCategory()} to appear in the click GUI.
 */
public abstract class Feature {
    protected MyauModuleCreator creator;
    protected MyauModuleManager manager;
    protected ModulePropertyManager properties;

    /** The Myau module instance, set after createModule(). */
    private Object moduleInstance;

    /** Myau property objects registered on this feature's module. */
    private final List<Object> myauProperties = new ArrayList<>();

    public void inject(MyauModuleCreator creator, MyauModuleManager manager, ModulePropertyManager properties) {
        this.creator = creator;
        this.manager = manager;
        this.properties = properties;
    }

    /**
     * Origin tag for MyauWorld tracking. Override for non-suim origins.
     */
    protected String getOrigin() {
        return "suim";
    }

    /** Creates a module tagged with this feature's name and origin. */
    protected Object createModule() throws Exception {
        Object module = creator.createModule(getName(), getOrigin());
        this.moduleInstance = module;
        return module;
    }

    /**
     * GUI category for this feature. Return null to hide from GUI.
     * Standalone modules override this; patches and commands return null.
     */
    public GuiCategory getGuiCategory() {
        return null;
    }

    /**
     * Whether this feature creates a standalone Myau module (shown in GUI).
     */
    public boolean isStandaloneModule() {
        return getGuiCategory() != null;
    }

    /**
     * Get the Myau module instance created by this feature.
     */
    public Object getModuleInstance() {
        return moduleInstance;
    }

    /**
     * Set the Myau module instance (called from subclasses or createModule).
     */
    protected void setModuleInstance(Object instance) {
        this.moduleInstance = instance;
    }

    /**
     * Track a Myau property created for this feature's module.
     */
    protected void trackProperty(Object property) {
        myauProperties.add(property);
    }

    /**
     * Get all Myau properties registered on this feature's module.
     */
    public List<Object> getMyauProperties() {
        return myauProperties;
    }

    /**
     * Called when the feature should initialize.
     *
     * @return true if successful, false otherwise
     */
    public abstract boolean initialize() throws Exception;

    /**
     * Get the feature name.
     */
    public abstract String getName();

    /**
     * Called when the feature should be disabled.
     */
    public void disable() throws Exception {
    }

    /**
     * Build a custom GuiModule for this feature.
     * Override to provide custom settings (e.g. ColorModuleSetting).
     * Return null to use default automatic property scanning.
     */
    public GuiModule buildGuiModule(MyauModuleManager mgr,
            ModulePropertyManager propMgr) {
        return null;
    }
}
