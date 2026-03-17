package coffee.axle.suim.mixin;

import coffee.axle.suim.feature.combat.AimAssistExtras;
import coffee.axle.suim.feature.world.FastPlaceExtras;
import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.rotation.AimAssistRotation;
import coffee.axle.suim.rotation.RotationMath;
import coffee.axle.suim.rotation.RotationState;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

import coffee.axle.suim.feature.SuffixRegistry;

/**
 * Hooks Myau's AimAssist (myau.HJ) to override rotation deltas based on
 * the aim-mode selected in {@link AimAssistExtras}.

 *
 * @author axle.coffee
 */
@Pseudo
@Mixin(targets = "myau.HJ", remap = false)
public class MixinAimAssist {
/** UNUSED: please see EventBus reference */
}
