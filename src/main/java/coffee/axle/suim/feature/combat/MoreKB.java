package coffee.axle.suim.feature.combat;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;

import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * MoreKB — sprint-reset knockback enhancement
 * Ported from OpenMyau's MoreKB module
 */
@SuppressWarnings("unused")

public class MoreKB extends Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private Object moduleInstance;
    private Object modeProperty;
    private Object intelligentProperty;
    private Object onlyGroundProperty;

    private boolean shouldSprintReset;
    private EntityLivingBase target;

    @Override
    public String getName() {
        return "MoreKB";
    }

    @Override
    public GuiCategory getGuiCategory() {
        return GuiCategory.COMBAT;
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");

            moduleInstance = createModule();
            creator.injectModule(moduleInstance, MoreKB.class);

            modeProperty = creator.createEnumProperty(
                    "mode", 0,
                    new String[] {
                            "LEGIT", "LEGIT_FAST", "LESS_PACKET",
                            "PACKET", "DOUBLE_PACKET"
                    });

            intelligentProperty = creator.createBooleanProperty("intelligent", false);
            onlyGroundProperty = creator.createBooleanProperty("only-ground", true);

            creator.registerProperties(moduleInstance,
                    modeProperty, intelligentProperty, onlyGroundProperty);

            manager.reloadModuleCommand();

            MinecraftForge.EVENT_BUS.register(this);

            manager.registerModuleCallbacks(
                    moduleInstance,
                    this::onModuleEnabled,
                    this::onModuleDisabled);

            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;

        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    private void onModuleEnabled() {
        shouldSprintReset = false;
        target = null;
    }

    private void onModuleDisabled() {
        shouldSprintReset = false;
        target = null;
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!manager.isModuleEnabled(moduleInstance))
            return;

        Entity targetEntity = event.target;
        if (targetEntity instanceof EntityLivingBase) {
            this.target = (EntityLivingBase) targetEntity;
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START)
            return;
        if (!manager.isModuleEnabled(moduleInstance))
            return;
        if (mc.thePlayer == null || mc.theWorld == null)
            return;

        int mode = getMode();

        // LEGIT_FAST mode uses attack target directly
        if (mode == 1) {
            if (this.target != null && isMoving()) {
                if ((getOnlyGround() && mc.thePlayer.onGround) || !getOnlyGround()) {
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

        // Intelligent check — skip if target is facing away
        if (getIntelligent()) {
            double x = mc.thePlayer.posX - entity.posX;
            double z = mc.thePlayer.posZ - entity.posZ;
            float calcYaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI - 90.0);
            float diffY = Math.abs(
                    MathHelper.wrapAngleTo180_float(calcYaw - entity.rotationYawHead));
            if (diffY > 120.0F)
                return;
        }

        if (entity.hurtTime == 10) {
            switch (mode) {
                case 0: // LEGIT
                    shouldSprintReset = true;
                    if (mc.thePlayer.isSprinting()) {
                        mc.thePlayer.setSprinting(false);
                        mc.thePlayer.setSprinting(true);
                    }
                    shouldSprintReset = false;
                    break;

                case 2: // LESS_PACKET
                    if (mc.thePlayer.isSprinting()) {
                        mc.thePlayer.setSprinting(false);
                    }
                    mc.getNetHandler().addToSendQueue(
                            new C0BPacketEntityAction(mc.thePlayer,
                                    C0BPacketEntityAction.Action.START_SPRINTING));
                    mc.thePlayer.setSprinting(true);
                    break;

                case 3: // PACKET
                    mc.thePlayer.sendQueue.addToSendQueue(
                            new C0BPacketEntityAction(mc.thePlayer,
                                    C0BPacketEntityAction.Action.STOP_SPRINTING));
                    mc.thePlayer.sendQueue.addToSendQueue(
                            new C0BPacketEntityAction(mc.thePlayer,
                                    C0BPacketEntityAction.Action.START_SPRINTING));
                    mc.thePlayer.setSprinting(true);
                    break;

                case 4: // DOUBLE_PACKET
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

    private int getMode() {
        try {
            return (Integer) properties.getPropertyValue(modeProperty);
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean getIntelligent() {
        try {
            return (Boolean) properties.getPropertyValue(intelligentProperty);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean getOnlyGround() {
        try {
            return (Boolean) properties.getPropertyValue(onlyGroundProperty);
        } catch (Exception e) {
            return true;
        }
    }
}





