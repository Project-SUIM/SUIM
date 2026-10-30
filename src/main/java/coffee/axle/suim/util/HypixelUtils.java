package coffee.axle.suim.util;

import coffee.axle.suim.hooks.MyauMappings;
import net.minecraft.entity.player.EntityPlayer;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

public final class HypixelUtils {

    private static Method isBotMethod;
    private static Method isTeamMethod;
    private static Method teamColorMethod;
    private static List<String> friendNames = Collections.emptyList();
    private static boolean tried;

    private HypixelUtils() {
    }

    public static boolean isBot(EntityPlayer player) {
        init();
        if (isBotMethod == null) return false;
        try {
            return (Boolean) isBotMethod.invoke(null, player);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isTeammate(EntityPlayer player) {
        init();
        if (isTeamMethod == null) return false;
        try {
            return (Boolean) isTeamMethod.invoke(null, player);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isFriend(EntityPlayer player) {
        init();
        return friendNames.contains(player.getName());
    }

    public static Color teamColor(EntityPlayer player) {
        init();
        if (teamColorMethod == null || player == null) return new Color(-1);
        try {
            return (Color) teamColorMethod.invoke(null, player, 1.0f);
        } catch (Exception e) {
            return new Color(-1);
        }
    }

    @SuppressWarnings("deprecation")
    public static boolean inBedWarsGame() {
        if (GameLocation.isKnown()) {
            return GameLocation.inGame("BEDWARS");
        }
        return GameLocation.bwViaSidebar();
    }

    @SuppressWarnings("unchecked")
    private static void init() {
        if (tried) return;
        tried = true;
        try {
            Class<?> util = Class.forName(MyauMappings.CLASS_TEAM_UTIL);
            isBotMethod = util.getMethod(MyauMappings.METHOD_TEAM_UTIL_IS_BOT, EntityPlayer.class);
            isTeamMethod = util.getMethod(MyauMappings.METHOD_TEAM_UTIL_IS_TEAM, EntityPlayer.class);
            teamColorMethod = util.getMethod(MyauMappings.METHOD_TEAM_UTIL_GET_COLOR,
                    EntityPlayer.class, float.class);
        } catch (Exception e) {
            MyauLogger.error("HypixelUtils:team", e);
        }
        try {
            Class<?> myau = Class.forName(MyauMappings.CLASS_MAIN);
            Field friendManagerField = myau.getDeclaredField(MyauMappings.FIELD_FRIEND_MANAGER);
            friendManagerField.setAccessible(true);
            Object friendManager = friendManagerField.get(null);
            Field playersField = friendManager.getClass().getSuperclass()
                    .getDeclaredField(MyauMappings.FIELD_PLAYER_LIST);
            playersField.setAccessible(true);
            // live reference, myau mutates this list in place
            friendNames = (List<String>) playersField.get(friendManager);
        } catch (Exception e) {
            MyauLogger.error("HypixelUtils:friends", e);
        }
    }
}
