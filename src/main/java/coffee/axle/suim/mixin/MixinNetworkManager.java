package coffee.axle.suim.mixin;

import coffee.axle.suim.events.ReceivePacketEvent;
import coffee.axle.suim.events.SendPacketEvent;
import coffee.axle.suim.util.BlinkManager;
import coffee.axle.suim.util.PacketUtils;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetworkManager.class, priority = 2000)
public class MixinNetworkManager {

    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onSendPacket(Packet<?> packetIn, CallbackInfo ci) {
        if (PacketUtils.skipSendEvent.contains(packetIn)) {
            PacketUtils.skipSendEvent.remove(packetIn);
            return;
        }

        SendPacketEvent sendPacketEvent = new SendPacketEvent(packetIn);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(sendPacketEvent);

        if (sendPacketEvent.isCanceled()) {
            ci.cancel();
            return;
        }

        // hold outbound packets while a blink module is active. tryBlink was never
        // called from anywhere before, so every blink feature was silently a no-op
        if (BlinkManager.getInstance().tryBlink(packetIn)) {
            ci.cancel();
        }
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    protected void onReceivePacket(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        ReceivePacketEvent receivePacketEvent = new ReceivePacketEvent(packet);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(receivePacketEvent);

        if (receivePacketEvent.isCanceled()) {
            ci.cancel();
        }
    }
}





