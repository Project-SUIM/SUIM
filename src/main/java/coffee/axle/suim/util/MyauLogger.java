package coffee.axle.suim.util;

import java.util.HashMap;
import java.util.Map;

public class MyauLogger {
    private static String PREFIX = "[d'Myau]";
    private static final Map<String, String> ERROR_TO_MSG = new HashMap<>();

    static {
        initializeCodes();
    }

    /**
     * Set the prefix/label used in all log messages.
     * Call this early in initialization (e.g., in your mod's init).
     */
    public static void setPrefix(String prefix) {
        PREFIX = prefix;
    }

    public static String getPrefix() {
        return PREFIX;
    }

    private static void initializeCodes() {
        ERROR_TO_MSG.put("HOOK_INIT", "Initializing hook...");
        ERROR_TO_MSG.put("HOOK_NOT_READY", "Hook not ready yet");
        ERROR_TO_MSG.put("HOOK_SUCCESS", "Hook initialized successfully");
        ERROR_TO_MSG.put("HOOK_FAIL", "Hook initialization failed");
        ERROR_TO_MSG.put("MODULE_NOT_FOUND", "Module not found");
        ERROR_TO_MSG.put("MODULE_FAIL", "Module initialization failed");
        ERROR_TO_MSG.put("PROPERTY_NOT_FOUND", "Property not found");
        ERROR_TO_MSG.put("FEATURE_INIT", "Initializing feature...");
        ERROR_TO_MSG.put("FEATURE_SUCCESS", "Feature initialized!");
        ERROR_TO_MSG.put("FEATURE_FAIL", "Feature initialization failed");
        ERROR_TO_MSG.put("PROPERTY_INJECT_FAIL", "Property injection failed");
        ERROR_TO_MSG.put("PROPERTY_INJECT_SUCCESS", "Property injected!");
        ERROR_TO_MSG.put("FM_HOOK_FAIL", "Feature manager hook failed");
        ERROR_TO_MSG.put("FM_SUCCESS", "Feature manager ready!");
        ERROR_TO_MSG.put("FM_PARTIAL", "Some features loaded");
        ERROR_TO_MSG.put("FM_NO_TUNA", "No features loaded");
        ERROR_TO_MSG.put("INIT_START", "Starting initialization...");
        ERROR_TO_MSG.put("CRITICAL_ERROR", "Critical error!");
        ERROR_TO_MSG.put("CALLBACK_FAIL", "Callback execution failed");
        ERROR_TO_MSG.put("ALL_GOOD", "All good!");
    }

    public static void log(String code, Object... args) {
        String msg = ERROR_TO_MSG.get(code);
        if (msg == null) {
            System.out.println(PREFIX + " " + code);
        } else {
            if (args.length > 0) {
                msg = String.format(msg, args);
            }
            System.out.println(PREFIX + " " + msg);
        }
    }

    public static void log(String component, String code, Object... args) {
        String msg = ERROR_TO_MSG.get(code);
        if (msg == null) {
            System.out.println(PREFIX + " [" + component + "] " + code);
        } else {
            if (args.length > 0) {
                msg = String.format(msg, args);
            }
            System.out.println(PREFIX + " [" + component + "] " + msg);
        }
    }

    public static void logMore(String code, String details) {
        String msg = ERROR_TO_MSG.get(code);
        if (msg == null) {
            System.out.println(PREFIX + " " + code);
        } else {
            System.out.println(PREFIX + " " + msg + " (" + details + ")");
        }
    }

    public static void error(String code, Exception e) {
        log(code);
        System.out.println(PREFIX + " EXC: " + e.getMessage());
        e.printStackTrace();
    }

    public static void info(String message) {
        System.out.println(PREFIX + " " + message);
    }

    public static void status(String component, boolean success) {
        if (success) {
            System.out.println(PREFIX + " [" + component + "] ✓");
        } else {
            System.out.println(PREFIX + " [" + component + "] ✗");
        }
    }

    public static void summary(int successful, int total) {
        if (successful == total && total > 0) {
            log("FM_SUCCESS");
        } else if (successful > 0) {
            logMore("FM_PARTIAL", successful + "/" + total);
        } else {
            log("FM_NO_TUNA");
        }
    }
}





