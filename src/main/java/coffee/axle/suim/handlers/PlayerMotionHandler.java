package coffee.axle.suim.handlers;

import coffee.axle.suim.events.PreMotionEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import static coffee.axle.suim.ProjectSUIM.mc;

public class PlayerMotionHandler {

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
                mc.thePlayer.rotationYaw = preEvent.getYaw();
                mc.thePlayer.rotationPitch = preEvent.getPitch();
            }
        }
    }
}





