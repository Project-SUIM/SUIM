package coffee.axle.suim.feature;

import coffee.axle.suim.feature.clickgui.ClickGui;
import coffee.axle.suim.feature.clickgui.EditHud;
import coffee.axle.suim.feature.combat.*;
import coffee.axle.suim.feature.command.*;
import coffee.axle.suim.feature.exploit.*;
import coffee.axle.suim.feature.misc.*;
import coffee.axle.suim.feature.player.*;
import coffee.axle.suim.feature.render.*;
import coffee.axle.suim.feature.world.*;
import coffee.axle.suim.hooks.ModulePropertyManager;
import coffee.axle.suim.hooks.MyauHook;
import coffee.axle.suim.hooks.MyauModuleCreator;
import coffee.axle.suim.hooks.MyauModuleManager;
import coffee.axle.suim.util.HudUtils;
import coffee.axle.suim.util.MyauLogger;

import java.util.ArrayList;
import java.util.List;

public class FeatureManager {
    private static final List<Feature> features = new ArrayList<>();

    private final MyauHook hook;
    private final MyauModuleCreator creator;
    private final MyauModuleManager manager;
    private final ModulePropertyManager propertyManager;

    public FeatureManager() {
        this.hook = MyauHook.getInstance();
        this.creator = new MyauModuleCreator(hook);
        this.manager = new MyauModuleManager(hook);
        this.propertyManager = new ModulePropertyManager(manager);
        HudUtils.getInstance().setManager(manager);

        // ClickGUI modules (feature/clickgui/)
        registerFeature(new ClickGui());
        registerFeature(new EditHud());
        registerFeature(new TestGuiModule());

        // Misc
        registerFeature(new TestCommand());
        registerFeature(new TestModule());

        // Commands
        registerFeature(new DMyauCommand());
        registerFeature(new FindCommand());
        registerFeature(new StatusCommand());
        registerFeature(new ClientInfoCommand());

        // Combat
        registerFeature(new MultiPointAiming());
        registerFeature(new ArmorExceptions());
        registerFeature(new AutoClickerExtras());
        registerFeature(new HitSelect());
        registerFeature(new KillAuraDisableOnDeath());
        registerFeature(new MoreKB());

        // Render
        registerFeature(new AimAssistShowTarget());
        registerFeature(new BedESPTeamColor());
        registerFeature(new Bedplates());
        registerFeature(new Freelook());
        registerFeature(new SkullESP());
        registerFeature(new XraySpawnerNameTags());

        // World
        registerFeature(new AutoBlockIn());
        registerFeature(new AutoClutch());
        registerFeature(new EagleAutoSwap());
        registerFeature(new FastPlaceExtras());

        // Player
        registerFeature(new InvManagerExtras());

        // Exploit
        registerFeature(new Freeze());
        registerFeature(new VelocityBuffer());
    }

    private void registerFeature(Feature feature) {
        feature.inject(creator, manager, propertyManager);
        features.add(feature);
    }

    public boolean initializeAll() {
        if (!hook.initialize()) {
            MyauLogger.log("FM_HOOK_FAIL");
            return false;
        }

        int successCount = 0;

        for (Feature feature : features) {
            try {
                if (feature.initialize()) {
                    successCount++;
                } else {
                    MyauLogger.status(feature.getName(), false);
                }
            } catch (Exception e) {
                MyauLogger.error("FEATURE_FAIL", e);
            }
        }

        MyauLogger.summary(successCount, features.size());
        return successCount > 0;
    }

    public void disableAll() {
        for (Feature feature : features) {
            try {
                feature.disable();
            } catch (Exception e) {
                MyauLogger.info("Failed to disable: " + feature.getName());
            }
        }
    }

    public List<Feature> getFeatures() {
        return features;
    }

    public MyauHook getHook() {
        return hook;
    }

    public MyauModuleCreator getCreator() {
        return creator;
    }

    public MyauModuleManager getManager() {
        return manager;
    }

    public ModulePropertyManager getPropertyManager() {
        return propertyManager;
    }
}
