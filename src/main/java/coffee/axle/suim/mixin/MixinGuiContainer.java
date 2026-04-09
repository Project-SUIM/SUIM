package coffee.axle.suim.mixin;

import coffee.axle.suim.feature.player.ClickOutOfContainers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiContainer.class)
public class MixinGuiContainer {

    @Shadow protected int guiLeft;
    @Shadow protected int guiTop;
    @Shadow protected int xSize;
    @Shadow protected int ySize;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void suim$clickOutOfContainers(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        if (!ClickOutOfContainers.isEnabled()) return;
        if (mouseButton != 0 && mouseButton != 1) return;

        boolean outside = mouseX < guiLeft || mouseY < guiTop
                || mouseX >= guiLeft + xSize || mouseY >= guiTop + ySize;

        if (outside && Minecraft.getMinecraft().thePlayer.inventory.getItemStack() == null) {
            Minecraft.getMinecraft().thePlayer.closeScreen();
            ci.cancel();
        }
    }
}
