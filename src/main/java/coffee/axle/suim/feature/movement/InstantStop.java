package coffee.axle.suim.feature.movement;

import coffee.axle.suim.events.PrePlayerInputEvent;
import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.property.Setting;
import coffee.axle.suim.util.Utils;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import static coffee.axle.suim.ProjectSUIM.mc;

public class InstantStop extends Feature {

    @Setting private boolean onlyGround = true;
    @Setting private boolean disableFlying = true;

    private float lastForward = 0.0f;
    private float lastStrafe = 0.0f;

    public InstantStop() {
        super("InstantStop", GuiCategory.WORLD);
    }

    @Override
    protected void onEnable() {
        lastForward = 0;
        lastStrafe = 0;
    }

    @Override
    protected void onDisable() {
        lastForward = 0;
        lastStrafe = 0;
    }

    @SubscribeEvent
    public void onPrePlayerInput(PrePlayerInputEvent e) {
        if (!isEnabled()) return;
        if (!Utils.nullCheck()) return;
        if (mc.currentScreen != null) return;

        if (onlyGround && !mc.thePlayer.onGround) {
            lastForward = e.getForward();
            lastStrafe = e.getStrafe();
            return;
        }

        if (disableFlying && mc.thePlayer.capabilities.isFlying) {
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
