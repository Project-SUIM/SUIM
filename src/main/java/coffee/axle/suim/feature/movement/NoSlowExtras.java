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

public class NoSlowExtras extends Feature {

    private static final int SWORD_AIR_MOTION = 2;
    private static final int FOOD_AIR_MOTION = 3;
    private static final int BOW_AIR_MOTION = 2;

    @Setting(existing = true) private int swordMode;
    @Setting(existing = true) private int foodMode;
    @Setting(existing = true) private int bowMode;
    @Setting(existing = true) private int swordMotion;
    @Setting(existing = true) private int foodMotion;
    @Setting(existing = true) private int bowMotion;
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
        extendEnum(findProperty("bow-mode"), new String[] { "NONE", "VANILLA", "FLOAT", "AIR_MOTION" });

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
                || foodMode == FOOD_AIR_MOTION && isConsumable(held)
                || bowMode == BOW_AIR_MOTION && held != null && held.getItem() instanceof net.minecraft.item.ItemBow;
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

        // universal mode: works everywhere. ground = keep sprint (0.8->1.1 bps),
        // air = bigger boost (pin 100 = full motion). the C08 hold + jump
        // handles getting into the air.
        if (isAirMotion() && using) {
            if (!mc.thePlayer.onGround) {
                // airborne: full motion (the big boost)
                pinMotion(100);
            } else {
                // ground: standard 0.2 slowdown but sprint is kept
                pinMotion(20);
            }
            // keep sprint alive while using (the 0.8->1.1 bps ground boost)
            if (!mc.thePlayer.isSprinting()
                    && mc.thePlayer.movementInput.moveForward > 0.0f
                    && !mc.thePlayer.isSneaking()
                    && mc.thePlayer.getFoodStats().getFoodLevel() > 6) {
                mc.thePlayer.setSprinting(true);
            }
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

    // ONLY suppress jump after our forced C08 jump. never blanket-suppress
    // vanilla jump while using items - that breaks eating/drawing bows
    @SubscribeEvent
    public void onPrePlayerInput(PrePlayerInputEvent e) {
        if (jumped) {
            jumped = false;
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
    private int pinnedBowMotion = -1;

    private void pinMotion(int value) {
        if (pinnedSwordMotion == -1) {
            pinnedSwordMotion = swordMotion;
        }
        set("swordMotion", value);
        if (pinnedFoodMotion == -1) {
            pinnedFoodMotion = foodMotion;
        }
        set("foodMotion", value);
        if (pinnedBowMotion == -1) {
            pinnedBowMotion = bowMotion;
        }
        set("bowMotion", value);
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
        if (pinnedBowMotion != -1) {
            set("bowMotion", pinnedBowMotion);
            pinnedBowMotion = -1;
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
