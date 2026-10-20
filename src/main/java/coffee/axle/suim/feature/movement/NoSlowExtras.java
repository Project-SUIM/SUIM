package coffee.axle.suim.feature.movement;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.EnumAction;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Injects KOL's AIR_MOTION mode into myau's NoSlow (myau.Jd) sword/food modes
 * plus the food-auto-jump setting.
 *
 * raven JUMP mode mechanics (NoSlowModule.java):
 * - on the ground/launch tick: tickSlow = 0.3f applies to movement input
 * - while airborne: NO tickSlow is set, so the slowdown falls back to the
 *   user's slow setting (default 100 = full motion). that's the "a lil bit
 *   faster" - you keep full speed in the air while eating/blocking
 * - onPlace holds the C08 and jumps + strafes on the launch
 * - onPostPlayerInput suppresses jump input after the initial jump
 *
 * myau's Jd.b(Ji) applies D()/100 every tick while using, where D() reads the
 * motion settings. so AIR_MOTION pins the motion setting to 30 while on
 * ground and 100 while airborne, restoring the user's value when inactive.
 */
public class NoSlowExtras extends Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int SWORD_AIR_MOTION = 2;
    private static final int FOOD_AIR_MOTION = 3;

    private Object noSlowModule;
    private Object swordModeProperty;
    private Object foodModeProperty;
    private Object foodAutoJumpProperty;
    private Object swordMotionProperty;
    private Object foodMotionProperty;

    private Field enabledField;

    @Override
    public String getName() {
        return "NoSlow:Extras";
    }

    @Override
    public boolean initialize() {
        try {
            noSlowModule = manager.findModule("NoSlow");
            if (noSlowModule == null) {
                MyauLogger.log(getName(), "MODULE_NOT_FOUND");
                return false;
            }

            enabledField = noSlowModule.getClass().getSuperclass()
                    .getDeclaredField(MyauMappings.FIELD_MODULE_ENABLED);
            enabledField.setAccessible(true);

            swordModeProperty = manager.findProperty(noSlowModule, "sword-mode");
            foodModeProperty = manager.findProperty(noSlowModule, "food-mode");
            if (swordModeProperty == null || foodModeProperty == null) {
                MyauLogger.log(getName(), "MODE_PROPERTIES_NOT_FOUND");
                return false;
            }

            extendEnum(swordModeProperty, new String[] { "NONE", "VANILLA", "AIR_MOTION" });
            extendEnum(foodModeProperty, new String[] { "NONE", "VANILLA", "FLOAT", "AIR_MOTION" });

            swordMotionProperty = manager.findProperty(noSlowModule, "sword-motion");
            foodMotionProperty = manager.findProperty(noSlowModule, "food-motion");

            foodAutoJumpProperty = creator.createBooleanProperty("food-auto-jump", false);
            creator.registerProperties(noSlowModule, foodAutoJumpProperty);

            MinecraftForge.EVENT_BUS.register(this);

            MyauLogger.log(getName(), "air-motion ready");
            return true;
        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    // myau.x stores its mode names in the final String[] P field. java 8 lets us
    // swap the array itself via the modifiers hack, the clickgui reads it through
    // V() which clones so the new options show up everywhere
    private void extendEnum(Object enumProperty, String[] newValues) throws Exception {
        Field valuesField = enumProperty.getClass()
                .getDeclaredField(MyauMappings.FIELD_ENUM_VALUES_ARRAY);
        valuesField.setAccessible(true);

        Field modifiers = Field.class.getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        modifiers.setInt(valuesField, valuesField.getModifiers() & ~Modifier.FINAL);

        valuesField.set(enumProperty, newValues);
    }

    private boolean isAirMotion() {
        if (!enabled() || mc.thePlayer == null) {
            return false;
        }
        int sword = intProp(swordModeProperty);
        int food = intProp(foodModeProperty);
        ItemStack held = mc.thePlayer.getHeldItem();
        return sword == SWORD_AIR_MOTION && held != null && held.getItem() instanceof ItemSword
                || food == FOOD_AIR_MOTION && isConsumable(held);
    }

    private boolean isFoodAirMotion() {
        return enabled() && intProp(foodModeProperty) == FOOD_AIR_MOTION;
    }

    private boolean enabled() {
        try {
            return enabledField.getBoolean(noSlowModule);
        } catch (Exception e) {
            return false;
        }
    }

    // mirrors myau k4.S(): EAT or DRINK action, non-splash potion
    private static boolean isConsumable(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        if (stack.getItem() instanceof ItemPotion) {
            return !ItemPotion.isSplash(stack.getMetadata());
        }
        EnumAction action = stack.getItemUseAction();
        return action == EnumAction.EAT || action == EnumAction.DRINK;
    }

    private int intProp(Object property) {
        try {
            Object value = properties.getPropertyValue(property);
            return value instanceof Number ? ((Number) value).intValue() : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !Utils.nullCheck()) {
            return;
        }

        boolean air = isAirMotion() && mc.thePlayer.isUsingItem();
        if (air) {
            // raven JUMP: 0.3 on the launch tick, full motion in the air.
            // myau's native D() reads the motion props so we drive them
            pinMotion(mc.thePlayer.onGround ? 30 : 100);

            // raven JUMP: force sprint while using in the air, a lil bit faster
            if (!mc.thePlayer.onGround
                    && mc.thePlayer.movementInput.moveForward > 0.0f
                    && !mc.thePlayer.isSneaking()
                    && mc.thePlayer.getFoodStats().getFoodLevel() > 6) {
                mc.thePlayer.setSprinting(true);
            }
        } else {
            unpinMotion();
        }

        if (foodAutoJumpProperty != null
                && properties.getBoolean(foodAutoJumpProperty, false)
                && isFoodAirMotion()
                && mc.thePlayer.isUsingItem()
                && mc.thePlayer.onGround) {
            // raven jumps once at use start (sinceUse > 3 gate); jumping every
            // ground tick would be a tower rhythm
            if (jumpCooldown-- <= 0) {
                mc.thePlayer.jump();
                jumpCooldown = 10;
            }
        }
    }

    private int jumpCooldown;

    private int pinnedSwordMotion = -1;
    private int pinnedFoodMotion = -1;

    private void pinMotion(int value) {
        if (swordMotionProperty != null) {
            if (pinnedSwordMotion == -1) {
                pinnedSwordMotion = intProp(swordMotionProperty);
            }
            setProp(swordMotionProperty, value);
        }
        if (foodMotionProperty != null) {
            if (pinnedFoodMotion == -1) {
                pinnedFoodMotion = intProp(foodMotionProperty);
            }
            setProp(foodMotionProperty, value);
        }
    }

    private void unpinMotion() {
        if (pinnedSwordMotion != -1 && swordMotionProperty != null) {
            setProp(swordMotionProperty, pinnedSwordMotion);
            pinnedSwordMotion = -1;
        }
        if (pinnedFoodMotion != -1 && foodMotionProperty != null) {
            setProp(foodMotionProperty, pinnedFoodMotion);
            pinnedFoodMotion = -1;
        }
    }

    private void setProp(Object property, int value) {
        try {
            // write Q directly - manager.setPropertyValue fires a config save
            // on every call and we flip this multiple times a second
            Field valueField = property.getClass().getSuperclass()
                    .getDeclaredField(MyauMappings.FIELD_VALUE_CURRENT);
            valueField.setAccessible(true);
            valueField.set(property, value);
        } catch (Exception e) {
            MyauLogger.error("NoSlow:Extras set", e);
        }
    }

    @Override
    public void disable() {
        unpinMotion();
        jumpCooldown = 0;
    }
}
