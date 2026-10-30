package coffee.axle.suim.feature;

import coffee.axle.suim.clickgui.module.GuiModule;
import coffee.axle.suim.hooks.ModulePropertyManager;
import coffee.axle.suim.hooks.MyauModuleCreator;
import coffee.axle.suim.hooks.MyauModuleManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Base class for all SUIM features also known as "modules" depending on the client
 * Features that create standalone Myau modules should override
 * {@link #getGuiCategory()} to appear in the click GUI.
 * example:
 * <pre>{@code
 * public class MyFeature extends Feature {
 *     @Override
 *     public String getName() {
 *         return "MyFeature";
 *     }
 *
 *     @Override
 *     public boolean initialize() {
 *         // Initialization logic here
 *         return true;
 *     }
 * }
 * }</pre>
 */
public abstract class Feature {
    protected MyauModuleCreator creator;
    protected MyauModuleManager manager;
    protected ModulePropertyManager properties;

    private Object moduleInstance;
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

    public boolean isStandaloneModule() {
        return getGuiCategory() != null;
    }

    public Object getModuleInstance() {
        return moduleInstance;
    }

    protected void setModuleInstance(Object instance) {
        this.moduleInstance = instance;
    }

    protected void trackProperty(Object property) {
        myauProperties.add(property);
    }

    public List<Object> getMyauProperties() {
        return myauProperties;
    }

    public abstract boolean initialize() throws Exception;

    public abstract String getName();

    public void disable() throws Exception {
        if (moduleInstance != null) {
            SuffixRegistry.unregister(moduleInstance);
        }
    }

    protected void setSuffix(String... suffix) {
        if (moduleInstance != null) {
            SuffixRegistry.registerStatic(moduleInstance, suffix);
        }
    }

    protected void setSuffixDynamic(Supplier<String[]> supplier) {
        if (moduleInstance != null) {
            SuffixRegistry.register(moduleInstance, supplier);
        }
    }

    protected void clearSuffix() {
        if (moduleInstance != null) {
            SuffixRegistry.unregister(moduleInstance);
        }
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
