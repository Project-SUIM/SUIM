package coffee.axle.suim.util;

import coffee.axle.suim.events.HypixelLocationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Where we are right now, per the hypixel mod api. Falls back to the sidebar
 * scan while the location packet has not arrived (or on other servers).
 */
public final class GameLocation {
    private static final Minecraft mc = Minecraft.getMinecraft();

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

    /** non-null while sitting in a lobby rather than a game */
    public static String getLobbyName() {
        return lobbyName;
    }

    /** true once the server has told us where we are */
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
            // vanilla only carries a lobby name for lobbies, so its presence
            // means we are not in a game yet
            lobbyName = e.getLobbyName();
            mode = e.getMode();
            map = e.getMap();
            known = true;
        }
    }

    /**
     * Sidebar fallback. Language agnostic: the title says BED WARS and the first
     * two team rows start with R and B, which the lobby has neither of.
     */
    public static boolean bedwarsBySidebar() {
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