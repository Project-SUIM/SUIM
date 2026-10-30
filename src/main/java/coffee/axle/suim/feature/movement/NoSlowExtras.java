package coffee.axle.suim.feature.movement;

import coffee.axle.suim.events.PrePlayerInputEvent;
import coffee.axle.suim.events.SendPacketEvent;
import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.property.Setting;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.PacketUtils;
import coffee.axle.suim.util.Utils;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.EnumAction;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import static coffee.axle.suim.ProjectSUIM.mc;

/**
 * Injects KOL's AIR_MOTION mode into myau's NoSlow (myau.Jd) sword/food modes
 * plus the food-auto-jump setting.
 *
 * KOL 261015 (a.bu) verified semantics:
 * - s() = sword-mode==2 && holdingSword || food-mode==3 && holdingFood
 * - v() (motion percent) returns 20 for AIR_MOTION - the vanilla 0.2 value
 * - the native handlers gate the actual application; user ground truth:
 *   the bypass only does anything while SPRINTING + IN AIR. on the ground
 *   it's vanilla (0.2 slowdown, no sprint force).
 *
 * So AIR_MOTION here: while airborne + sprinting + using, pin the motion
 * setting to 100 (full motion, the "a lil bit faster") and force sprint.
 * On the ground: unpin, myau's base applies the user's motion (default 20
 * = vanilla). C08 hold: intercept the first use packet,
 * jump + strafe, replay it 2 ticks later so the server sees the jump land
 * before the use starts.
 */
public class NoSlowExtras extends Feature {

    private static final int SWORD_AIR_MOTION = 2;
    private static final int FOOD_AIR_MOTION = 3;

    @Setting(existing = true) private int swordMode;
    @Setting(existing = true) private int foodMode;
    @Setting(existing = true) private int swordMotion;
    @Setting(existing = true) private int foodMotion;
    @Setting private boolean foodAutoJump = false;

    // use packet hold state
    private long sinceUse = 100;
    private C08PacketPlayerBlockPlacement heldPlace;
    private int holdTicks;
    private boolean jumped;

    public NoSlowExtras() {
        super("NoSlow:Extras");
    }

    @Override
    protected String targetModule() {
        return "NoSlow";
    }

    @Override
    protected void init() throws Exception {
        extendEnum(findProperty("sword-mode"), new String[] { "NONE", "VANILLA", "AIR_MOTION" });
        extendEnum(findProperty("food-mode"), new String[] { "NONE", "VANILLA", "FLOAT", "AIR_MOTION" });

        MyauLogger.log(getName(), "air-motion ready");
    }

    // the enum options array is final, java 8 lets us swap the array itself via
    // the modifiers hack. the clickgui reads it through a clone, so the new
    // options show up everywhere
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
        if (!isEnabled() || mc.thePlayer == null) {
            return false;
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        return swordMode == SWORD_AIR_MOTION && held != null && held.getItem() instanceof ItemSword
                || foodMode == FOOD_AIR_MOTION && isConsumable(held);
    }

    private boolean isFoodAirMotion() {
        return isEnabled() && foodMode == FOOD_AIR_MOTION;
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

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !Utils.nullCheck()) {
            return;
        }

        boolean using = mc.thePlayer.isUsingItem();
        if (using) {
            sinceUse = 0;
        } else {
            sinceUse++;
        }

        // replay the held C08 after 2 ticks - the jump has landed by then,
        // so the server registers the use start while we're airborne.
        // NOT gated on isUsingItem: the client won't report using until the
        // server acks the C08, which never happens while we hold it
        if (heldPlace != null && ++holdTicks >= 2) {
            PacketUtils.sendPacketNoEvent(heldPlace);
            heldPlace = null;
            holdTicks = 0;
        }

        // the bypass only lives in the air: airborne + sprinting + using.
        // on the ground myau's base does its normal thing (vanilla 0.2)
        boolean air = isAirMotion() && using
                && !mc.thePlayer.onGround && mc.thePlayer.isSprinting();
        if (air) {
            // KOL v() returns 20 for AIR_MOTION - the vanilla 0.2 value.
            // the native handler then skips applying it while airborne,
            // which through myau's Jd.b (applies D()/100 every tick) is
            // equivalent to pinning 100 while in the air
            pinMotion(100);
        } else if (isAirMotion() && using) {
            // on the ground: KOL v() = 20, the vanilla slowdown value
            pinMotion(20);
        } else {
            unpinMotion();
        }

