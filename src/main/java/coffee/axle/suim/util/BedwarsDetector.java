package coffee.axle.suim.util;

import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;

/**
 * Bedwars game check, kol cG.c flavor. Title must say BED WARS, then the
 * first 10 sidebar lines need both an R and a B team entry. The team check
 * is language agnostic (Rouge/Bleu etc) and the lobby has neither.
 */
public final class BedwarsDetector {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private BedwarsDetector() {}

    public static boolean inBedWarsGame() {
        if (mc.theWorld == null) return false;
        Scoreboard sb = mc.theWorld.getScoreboard();
        if (sb == null) return false;
        ScoreObjective obj = sb.getObjectiveInDisplaySlot(1);
        if (obj == null) return false;
        if (!strip(obj.getDisplayName()).contains("BED WARS")) return false;
        // language agnostic: any team sidebar (red/rouge, blue/bleu, ...) shows
        // the first two team entries as R and B. both present = bedwars game
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
