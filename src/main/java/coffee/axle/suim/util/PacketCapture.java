package coffee.axle.suim.util;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import coffee.axle.suim.events.ReceivePacketEvent;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import static coffee.axle.suim.ProjectSUIM.mc;

/**
 * Runtime packet capture for reverse engineering (.packetcapture).
 * Logs both directions of the connection as JSON; fields are dumped by
 * reflection so every packet type works without special handling.
 */
public final class PacketCapture {

    private static final PacketCapture INSTANCE = new PacketCapture();
    private static final int MAX_ENTRIES = 20000;

    public static PacketCapture getInstance() {
        return INSTANCE;
    }

    private final List<JsonObject> entries = new ArrayList<JsonObject>();
    private final Map<Class<?>, Field[]> fieldCache = new ConcurrentHashMap<Class<?>, Field[]>();

    private volatile boolean capturing;
    private boolean overflowed;
    private String targetName;
    private int targetEntityId = -1;
    private long startMs;
    private String lastOutputPath;

    private PacketCapture() {
    }

    public void register() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    public boolean isCapturing() {
        return capturing;
    }

    public String getLastOutputPath() {
        return lastOutputPath;
    }

    public int getEntryCount() {
        synchronized (entries) {
            return entries.size();
        }
    }

    public void start() {
        synchronized (entries) {
            entries.clear();
        }
        overflowed = false;
        startMs = System.currentTimeMillis();
        capturing = true;
    }

    public void setTarget(String name) {
        targetName = name;
        targetEntityId = -1;
    }

    public void stop() {
        capturing = false;
        String json = buildJson();
        try {
            File out = new File("packetcapture-"
                    + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".json");
            FileWriter writer = new FileWriter(out);
            writer.write(json);
            writer.close();
            lastOutputPath = out.getAbsolutePath();
        } catch (Exception e) {
            MyauLogger.error("PacketCapture:write", e);
        }
        // clipboard can silently fail on some setups, the file is the backup
        try {
            GuiScreen.setClipboardString(json);
        } catch (Exception e) {
            MyauLogger.error("PacketCapture:clipboard", e);
        }
    }

    @SubscribeEvent
    public void onReceive(ReceivePacketEvent e) {
        capture(e.getPacket(), true);
    }

    public void recordSent(Packet<?> packet) {
        capture(packet, false);
    }

    private void capture(Packet<?> packet, boolean inbound) {
        if (!capturing || packet == null)
            return;
        if (packet instanceof C00PacketKeepAlive || packet instanceof S00PacketKeepAlive)
            return;
        synchronized (entries) {
            if (entries.size() >= MAX_ENTRIES) {
                overflowed = true;
                return;
            }
        }
        if (targetName != null && targetEntityId == -1)
            resolveTarget();

        JsonObject o = new JsonObject();
        o.addProperty("t", System.currentTimeMillis() - startMs);
        o.addProperty("dir", inbound ? "S2C" : "C2S");
        o.addProperty("cls", packet.getClass().getSimpleName());
        if (!inbound && mc.thePlayer != null) {
            // real angles next to the packet's yaw makes spoof desyncs obvious
            o.addProperty("clientYaw", mc.thePlayer.rotationYaw);
            o.addProperty("clientPitch", mc.thePlayer.rotationPitch);
        }
        dumpFields(o, packet);
        synchronized (entries) {
            entries.add(o);
        }
    }

    private void resolveTarget() {
        if (mc.theWorld == null)
            return;
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (targetName.equalsIgnoreCase(p.getName())) {
                targetEntityId = p.getEntityId();
                return;
            }
        }
    }

    private void dumpFields(JsonObject o, Packet<?> packet) {
        for (Field f : fields(packet.getClass())) {
            try {
                Class<?> t = f.getType();
                if (t == int.class) {
                    int v = f.getInt(packet);
                    o.addProperty(f.getName(), v);
                    if (v == targetEntityId)
                        o.addProperty("target", true);
                } else if (t == float.class) {
                    o.addProperty(f.getName(), f.getFloat(packet));
                } else if (t == double.class) {
                    o.addProperty(f.getName(), f.getDouble(packet));
                } else if (t == long.class) {
                    o.addProperty(f.getName(), f.getLong(packet));
                } else if (t == boolean.class) {
                    o.addProperty(f.getName(), f.getBoolean(packet));
                } else if (t == String.class) {
                    String s = (String) f.get(packet);
                    if (s != null)
                        o.addProperty(f.getName(), abbreviate(s));
                } else {
                    Object v = f.get(packet);
                    if (v != null && !t.isArray())
                        o.addProperty(f.getName(), abbreviate(String.valueOf(v)));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static String abbreviate(String s) {
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }

    private Field[] fields(Class<?> c) {
        Field[] cached = fieldCache.get(c);
        if (cached != null)
            return cached;
        List<Field> out = new ArrayList<Field>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()))
                    continue;
                f.setAccessible(true);
                out.add(f);
            }
        }
        Field[] arr = out.toArray(new Field[0]);
        fieldCache.put(c, arr);
        return arr;
    }

    private String buildJson() {
        JsonObject root = new JsonObject();
        root.addProperty("started", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(startMs)));
        root.addProperty("durationMs", System.currentTimeMillis() - startMs);
        root.addProperty("outboundStage", "dispatch");
        if (targetName != null) {
            root.addProperty("target", targetName);
            root.addProperty("targetEntityId", targetEntityId);
        }
        if (overflowed)
            root.addProperty("truncated", true);
        JsonArray arr = new JsonArray();
        synchronized (entries) {
            for (JsonElement el : entries)
                arr.add(el);
        }
        root.add("packets", arr);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }
}
