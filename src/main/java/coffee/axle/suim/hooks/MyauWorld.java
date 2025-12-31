package coffee.axle.suim.hooks;

import java.net.URL;
import java.security.CodeSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyauWorld {
    private static final Map<Class<?>, String> originRegistry = new ConcurrentHashMap<>();
    private static final Pattern JAR_PATTERN = Pattern.compile("([^/\\\\]+)\\.jar", Pattern.CASE_INSENSITIVE);
    private static final Pattern MODID_PATTERN = Pattern.compile("^[a-z][a-z0-9_-]*$");

    public static void registerOrigin(Class<?> moduleClass, String origin) {
        if (moduleClass != null && origin != null && !origin.isEmpty()) {
            originRegistry.put(moduleClass, origin.toLowerCase());
        }
    }

    public static void registerOrigin(Object module, String origin) {
        if (module != null) {
            registerOrigin(module.getClass(), origin);
        }
    }

    public static String getOrigin(Object module) {
        if (module == null)
            return "unknown";
        return getOrigin(module.getClass());
    }

    public static String getOrigin(Class<?> moduleClass) {
        if (moduleClass == null)
            return "unknown";

        String registered = originRegistry.get(moduleClass);
        if (registered != null)
            return registered;

        String className = moduleClass.getName();

        if (className.startsWith("myau.")) {
            return "myau";
        }

        String fromJar = extractFromCodeSource(moduleClass);
        if (fromJar != null)
            return fromJar;

        return extractFromPackage(className);
    }

    private static String extractFromCodeSource(Class<?> clazz) {
        try {
            CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
            if (codeSource == null)
                return null;

            URL location = codeSource.getLocation();
            if (location == null)
                return null;

            String path = location.getPath();
            Matcher matcher = JAR_PATTERN.matcher(path);
            if (matcher.find()) {
                String jarName = matcher.group(1).toLowerCase();
                jarName = jarName.replaceAll("[\\d._-]+$", "");
                if (MODID_PATTERN.matcher(jarName).matches()) {
                    return jarName;
                }
                return sanitizeToModId(jarName);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String extractFromPackage(String className) {
        String[] parts = className.split("\\.");
        if (parts.length >= 2) {
            String candidate = parts[1].toLowerCase();
            if (MODID_PATTERN.matcher(candidate).matches()) {
                return candidate;
            }
        }
        if (parts.length >= 1) {
            return sanitizeToModId(parts[0]);
        }
        return "unknown";
    }

    private static String sanitizeToModId(String input) {
        String sanitized = input.toLowerCase()
                .replaceAll("[^a-z0-9_-]", "")
                .replaceAll("^[^a-z]+", "");
        return sanitized.isEmpty() ? "unknown" : sanitized;
    }

    public static Map<String, String> getModuleOrigins(MyauHook hook) {
        Map<String, String> result = new LinkedHashMap<>();
        if (hook == null || hook.getModulesMap() == null)
            return result;

        for (Map.Entry<Class<?>, Object> entry : hook.getModulesMap().entrySet()) {
            try {
                String name = hook.getModuleName(entry.getValue());
                String origin = getOrigin(entry.getValue());
                result.put(name, origin);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    public static String lookupModule(MyauHook hook, String moduleName) {
        if (hook == null || moduleName == null)
            return "unknown";

        for (Map.Entry<Class<?>, Object> entry : hook.getModulesMap().entrySet()) {
            try {
                String name = hook.getModuleName(entry.getValue());
                if (moduleName.equalsIgnoreCase(name)) {
                    return getOrigin(entry.getValue());
                }
            } catch (Exception ignored) {
            }
        }
        return "unknown";
    }

    public static void clearRegistry() {
        originRegistry.clear();
    }
}
