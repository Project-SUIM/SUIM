package coffee.axle.suim.handlers;

import coffee.axle.suim.events.ClientRotationEvent;
import coffee.axle.suim.events.PreMotionEvent;
import coffee.axle.suim.events.PostMotionEvent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * PlayerMotionHandler
 * Fires PreMotionEvent, PostMotionEvent, and ClientRotationEvent
 * 
 * @author maybsomeday
 */
public class PlayerMotionHandler {
    private static final Minecraft mc = Minecraft.getMinecraft();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.player == mc.thePlayer && mc.thePlayer != null) {
            if (event.phase == TickEvent.Phase.START) {
                PreMotionEvent preEvent = new PreMotionEvent(
                        mc.thePlayer.posX,
                        mc.thePlayer.posY,
                        mc.thePlayer.posZ,
                        mc.thePlayer.rotationYaw,
                        mc.thePlayer.rotationPitch,
                        mc.thePlayer.onGround);
                MinecraftForge.EVENT_BUS.post(preEvent);

                MinecraftForge.EVENT_BUS.post(new ClientRotationEvent());

            } else if (event.phase == TickEvent.Phase.END) {
                MinecraftForge.EVENT_BUS.post(new PostMotionEvent());
            }
        }
    }
}





