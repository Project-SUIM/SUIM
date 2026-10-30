package coffee.axle.suim.mixin;

import coffee.axle.suim.feature.player.ArmorInfo;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

/**
 * Reroutes myau's EnchantmentText.labels (myau.f.n) through ArmorInfo so
 * hypixel's fake stack of "Unbreaking 1" shows our predicted prot/feather
 * levels on nametag armor instead. Stack identity comes from what we saw in
 * the entity-equipment packets.
 */
@Pseudo
@Mixin(targets = "myau.f", remap = false)
public class MixinEnchantmentText {

    @Inject(method = "n", at = @At("HEAD"), cancellable = true, remap = false)
    private static void suim$replaceLabels(ItemStack stack, CallbackInfoReturnable<ArrayList<String>> cir) {
        ArrayList<String> replaced = ArmorInfo.labelsFor(stack);
        if (replaced != null) cir.setReturnValue(replaced);
    }
}
