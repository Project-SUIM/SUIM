package coffee.axle.suim.handlers;

import coffee.axle.suim.events.PreMotionEvent;
import coffee.axle.suim.events.PostMotionEvent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class PlayerMotionHandler {
    private static final Minecraft mc = Minecraft.getMinecraft();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (mc.thePlayer != null && event.player == mc.thePlayer) {
            if (event.phase == TickEvent.Phase.START) {
                PreMotionEvent preEvent = new PreMotionEvent(
                        mc.thePlayer.posX,
                        mc.thePlayer.posY,
                        mc.thePlayer.posZ,
                        mc.thePlayer.rotationYaw,
                        mc.thePlayer.rotationPitch,
                        mc.thePlayer.onGround);
                MinecraftForge.EVENT_BUS.post(preEvent);

                // apply rotation features set (Displace, AutoBedDefense) so the
                // motion packet carries it; position/onGround left to vanilla
                mc.thePlayer.rotationYaw = preEvent.getYaw();
                mc.thePlayer.rotationPitch = preEvent.getPitch();

            } else if (event.phase == TickEvent.Phase.END) {
                MinecraftForge.EVENT_BUS.post(new PostMotionEvent());
            }
        }
    }
}





