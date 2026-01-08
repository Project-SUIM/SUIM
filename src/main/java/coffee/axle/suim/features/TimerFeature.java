package coffee.axle.suim.features;

import coffee.axle.suim.hooks.MyauHook;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class TimerFeature implements Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final MyauHook hook = MyauHook.getInstance();

    private Object moduleInstance;

    private static TimerFeature instance;
    private double storedMotionX = 0;
    private double storedMotionY = 0;
    private double storedMotionZ = 0;
    private boolean motionStored = false;

    @Override
    public String getName() {
        return "Freeze";
    }

    @Override
    public boolean initialize() {
        try {
            instance = this;
            moduleInstance = hook.createModule(getName());
            hook.injectModule(moduleInstance, TimerFeature.class);

            hook.registerModuleCallbacks(
                    moduleInstance,
                    this::onEnable,
                    this::onDisable);

            MinecraftForge.EVENT_BUS.register(this);
            return true;

        } catch (Exception e) {
            MyauLogger.error("f", e);
            return false;
        }
    }

    private void onEnable() {
        motionStored = false;
        storeAndFreezeMotion();
    }

    private void onDisable() {
        restoreMotion();
        motionStored = false;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START)
            return;
        if (!hook.isModuleEnabled(moduleInstance))
            return;

        applyFreeze();
    }

    private void storeAndFreezeMotion() {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null)
            return;

        if (!motionStored) {
            storedMotionX = player.motionX;
            storedMotionY = player.motionY;
            storedMotionZ = player.motionZ;
            motionStored = true;
        }

        player.motionX = 0;
        player.motionY = 0;
        player.motionZ = 0;
    }

    private void applyFreeze() {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null)
            return;

        player.motionX = 0;
        player.motionY = 0;
        player.motionZ = 0;
    }

    private void restoreMotion() {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || !motionStored)
            return;

        player.motionX = storedMotionX;
        player.motionY = storedMotionY;
        player.motionZ = storedMotionZ;
        motionStored = false;
    }

    public static boolean shouldCancelUpdate() {
        if (instance == null || instance.moduleInstance == null) {
            return false;
        }

        try {
            return MyauHook.getInstance().isModuleEnabled("Freeze");
        } catch (Exception e) {
            return false;
        }
    }
}
