package coffee.axle.suim.feature.movement;

import coffee.axle.suim.events.PreMotionEvent;
import coffee.axle.suim.events.PrePlayerInputEvent;
import coffee.axle.suim.events.SendPacketEvent;
import coffee.axle.suim.events.ReceivePacketEvent;
import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class Stasis extends Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private Object moduleInstance;
    private boolean allowNextC03 = false;
    private Object restoreMotionProperty;
    private Object pulseProperty;
    private double savedMotionX = 0.0;
    private double savedMotionY = 0.0;
    private double savedMotionZ = 0.0;

    // pulse: release the freeze for 4 ticks every 80 so the server keeps seeing
    // movement and doesn't flag the frozen position
    private long pulseTick = 0;
    private boolean pulseActive = false;

    @Override
    public String getName() {
        return "Stasis";
    }

    @Override
    public GuiCategory getGuiCategory() {
        return GuiCategory.WORLD;
    }

    @Override
    public boolean initialize() {
        try {
            moduleInstance = createModule();
            creator.injectModule(moduleInstance, Stasis.class);
            restoreMotionProperty = creator.createBooleanProperty("restore-motion", false); // :ponder:
            pulseProperty = creator.createBooleanProperty("pulse", false); // credits to @dedicatedlite in SUIM discord for this
            creator.registerProperties(moduleInstance, restoreMotionProperty, pulseProperty);
            manager.reloadModuleCommand();

            MinecraftForge.EVENT_BUS.register(this);
            manager.registerModuleCallbacks(moduleInstance,
                    () -> {
                        allowNextC03 = false;
                        pulseTick = 0;
                        pulseActive = false;
                    },
                    () -> {
                        allowNextC03 = false;
                        pulseTick = 0;
                        pulseActive = false;
                        if (properties.getBoolean(restoreMotionProperty, false) && Utils.nullCheck()) {
                            mc.thePlayer.motionX = savedMotionX;
                            mc.thePlayer.motionY = savedMotionY;
                            mc.thePlayer.motionZ = savedMotionZ;
                        }
                    });

            return true;
        } catch (Exception e) {
            MyauLogger.error("Stasis:init", e);
            return false;
        }
    }

    @SubscribeEvent
    public void onPreMotion(PreMotionEvent e) {
        if (!manager.isModuleEnabled(moduleInstance)) return;
        if (!Utils.nullCheck()) return;
        pulseActive = properties.getBoolean(pulseProperty, false) && pulseTick % 80L < 4L;
        pulseTick++;
        if (mc.thePlayer.hurtTime != 0) return;
        if (pulseActive) return;
        savedMotionX = mc.thePlayer.motionX;
        savedMotionY = mc.thePlayer.motionY;
        savedMotionZ = mc.thePlayer.motionZ;
        mc.thePlayer.motionX = 0.0;
        mc.thePlayer.motionY = 0.0;
        mc.thePlayer.motionZ = 0.0;
    }

    @SubscribeEvent
    public void onPrePlayerInput(PrePlayerInputEvent e) {
        if (!manager.isModuleEnabled(moduleInstance)) return;
        e.setForward(0.0f);
        e.setStrafe(0.0f);
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (!manager.isModuleEnabled(moduleInstance)) return;
        if (!(e.getPacket() instanceof C03PacketPlayer)) return;
        if (allowNextC03) {
            allowNextC03 = false;
            return;
        }
        if (!Utils.nullCheck() || mc.thePlayer.hurtTime != 0) return;
        if (pulseActive) return;
        if (!(e.getPacket() instanceof C03PacketPlayer.C05PacketPlayerLook)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (!manager.isModuleEnabled(moduleInstance)) return;
        if (e.getPacket() instanceof S08PacketPlayerPosLook) {
            allowNextC03 = true;
            // a lagback/teleport invalidates the frozen position - restart the
            // cycle so the next pulse window measures from the corrected spot
            pulseTick = 0;
            pulseActive = false;
        }
    }
}
