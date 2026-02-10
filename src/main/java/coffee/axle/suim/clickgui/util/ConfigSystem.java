package coffee.axle.suim.clickgui.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;

/**
 * JSON config save/load using Gson.
 *
 * <p>
 * Usage example:
 * 
 * <pre>{@code
 * File exampleFile = new File("config/suim/example.json");
 *
 * List<MyData> loaded = ConfigSystem.loadConfig(exampleFile,
 *         new TypeToken<List<MyData>>() {
 *         }.getType());
 *
 * ConfigSystem.saveConfig(exampleFile, myDataList);
 * }</pre>
 */
public final class ConfigSystem {

    public static final ConfigSystem INSTANCE = new ConfigSystem();

    private static final Gson gson = new Gson();

    private ConfigSystem() {
    }

    /**
     * Saves the given object as JSON to the specified file.
     */
    public static void saveConfig(File file, Object configData) {
        try {
            FileWriter writer = new FileWriter(file);
            gson.toJson(configData, writer);
            writer.close();
        } catch (IOException e) {
            // silently ignore
        }
    }

    /**
     * Loads a JSON config from the specified file.
     *
     * @param file The file to read from.
     * @param type The type to deserialize into (use
     *             {@code new TypeToken<...>(){}.getType()}).
     * @param <T>  The return type.
     * @return The deserialized object, or {@code null} if the file doesn't exist or
     *         an error occurs.
     */
    @SuppressWarnings("unchecked")
    public static <T> T loadConfig(File file, Type type) {
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException ignored) {
            }
            return null;
        }

        try {
            FileReader reader = new FileReader(file);
            T configData = gson.fromJson(reader, type);
            reader.close();
            return configData;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Saves a single key-value pair to the specified JSON file.
     * Loads existing data first, merges, then saves.
     */
    @SuppressWarnings("unchecked")
    public static void saveToFile(File file, String key, Object value) {
        Type mapType = new TypeToken<java.util.Map<String, Object>>() {
        }.getType();
        java.util.Map<String, Object> configData = loadConfig(file, mapType);
        if (configData == null) {
            configData = new java.util.HashMap<>();
        }
        configData.put(key, value);
        saveConfig(file, configData);
    }

    /**
     * Loads a single value by key from the specified JSON file.
     *
     * @param file  The file to read from.
     * @param key   The JSON key to retrieve.
     * @param clazz The class of the value type.
     * @param <T>   The return type.
     * @return The value, or {@code null} if not found or an error occurs.
     */
    @SuppressWarnings("unchecked")
    public static <T> T loadFromFile(File file, String key, Class<T> clazz) {
        Type mapType = new TypeToken<java.util.Map<String, Object>>() {
        }.getType();
        java.util.Map<String, Object> configData = loadConfig(file, mapType);
        if (configData == null) {
            return null;
        }
        Object raw = configData.get(key);
        if (raw == null) {
            return null;
        }
        // Re-serialize and deserialize for proper type conversion
        String json = gson.toJson(raw);
        return gson.fromJson(json, clazz);
    }
}





