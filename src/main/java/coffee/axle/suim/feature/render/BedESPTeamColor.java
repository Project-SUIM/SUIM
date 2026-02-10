package coffee.axle.suim.feature.render;

import coffee.axle.suim.feature.Feature;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.util.BedLocationAPI;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.awt.*;
import java.lang.reflect.Field;

/**
 * BedESP Color mode: team
 */
@SuppressWarnings("unused")
public class BedESPTeamColor extends Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static BedESPTeamColor instance;
    private Object bedESPModule;
    private Object colorProperty;
    private Field currentValueField;
    private Field bedsField;
    private String currentMap = null;
    private String currentMode = null;
    private String[] currentBedOrder = null;

    private boolean awaitingLocraw = false;
    private String lastMap = null;

    @Override
    public String getName() {
        return "BedESPTeamColor";
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");

            instance = this;
            BedLocationAPI.fetchData();

            bedESPModule = manager.findModule("BedESP");
            if (bedESPModule == null) {
                MyauLogger.error("FEATURE_FAIL", new RuntimeException("BedESP module not found"));
                return false;
            }

            bedsField = manager.getCachedField(bedESPModule.getClass(), MyauMappings.FIELD_BED_ESP_BEDS);

            colorProperty = manager.findProperty(bedESPModule, "color");
            if (colorProperty == null) {
                MyauLogger.error("FEATURE_FAIL", new RuntimeException("Could not find BedESP color property"));
                return false;
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

            MinecraftForge.EVENT_BUS.register(this);

            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;

        } catch (Exception t) {
            MyauLogger.error("FEATURE_FAIL", t);
            return false;
        }
    }

    public static BedESPTeamColor getInstance() {
        return instance;
    }

    public Color getTeamColorForBed(BlockPos pos) {
        try {
            Integer modeVal = (Integer) currentValueField.get(colorProperty);
            if (modeVal == null || modeVal != 2)
                return null;

            if (currentBedOrder == null && currentMap == null && mc.thePlayer != null) {
                // Placeholder for map detection
            }

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
    public void onClientChat(ClientChatReceivedEvent event) {
        String message = event.message.getUnformattedText();

        if (message.startsWith("Sending you to mini") || message.startsWith("Sending you to mega")) {
            new Thread(() -> {
                try {
                    Thread.sleep(400);
                } catch (InterruptedException e) {
                }
                if (mc.thePlayer != null) {
                    mc.thePlayer.sendChatMessage("/locraw");
                    awaitingLocraw = true;
                }
            }).start();
        }

        if (awaitingLocraw && message.startsWith("{") && message.contains("\"gametype\"")) {
            try {
                JsonObject json = new JsonParser().parse(message).getAsJsonObject();

                if (json.has("map") && json.has("mode")) {
                    this.currentMap = json.get("map").getAsString();
                    this.currentMode = json.get("mode").getAsString();
                    this.currentBedOrder = BedLocationAPI.getBedOrderForMap(currentMap, currentMode);
                    event.setCanceled(true);
                }

                awaitingLocraw = false;

            } catch (Exception e) {
                // Silent fail
            }
        }
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





