package coffee.axle.suim.feature.combat;

import coffee.axle.suim.events.ClickEvent;
import coffee.axle.suim.events.PrePlayerInputEvent;
import coffee.axle.suim.events.ReceivePacketEvent;
import coffee.axle.suim.events.SendPacketEvent;
import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.util.BlinkManager;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.PacketUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

// Breeze KnockbackManipulator port. cancel click -> swap KB weapon -> flick C03 ->
// refire click on restore tick. optional blink holds the batch.
public class Displace extends Feature {

    private static final Minecraft mc = Minecraft.getMinecraft();

    // MCP name first, SRG fallback
    private static final Field leftClickCounterField = findField(
            Minecraft.class, "leftClickCounter", "field_71429_W");
    private static final Method syncItemMethod = findMethod(
            PlayerControllerMP.class, "syncCurrentPlayItem", "func_78750_j");
    private static final Method clickMouseMethod = findMethod(
            Minecraft.class, "clickMouse", "func_147116_af");

    private static Field findField(Class<?> clazz, String mcp, String srg) {
        try {
            Field f = clazz.getDeclaredField(mcp);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            try {
                Field f = clazz.getDeclaredField(srg);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                return null;
            }
        }
    }

    private static Method findMethod(Class<?> clazz, String mcp, String srg) {
        try {
            Method m = clazz.getDeclaredMethod(mcp);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            try {
                Method m = clazz.getDeclaredMethod(srg);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }
    }

    private static final long BOT_CACHE_TTL = 1000L;
    private static final boolean ATTACK_FRIENDS = false;
    private static final boolean ATTACK_INVISIBLES = false;
    private static final boolean BOT_CHECK_ENABLED = false;

    private Object moduleInstance;
    private Object weaponOnlyProperty;
    private Object chanceProperty;
    private Object yawMinProperty;
    private Object yawMaxProperty;
    private Object blinkProperty;
    private Object smartModeProperty;
    private Object silentSwapProperty;
    private Object hitSelectProperty;
    private Object knockbackOnlyProperty;
   // private Object killauraProperty;
    private Object sprintOnlyProperty;

    private final Random random = new Random();

    private boolean flickActive = false;
    private boolean pendingFlick = false;
    private float offset = 0.0f;
    private int prevSlot = -1;
    private volatile long curTick = 0L;
    private long armTick = -1L;
    private long flickTick = -1L;
    private float flickOffset = 0.0f;
    // mouse sensitivity GCD - rotations snap to this lattice
    private static final float ROTATION_GCD = 0.2f * 0.2f * 0.2f * 8.0f * 0.15f;
    private float lastSentYaw = Float.NaN;
    private float lastSentPitch = Float.NaN;

    // guard: lets re-fired click packets pass through handlers untouched
    private boolean refiringClick = false;
    private boolean sprintKeyForced = false;
    private boolean sprintKeyWasPressed = false;
    // killaura's target, captured from the canceled C02 - no reflection needed
    private EntityPlayer pendingTarget;

    private final Map<Integer, Long> seenMoving = new HashMap<>();
    private final Map<Integer, Long> seenTeleport = new HashMap<>();
    private final ArrayList<Integer> confirmedPlayers = new ArrayList<>();
    private final ArrayList<Integer> confirmedBots = new ArrayList<>();
    private long lastBotCleanup = 0L;

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

            weaponOnlyProperty = creator.createBooleanProperty("weapon-only", true);
            chanceProperty = creator.createIntegerProperty("chance", 100, 0, 100);
            yawMinProperty = creator.createIntegerProperty("yaw-min", 90, 0, 180);
            yawMaxProperty = creator.createIntegerProperty("yaw-max", 90, 0, 180);
            blinkProperty = creator.createBooleanProperty("blink", false);
            smartModeProperty = creator.createBooleanProperty("smart-mode", true);
            silentSwapProperty = creator.createBooleanProperty("silent-swap", false);
            hitSelectProperty = creator.createBooleanProperty("hit-select", false);
            knockbackOnlyProperty = creator.createBooleanProperty("knockback-only", false);
            // works but rots broken and mitigates to hell and back so disabled until i have time to debug Myau's KA
            // killauraProperty = creator.createBooleanProperty("killaura", false);
            sprintOnlyProperty = creator.createBooleanProperty("sprint-only", true);

            creator.registerProperties(moduleInstance, weaponOnlyProperty, chanceProperty,
                    yawMinProperty, yawMaxProperty, blinkProperty, smartModeProperty,
                    silentSwapProperty, hitSelectProperty, knockbackOnlyProperty, /* killauraProperty, */ sprintOnlyProperty);
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
        reset();
    }

