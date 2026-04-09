package coffee.axle.suim.feature.movement;

import coffee.axle.suim.events.PrePlayerInputEvent;
import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class NullMove extends Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private Object moduleInstance;

    private boolean prevW = false;
    private boolean prevS = false;
    private boolean prevA = false;
    private boolean prevD = false;
    private int lastForwardSign = 0;
    private int lastStrafeSign = 0;

    @Override
    public String getName() {
        return "NullMove";
    }

    @Override
    public GuiCategory getGuiCategory() {
        return GuiCategory.WORLD;
    }

    @Override
    public boolean initialize() {
        try {
            moduleInstance = createModule();
            creator.injectModule(moduleInstance, NullMove.class);
            manager.reloadModuleCommand();

            MinecraftForge.EVENT_BUS.register(this);
            manager.registerModuleCallbacks(moduleInstance, this::resetState, this::resetState);

            return true;
        } catch (Exception e) {
            MyauLogger.error("NullMove:init", e);
            return false;
        }
    }

    private void resetState() {
        prevW = false;
        prevS = false;
        prevA = false;
        prevD = false;
        lastForwardSign = 0;
        lastStrafeSign = 0;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPrePlayerInput(PrePlayerInputEvent e) {
        if (!manager.isModuleEnabled(moduleInstance)) return;
        if (!Utils.nullCheck()) return;
        if (mc.currentScreen != null) return;

        boolean w = mc.gameSettings.keyBindForward.isKeyDown();
        boolean s = mc.gameSettings.keyBindBack.isKeyDown();
        boolean a = mc.gameSettings.keyBindLeft.isKeyDown();
        boolean d = mc.gameSettings.keyBindRight.isKeyDown();

        if (w && !prevW) lastForwardSign = 1;
        if (s && !prevS) lastForwardSign = -1;
        if (a && !prevA) lastStrafeSign = 1;
        if (d && !prevD) lastStrafeSign = -1;

        if (w && s) e.setForward(lastForwardSign >= 0 ? 1.0f : -1.0f);
        if (a && d) e.setStrafe(lastStrafeSign >= 0 ? 1.0f : -1.0f);

        prevW = w;
        prevS = s;
        prevA = a;
        prevD = d;
    }
}
