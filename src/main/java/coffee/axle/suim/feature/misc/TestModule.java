package coffee.axle.suim.feature.misc;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.render.AimDotTracker;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.render.TestModuleRenderer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;

public class TestModule extends Feature {

    private Object moduleInstance;
    private Object useMixinProperty;
    private Object aimDotsProperty;

    @Override
    public String getName() {
        return "TestModule";
    }

    @Override
    public boolean initialize() {
        try {
            this.moduleInstance = createModule();
            creator.injectModule(this.moduleInstance, TestModule.class);

            // Create properties
            this.useMixinProperty = creator.createBooleanProperty("use-mixin", false);
            this.aimDotsProperty = creator.createBooleanProperty("debug-aim-dots", false);
            creator.registerProperties(this.moduleInstance, this.useMixinProperty, this.aimDotsProperty);

            // Reload module command so it appears in .myau list
            manager.reloadModuleCommand();

            // Register this command handler
            ArrayList<String> commandNames = new ArrayList<>();
            commandNames.add("testmodule");
            creator.registerCommand(commandNames, this::handleCommand);

            // Register Forge event bus for rendering and tick updates
            MinecraftForge.EVENT_BUS.register(this);

            // Register enable/disable callbacks
            manager.registerModuleCallbacks(
                    this.moduleInstance,
                    () -> {
                        TestModuleRenderer.setModuleEnabled(true);
                        MyauLogger.info("TestModule enabled!");
                    },
                    () -> {
                        TestModuleRenderer.setModuleEnabled(false);
                        AimDotTracker.clear();
                        MyauLogger.info("TestModule disabled!");
                    });

            MyauLogger.info("TestModule initialized successfully");
            return true;
        } catch (Exception e) {
            MyauLogger.error("TestModule:init", e);
            MyauLogger.error("MODULE_FAIL", e);
            return false;
        }
    }

    private void handleCommand(ArrayList<String> args) {
        if (args.isEmpty()) {
            boolean useMixin = getUseMixin();
            manager.sendMessage("&7Usage: &f.testmodule <use-mixin> <true/false>");
            manager.sendMessage("&7Current use-mixin: &f" + useMixin);
            manager.sendMessage("&7Box color: " + (useMixin ? "&aGREEN (mixin)" : "&5PURPLE (direct)"));
            return;
        }

        String subCommand = args.get(0).toLowerCase();
        if (subCommand.equals("use-mixin") && args.size() > 1) {
            boolean value = args.get(1).equalsIgnoreCase("true");
            setUseMixin(value);
            TestModuleRenderer.setMixinEnabled(value);
            manager.sendMessage("&aSet use-mixin to: &f" + value);
            manager.sendMessage("&7Box will now be: " + (value ? "&aGREEN (mixin)" : "&5PURPLE (direct)"));
        } else {
            manager.sendMessage("&cUnknown option: &f" + subCommand);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START)
            return;

        // Sync module enabled state with renderer
        boolean isEnabled = manager.isModuleEnabled(this.getName());
        TestModuleRenderer.setModuleEnabled(isEnabled);

        // Sync use-mixin property with renderer
        TestModuleRenderer.setMixinEnabled(getUseMixin());
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (!manager.isModuleEnabled(this.getName()))
            return;
        if (!getAimDots())
            return;
        if (event.target == null)
            return;

        AimDotTracker.recordHit(event.target);
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        // Only render if NOT using mixin path (purple = direct, green = mixin)
        // The mixin handles green rendering via TestModuleRenderer.renderMixinBox()
        TestModuleRenderer.renderDirectBox(event.partialTicks);

        if (manager.isModuleEnabled(this.getName()) && getAimDots()) {
            AimDotTracker.renderDots(event.partialTicks);
        }
    }

    private boolean getUseMixin() {
        try {
            return (Boolean) manager.getPropertyValue(this.useMixinProperty);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean getAimDots() {
        try {
            return (Boolean) manager.getPropertyValue(this.aimDotsProperty);
        } catch (Exception e) {
            return false;
        }
    }

    private void setUseMixin(boolean value) {
        try {
            if (this.useMixinProperty != null) {
                java.lang.reflect.Method setValueMethod = this.useMixinProperty.getClass().getMethod("a", Object.class);
                setValueMethod.invoke(this.useMixinProperty, value);
            }
        } catch (Exception e) {
            MyauLogger.error("TestModule:onEnable", e);
        }
    }

    @Override
    public void disable() {
        try {
            MinecraftForge.EVENT_BUS.unregister(this);
            TestModuleRenderer.setModuleEnabled(false);
            AimDotTracker.clear();
            if (this.moduleInstance != null) {
                manager.setModuleEnabled(this.moduleInstance, false);
            }
        } catch (Exception e) {
            MyauLogger.error("TestModule:onDisable", e);
        }
    }
}
