package coffee.axle.suim.feature.combat;

import coffee.axle.suim.events.PreMotionEvent;
import coffee.axle.suim.events.PrePlayerInputEvent;
import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@SuppressWarnings({ "unused" })
public class Displace extends Feature {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int DISPLACE_WINDOW_TICKS = 10;
    private static final String[] DIRECTIONS = { "Left", "Right" };

    private Object moduleInstance;
    private Object yawOffsetProperty;
    private Object delayProperty;
    private Object directionProperty;
    private Object findVoidProperty;
    private Object weaponOnly, knockbackOnly;

    private boolean displaceThisTick = false;
    private boolean active = false;
    private boolean compensateNextTick = false;
    private boolean displaceLeft = false;
    private boolean wasDisplacingLastTick = false;
    private boolean hasKB = false;
    private Entity attackedLivingEntity = null;
    private int tickCounter = 0;
    private final Map<Integer, Integer> targetWindowStartTicks = new HashMap<>();

    @Override
    public String getName() {
        return "Displace";
    }

    @Override
    public GuiCategory getGuiCategory() {
        return GuiCategory.COMBAT;
    }

    @Override
    public boolean initialize() {
        try {
            moduleInstance = createModule();
            creator.injectModule(moduleInstance, Displace.class);

            yawOffsetProperty = creator.createIntegerProperty("yaw-offset", 90, 0, 180);
            delayProperty = creator.createIntegerProperty("delay-ms", 0, 0, 500);
            directionProperty = creator.createEnumProperty("direction", 0, DIRECTIONS);
            findVoidProperty = creator.createBooleanProperty("find-void", false);
            weaponOnly = creator.createBooleanProperty("weapon-only", true);
            knockbackOnly = creator.createBooleanProperty("knockback-only", false);

            creator.registerProperties(moduleInstance, yawOffsetProperty, delayProperty,
                    directionProperty, findVoidProperty, weaponOnly, knockbackOnly);
            manager.reloadModuleCommand();

            MinecraftForge.EVENT_BUS.register(this);

            manager.registerModuleCallbacks(moduleInstance, this::onEnable, this::onDisable);

            return true;
        } catch (Exception e) {
            MyauLogger.error("Displace:init", e);
            return false;
        }
    }

    private void onEnable() {
        displaceThisTick = false;
        active = false;
        hasKB = false;
        compensateNextTick = false;
        wasDisplacingLastTick = false;
        attackedLivingEntity = null;
        tickCounter = 0;
        targetWindowStartTicks.clear();
    }

    private void onDisable() {
        active = false;
        hasKB = false;
        compensateNextTick = false;
        wasDisplacingLastTick = false;
        attackedLivingEntity = null;
        targetWindowStartTicks.clear();
    }

    private int msToTicks(double ms) {
        if (ms <= 0.0)
            return 0;
        return (int) Math.ceil(ms / 50.0);
    }

    private boolean anyMovementKey() {
        return mc.gameSettings.keyBindForward.isKeyDown()
                || mc.gameSettings.keyBindBack.isKeyDown()
                || mc.gameSettings.keyBindLeft.isKeyDown()
                || mc.gameSettings.keyBindRight.isKeyDown();
    }

    private boolean isHoldingAllowedWeapon() {
        ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (heldItem == null)
            return false;
        return heldItem.getItem() instanceof ItemSword || heldItem.getItem() == Items.stick;
    }