        if (foodAutoJump
                && isFoodAirMotion()
                && mc.thePlayer.isUsingItem()
                && mc.thePlayer.onGround) {
            // jump once at use start (sinceUse > 3 gate), jumping every
            // ground tick would be a tower rhythm
            if (jumpCooldown-- <= 0) {
                mc.thePlayer.jump();
                jumpCooldown = 10;
            }
        }
    }

    // JUMP/FLOAT: intercept the use-item C08 at click time
    @SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (!(e.getPacket() instanceof C08PacketPlayerBlockPlacement)) {
            return;
        }
        C08PacketPlayerBlockPlacement p = (C08PacketPlayerBlockPlacement) e.getPacket();
        if (p.getPlacedBlockDirection() != 255 || !isAirMotion() || !Utils.nullCheck()) {
            return;
        }
        if (mc.thePlayer.capabilities.isCreativeMode
                || mc.thePlayer.isInWater() || mc.thePlayer.isInLava()) {
            return;
        }
        // only hold when not facing an interactable block, blocking any
        // block hit here is the conservative version of that check
        if (mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }
        // repeated C08s while eating pass through - the server needs them
        if (!mc.thePlayer.onGround) {
            return;
        }
        if (sinceUse > 3L) {
            mc.thePlayer.jump();
            if (mc.thePlayer.movementInput.moveForward > 0.0f && !mc.thePlayer.isSneaking()) {
                strafe(0.48 + 0.001 * Math.random());
            }
            jumped = true;
            // fresh-use jump consumed the auto-jump window (sinceUse gate)
            jumpCooldown = 10;
        }
        heldPlace = p;
        e.setCanceled(true);
    }

    // kill the jump input after the forced jump,
    // and keep it off while using on the ground so vanilla doesn't bounce
    @SubscribeEvent
    public void onPrePlayerInput(PrePlayerInputEvent e) {
        if (jumped) {
            jumped = false;
            e.setJump(false);
        } else if (mc.thePlayer != null && mc.thePlayer.isUsingItem() && mc.thePlayer.onGround) {
            e.setJump(false);
        }
    }

    // set motion along the input direction
    private void strafe(double speed) {
        float yaw = mc.thePlayer.rotationYaw;
        float forward = mc.thePlayer.movementInput.moveForward;
        float strafe = mc.thePlayer.movementInput.moveStrafe;
        float scale = 1.0f;
        if (forward < 0.0f) {
            yaw += 180.0f;
            scale = -0.5f;
        } else if (forward > 0.0f) {
            scale = 0.5f;
        }
        if (strafe > 0.0f) {
            yaw -= 90.0f * scale;
        } else if (strafe < 0.0f) {
            yaw += 90.0f * scale;
        }
        yaw = (float) Math.toRadians(yaw);
        mc.thePlayer.motionX = -Math.sin(yaw) * speed;
        mc.thePlayer.motionZ = Math.cos(yaw) * speed;
    }

    private int jumpCooldown;

    private int pinnedSwordMotion = -1;
    private int pinnedFoodMotion = -1;

    private void pinMotion(int value) {
        if (pinnedSwordMotion == -1) {
            pinnedSwordMotion = swordMotion;
        }
        set("swordMotion", value);
        if (pinnedFoodMotion == -1) {
            pinnedFoodMotion = foodMotion;
        }
        set("foodMotion", value);
    }

    private void unpinMotion() {
        if (pinnedSwordMotion != -1) {
            set("swordMotion", pinnedSwordMotion);
            pinnedSwordMotion = -1;
        }
        if (pinnedFoodMotion != -1) {
            set("foodMotion", pinnedFoodMotion);
            pinnedFoodMotion = -1;
        }
    }

    @Override
    public void disable() {
        unpinMotion();
        jumpCooldown = 0;
        heldPlace = null;
        holdTicks = 0;
        jumped = false;
    }
}
