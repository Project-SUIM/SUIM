package coffee.axle.suim.feature;

import coffee.axle.suim.hooks.ModulePropertyManager;
import coffee.axle.suim.hooks.MyauModuleCreator;
import coffee.axle.suim.hooks.MyauModuleManager;

/**
 * Base class for all SUIM features.
 * Provides shared access to module creation, management, and property access.
 */
public abstract class Feature {
    protected MyauModuleCreator creator;
    protected MyauModuleManager manager;
    protected ModulePropertyManager properties;

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
        return creator.createModule(getName(), getOrigin());
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
}
