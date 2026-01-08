package coffee.axle.suim.events;

import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * Client rotation event for rotation modifications
 * 
 * @author maybsomeday (meow) - ported to d'Myau
 */
public class ClientRotationEvent extends Event {
    private float yaw;
    private float pitch;

    public ClientRotationEvent() {
    }

    public void setRotations(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }
}
