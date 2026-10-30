package coffee.axle.suim.events;

import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * Fired when hypixel reports where we are. Only ever fires on hypixel, and only
 * if the mod api answered.
 * <p>
 * Plain strings on purpose: only the hook that reads the mod api should have to
 * link against it.
 */
public class HypixelLocationEvent extends Event {
    private final String gameType;
    private final String lobbyName;
    private final String mode;
    private final String map;

    public HypixelLocationEvent(String gameType, String lobbyName, String mode, String map) {
        this.gameType = gameType;
        this.lobbyName = lobbyName;
        this.mode = mode;
        this.map = map;
    }

    /** BEDWARS, SKYWARS, MAIN while in a lobby, ... */
    public String getGameType() {
        return gameType;
    }

    public String getLobbyName() {
        return lobbyName;
    }

    public String getMode() {
        return mode;
    }

    public String getMap() {
        return map;
    }
}