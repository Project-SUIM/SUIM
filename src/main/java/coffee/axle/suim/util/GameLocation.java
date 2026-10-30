package coffee.axle.suim.util;

import coffee.axle.suim.events.HypixelLocationEvent;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import static coffee.axle.suim.ProjectSUIM.mc;

/**
 * Service to know game location via Hypixel Mod API, essentially never wrong
 */
public final class GameLocation {

    private static volatile String gameType;
    private static volatile String mode;
    private static volatile String map;
    private static volatile String lobbyName;
    private static volatile boolean known;
    private static boolean registered;

    private GameLocation() {}

    public static void init() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.register(new Handler());
    }

    public static String getGameType() {
        return gameType;
    }

    public static String getMode() {
        return mode;
    }

    public static String getMap() {
        return map;
    }

    public static String getLobbyName() {
        return lobbyName;
    }

    public static boolean isKnown() {
        return known;
    }

    public static boolean inGame(String type) {
        return known && type.equals(gameType) && mode != null;
    }

    private static class Handler {
        @SubscribeEvent
        public void onLocation(HypixelLocationEvent e) {
            gameType = e.getGameType();
            lobbyName = e.getLobbyName();
            mode = e.getMode();
            map = e.getMap();
            known = true;
        }
    }

    /**
     * @deprecated use {@link #inGame(String)} with "BEDWARS" as the argument instead.
     */
    public static boolean bwViaSidebar() {
        if (mc.theWorld == null) return false;
        Scoreboard sb = mc.theWorld.getScoreboard();
        if (sb == null) return false;
        ScoreObjective obj = sb.getObjectiveInDisplaySlot(1);
        if (obj == null) return false;
        if (!strip(obj.getDisplayName()).contains("BED WARS")) return false;
        boolean red = false, blue = false;
        int line = 0;
        for (Score s : sb.getSortedScores(obj)) {
            if (line++ >= 10) break;
            String text = strip(ScorePlayerTeam.formatPlayerName(
                    sb.getPlayersTeam(s.getPlayerName()), s.getPlayerName()));
            if (text.startsWith("R ")) red = true;
            else if (text.startsWith("B ")) blue = true;
            if (red && blue) return true;
        }
        return false;
    }

    private static String strip(String s) {
        return s == null ? "" : EnumChatFormatting.getTextWithoutFormattingCodes(s)
                .replaceAll("[^\\x20-\\x7E]", "").trim();
    }
}