    private void onDisable() {
        reset();
    }

    private void reset() {
        flickActive = false;
        pendingFlick = false;
        curTick = 0L;
        armTick = -1L;
        flickTick = -1L;
        flickOffset = 0.0f;
        offset = 0.0f;
        prevSlot = -1;
        lastSentYaw = Float.NaN;
        lastSentPitch = Float.NaN;
        refiringClick = false;
        sprintKeyForced = false;
        sprintKeyWasPressed = false;
        pendingTarget = null;
        if (BlinkManager.getInstance().isBlinking(BlinkManager.BlinkModule.DISPLACE)) {
            BlinkManager.getInstance().drop();
        }
    }

    private boolean blinkOn() {
        return properties.getBoolean(blinkProperty, false);
    }

    private boolean smartMode() {
        return properties.getBoolean(smartModeProperty, true);
    }

    private float yawRange() {
        int min = properties.getInt(yawMinProperty, 70);
        int max = properties.getInt(yawMaxProperty, 90);
        if (max < min)
            max = min;
        return min + random.nextFloat() * (max - min);
    }

    // tick-advanced from PrePlayerInputEvent (main thread, before movement packet)
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPreInput(PrePlayerInputEvent e) {
        if (sprintKeyForced) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), sprintKeyWasPressed);
            sprintKeyForced = false;
        }
        curTick++;
        armAim();
        // restore weapon on attack-key release, outside the flick cycle
        if (prevSlot != -1 && !mc.gameSettings.keyBindAttack.isKeyDown()
                && !pendingFlick && !flickActive) {
            restoreWpn();
        }
        // flick tick: re-project movement input onto flick yaw frame
        if (pendingFlick && curTick >= armTick) {
            flickOffset = flickDir();
            double d = Math.toRadians(-flickOffset);
            float c = (float) Math.round(Math.cos(d));
            float s = (float) Math.round(Math.sin(d));
            float forward = e.getForward();
            float strafe = e.getStrafe();
            float sf = Math.signum(strafe * c - forward * s);
            float fw = Math.signum(strafe * s + forward * c);
            // re-apply sneak/use-item scale erased by our signum overwrite
            if (mc.gameSettings.keyBindSneak.isKeyDown()) {
                sf *= 0.3f;
                fw *= 0.3f;
            }
            if (mc.thePlayer.isUsingItem()) {
                sf *= 0.2f;
                fw *= 0.2f;
            }
            e.setStrafe(sf);
            e.setForward(fw);
        }
        // restore tick: refire clickMouse for fresh attack packets
        if (flickActive && curTick >= flickTick + 1) {
            refiringClick = true;
            refire();
            refiringClick = false;
        }
        // force sprint held on restore tick so toggle-sprint recovers immediately
        if (flickActive && !mc.thePlayer.isSprinting()) {
            sprintKeyWasPressed = mc.gameSettings.keyBindSprint.isKeyDown();
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
            sprintKeyForced = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onSendPacket(SendPacketEvent e) {
        if (!manager.isModuleEnabled(moduleInstance))
            return;
        if (e.isCanceled() || mc.thePlayer == null || mc.theWorld == null)
            return;
        Packet<?> packet = e.getPacket();
        if (packet instanceof C0APacketAnimation) {
            onSwing(e);
        } else if (packet instanceof C02PacketUseEntity) {
            onUse(e);
        } else if (packet instanceof C03PacketPlayer) {
            onMove(e);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onReceivePacket(ReceivePacketEvent e) {
        if (mc.theWorld == null)
            return;
        Packet<?> packet = e.getPacket();
        if (packet instanceof S14PacketEntity) {
            Entity entity = ((S14PacketEntity) packet).getEntity(mc.theWorld);
            if (entity instanceof EntityPlayer) {
                seenMoving.put(entity.getEntityId(), System.currentTimeMillis());
            }
        } else if (packet instanceof S18PacketEntityTeleport) {
            Entity entity = mc.theWorld.getEntityByID(((S18PacketEntityTeleport) packet).getEntityId());
            if (entity instanceof EntityPlayer) {
                seenTeleport.put(entity.getEntityId(), System.currentTimeMillis());
            }
        }
    }

    //  swing/attack packets never run at the click.
    // attack is rebuilt by refired clickMouse on restore tick.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onClick(ClickEvent e) {
        if (!manager.isModuleEnabled(moduleInstance))
            return;
        if (refiringClick)
            return;
        if (pendingFlick || flickActive || curTick == flickTick) {
            e.setCanceled(true);
            return;
        }
        if (!passConds())
            return;
        if (!checkTarget())
            return;
        if (leftClicks() > 0)
            return;
        e.setCanceled(true);
        if (hitBlocked()) {
            return;
        }
        silentSwap();
        pendingFlick = true;
        armTick = curTick;
    }

    // killaura swings: no targetCheck / leftClickCounter, just conditions + hitSelect
    private void onSwing(SendPacketEvent e) {
        if (!killauraOn())
            return;
        if (refiringClick)
            return;
        if (pendingFlick || flickActive || curTick == flickTick) {
            e.setCanceled(true);
            return;
        }
        if (!passConds())
            return;
        e.setCanceled(true);
        if (hitBlocked()) {
            return;
        }
        armTick = curTick;
    }

    private void onUse(SendPacketEvent e) {
        C02PacketUseEntity use = (C02PacketUseEntity) e.getPacket();
        if (use.getAction() != C02PacketUseEntity.Action.ATTACK)
            return;
        if (!killauraOn())
            return;
        if (refiringClick)
            return;
        if (pendingFlick || flickActive || curTick == flickTick) {
            e.setCanceled(true);
            return;
        }
        // capture killaura's target so conditions/smart-scan/refire aim at it
        EntityPlayer kt = useTarget(use);
        if (kt != null)
            pendingTarget = kt;
        // C02 for a C0A we armed this tick (killaura with a swing)
        if (armTick == curTick) {
            e.setCanceled(true);
            silentSwap();
            pendingFlick = true;
            armTick = curTick;
            return;
        }
        // standalone attack (killaura without swing)
        if (!passConds())
            return;
        if (hitBlocked()) {
            e.setCanceled(true);
            return;
        }
        e.setCanceled(true);
        silentSwap();
        pendingFlick = true;
        armTick = curTick;
    }

    private void onMove(SendPacketEvent e) {
        C03PacketPlayer packet = (C03PacketPlayer) e.getPacket();
        // flick C03: cancel original, send flicked yaw, start blink batch
        if (pendingFlick && curTick >= armTick) {
            e.setCanceled(true);
            Packet<?> flick = flickPacket(packet);
            startBlink();
            emit(flick);
            pendingFlick = false;
            flickActive = true;
            flickTick = curTick;
            return;
        }
        // restore C03: snap real yaw back, flush blink batch
        if (flickActive && curTick >= flickTick + 1) {
            flickActive = false;
            e.setCanceled(true);
            emit(restorePacket(packet));
            if (batching()) {
                stopBlink();
            }
            return;
        }
        if (packet.getRotating()) {
            // chain onto mouse lattice inside flick window; idle C03s pass through
            if (pendingFlick || flickActive) {
                Packet<?> chained = chainRot(packet);
                if (chained != packet) {
                    e.setCanceled(true);
                    emit(chained);
                }
            } else {
                lastSentYaw = packet.getYaw();
                lastSentPitch = packet.getPitch();
            }
            return;
        }
    }


    private boolean passConds() {
        if (mc.currentScreen != null)
            return false;
        if (properties.getBoolean(sprintOnlyProperty, true) && !mc.thePlayer.isSprinting())
            return false;
        if (properties.getBoolean(weaponOnlyProperty, true) && !holdingWpn())
            return false;
        if (properties.getBoolean(knockbackOnlyProperty, false) && !holdingKb())
            return false;
        if (!passChance())
            return false;
        return smartOffset();
    }

    private boolean passChance() {
        return properties.getInt(chanceProperty, 80) / 100.0 >= random.nextDouble();
    }

    // SmartMode void scan: computes offset direction toward/away from void
    private boolean smartOffset() {
        if (!smartMode())
            return true;
        EntityPlayer tgt = target();
        if (tgt == null)
            return false;
        double tx = tgt.posX;
        double tz = tgt.posZ;
        int ty = (int) Math.floor(tgt.posY);
        float playerYaw = mc.thePlayer.rotationYaw;
        if (blockInColumn(new BlockPos((int) Math.floor(tx), ty, (int) Math.floor(tz)))) {
            // target over void: push away from nearest solid ground
            float yawDelta = 0.0f;
            double bestDist = Double.MAX_VALUE;
            boolean found = false;
            for (int i = 0; i < 18; ++i) {
                float f3 = i * 20.0f;
                double rad = Math.toRadians(f3);
                double d5 = -Math.sin(rad);
                double d6 = Math.cos(rad);
                for (double d7 = 0.5; d7 <= 4.0; d7 += 0.3) {
                    int nx = (int) Math.floor(tx + d5 * d7);
                    int nz = (int) Math.floor(tz + d6 * d7);
                    if (!hasNearbyBlock(nx, ty, nz) || !(d7 < bestDist))
                        continue;
                    bestDist = d7;
                    found = true;
                    yawDelta = MathHelper.wrapAngleTo180_float(f3 - playerYaw);
                    break;
                }
            }
            if (!found) {
                offset = 0.0f;
                return false;
            }
            offset = (yawDelta > 0.0f ? -1.0f : 1.0f) * yawRange();
            return true;
        }
        // target on ground: push toward nearest void lane
        float yawDelta = 0.0f;
        double bestDist = Double.MAX_VALUE;
        boolean found = false;
        for (int i = 0; i < 18; ++i) {
            float f6 = i * 20.0f;
            double rad = Math.toRadians(f6);
            double d12 = -Math.sin(rad);
            double d13 = Math.cos(rad);
            float f7 = MathHelper.wrapAngleTo180_float(f6 - playerYaw);
            float abs = Math.abs(f7);
            if (abs < 30.0f || abs > 150.0f)
                continue;
            double d14 = Double.MAX_VALUE;
            boolean walled = false;
            for (double d15 = 0.5; d15 <= 4.0; d15 += 0.5) {
                int nx = (int) Math.floor(tx + d12 * d15);
                int nz = (int) Math.floor(tz + d13 * d15);
                for (int j = 0; j <= 1; ++j) {
                    BlockPos bp = new BlockPos(nx, ty + j, nz);
                    IBlockState st = mc.theWorld.getBlockState(bp);
                    if (st.getBlock().getCollisionBoundingBox(mc.theWorld, bp, st) == null)
                        continue;
                    walled = true;
                    break;
                }
                if (walled)
                    break;
                if (!blockInColumn(new BlockPos(nx, ty, nz)))
                    continue;
                d14 = d15;
                break;
            }
            if (walled || d14 > 4.0 || found && !(d14 < bestDist))
                continue;
            yawDelta = f7;
            bestDist = d14;
            found = true;
        }
        if (!found)
            return false;
        offset = (yawDelta > 0.0f ? 1.0f : -1.0f) * yawRange();
        return true;
    }

    private boolean killauraOn() {
        return false; // disabled see reason above 
        // return properties.getBoolean(killauraProperty, false);
    }

    // flick offset: smart mode uses scanned direction, otherwise random sign
    private float flickDir() {
        if (!smartMode())
            return random.nextBoolean() ? yawRange() : -yawRange();
        return offset;
    }

    private boolean hasNearbyBlock(int x, int y, int z) {
        for (int i = -2; i <= 3; ++i) {
            BlockPos bp = new BlockPos(x, y + i, z);
            if (bp.getY() < 0)
                continue;
            IBlockState st = mc.theWorld.getBlockState(bp);
            if (st.getBlock().getCollisionBoundingBox(mc.theWorld, bp, st) == null)
                continue;
            return true;
        }
        return false;
    }

    // column below pos free of solid blocks for 10 blocks
    private boolean blockInColumn(BlockPos pos) {
        for (int i = 0; i < 10; ++i) {
            BlockPos bp = pos.add(0, -i, 0);
            if (bp.getY() < 1)
                return true;
            IBlockState st = mc.theWorld.getBlockState(bp);
            if (st.getBlock().getCollisionBoundingBox(mc.theWorld, bp, st) == null)
                continue;
            return false;
        }
        return true;
    }

    private boolean checkTarget() {
        EntityPlayer t = target();
        if (t == null)
            return false;
        return canAttack(t);
    }

    private EntityPlayer target() {
        // killaura target beats the crosshair - the attack being displaced is theirs
        if (pendingTarget != null)
            return pendingTarget;
        if (mc.objectMouseOver == null || !(mc.objectMouseOver.entityHit instanceof EntityPlayer))
            return null;
        return (EntityPlayer) mc.objectMouseOver.entityHit;
    }

    private boolean hitBlocked() {
        EntityPlayer t = target();
        return properties.getBoolean(hitSelectProperty, false) && t != null && t.hurtTime > 8;
    }

    private boolean canAttack(EntityPlayer player) {
        cleanBots();
        if (player == null || player.isDead || mc.thePlayer == player)
            return false;
        Relationship r = rel(player);
        if (r == Relationship.TEAM)
            return false;
        if (r == Relationship.FRIEND && !ATTACK_FRIENDS)
            return false;
        if (!BOT_CHECK_ENABLED)
            return true;
        Integer id = player.getEntityId();
        if (!seenMoving.containsKey(id) && seenTeleport.containsKey(id))
            return false;
        boolean suspect = player.isInvisible() && !ATTACK_INVISIBLES
                || player.ticksExisted > 105
                || !moving(player);
        if (!suspect) {
            if (!confirmedPlayers.contains(id))
                confirmedPlayers.add(id);
            return true;
        }
        return confirmedPlayers.contains(id) || player.ticksExisted > 105 && confirmedBots.contains(id);
    }

    private boolean moving(EntityPlayer player) {
        return player.prevPosX != player.posX || player.prevPosY != player.posY || player.prevPosZ != player.posZ;
    }

    private void cleanBots() {
        long now = System.currentTimeMillis();
        if (now <= lastBotCleanup + BOT_CACHE_TTL)
            return;
        lastBotCleanup = now;
        long cutoff = now - BOT_CACHE_TTL;
        seenMoving.entrySet().removeIf(en -> en.getValue() < cutoff);
        seenTeleport.entrySet().removeIf(en -> en.getValue() < cutoff);
    }

    // friend list check only (team mode defaults to NONE)
    private Relationship rel(EntityPlayer player) {
        if (friend(player.getName()))
            return Relationship.FRIEND;
        return Relationship.ENEMY;
    }

    private boolean friend(String name) {
        try {
            Class<?> mainClass = Class.forName(MyauMappings.CLASS_MAIN);
            Field friendField = mainClass.getDeclaredField(MyauMappings.FIELD_FRIEND_MANAGER);
            friendField.setAccessible(true);
            Object friendManager = friendField.get(null);
            Field playersField = friendManager.getClass().getSuperclass()
                    .getDeclaredField(MyauMappings.FIELD_PLAYER_LIST);
            playersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ArrayList<String> friends = (ArrayList<String>) playersField.get(friendManager);
            return friends.contains(name);
        } catch (Exception ex) {
            return false;
        }
    }

    // silent swap to hotbar item with most knockback
    private void silentSwap() {
        if (!properties.getBoolean(silentSwapProperty, true) || !smartMode())
            return;
        int slot = -1;
        float best = 0.0f;
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            int kb;
            if (stack == null || !((float) (kb = EnchantmentHelper
                    .getEnchantmentLevel(Enchantment.knockback.effectId, stack)) > best)
                    && mc.thePlayer.inventory.currentItem != i)
                continue;
            best = kb;
            slot = i;
        }
        if (slot == -1 && slot != mc.thePlayer.inventory.currentItem)
            return;
        prevSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = slot;
        syncItem();
    }

    private void restoreWpn() {
        if (prevSlot == -1)
            return;
        mc.thePlayer.inventory.currentItem = prevSlot;
        syncItem();
        prevSlot = -1;
    }

    private static int leftClicks() {
        try {
            return leftClickCounterField == null ? 0 : leftClickCounterField.getInt(mc);
        } catch (Exception ex) {
            return 0;
        }
    }

    private static void syncItem() {
        try {
            if (syncItemMethod != null)
                syncItemMethod.invoke(mc.playerController);
        } catch (Exception ignored) {
        }
    }

    private boolean holdingWpn() {
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack == null)
            return false;
        return stack.getItem() instanceof ItemSword
                || stack.getItem() instanceof ItemAxe
                || stack.getItem() == Items.stick;
    }

    private boolean holdingKb() {
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack == null)
            return false;
        boolean hasKnockback = EnchantmentHelper.getEnchantmentLevel(Enchantment.knockback.effectId, stack) > 0;
        // js incase hypixel decides to do weird things to kb stick :3
        boolean isEnchantedStick = stack.getItem() == Items.stick && EnchantmentHelper.getEnchantments(stack).size() > 0;
        return hasKnockback || isEnchantedStick;
    }

    private boolean batching() {
        return blinkOn() && BlinkManager.getInstance().isBlinking(BlinkManager.BlinkModule.DISPLACE);
    }

    private void startBlink() {
        if (!blinkOn())
            return;
        BlinkManager.getInstance().setBlinkState(true, BlinkManager.BlinkModule.DISPLACE);
    }

    private void stopBlink() {
        BlinkManager.getInstance().setBlinkState(false, BlinkManager.BlinkModule.DISPLACE);
    }

    private void emit(Packet<?> packet) {
        if (packet == null)
            return;
        if (batching()) {
            BlinkManager.getInstance().queueDirect(packet);
        } else {
            PacketUtils.sendPacketNoEvent(packet);
        }
    }

    // refresh mouseover then refire clickMouse for fresh attack packets;
    // killaura attacks go straight to their captured target instead
    private void refire() {
        try {
            EntityPlayer t = pendingTarget;
            pendingTarget = null;
            if (t != null && !t.isDead && mc.theWorld.getEntityByID(t.getEntityId()) == t) {
                mc.thePlayer.swingItem();
                mc.playerController.attackEntity(mc.thePlayer, t);
                return;
            }
            mc.entityRenderer.getMouseOver(1.0f);
            if (clickMouseMethod != null)
                clickMouseMethod.invoke(mc);
        } catch (Exception ignored) {
        }
    }

    private EntityPlayer useTarget(C02PacketUseEntity use) {
        Entity ent = use.getEntityFromWorld(mc.theWorld);
        return ent instanceof EntityPlayer ? (EntityPlayer) ent : null;
    }

    private void armAim() {
        manager.findModule("AimAssist");
    }

    private static float quantRot(float target, float prev) {
        double d = (target - prev) / ROTATION_GCD;
        return (float) (prev + Math.rint(d) * (double) ROTATION_GCD);
    }

    private Packet<?> chainRot(C03PacketPlayer orig) {
        float yaw = orig.getYaw();
        float pitch = orig.getPitch();
        if (Float.isNaN(lastSentYaw)) {
            lastSentYaw = yaw;
            lastSentPitch = pitch;
            return orig;
        }
        yaw = quantRot(yaw, lastSentYaw);
        pitch = quantRot(pitch, lastSentPitch);
        if (yaw == orig.getYaw() && pitch == orig.getPitch()) {
            lastSentYaw = yaw;
            lastSentPitch = pitch;
            return orig;
        }
        lastSentYaw = yaw;
        lastSentPitch = pitch;
        return copyRot(orig, yaw, pitch);
    }

    private static Packet<?> copyRot(C03PacketPlayer orig, float yaw, float pitch) {
        if (orig instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
            C03PacketPlayer.C06PacketPlayerPosLook pos =
                    (C03PacketPlayer.C06PacketPlayerPosLook) orig;
            return new C03PacketPlayer.C06PacketPlayerPosLook(
                    pos.getPositionX(), pos.getPositionY(), pos.getPositionZ(),
                    yaw, pitch, orig.isOnGround());
        }
        if (orig instanceof C03PacketPlayer.C04PacketPlayerPosition) {
            C03PacketPlayer.C04PacketPlayerPosition pos =
                    (C03PacketPlayer.C04PacketPlayerPosition) orig;
            return new C03PacketPlayer.C06PacketPlayerPosLook(
                    pos.getPositionX(), pos.getPositionY(), pos.getPositionZ(),
                    yaw, pitch, orig.isOnGround());
        }
        return new C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, orig.isOnGround());
    }

    // flick C03: compose flick on intercepted rotation (AimAssist's server aim)
    private Packet<?> flickPacket(C03PacketPlayer orig) {
        float baseYaw = orig.getRotating() ? orig.getYaw() : mc.thePlayer.rotationYaw;
        float basePitch = orig.getRotating() ? orig.getPitch() : mc.thePlayer.rotationPitch;
        return rotPacket(orig, baseYaw + flickOffset, basePitch);
    }

    // restore C03: snap real yaw back into the packet
    private Packet<?> restorePacket(C03PacketPlayer orig) {
        float baseYaw = orig.getRotating() ? orig.getYaw() : mc.thePlayer.rotationYaw;
        float basePitch = orig.getRotating() ? orig.getPitch() : mc.thePlayer.rotationPitch;
        return rotPacket(orig, baseYaw, basePitch);
    }

    private Packet<?> rotPacket(C03PacketPlayer orig, float yaw, float pitch) {
        if (!Float.isNaN(lastSentYaw)) {
            yaw = quantRot(yaw, lastSentYaw);
            pitch = quantRot(pitch, lastSentPitch);
        }
        lastSentYaw = yaw;
        lastSentPitch = pitch;
        return copyRot(orig, yaw, pitch);
    }

    private enum Relationship {
        FRIEND,
        TEAM,
        ENEMY
    }
}
