package coffee.axle.suim.feature.movement;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;

/**
 * Injects KOL's prediction-air mode into myau's KeepSprint (myau.J5).
 *
 * KOL cw.b: the soft attack slowdown only applies when mode==0 (NORMAL).
 * In PREDICTION_AIR both attack paths go vanilla (motion*0.6 +
 * setSprinting(false)), and bn's own handlers re-force sprint while airborne.
 * raven's name for it is "sprint in air", the disable-while-jumping setting
 * inverted: the server predicted you sprinting when you left the ground, so
 * re-sprinting mid-air matches its prediction instead of contradicting it.
 *
 * The re-force holds until landing or until vanilla sprint conditions fail -
 * never a fixed tick window, that fights myau's sprint module and flags.
 */
public class KeepSprintExtras extends Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private Object keepSprintModule;
    private Object modeProperty;

    private Field enabledField;

    private boolean sprintPending;

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

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!isPredictionAir() || !Utils.nullCheck()) {
            return;
        }
        // airborne attacks only - on the ground myau's normal keepsprint
        // path already handles it
        if (!mc.thePlayer.onGround) {
            sprintPending = true;
        }
    }

    // PlayerTick START runs before this tick's onLivingUpdate sprint logic,
    // so the re-force lands after the attack reset and sticks
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.player != mc.thePlayer) {
            return;
        }
        if (!sprintPending) {
            return;
        }
        if (!isPredictionAir() || !Utils.nullCheck() || mc.thePlayer.onGround) {
            sprintPending = false;
            return;
        }
        // same gates vanilla's own sprint re-acquire uses (EntityPlayerSP
        // onLivingUpdate): moving forward, food, not sneaking/using/riding
        if (mc.thePlayer.movementInput.moveForward <= 0.0f
                || mc.thePlayer.getFoodStats().getFoodLevel() <= 6
                || mc.thePlayer.isSneaking()
                || mc.thePlayer.isUsingItem()
                || mc.thePlayer.isRiding()) {
            sprintPending = false;
            return;
        }
        mc.thePlayer.setSprinting(true);
        // hold until a gate fails or we land; setSprinting when already
        // sprinting sends nothing (vanilla only emits C0B on state change)
    }

    @Override
    public void disable() {
        sprintPending = false;
    }
}
