package coffee.axle.suim.mixin;

import coffee.axle.suim.feature.world.FastPlaceExtras;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hook into FastPlace's tick handler.
 * Cancels the tick when skip conditions are met (e.g. holding obsidian).
 * canPlace is native in 260317 so we intercept at the tick level instead.
 */
@Pseudo
@Mixin(targets = "myau.vx", remap = false)
public class MixinFastPlace {
/* UNUSED: please see EventBus reference */
}