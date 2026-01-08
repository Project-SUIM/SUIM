package coffee.axle.suim.features;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import coffee.axle.suim.hooks.MyauHook;
import coffee.axle.suim.util.BedLocationAPI;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.*;

/**
 * BedESP Color mode: team
 */
public class BedESPTeamColor implements Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final MyauHook hook = MyauHook.getInstance();

    private static BedESPTeamColor instance;
    private Object bedESPModule;
    private Object colorProperty;
    private Field valuesField;
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

            Class<?> moduleManagerClass = Class.forName("myau.mJ");
            Field mmField = hook.getClass().getDeclaredField("moduleManager");
            mmField.setAccessible(true);
            Object moduleManager = mmField.get(hook);

            Field modulesField = moduleManagerClass.getDeclaredField("E");
            modulesField.setAccessible(true);
            LinkedHashMap<Class<?>, Object> modulesMap = (LinkedHashMap<Class<?>, Object>) modulesField
                    .get(moduleManager);

            for (Object module : modulesMap.values()) {
                try {
                    java.lang.reflect.Method getNameMethod = module.getClass().getSuperclass().getDeclaredMethod("J");
                    getNameMethod.setAccessible(true);
                    if ("BedESP".equals(getNameMethod.invoke(module))) {
                        bedESPModule = module;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }

            if (bedESPModule == null) {
                MyauLogger.error("FEATURE_FAIL", new RuntimeException("BedESP module not found"));
                return false;
            }

            for (Field f : bedESPModule.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                if (f.getType() == java.util.concurrent.CopyOnWriteArraySet.class) {
                    bedsField = f;
                    break;
                }
            }

            if (bedsField == null) {
                MyauLogger.error("FEATURE_FAIL", new RuntimeException("Could not find 'beds' field"));
                return false;
            }

            boolean modified = false;
            for (Field field : bedESPModule.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(bedESPModule);

                if (value != null && value.getClass().getName().equals("myau.n")) {
                    Field uField = value.getClass().getDeclaredField("u");
                    uField.setAccessible(true);
                    String[] modes = (String[]) uField.get(value);

                    if (modes.length >= 2 && modes[0].equals("CUSTOM") && modes[1].equals("HUD")) {
                        colorProperty = value;
                        valuesField = uField;

                        String[] newModes = new String[modes.length + 1];
                        System.arraycopy(modes, 0, newModes, 0, modes.length);
                        newModes[modes.length] = "TEAM";

                        valuesField.set(colorProperty, newModes);
                        currentValueField = colorProperty.getClass().getSuperclass().getDeclaredField("J");
                        currentValueField.setAccessible(true);

                        modified = true;
                        break;
                    }
                }
            }

            if (!modified) {
                MyauLogger.error("FEATURE_FAIL", new RuntimeException("Could not inject TEAM mode"));
                return false;
            }

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
            e.printStackTrace();
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
