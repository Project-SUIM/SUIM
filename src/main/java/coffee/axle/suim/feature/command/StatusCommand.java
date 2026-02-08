package coffee.axle.suim.feature.command;

import coffee.axle.suim.feature.Feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Command to check player's online status and recent games
 * Usage: .status <playername>
 * 
 * @maybsomeday
 */
public class StatusCommand extends Feature {
    private final Minecraft mc = Minecraft.getMinecraft();

    @Override
    public String getName() {
        return "Command:Status";
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");
            ArrayList<String> commandNames = new ArrayList<>(Arrays.asList("status"));
            creator.registerCommand(commandNames, this::handleCommand);
            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;
        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    private void handleCommand(ArrayList<String> args) {
        if (args.size() < 2) {
            manager.sendMessage("§c§lUsage: §7.status §5<playername>");
            return;
        }

        String username = args.get(1);

        new Thread(() -> {
            try {
                PlayerInfo playerInfo = fetchUUID(username);

                if (playerInfo == null) {
                    manager.sendMessage("§c" + username + " not found or API error.");
                    return;
                }

                String formattedName = playerInfo.name;
                String uuid = playerInfo.uuid;

                String statusJson = fetchStatus(uuid);
                if (statusJson == null) {
                    manager.sendMessage("§cFailed to fetch status for " + formattedName);
                    return;
                }

                JsonObject statusResponse = new JsonParser().parse(statusJson).getAsJsonObject();
                if (!statusResponse.has("success") || !statusResponse.get("success").getAsBoolean()) {
                    manager.sendMessage("§cCould not retrieve status for " + formattedName);
                    return;
                }

                String recentJson = fetchRecent(uuid);
                JsonObject recentResponse = null;
                JsonArray games = null;

                if (recentJson != null) {
                    recentResponse = new JsonParser().parse(recentJson).getAsJsonObject();
                    if (recentResponse.has("success") && recentResponse.get("success").getAsBoolean()) {
                        games = recentResponse.getAsJsonArray("games");
                    }
                }

                JsonObject session = statusResponse.getAsJsonObject("session");
                boolean online = session.get("online").getAsBoolean();

                sendMessage("§7§m-----------------------------------");
                sendMessage("§e" + formattedName + "§r's status");
                sendMessage("§7§m-----------------------------------");

                if (online) {
                    String gameType = session.has("gameType") ? session.get("gameType").getAsString() : "Unknown";
                    String mode = session.has("mode") ? session.get("mode").getAsString() : "Unknown";
                    String map = session.has("map") ? session.get("map").getAsString() : "Unknown";

                    String formattedGameType = formatGameType(gameType);
                    String formattedMode = formatMode(mode);

                    sendMessage("§astatus: §2ONLINE");
                    sendMessage("§7game: §6" + formattedGameType);
                    sendMessage("§7mode: §b" + formattedMode);
                    sendMessage("§7map: §d" + map);
                } else {
                    sendMessage("§astatus: §cOFFLINE");
                }

                if (games != null && games.size() > 0) {
                    sendMessage("");
                    sendMessage("§erecent games §7(" + Math.min(games.size(), 5) + ")");
                    sendMessage("§7§m-----------------------------------");

                    for (int i = 0; i < Math.min(games.size(), 5); i++) {
                        JsonObject game = games.get(i).getAsJsonObject();

                        long date = game.get("date").getAsLong();
                        String gameType = game.get("gameType").getAsString();
                        String mode = game.get("mode").getAsString();
                        String map = game.get("map").getAsString();
                        boolean hasEnded = game.has("ended");

                        String formattedGameType = formatGameType(gameType);
                        String formattedMode = formatMode(mode);
                        String timeAgo = getTimeAgo(date);
                        String status = hasEnded ? "§aEnded" : "§eOngoing";

                        String duration = "";
                        if (hasEnded) {
                            long ended = game.get("ended").getAsLong();
                            long durationMs = ended - date;
                            duration = " §7(" + formatDuration(durationMs) + ")";
                        }

                        sendMessage("§7" + (i + 1) + ". " + status + " §6" + formattedGameType +
                                " §b" + formattedMode + " §7on §d" + map + duration + " §8- " + timeAgo);
                    }
                } else {
                    sendMessage("");
                    sendMessage("§7no recent games found.");
                }

                sendMessage("§7§m-----------------------------------");

            } catch (Exception e) {
                manager.sendMessage("§cerror fetching data for " + username);
                MyauLogger.error("StatusCommand:modules", e);
            }
        }).start();
    }

    private void sendMessage(String message) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText(message));
            }
        });
    }

    private PlayerInfo fetchUUID(String username) {
        try {
            String urlString = "https://api.minecraftservices.com/minecraft/profile/lookup/name/" + username;
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JsonObject json = new JsonParser().parse(response.toString()).getAsJsonObject();
                String uuid = json.get("id").getAsString();
                String name = json.get("name").getAsString();
                return new PlayerInfo(name, uuid);
            }
        } catch (Exception e) {
        }

        try {
            String urlString = "https://api.minetools.eu/uuid/" + username;
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JsonObject json = new JsonParser().parse(response.toString()).getAsJsonObject();
                if (json.has("status") && json.get("status").getAsString().equals("OK")) {
                    String uuid = json.get("id").getAsString();
                    String name = json.get("name").getAsString();
                    return new PlayerInfo(name, uuid);
                }
            }
        } catch (Exception e) {
            MyauLogger.error("StatusCommand:system", e);
        }

        return null;
    }

    private String fetchStatus(String uuid) {
        try {
            String urlString = "https://www.shmeado.club/player/online/" + uuid;
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                return response.toString();
            }
        } catch (Exception e) {
            MyauLogger.error("StatusCommand:render", e);
        }

        return null;
    }

    private String fetchRecent(String uuid) {
        try {
            String urlString = "https://www.shmeado.club/player/recent/" + uuid;
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                return response.toString();
            }
        } catch (Exception e) {
            MyauLogger.error("StatusCommand:handler", e);
        }

        return null;
    }

    private String formatGameType(String gameType) {
        switch (gameType) {
            case "BEDWARS":
                return "Bed Wars";
            case "DUELS":
                return "Duels";
            case "SKYWARS":
                return "SkyWars";
            case "LOBBY":
                return "Lobby";
            default:
                return gameType;
        }
    }

    private String formatMode(String mode) {
        mode = mode.replace("BEDWARS_", "").replace("DUELS_", "").replace("SKYWARS_", "");

        switch (mode) {
            case "EIGHT_ONE":
                return "Solo";
            case "EIGHT_TWO":
                return "Doubles";
            case "FOUR_THREE":
                return "3v3v3v3";
            case "FOUR_FOUR":
                return "4v4";
            case "TWO_FOUR":
                return "4v4v4v4";
            case "CASTLE":
                return "Castle";
            case "CLASSIC_DUEL":
                return "Classic 1v1";
            case "SUMO_DUEL":
                return "Sumo 1v1";
            case "UHC_DUEL":
                return "UHC 1v1";
            case "OP_DUEL":
                return "OP 1v1";
            case "BRIDGE_DUEL":
                return "Bridge 1v1";
            case "solo_normal":
                return "Solo Normal";
            case "solo_insane":
                return "Solo Insane";
            case "teams_normal":
                return "Teams Normal";
            case "teams_insane":
                return "Teams Insane";
            default:
                return mode.replace("_", " ");
        }
    }

    private String getTimeAgo(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "d ago";
        } else if (hours > 0) {
            return hours + "h ago";
        } else if (minutes > 0) {
            return minutes + "m ago";
        } else {
            return seconds + "s ago";
        }
    }

    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;

        if (minutes > 0) {
            return minutes + "m " + remainingSeconds + "s";
        } else {
            return seconds + "s";
        }
    }

    @Override
    public void disable() {
    }

    private static class PlayerInfo {
        String name;
        String uuid;

        PlayerInfo(String name, String uuid) {
            this.name = name;
            this.uuid = uuid;
        }
    }
}
