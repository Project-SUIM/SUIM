package coffee.axle.suim.feature.command;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.util.MyauLogger;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;

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
            manager.sendMessage("&c&lUsage: &7.status &5<playername>");
            return;
        }

        String username = args.get(1);

        new Thread(() -> {
            try {
                PlayerInfo playerInfo = fetchUUID(username);
                if (playerInfo == null) {
                    manager.sendMessage("&c" + username + " not found or API error.");
                    return;
                }

                String statusJson = fetchStatus(playerInfo.uuid);
                if (statusJson == null) {
                    manager.sendMessage("&cFailed to fetch status for " + playerInfo.name);
                    return;
                }

                JsonObject statusResponse = new JsonParser().parse(statusJson).getAsJsonObject();
                if (!statusResponse.has("success") || !statusResponse.get("success").getAsBoolean()) {
                    manager.sendMessage("&cCould not retrieve status for " + playerInfo.name);
                    return;
                }

                JsonObject recentResponse = null;
                JsonArray games = null;
                String recentJson = fetchRecent(playerInfo.uuid);
                if (recentJson != null) {
                    recentResponse = new JsonParser().parse(recentJson).getAsJsonObject();
                    if (recentResponse.has("success") && recentResponse.get("success").getAsBoolean()) {
                        games = recentResponse.getAsJsonArray("games");
                    }
                }

                JsonObject session = statusResponse.getAsJsonObject("session");
                boolean online = session.get("online").getAsBoolean();

                sendMessage(color("&7&m-----------------------------------"));
                sendMessage(color("&e" + playerInfo.name + "&r's status"));
                sendMessage(color("&7&m-----------------------------------"));

                if (online) {
                    String gameType = session.has("gameType") ? session.get("gameType").getAsString() : "Unknown";
                    String mode = session.has("mode") ? session.get("mode").getAsString() : "Unknown";
                    String map = session.has("map") ? session.get("map").getAsString() : "Unknown";

                    sendMessage(color("&astatus: &2ONLINE"));
                    sendMessage(color("&7game: &6" + formatGameType(gameType)));
                    sendMessage(color("&7mode: &b" + formatMode(mode)));
                    sendMessage(color("&7map: &d" + map));
                } else {
                    sendMessage(color("&astatus: &cOFFLINE"));
                }

                if (games != null && games.size() > 0) {
                    sendMessage("");
                    sendMessage(color("&erecent games &7(" + Math.min(games.size(), 5) + ")"));
                    sendMessage(color("&7&m-----------------------------------"));

                    for (int i = 0; i < Math.min(games.size(), 5); i++) {
                        JsonObject game = games.get(i).getAsJsonObject();
                        long date = game.get("date").getAsLong();
                        String gameType = game.get("gameType").getAsString();
                        String mode = game.get("mode").getAsString();
                        String map = game.get("map").getAsString();

                        boolean hasEnded = game.has("ended");
                        String status = hasEnded ? "&aEnded" : "&eOngoing";
                        String duration = "";

                        if (hasEnded) {
                            long ended = game.get("ended").getAsLong();
                            duration = " &7(" + formatDuration(ended - date) + ")";
                        }

                        sendMessage(color("&7" + (i + 1) + ". " + status +
                                " &6" + formatGameType(gameType) +
                                " &b" + formatMode(mode) +
                                " &7on &d" + map + duration +
                                " &8- " + getTimeAgo(date)));
                    }
                } else {
                    sendMessage("");
                    sendMessage(color("&7no recent games found."));
                }

                sendMessage(color("&7&m-----------------------------------"));
            } catch (Exception e) {
                manager.sendMessage("&cError fetching data for " + username);
                MyauLogger.error("StatusCommand:handle", e);
            }
        }, "dmyau-status").start();
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
            JsonObject json = getJson(urlString);
            if (json != null && json.has("id") && json.has("name")) {
                return new PlayerInfo(json.get("name").getAsString(), json.get("id").getAsString());
            }
        } catch (Exception ignored) {
        }

        try {
            String urlString = "https://api.minetools.eu/uuid/" + username;
            JsonObject json = getJson(urlString);
            if (json != null && json.has("status") && "OK".equals(json.get("status").getAsString())) {
                return new PlayerInfo(json.get("name").getAsString(), json.get("id").getAsString());
            }
        } catch (Exception e) {
            MyauLogger.error("StatusCommand:uuid", e);
        }

        return null;
    }

    private String fetchStatus(String uuid) {
        return getRaw("https://www.shmeado.club/player/online/" + uuid);
    }

    private String fetchRecent(String uuid) {
        return getRaw("https://www.shmeado.club/player/recent/" + uuid);
    }

    private JsonObject getJson(String urlString) {
        String raw = getRaw(urlString);
        if (raw == null) {
            return null;
        }
        return new JsonParser().parse(raw).getAsJsonObject();
    }

    private String getRaw(String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }

            in.close();
            return response.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
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
        mode = mode.replace("BEDWARS_", "")
                .replace("DUELS_", "")
                .replace("SKYWARS_", "");

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
        }
        if (hours > 0) {
            return hours + "h ago";
        }
        if (minutes > 0) {
            return minutes + "m ago";
        }
        return seconds + "s ago";
    }

    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;

        if (minutes > 0) {
            return minutes + "m " + remainingSeconds + "s";
        }
        return seconds + "s";
    }

    private String color(String text) {
        return text.replace('&', '§');
    }

    private static class PlayerInfo {
        private final String name;
        private final String uuid;

        private PlayerInfo(String name, String uuid) {
            this.name = name;
            this.uuid = uuid;
        }
    }
}
