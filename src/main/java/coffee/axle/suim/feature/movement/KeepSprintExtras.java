package coffee.axle.suim.feature.movement;

import coffee.axle.suim.events.PrePlayerInputEvent;
import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.property.Setting;
import coffee.axle.suim.util.Utils;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import static coffee.axle.suim.ProjectSUIM.mc;


public class KeepSprintExtras extends Feature {

    public enum Mode {
        NORMAL, PREDICTION_AIR
    }

    @Setting private Mode mode = Mode.NORMAL;

    private boolean pendingGroundFix;

    public KeepSprintExtras() {
        super("KeepSprint:Extras");
    }

    @Override
    protected String targetModule() {
        return "KeepSprint";
    }

    private boolean isPredictionAir() {
        return isEnabled() && mode == Mode.PREDICTION_AIR;
    }

    // fires before the attack (manual via ForgeHooks.onPlayerAttackTarget,
    // KA via a.W which also calls ForgeHooks.onPlayerAttackTarget)
    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!isPredictionAir() || !Utils.nullCheck()) {
            return;
        }
        // on ground: myau will apply soft (1.0x + sprint kept). we want
        // vanilla (0.6x + sprint dropped). flag for post-attack override.
        if (mc.thePlayer.onGround) {
            pendingGroundFix = true;
        }
        // in air: myau's soft branch is exactly KOL's behavior. no fix needed.
    }

    // runs after the attack in the same tick (PrePlayerInput fires during
    // onLivingUpdate, after clickMouse/attackLocally but before the walking
    // packet). fix up ground attacks to vanilla here.
    @SubscribeEvent
    public void onPreInput(PrePlayerInputEvent e) {
        if (!pendingGroundFix) {
            return;
        }
        pendingGroundFix = false;
        if (!Utils.nullCheck()) {
            return;
        }
        // myau applied soft: motion *= 1.0 (slowdown=0) + sprint kept.
        // undo to vanilla: motion *= 0.6 + setSprinting(false)
        mc.thePlayer.motionX *= 0.6;
        mc.thePlayer.motionZ *= 0.6;
        mc.thePlayer.setSprinting(false);
    }

    @Override
    public void disable() {
        pendingGroundFix = false;
    }
}
