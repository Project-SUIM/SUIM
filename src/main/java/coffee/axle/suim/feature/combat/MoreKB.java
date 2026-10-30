package coffee.axle.suim.feature.combat;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.property.Setting;
import coffee.axle.suim.rotation.RotationMath;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import static coffee.axle.suim.ProjectSUIM.mc;

/**
 * MoreKB - sprint-reset knockback enhancement
 * Ported from OpenMyau's MoreKB module
 */
public class MoreKB extends Feature {

    public enum Mode {
        LEGIT, LEGIT_FAST, LESS_PACKET, PACKET, DOUBLE_PACKET
    }

    @Setting private Mode mode = Mode.LEGIT;
    @Setting private boolean intelligent = false;
    @Setting private boolean onlyGround = true;

    @SuppressWarnings("unused")
    private boolean shouldSprintReset;
    private EntityLivingBase target;

    public MoreKB() {
        super("MoreKB", GuiCategory.COMBAT);
    }

    @Override
    protected void onEnable() {
        shouldSprintReset = false;
        target = null;
    }

    @Override
    protected void onDisable() {
        shouldSprintReset = false;
        target = null;
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!isEnabled())
            return;

        Entity targetEntity = event.target;
        if (targetEntity instanceof EntityLivingBase) {
            this.target = (EntityLivingBase) targetEntity;
        }
    }

    @SuppressWarnings("incomplete-switch")
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START)
            return;
        if (!isEnabled())
            return;
        if (mc.thePlayer == null || mc.theWorld == null)
            return;

        // LEGIT_FAST mode uses attack target directly
        if (mode == Mode.LEGIT_FAST) {
            if (this.target != null && isMoving()) {
                if ((onlyGround && mc.thePlayer.onGround) || !onlyGround) {
                    mc.thePlayer.sprintingTicksLeft = 0;
                }
                this.target = null;
            }
            return;
        }

        // Other modes use crosshair entity
        EntityLivingBase entity = null;
        if (mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && mc.objectMouseOver.entityHit instanceof EntityLivingBase) {
            entity = (EntityLivingBase) mc.objectMouseOver.entityHit;
        }

        if (entity == null)
            return;

        // Intelligent check - skip if target is facing away
        if (intelligent) {
            float calcYaw = RotationMath.toRotationsFromDelta(
                    mc.thePlayer.posX - entity.posX,
                    0,
                    mc.thePlayer.posZ - entity.posZ)[0];
            float diffY = Math.abs(
                    MathHelper.wrapAngleTo180_float(calcYaw - entity.rotationYawHead));
            if (diffY > 120.0F)
                return;
        }

        if (entity.hurtTime == 10) {
            switch (mode) {
                case LEGIT:
                    shouldSprintReset = true;
                    if (mc.thePlayer.isSprinting()) {
                        mc.thePlayer.setSprinting(false);
                        mc.thePlayer.setSprinting(true);
                    }
                    shouldSprintReset = false;
                    break;

                case LESS_PACKET:
                    if (mc.thePlayer.isSprinting()) {
                        mc.thePlayer.setSprinting(false);
                    }
                    mc.getNetHandler().addToSendQueue(
                            new C0BPacketEntityAction(mc.thePlayer,
                                    C0BPacketEntityAction.Action.START_SPRINTING));
                    mc.thePlayer.setSprinting(true);
                    break;

                case PACKET:
                    mc.thePlayer.sendQueue.addToSendQueue(
                            new C0BPacketEntityAction(mc.thePlayer,
                                    C0BPacketEntityAction.Action.STOP_SPRINTING));
                    mc.thePlayer.sendQueue.addToSendQueue(
                            new C0BPacketEntityAction(mc.thePlayer,
                                    C0BPacketEntityAction.Action.START_SPRINTING));
                    mc.thePlayer.setSprinting(true);
                    break;

                case DOUBLE_PACKET:
                    mc.thePlayer.sendQueue.addToSendQueue(
                            new C0BPacketEntityAction(mc.thePlayer,
                                    C0BPacketEntityAction.Action.STOP_SPRINTING));
                    mc.thePlayer.sendQueue.addToSendQueue(
                            new C0BPacketEntityAction(mc.thePlayer,
                                    C0BPacketEntityAction.Action.START_SPRINTING));
                    mc.thePlayer.sendQueue.addToSendQueue(
                            new C0BPacketEntityAction(mc.thePlayer,
                                    C0BPacketEntityAction.Action.STOP_SPRINTING));
                    mc.thePlayer.sendQueue.addToSendQueue(
                            new C0BPacketEntityAction(mc.thePlayer,
                                    C0BPacketEntityAction.Action.START_SPRINTING));
                    mc.thePlayer.setSprinting(true);
                    break;
            }
        }
    }

    private boolean isMoving() {
        return mc.thePlayer.moveForward != 0.0F
                || mc.thePlayer.moveStrafing != 0.0F;
    }
}
