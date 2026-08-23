package coffee.axle.suim.hooks;

import coffee.axle.suim.util.MyauLogger;

import java.net.URL;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyauWorld {
    private static final String UNKNOWN = "unknown"; // no shit
    private static final Pattern JAR_PATTERN = Pattern.compile(
            "([^/\\\\]+)\\.jar", Pattern.CASE_INSENSITIVE);
    private static final Pattern MODID_PATTERN = Pattern.compile(
            "^[a-z][a-z0-9_-]*$");

    private static final Map<Class<?>, String> manualOrigins = new ConcurrentHashMap<>();
    private static final Map<Class<?>, String> resolvedCache = new ConcurrentHashMap<>();

    private static final String SUIM_PACKAGE_PREFIX = "coffee.axle.suim.";

    private static final List<Function<Class<?>, Optional<String>>> RESOLVERS = Arrays.asList(
            MyauWorld::resolveFromRegistry,
            MyauWorld::resolveFromSuimPackage,
            MyauWorld::resolveFromPackagePrefix,
            MyauWorld::resolveFromCodeSource,
            MyauWorld::resolveFromPackageName);

    public static void registerOrigin(Class<?> moduleClass, String origin) {
        if (moduleClass != null && origin != null && !origin.isEmpty()) {
            String normalized = origin.toLowerCase();
            manualOrigins.put(moduleClass, normalized);
            resolvedCache.put(moduleClass, normalized);
        }
    }

    public static void registerOrigin(Object module, String origin) {
        if (module != null) {
            registerOrigin(module.getClass(), origin);
        }
    }

    public static String getOrigin(Object module) {
        if (module == null)
            return UNKNOWN;
        return getOrigin(module.getClass());
    }

    public static String getOrigin(Class<?> moduleClass) {
        if (moduleClass == null)
            return UNKNOWN;
        return resolvedCache.computeIfAbsent(moduleClass, MyauWorld::resolve);
    }

    public static void clearRegistry() {
        manualOrigins.clear();
        resolvedCache.clear();
    }

    private static String resolve(Class<?> clazz) {
        for (Function<Class<?>, Optional<String>> resolver : RESOLVERS) {
            Optional<String> result = resolver.apply(clazz);
            if (result.isPresent())
                return result.get();
        }
        return UNKNOWN;
    }

    private static Optional<String> resolveFromRegistry(Class<?> clazz) {
        return Optional.ofNullable(manualOrigins.get(clazz));
    }

    private static Optional<String> resolveFromSuimPackage(Class<?> clazz) {
        if (clazz.getName().startsWith(SUIM_PACKAGE_PREFIX)) {
            return Optional.of("suim");
        }
        return Optional.empty();
    }

    private static Optional<String> resolveFromPackagePrefix(Class<?> clazz) {
        if (clazz.getName().startsWith("myau.")) {
            return Optional.of("myau");
        }
        return Optional.empty();
    }

    private static Optional<String> resolveFromCodeSource(Class<?> clazz) {
        try {
            CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
            if (codeSource == null)
                return Optional.empty();

            URL location = codeSource.getLocation();
            if (location == null)
                return Optional.empty();

            Matcher matcher = JAR_PATTERN.matcher(location.getPath());
            if (matcher.find()) {
                String jarName = matcher.group(1).toLowerCase()
                        .replaceAll("[\\d._-]+$", "");
                return Optional.of(
                        MODID_PATTERN.matcher(jarName).matches()
                                ? jarName
                                : sanitizeToModId(jarName));
            }
        } catch (Exception e) {
            MyauLogger.info("CodeSource resolution failed: " + e.getMessage());
        }
        return Optional.empty();
    }

    private static Optional<String> resolveFromPackageName(Class<?> clazz) {
        String[] parts = clazz.getName().split("\\.");
        if (parts.length >= 2) {
            String candidate = parts[1].toLowerCase();
            if (MODID_PATTERN.matcher(candidate).matches()) {
                return Optional.of(candidate);
            }
        }
        if (parts.length >= 1) {
            return Optional.of(sanitizeToModId(parts[0]));
        }
        return Optional.empty();
    }

    private static String sanitizeToModId(String input) {
        String sanitized = input.toLowerCase()
                .replaceAll("[^a-z0-9_-]", "")
                .replaceAll("^[^a-z]+", "");
        return sanitized.isEmpty() ? UNKNOWN : sanitized;
    }
}





