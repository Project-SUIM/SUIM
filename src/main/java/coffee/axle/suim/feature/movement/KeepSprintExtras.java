package coffee.axle.suim.feature.movement;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;

/**
 * Injects KOL's prediction-air mode into myau's KeepSprint (myau.J5).
 *
 * KOL 261015 (a.bh) semantics, verified from the running jar's mixins:
 * MixinEntityPlayer gates the soft slowdown + sprint-keep on enabled && o(),
 * where o() is the native mode gate. User ground truth: PREDICTION_AIR means
 * keep sprint ONLY while airborne AND sprinting - on the ground the module
 * has no effect (pure vanilla attack reset).
 *
 * myau's a.W gate chain: ground-only && onGround -> vanilla branch;
 * ground-only && airborne -> soft branch (sprint kept). So pinning
 * ground-only=true while airborne gives exactly the air-only behavior, and
 * un-pinning on landing restores the no-effect ground state. The pin flips
 * per tick based on onGround, not per attack.
 */
public class KeepSprintExtras extends Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private Object keepSprintModule;
    private Object modeProperty;
    private Object groundOnlyProperty;

    private Field enabledField;

    private boolean pinned;
    private boolean savedGroundOnly;

    @Override
    public String getName() {
        return "KeepSprint:Extras";
    }

    @Override
    public boolean initialize() {
        try {
            keepSprintModule = manager.findModule("KeepSprint");
            if (keepSprintModule == null) {
                MyauLogger.log(getName(), "MODULE_NOT_FOUND");
                return false;
            }

            enabledField = keepSprintModule.getClass().getSuperclass()
                    .getDeclaredField(MyauMappings.FIELD_MODULE_ENABLED);
            enabledField.setAccessible(true);

            modeProperty = creator.createEnumProperty("mode", 0,
                    new String[] { "NORMAL", "PREDICTION_AIR" });
            creator.registerProperties(keepSprintModule, modeProperty);

            groundOnlyProperty = manager.findProperty(keepSprintModule, "ground-only");

            MinecraftForge.EVENT_BUS.register(this);

            MyauLogger.log(getName(), "prediction-air ready");
            return true;
        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    private boolean isPredictionAir() {
        if (modeProperty == null || keepSprintModule == null) {
            return false;
        }
        try {
            if (!enabledField.getBoolean(keepSprintModule)) {
                return false;
            }
            Object value = properties.getPropertyValue(modeProperty);
            return value instanceof Integer && (Integer) value == 1;
        } catch (Exception e) {
            return false;
        }
    }

    // ClientTick START runs before any attack this tick, and a.W/mixin f
    // read the flag live at attack time, so a pre-tick pin covers everything.
    // The pin is AIRBORNE-ONLY: on the ground the module must have no effect,
    // so ground-only stays at the user's saved value (false = soft branch
    // would apply on ground attacks - that's NORMAL mode's job, not ours).
    // We only force ground-only=true while actually in the air.
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !Utils.nullCheck()) {
            return;
        }
        if (!isPredictionAir()) {
            restore();
            return;
        }
        // PREDICTION_AIR: only in air, only while sprinting. on ground the
        // module has no effect - vanilla attack reset happens untouched
        boolean airborne = !mc.thePlayer.onGround && mc.thePlayer.isSprinting();
        if (airborne) {
            if (!pinned && groundOnlyProperty != null) {
                savedGroundOnly = properties.getBoolean(groundOnlyProperty, false);
                pinned = true;
            }
            if (groundOnlyProperty != null) {
                setBool(groundOnlyProperty, true);
            }
        } else {
            restore();
        }
    }

    private void restore() {
        if (pinned && groundOnlyProperty != null) {
            setBool(groundOnlyProperty, savedGroundOnly);
        }
        pinned = false;
    }

    private void setBool(Object property, boolean value) {
        try {
            // write Q directly - manager.setPropertyValue fires a config
            // save on every call and we flip this every mode change
            Field valueField = property.getClass().getSuperclass()
                    .getDeclaredField(MyauMappings.FIELD_VALUE_CURRENT);
            valueField.setAccessible(true);
            valueField.set(property, value);
        } catch (Exception e) {
            MyauLogger.error("KeepSprint:Extras set", e);
        }
    }

    @Override
    public void disable() {
        restore();
    }
}
