package coffee.axle.suim.events;

import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;

@Cancelable
public class PrePlayerInputEvent extends Event {
    private float forward;
    private float strafe;
    private boolean jump;

    public PrePlayerInputEvent(float forward, float strafe, boolean jump) {
        this.forward = forward;
        this.strafe = strafe;
        this.jump = jump;
    }
    // "isjump" aha
    public boolean isJump() {
        return jump;
    }

    public void setJump(boolean jump) {
        this.jump = jump;
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
