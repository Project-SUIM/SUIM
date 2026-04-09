package coffee.axle.suim.events;

import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;

@Cancelable
public class PrePlayerInputEvent extends Event {
    private float forward;
    private float strafe;

    public PrePlayerInputEvent(float forward, float strafe) {
        this.forward = forward;
        this.strafe = strafe;
    }

    public float getForward() {
        return forward;
    }

    public void setForward(float forward) {
        this.forward = forward;
    }

    public float getStrafe() {
        return strafe;
    }

    public void setStrafe(float strafe) {
        this.strafe = strafe;
    }
}
