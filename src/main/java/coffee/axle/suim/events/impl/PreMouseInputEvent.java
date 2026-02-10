package coffee.axle.suim.events.impl;

import net.minecraftforge.fml.common.eventhandler.Event;

public class PreMouseInputEvent extends Event {
    private final int button;

    public PreMouseInputEvent(int button) {
        this.button = button;
    }

    public int getButton() {
        return button;
    }
}