    private boolean tryFindVoidDirection(Entity target) {
        double dx = target.posX - mc.thePlayer.posX;
        double dz = target.posZ - mc.thePlayer.posZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.001)
            return false;
        dx /= dist;
        dz /= dist;
        double rightX = -dz;
        double rightZ = dx;
        double eyeY = target.posY + (double) target.getEyeHeight();
        int leftVoidCount = 0;
        int rightVoidCount = 0;
        for (int i = 1; i <= 12; i++) {
            double off = i * 0.5;
            double rx = target.posX + rightX * off;
            double rz = target.posZ + rightZ * off;
            if (mc.theWorld.rayTraceBlocks(new Vec3(rx, eyeY, rz), new Vec3(rx, eyeY - 10, rz)) == null)
                rightVoidCount++;
            double lx = target.posX - rightX * off;
            double lz = target.posZ - rightZ * off;
            if (mc.theWorld.rayTraceBlocks(new Vec3(lx, eyeY, lz), new Vec3(lx, eyeY - 10, lz)) == null)
                leftVoidCount++;
        }
        if (leftVoidCount == 0 && rightVoidCount == 0)
            return false;
        if (leftVoidCount != rightVoidCount)
            displaceLeft = leftVoidCount > rightVoidCount;
        return true;
    }

    private void pruneTargetDelayStates() {
        if (mc.theWorld == null) {
            targetWindowStartTicks.clear();
            return;
        }
        Iterator<Map.Entry<Integer, Integer>> it = targetWindowStartTicks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> entry = it.next();
            net.minecraft.entity.Entity entity = mc.theWorld.getEntityByID(entry.getKey());
            if (!(entity instanceof EntityPlayer) || entity.isDead || ((EntityPlayer) entity).deathTime != 0)
                it.remove();
        }
    }

    private boolean shouldDisplaceInCurrentWindow(Entity target, int currentTick) {
        if (target == null)
            return true;
        int targetId = target.getEntityId();
        Integer windowStartTick = targetWindowStartTicks.get(targetId);
        if (windowStartTick == null || currentTick - windowStartTick >= DISPLACE_WINDOW_TICKS) {
            targetWindowStartTicks.put(targetId, currentTick);
            return true;
        }
        int delayTicks = msToTicks(properties.getInt(delayProperty, 0));
        if (delayTicks <= 0)
            return true;
        return currentTick - windowStartTick >= delayTicks;
    }

    private EntityPlayer findClosestTarget(double rangeSq) {
        EntityPlayer closest = null;
        double closestDist = rangeSq;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || player.isDead || player.deathTime != 0)
                continue;
            double dist = mc.thePlayer.getDistanceSqToEntity(player);
            if (dist < closestDist) {
                closestDist = dist;
                closest = player;
            }
        }
        return closest;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPrePlayerInput(PrePlayerInputEvent e) {
        if (!manager.isModuleEnabled(moduleInstance))
            return;
        if (!active) {
            compensateNextTick = false;
            return;
        }

        if (compensateNextTick && !displaceThisTick) {
            compensateNextTick = false;
            e.setStrafe(displaceLeft ? -1 : 1);
            return;
        }

        if (!displaceThisTick || hasKB)
            return;
        if (!anyMovementKey())
            return;

        e.setForward(1);
        compensateNextTick = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(AttackEntityEvent e) {
        if (e.entityPlayer != mc.thePlayer)
            return;
        if (!(e.target instanceof EntityLivingBase)) return;
//        if (!(e.target instanceof EntityPlayer)) return;
        attackedLivingEntity = e.target;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onPreMotion(PreMotionEvent e) {
        if (!manager.isModuleEnabled(moduleInstance))
            return;
        if (!Utils.nullCheck()) {
            active = false;
            compensateNextTick = false;
            wasDisplacingLastTick = false;
            return;
        }

        tickCounter++;
        int currentTick = tickCounter;
        pruneTargetDelayStates();
        boolean knockbackOnlyEnabled = properties.getBoolean(knockbackOnly, false);
        boolean hasKBEnchant = EnchantmentHelper.getKnockbackModifier(mc.thePlayer) > 0;
        boolean weaponOnlyEnabled = properties.getBoolean(weaponOnly, true);

        Entity target = attackedLivingEntity;
        attackedLivingEntity = null;

        hasKB = hasKBEnchant;
        boolean weaponAllowed = !weaponOnlyEnabled || isHoldingAllowedWeapon();
        boolean knockbackFlag = !knockbackOnlyEnabled || hasKB;
        active = target != null && weaponAllowed && (hasKB || anyMovementKey());
        if (!active) {
            displaceThisTick = false;
            compensateNextTick = false;
            wasDisplacingLastTick = false;
            return;
        }

        boolean findVoid = properties.getBoolean(findVoidProperty, false);
        if (!findVoid || !tryFindVoidDirection(target)) {
            displaceLeft = properties.getInt(directionProperty, 0) == 0;
        }

        displaceThisTick = !displaceThisTick;

        if (displaceThisTick && !shouldDisplaceInCurrentWindow(target, currentTick)) {
            displaceThisTick = false;
            compensateNextTick = false;
            wasDisplacingLastTick = false;
            return;
        }

        if (!displaceThisTick && wasDisplacingLastTick) {
            int key = mc.gameSettings.keyBindAttack.getKeyCode();
            if (key != 0) {
                KeyBinding.onTick(key);
            }
        }
        wasDisplacingLastTick = displaceThisTick;

        if (!displaceThisTick)
            return;

        float baseYaw = mc.thePlayer.rotationYaw;
        float offset = properties.getInt(yawOffsetProperty, 90);
        if (displaceLeft)
            baseYaw -= offset;
        else
            baseYaw += offset;
        e.setYaw(baseYaw);
    }

}
