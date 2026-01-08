package coffee.axle.suim.mixin;

import coffee.axle.suim.events.*;
import coffee.axle.suim.util.PacketUtils;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Network packet event handling mixin
 * 
 * @author maybsomeday (meow) - ported to d'Myau
 */
@Mixin(value = NetworkManager.class, priority = 2000)
public class MixinNetworkManager {

    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onSendPacket(Packet<?> packetIn, CallbackInfo ci) {
        if (PacketUtils.skipSendEvent.contains(packetIn)) {
            PacketUtils.skipSendEvent.remove(packetIn);
            NoEventPacketEvent noEventPacketEvent = new NoEventPacketEvent(packetIn);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(noEventPacketEvent);
            if (noEventPacketEvent.isCanceled()) {
                ci.cancel();
            }
            return;
        }

        SendPacketEvent sendPacketEvent = new SendPacketEvent(packetIn);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(sendPacketEvent);

        if (sendPacketEvent.isCanceled()) {
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
