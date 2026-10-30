package coffee.axle.suim.feature.movement;

import coffee.axle.suim.events.PrePlayerInputEvent;
import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.util.Utils;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import static coffee.axle.suim.ProjectSUIM.mc;

public class NullMove extends Feature {

    private boolean prevW = false;
    private boolean prevS = false;
    private boolean prevA = false;
    private boolean prevD = false;
    private int lastForwardSign = 0;
    private int lastStrafeSign = 0;

    public NullMove() {
        super("NullMove", GuiCategory.WORLD);
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        resetState();
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
        if (!isEnabled()) return;
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
