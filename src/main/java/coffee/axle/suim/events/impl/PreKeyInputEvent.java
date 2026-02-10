package coffee.axle.suim.events.impl;

import net.minecraftforge.fml.common.eventhandler.Event;

public class PreKeyInputEvent extends Event {
    private final int key;
    private final char character;

    public PreKeyInputEvent(int key, char character) {
        this.key = key;
        this.character = character;
    }

    public int getKey() {
        return key;
    }

    public char getCharacter() {
        return character;
    }
}





