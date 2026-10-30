package coffee.axle.suim.util;

/**
 * Bedwars game check. Uses the hypixel location packet when we have it, and
 * falls back to the sidebar scan until then.
 */
public final class BedwarsDetector {

    private BedwarsDetector() {}

    public static boolean inBedWarsGame() {
        if (GameLocation.isKnown()) {
            return GameLocation.inGame("BEDWARS");
        }
        return GameLocation.bedwarsBySidebar();
    }
}