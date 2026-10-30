package coffee.axle.suim.feature.movement;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.property.Setting;
import coffee.axle.suim.util.Utils;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import static coffee.axle.suim.ProjectSUIM.mc;

/**
 * Injects KOL's prediction-air mode into myau's KeepSprint (myau.J5).
 *
 * KOL 261015 (a.bh) semantics, verified from the running jar's mixins:
 * MixinEntityPlayer gates the soft slowdown + sprint-keep on enabled && o(),
 * where o() is the native mode gate. User ground truth: PREDICTION_AIR means
 * keep sprint ONLY while airborne AND sprinting - on the ground the module
 * has no effect (pure vanilla attack reset).
 *
 * myau's a.W gate chain: ground-only && onGround -> vanilla branch;
 * ground-only && airborne -> soft branch (sprint kept). So pinning
 * ground-only=true while airborne gives exactly the air-only behavior, and
 * un-pinning on landing restores the no-effect ground state. The pin flips
 * per tick based on onGround, not per attack.
 */
public class KeepSprintExtras extends Feature {

    public enum Mode {
        NORMAL, PREDICTION_AIR
    }

    @Setting private Mode mode = Mode.NORMAL;
    @Setting(existing = true) private boolean groundOnly = false;

    private boolean pinned;
    private boolean savedGroundOnly;

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

    // ClientTick START runs before any attack this tick, and a.W/mixin f
    // read the flag live at attack time, so a pre-tick pin covers everything.
    // The pin is AIRBORNE-ONLY: on the ground the module must have no effect,
    // so ground-only stays at the user's saved value (false = soft branch
    // would apply on ground attacks - that's NORMAL mode's job, not ours).
    // We only force ground-only=true while actually in the air.
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !Utils.nullCheck()) {
            return;
        }
        if (!isPredictionAir()) {
            restore();
            return;
        }
        // PREDICTION_AIR: only in air, only while sprinting. on ground the
        // module has no effect - vanilla attack reset happens untouched
        boolean airborne = !mc.thePlayer.onGround && mc.thePlayer.isSprinting();
        if (airborne) {
            if (!pinned) {
                savedGroundOnly = groundOnly;
                pinned = true;
            }
            set("groundOnly", true);
        } else {
            restore();
        }
    }

    private void restore() {
        if (pinned) {
            set("groundOnly", savedGroundOnly);
        }
        pinned = false;
    }

    @Override
    public void disable() {
        restore();
    }
}
