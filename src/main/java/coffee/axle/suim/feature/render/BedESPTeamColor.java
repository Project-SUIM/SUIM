package coffee.axle.suim.feature.render;

import coffee.axle.suim.feature.Feature;

import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.util.BedLocationAPI;
import coffee.axle.suim.util.GameLocation;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.util.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.awt.*;
import java.lang.reflect.Field;

/**
 * BedESP Color mode: team
 */
public class BedESPTeamColor extends Feature {
    private static BedESPTeamColor instance;
    private Object colorProperty;
    private Field currentValueField;
    @SuppressWarnings("unused")
    private Field bedsField;
    private String currentMap = null;
    private String currentMode = null;
    private String[] currentBedOrder = null;

    @SuppressWarnings("unused")
    private String lastMap = null;

    public BedESPTeamColor() {
        super("BedESPTeamColor");
    }

    @Override
    protected String targetModule() {
        return "BedESP";
    }

    @Override
    protected void init() throws Exception {
        instance = this;
        BedLocationAPI.fetchData();

        bedsField = manager.getCachedField(module.getClass(), MyauMappings.FIELD_BED_ESP_BEDS);

        colorProperty = manager.findProperty(module, "color");
        if (colorProperty == null) {
            MyauLogger.error("FEATURE_FAIL", new RuntimeException("Could not find BedESP color property"));
            return;
        }

        Field uField = colorProperty.getClass().getDeclaredField(MyauMappings.FIELD_ENUM_VALUES_ARRAY);
        uField.setAccessible(true);
        String[] modes = (String[]) uField.get(colorProperty);

        String[] newModes = new String[modes.length + 1];
        System.arraycopy(modes, 0, newModes, 0, modes.length);
        newModes[modes.length] = "TEAM";
        uField.set(colorProperty, newModes);

        Class<?> valueBase = manager.getCachedClass(MyauMappings.CLASS_VALUE_BASE);
        currentValueField = valueBase.getDeclaredField(MyauMappings.FIELD_VALUE_CURRENT);
        currentValueField.setAccessible(true);
    }

    public static BedESPTeamColor getInstance() {
        return instance;
    }

    public Color getTeamColorForBed(BlockPos pos) {
        try {
            Integer modeVal = (Integer) currentValueField.get(colorProperty);
            if (modeVal == null || modeVal != 2)
                return null;

            if (currentBedOrder == null)
                return null;

            double angle = Math.atan2(pos.getZ(), pos.getX());
            double sectorSize = Math.toRadians(45.0);
            int side = (int) ((angle + Math.PI * 4) / sectorSize) % 8;

            if (side >= 0 && side < currentBedOrder.length) {
                return teamColorNameToRGB(currentBedOrder[side]);
            }

        } catch (Exception e) {
            MyauLogger.error("BedESPTeamColor:tick", e);
        }
        return null;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!BedLocationAPI.isLoaded()) return;

        String map = GameLocation.getMap();
        String mode = GameLocation.getMode();
        if (map == null || mode == null) return;
        if (map.equals(currentMap) && mode.equals(currentMode)) return;

        this.currentMap = map;
        this.currentMode = mode;
        this.currentBedOrder = BedLocationAPI.getBedOrderForMap(map, mode);
    }

    private Color teamColorNameToRGB(String colorName) {
        switch (colorName.toLowerCase()) {
            case "white":
                return new Color(255, 255, 255);
            case "gray":
                return new Color(111, 111, 111);
            case "red":
                return new Color(255, 85, 85);
            case "blue":
                return new Color(85, 85, 255);
            case "green":
                return new Color(85, 255, 85);
            case "yellow":
                return new Color(255, 255, 85);
            case "aqua":
                return new Color(85, 255, 255);
            case "pink":
                return new Color(255, 85, 255);
            default:
                return Color.WHITE;
        }
    }

    @Override
    public void disable() {
        lastMap = null;
        currentBedOrder = null;
    }
}





