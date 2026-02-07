package coffee.axle.suim;

import coffee.axle.suim.features.*;
import coffee.axle.suim.hooks.MyauHook;
import coffee.axle.suim.hooks.MyauModuleCreator;
import coffee.axle.suim.hooks.MyauModuleManager;
import coffee.axle.suim.util.MyauLogger;

import java.util.ArrayList;
import java.util.List;

public class FeatureManager {
    private static final List<Feature> features = new ArrayList<>();

    private final MyauHook hook;
    private final MyauModuleCreator creator;
    private final MyauModuleManager manager;

    public FeatureManager() {
        this.hook = MyauHook.getInstance();
        this.creator = new MyauModuleCreator(hook);
        this.manager = new MyauModuleManager(hook);

        // Test features
        registerFeature(new TestCommand(creator, manager));
        registerFeature(new TestModule(hook, creator, manager));

        // Commands
        registerFeature(new DMyauCommand());
        registerFeature(new FindCommand());
        registerFeature(new StatusCommand());
        registerFeature(new ClientInfoCommand());

        // Module extensions
        registerFeature(new MultiPointAiming());
        registerFeature(new AimAssistShowTarget());
        registerFeature(new ArmorExceptions());
        registerFeature(new AutoClickerExtras());
        registerFeature(new BedESPTeamColor());
        registerFeature(new Bedplates());
        registerFeature(new EagleAutoSwap());
        registerFeature(new FastPlaceExtras());
        registerFeature(new Freelook());
        registerFeature(new HitSelect());
        registerFeature(new InvManagerExtras());
        registerFeature(new KillAuraDisableOnDeath());
        registerFeature(new MoreKB());
        registerFeature(new AutoBlockIn());
        registerFeature(new AutoClutch());
        registerFeature(new SkullESP());
        registerFeature(new TimerFeature());
        registerFeature(new VelocityBuffer());
        registerFeature(new XraySpawnerNameTags());
    }

    private void registerFeature(Feature feature) {
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
}
