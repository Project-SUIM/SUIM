package coffee.axle.suim.init;

import net.hypixel.modapi.tweaker.HypixelModAPITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.relauncher.CoreModManager;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.MixinTweaker;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.io.File;
import java.net.URISyntaxException;
import java.util.List;

public class SuimTweaker extends HypixelModAPITweaker {
    private static final String MIXIN_CONFIG = "mixins.suim.json";

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        super.acceptOptions(args, gameDir, assetsDir, profile);
        try {
            String name = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getName();
            CoreModManager.getIgnoredMods().remove(name);
            CoreModManager.getReparseableCoremods().add(name);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Could not locate SUIM jar", e);
        }
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        super.injectIntoClassLoader(classLoader);
        @SuppressWarnings("unchecked")
        List<String> tweakers = (List<String>) Launch.blackboard.get("TweakClasses");
        if (tweakers == null) {
            throw new IllegalStateException("LaunchWrapper TweakClasses missing, cannot start Mixin");
        }
        if (!tweakers.contains(MixinTweaker.class.getName())) {
            tweakers.add(MixinTweaker.class.getName());
        }
        MixinBootstrap.init();
        MixinEnvironment env = MixinEnvironment.getDefaultEnvironment();
        if (env != null && !env.getMixinConfigs().contains(MIXIN_CONFIG)) {
            env.addConfiguration(MIXIN_CONFIG);
        }
    }
}