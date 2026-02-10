package coffee.axle.suim.clickgui;

import coffee.axle.suim.clickgui.mode.cga.CgaClickGuiMode;
import coffee.axle.suim.clickgui.mode.flopper.FlopperClickGuiMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ClickGuiModeRegistry {
    private static final List<ClickGuiMode> MODES = new ArrayList<>();
    private static boolean defaultsRegistered = false;

    private ClickGuiModeRegistry() {
    }

    public static void registerDefaults() {
        if (defaultsRegistered) {
            return;
        }
        register(new CgaClickGuiMode());
        register(new FlopperClickGuiMode());
        defaultsRegistered = true;
    }

    public static void register(ClickGuiMode mode) {
        if (mode == null) {
            return;
        }
        MODES.add(mode);
    }

    public static List<ClickGuiMode> getModes() {
        return Collections.unmodifiableList(MODES);
    }

    public static ClickGuiMode getMode(int index) {
        if (MODES.isEmpty()) {
            return null;
        }
        int clamped = Math.max(0, Math.min(index, MODES.size() - 1));
        return MODES.get(clamped);
    }

    public static String[] getModeIds() {
        List<String> ids = new ArrayList<>();
        for (ClickGuiMode mode : MODES) {
            ids.add(mode.getId());
        }
        return ids.toArray(new String[0]);
    }
}
