package coffee.axle.suim.feature.movement;

import coffee.axle.suim.events.PrePlayerInputEvent;
import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class InstantStop extends Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private Object moduleInstance;
    private Object onlyGroundProperty;
    private Object disableFlyingProperty;

    private float lastForward = 0.0f;
    private float lastStrafe = 0.0f;

    @Override
    public String getName() {
        return "InstantStop";
    }

    @Override
    public GuiCategory getGuiCategory() {
        return GuiCategory.WORLD;
    }

    @Override
    public boolean initialize() {
        try {
            moduleInstance = createModule();
            creator.injectModule(moduleInstance, InstantStop.class);

            onlyGroundProperty = creator.createBooleanProperty("only-ground", true);
            disableFlyingProperty = creator.createBooleanProperty("disable-flying", true);

            creator.registerProperties(moduleInstance, onlyGroundProperty, disableFlyingProperty);
            manager.reloadModuleCommand();

            MinecraftForge.EVENT_BUS.register(this);
            manager.registerModuleCallbacks(moduleInstance,
                    () -> { lastForward = 0; lastStrafe = 0; },
                    () -> { lastForward = 0; lastStrafe = 0; });

            return true;
        } catch (Exception e) {
            MyauLogger.error("InstantStop:init", e);
            return false;
        }
    }

    @SubscribeEvent
    public void onPrePlayerInput(PrePlayerInputEvent e) {
        if (!manager.isModuleEnabled(moduleInstance)) return;
        if (!Utils.nullCheck()) return;
        if (mc.currentScreen != null) return;

        if (properties.getBoolean(onlyGroundProperty, true) && !mc.thePlayer.onGround) {
            lastForward = e.getForward();
            lastStrafe = e.getStrafe();
            return;
        }

        if (properties.getBoolean(disableFlyingProperty, true) && mc.thePlayer.capabilities.isFlying) {
            lastForward = e.getForward();
            lastStrafe = e.getStrafe();
            return;
        }

        float rawF = e.getForward();
        float rawS = e.getStrafe();

        if (rawF == 0.0f && rawS == 0.0f && (lastForward != 0.0f || lastStrafe != 0.0f)) {
            e.setForward(Math.signum(-lastForward));
            e.setStrafe(Math.signum(-lastStrafe));
        }

        lastForward = rawF;
        lastStrafe = rawS;
    }
}
