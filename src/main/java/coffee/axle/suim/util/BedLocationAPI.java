package coffee.axle.suim.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Fetches off Polyfrost data to get bed locations for BedESP teams color mode
 * 
 * @author maybsomeday (meow)
 */
public class BedLocationAPI {
    private static final String API_URL = "https://data.polyfrost.org/bed_locations.json";
    private static JsonObject bedData = null;
    private static boolean loaded = false;

    private static final Map<String, Integer> COLOR_TO_INDEX = new HashMap<String, Integer>() {
        {
            put("yellow", 0);
            put("aqua", 1);
            put("white", 2);
            put("pink", 3);
            put("gray", 4);
            put("red", 5);
            put("blue", 6);
            put("green", 7);
        }
    };

    public static void fetchData() {
        if (loaded)
            return;

        new Thread(() -> {
            try {
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "dMyau/1.0");

                InputStreamReader reader = new InputStreamReader(conn.getInputStream());
                bedData = new JsonParser().parse(reader).getAsJsonObject();
                reader.close();
                loaded = true;
            } catch (Exception e) {
                MyauLogger.error("BedLocationAPI:init", e);
            }
        }).start();
    }

    public static String[] getBedOrderForMap(String mapName, String mode) {
        if (bedData == null)
            return getDefaultOrder();

        try {
            // Check overrides
            JsonArray overrides = bedData.getAsJsonArray("overrides");
            for (JsonElement override : overrides) {
                JsonObject obj = override.getAsJsonObject();

                // Check if this override matches the map
                if (obj.has("maps") && mapName != null) {
                    JsonArray maps = obj.getAsJsonArray("maps");
                    for (JsonElement mapElement : maps) {
                        if (mapElement.getAsString().equalsIgnoreCase(mapName)) {
                            return parseColorArray(obj.getAsJsonArray("locations"));
                        }
                    }
                }

                // Check if this override matches the mode
                if (obj.has("modes") && mode != null) {
                    JsonArray modes = obj.getAsJsonArray("modes");
                    for (JsonElement modeElement : modes) {
                        if (modeElement.getAsString().equalsIgnoreCase(mode)) {
                            return parseColorArray(obj.getAsJsonArray("locations"));
                        }
                    }
                }
            }
        } catch (Exception e) {
            MyauLogger.error("BedLocationAPI:overrides", e);
        }

        // Return default order
        return getDefaultOrder();
    }

    private static String[] parseColorArray(JsonArray array) {
        String[] colors = new String[8];
        int i = 0;
        for (JsonElement element : array) {
            if (i >= 8)
                break;
            colors[i++] = element.getAsString();
        }
        return colors;
    }

    private static String[] getDefaultOrder() {
        if (bedData != null && bedData.has("default")) {
            return parseColorArray(bedData.getAsJsonArray("default"));
        }
        // Fallback default
        return new String[] { "yellow", "aqua", "white", "pink", "gray", "red", "blue", "green" };
    }

    public static int colorToIndex(String color) {
        return COLOR_TO_INDEX.getOrDefault(color.toLowerCase(), 5);
    }
}
