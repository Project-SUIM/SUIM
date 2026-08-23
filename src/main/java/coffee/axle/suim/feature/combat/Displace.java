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

/**
 * Displace - 1:1 port of Breeze's KnockbackManipulator (rip.breeze.client.dq).
 * The only setting difference: Breeze's YawOffset random range is exposed as
 * yaw-min / yaw-max.
 *
 * How Breeze works (and what this reproduces on the wire):
 * - a left click is cancelled, the KB weapon is silently swapped in, and the
 *   module arms.
 * - on the next rotation tick the flick rotation is applied server-only, so the
 *   movement C03 carries the flick yaw. the client rotationYaw never moves.
 * - on the tick after, the real yaw is restored and clickMouse() fires the
 *   attack. wire order is therefore C03(flick) -> C02(attack) -> C03(real),
 *   which is what makes the server compute knockback from the flicked yaw.
 *
 * SUIM reproduces Breeze's input-level cancel: a clickMouse HEAD mixin fires a
 * cancellable ClickEvent (Breeze's cX dispatch), so the swing/attack packets
 * and their client-side prediction never run at the click; on the restore tick
 * clickMouse is re-fired (Breeze's ef.W) and builds C0A+C02 fresh, then the
 * restore C03 snaps the yaw back. rotationYaw is never touched. the movement
 * input is re-projected onto the flick yaw's frame for one tick (Breeze's
 * et.n()) so the flick C03's position delta matches its yaw, and the sprint
 * key is forced held on the restore tick (Breeze's isKeyDown redirect) so
 * sprint recovers immediately.
 *
 * Blink (Breeze's eo packet-delay) is reproduced exactly: while the module is
 * blinking, BlinkManager holds every outbound packet with no exemptions, the
 * real-yaw C03 is held into the batch too, and the batch flushes once a second
 * C03 is queued (Breeze: n > 1 -> flush).
 */
public class Displace extends Feature {

    private static final Minecraft mc = Minecraft.getMinecraft();

    // ef.c / ef.R equivalents: private MC state Breeze reaches through
    // MethodHandles. resolve MCP name first, SRG fallback (same as
    // AimAssistShowTarget.hurtTime)
    private static final Field leftClickCounterField = findField(
            Minecraft.class, "leftClickCounter", "field_71429_W");
    private static final Method syncCurrentPlayItemMethod = findMethod(
            PlayerControllerMP.class, "syncCurrentPlayItem", "func_78750_j");
    private static final Method clickMouseMethod = findMethod(
            Minecraft.class, "clickMouse", "func_147116_af");   // ef.W (Breeze)

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

    // AntiBot cache ttl (dF.c's cleanup interval)
    private static final long BOT_CACHE_TTL = 1000L;
    // internal Breeze settings KnockbackManipulator reads but does not expose
    private static final boolean ATTACK_FRIENDS = false;
    private static final boolean ATTACK_INVISIBLES = false;
    private static final boolean BOT_CHECK_ENABLED = false; // AntiBot module off

    private Object moduleInstance;
    private Object weaponOnlyProperty;    // n
    private Object chanceProperty;        // o
    private Object yawMinProperty;        // p.min
    private Object yawMaxProperty;        // p.max
    private Object blinkProperty;         // q
    private Object smartModeProperty;     // r
    private Object silentSwapProperty;    // s
    private Object hitSelectProperty;     // t
    private Object sprintOnlyProperty;    // u

    private final Random random = new Random();  // v

    private boolean flickActive = false;   // w
    private boolean pendingFlick = false;  // x
    private float offset = 0.0f;           // A
    private int prevSlot = -1;             // B
    private volatile long curTick = 0L;    // player tick index (arm/flick/restore lattice)
    private long armTick = -1L;
    private long flickTick = -1L;
    private float flickOffset = 0.0f;      // the offset used by THIS cycle (flick + input re-projection)
    // et.a(): the mouse sensitivity step every Breeze rotation is chained onto.
    // a raw yaw+offset is never a lattice multiple -> impossible for a mouse
    private static final float ROTATION_GCD = 0.2f * 0.2f * 0.2f * 8.0f * 0.15f;
    private float lastSentYaw = Float.NaN;
    private float lastSentPitch = Float.NaN;

    // Breeze cancels the click at INPUT level (cX event -> clickMouse HEAD) and
    // re-fires clickMouse in the w-branch; refiringClick lets the re-fired
    // click's packets through the packet handlers untouched
    private boolean refiringClick = false;
    // Breeze's isKeyDown redirect forces keyBindSprint held for the flick
    // window; we restore the real key state on the next tick
    private boolean sprintKeyForced = false;
    private boolean sprintKeyWasPressed = false;

    // AntiBot (dF) heuristics
    private final Map<Integer, Long> seenMoving = new HashMap<>();    // o
    private final Map<Integer, Long> seenTeleport = new HashMap<>();  // p
    private final ArrayList<Integer> confirmedPlayers = new ArrayList<>(); // n
    private final ArrayList<Integer> confirmedBots = new ArrayList<>();    // r
    private long lastBotCleanup = 0L;   // t

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
            chanceProperty = creator.createIntegerProperty("chance", 80, 0, 100);
            yawMinProperty = creator.createIntegerProperty("yaw-min", 70, 0, 180);
            yawMaxProperty = creator.createIntegerProperty("yaw-max", 90, 0, 180);
            blinkProperty = creator.createBooleanProperty("blink", false);
            smartModeProperty = creator.createBooleanProperty("smart-mode", true);
            silentSwapProperty = creator.createBooleanProperty("silent-swap", true);
            hitSelectProperty = creator.createBooleanProperty("hit-select", false);
            sprintOnlyProperty = creator.createBooleanProperty("sprint-only", true);

            creator.registerProperties(moduleInstance, weaponOnlyProperty, chanceProperty,
                    yawMinProperty, yawMaxProperty, blinkProperty, smartModeProperty,
                    silentSwapProperty, hitSelectProperty, sprintOnlyProperty);
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
        // Breeze b(): clears module state and drops the delayed packet queue.
        // no weapon restore here - that only happens on attack-key release
        reset();
    }

    private void reset() {
        flickActive = false;      // w
        pendingFlick = false;     // x
        curTick = 0L;
        armTick = -1L;
        flickTick = -1L;
        flickOffset = 0.0f;
        offset = 0.0f;            // A
        prevSlot = -1;            // B
        lastSentYaw = Float.NaN;
        lastSentPitch = Float.NaN;
        refiringClick = false;
        sprintKeyForced = false;
        sprintKeyWasPressed = false;
        if (BlinkManager.getInstance().isBlinking(BlinkManager.BlinkModule.DISPLACE)) {
            BlinkManager.getInstance().drop();
        }
    }

    // ---------------------------------------------------------------- settings

    private boolean weaponOnly() {
        return properties.getBoolean(weaponOnlyProperty, true);
    }

    private boolean blinkOn() {
        return properties.getBoolean(blinkProperty, false);
    }

    private boolean smartMode() {
        return properties.getBoolean(smartModeProperty, true);
    }

    private boolean silentSwap() {
        return properties.getBoolean(silentSwapProperty, true);
    }

    private boolean hitSelect() {
        return properties.getBoolean(hitSelectProperty, false);
    }

    private boolean sprintOnly() {
        return properties.getBoolean(sprintOnlyProperty, true);
    }

    // Breeze cJ.a(): uniform random in [yaw-min, yaw-max]
    private float yawRange() {
        int min = properties.getInt(yawMinProperty, 70);
        int max = properties.getInt(yawMaxProperty, 90);
        if (max < min)
            max = min;
        return min + random.nextFloat() * (max - min);
    }

    // the arm -> flick -> restore cycle runs on the player tick. the packet
    // handlers run on the Netty IO thread, so the tick is advanced from the
    // input event (fires on the main thread every tick, before the movement
    // packet) - the same lattice Breeze's single dispatcher gives a(et)/a(cX)
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPreInput(PrePlayerInputEvent e) {
        if (sprintKeyForced) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), sprintKeyWasPressed);
            sprintKeyForced = false;
        }
        curTick++;
        // Breeze a(et): I.a.a(du.class).c() re-arms SilentAim's click processing
        // for the tick. Myau's AimAssist is the equivalent module (state-driven,
        // AimAssistHook re-applies the rotation); the lookup mirrors the call
        armAimAssist();
        // Breeze a(et): restore the silent-swapped weapon once the attack key is
        // released - but only outside the flick cycle. Breeze's w-branch (the
        // C02 re-fire) runs BEFORE the weapon-restore branch in a(et), so the
        // attack always goes out with the KB weapon equipped; restoring here
        // mid-cycle would send the C02 with the original weapon instead.
        if (prevSlot != -1 && !mc.gameSettings.keyBindAttack.isKeyDown()
                && !pendingFlick && !flickActive) {
            restoreWeapon();
        }
        // Breeze et.n() movement compensation: on the flick tick, re-project the
        // movement input onto the flick yaw's frame (d = realYaw - flickYaw =
        // -offset, signum'd). moveEntityWithHeading reads the REAL rotationYaw
        // plus this input, so the player physically walks/strafes toward the
        // flick yaw for one tick and the flick C03's motion is consistent with
        // the flicked yaw (no "sprint backward" motion for prediction). the
        // re-projected forward is also < 0.8, so vanilla's own sprint toggle
        // turns the sprint off this tick - exactly the getMoveForward2 redirect.
        if (pendingFlick && curTick >= armTick) {
            flickOffset = currentOffset();
            double d = Math.toRadians(-flickOffset);
            float c = (float) Math.round(Math.cos(d));
            float s = (float) Math.round(Math.sin(d));
            float forward = e.getForward();
            float strafe = e.getStrafe();
            e.setStrafe(Math.signum(strafe * c - forward * s));
            e.setForward(Math.signum(strafe * s + forward * c));
        }
        // Breeze's w-branch re-fires clickMouse (ef.W) after queuing the restore
        // yaw, so the C0A+C02 are built FRESH this tick: swing, crit prediction
        // and the hit-slowdown all run here (the tick of the actual attack),
        // not a tick early, and the attack re-targets the current mouseover
        if (flickActive && curTick >= flickTick + 1) {
            refiringClick = true;
            refireClick();
            refiringClick = false;
        }
        // Breeze's isKeyDown redirect: force the sprint key held on the restore
        // tick so the key-held sprint check re-enables sprint immediately.
        // without it, toggle-sprint users walk for sprintToggleTimer ticks after
        // a flick ("the process after the flick is weird")
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
            onUseEntity(e);
        } else if (packet instanceof C03PacketPlayer) {
            onMovement(e);
        }
        // everything else flows through; BlinkManager holds it while batching
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onReceivePacket(ReceivePacketEvent e) {
        // Breeze dF.a(cY): world-null guard before entity lookups
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

    // the input-level click (MixinMinecraft.clickMouse HEAD -> ClickEvent).
    // cancelling here means swingItem()/attackEntity() never run: no packets and
    // no client-side prediction at the click. the attack is rebuilt by the
    // re-fired clickMouse on the restore tick, like Breeze's a(cX) + ef.W
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onClick(ClickEvent e) {
        if (!manager.isModuleEnabled(moduleInstance))
            return;
        if (refiringClick)
            return;  // our own restore-tick re-fire must pass through
        if (pendingFlick || flickActive || curTick == flickTick) {
            // Breeze a(cX): w || holding -> cancel the click at input level
            e.setCanceled(true);
            return;
        }
        if (!conditionsPass())
            return;
        if (!targetCheck())
            return;
        if (leftClickCounter() > 0)  // ef.c(mc) > 0
            return;
        e.setCanceled(true);
        if (hitSelectBlocked()) {
            return;  // click dropped, no arm (Breeze cancels then gates hitSelect)
        }
        doSilentSwap();
        pendingFlick = true;
        armTick = curTick;
    }

    // ------------------------------------------------------------------ clicks

    // killaura/other-mod swings - manual clicks are handled at input level by
    // onClick, so any C0A that reaches here is a killaura attack. Breeze's a(c5)
    // PreAttackEvent path: conditions + hitSelect only, no targetCheck / no
    // leftClickCounter. arm on the swing, confirm on the C02.
    private void onSwing(SendPacketEvent e) {
        if (refiringClick)
            return;  // our re-fired click's packets pass through untouched
        if (pendingFlick || flickActive || curTick == flickTick) {
            // Breeze a(c5): w || holding -> cancel the click
            e.setCanceled(true);
            return;
        }
        if (!conditionsPass())
            return;
        e.setCanceled(true);
        if (hitSelectBlocked()) {
            return;  // attack dropped, no arm
        }
        armTick = curTick;
    }

    private void onUseEntity(SendPacketEvent e) {
        C02PacketUseEntity use = (C02PacketUseEntity) e.getPacket();
        if (use.getAction() != C02PacketUseEntity.Action.ATTACK)
            return;  // an interact, not an attack - let it through
        if (refiringClick)
            return;  // our re-fired click's packets pass through untouched
        if (pendingFlick || flickActive || curTick == flickTick) {
            // Breeze: w || holding -> cancel the click
            e.setCanceled(true);
            return;
        }
        if (armTick == curTick) {
            // the C02 for the C0A we armed this tick (killaura with a swing)
            e.setCanceled(true);
            doSilentSwap();
            pendingFlick = true;
            armTick = curTick;
            return;
        }
        // standalone attack (killaura without a swing) - Breeze a(c5)
        if (!conditionsPass())
            return;
        if (hitSelectBlocked()) {
            e.setCanceled(true);
            return;
        }
        e.setCanceled(true);
        doSilentSwap();
        pendingFlick = true;
        armTick = curTick;
    }

    // ----------------------------------------------------------------- packets

    private void onMovement(SendPacketEvent e) {
        C03PacketPlayer packet = (C03PacketPlayer) e.getPacket();
        if (pendingFlick && curTick >= armTick) {
            // the flick C03. the held C0A+C02 go out with the restore next tick,
            // exactly Breeze's wire: C03(flick) -> [1 tick] -> C0A,C02 then
            // C03(real) (the et tick re-fires clickMouse in the w-branch, a full
            // tick after the flick C03)
            e.setCanceled(true);
            Packet<?> flick = buildFlickPacket(packet);
            startBlink();
            emit(flick);
            pendingFlick = false;
            flickActive = true;
            flickTick = curTick;
            return;
        }
        if (flickActive && curTick >= flickTick + 1) {
            // restore the real yaw. the C0A+C02 already went out via the
            // re-fired clickMouse in onPreInput this tick (Breeze's w-branch
            // fires clickMouse before the next C03); this only snaps the yaw
            // back so the server sees the real rotation from here on
            flickActive = false;
            e.setCanceled(true);
            emit(buildRestorePacket(packet));
            if (batching()) {
                // blink: flush the batch here so the flick C03 is only 1 tick
                // stale - waiting for a later C03 leaves a 2-tick position delta
                // in the flushed C03s and trips the server's speed check
                stopBlink();
            }
            return;
        }
        if (packet.getRotating()) {
            if (pendingFlick || flickActive) {
                // chain onto the mouse lattice only inside the flick window,
                // like Breeze's et (which manages rotations only while a module
                // holds a target); idle C03s carry the real yaw untouched
                Packet<?> chained = chainRotation(packet);
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

    // ------------------------------------------------------------------ state

    // Breeze c()
    private boolean conditionsPass() {
        if (sprintOnly() && !mc.thePlayer.isSprinting())  // u
            return false;
        if (weaponOnly() && !isHoldingWeapon())           // n
            return false;
        if (!chancePass())                                // u()
            return false;
        return computeOffset();                           // e()
    }

    private boolean chancePass() {
        return properties.getInt(chanceProperty, 80) / 100.0 >= random.nextDouble();
    }

    // Breeze e() - SmartMode void scan, computes the offset direction
    private boolean computeOffset() {
        if (!smartMode())
            return true;
        EntityPlayer target = currentTarget();
        if (target == null)
            return false;
        double tx = target.posX;
        double tz = target.posZ;
        int ty = (int) Math.floor(target.posY);
        float playerYaw = mc.thePlayer.rotationYaw;
        if (blockInColumn(new BlockPos((int) Math.floor(tx), ty, (int) Math.floor(tz)))) {
            // target is over the void: push away from the nearest solid ground
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
        // target on the ground: push toward the nearest void lane
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

    // Breeze t(): the flick offset. smart mode uses the scanned A, otherwise a
    // random sign
    private float currentOffset() {
        if (!smartMode())
            return random.nextBoolean() ? yawRange() : -yawRange();
        return offset;
    }

    // Breeze a(int,int,int): any solid block within y-2..y+3
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

    // Breeze a(BlockPos): is the column below (10 blocks) free of solid blocks
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

    // Breeze v(): the object mouse-over must be a real attackable player
    private boolean targetCheck() {
        EntityPlayer target = currentTarget();
        if (target == null)
            return false;
        return allowAttack(target);
    }

    private EntityPlayer currentTarget() {
        if (mc.objectMouseOver == null || !(mc.objectMouseOver.entityHit instanceof EntityPlayer))
            return null;
        return (EntityPlayer) mc.objectMouseOver.entityHit;
    }

    private boolean hitSelectBlocked() {
        EntityPlayer target = currentTarget();
        return hitSelect() && target != null && target.hurtTime > 8;
    }

    // Breeze dF.c(): friend/team checks always apply, bot heuristics only when
    // the AntiBot module is on (BOT_CHECK_ENABLED)
    private boolean allowAttack(EntityPlayer player) {
        cleanupBotCache();
        if (player == null || player.isDead || mc.thePlayer == player)
            return false;
        Relationship rel = relationship(player);
        if (rel == Relationship.TEAM)
            return false;
        if (rel == Relationship.FRIEND && !ATTACK_FRIENDS)
            return false;
        if (!BOT_CHECK_ENABLED)
            return true;
        Integer id = player.getEntityId();
        if (!seenMoving.containsKey(id) && seenTeleport.containsKey(id))
            return false;
        boolean suspect = player.isInvisible() && !ATTACK_INVISIBLES
                || player.ticksExisted > 105
                || !isMoving(player);
        if (!suspect) {
            if (!confirmedPlayers.contains(id))
                confirmedPlayers.add(id);
            return true;
        }
        return confirmedPlayers.contains(id) || player.ticksExisted > 105 && confirmedBots.contains(id);
    }

    private boolean isMoving(EntityPlayer player) {
        return player.prevPosX != player.posX || player.prevPosY != player.posY || player.prevPosZ != player.posZ;
    }

    private void cleanupBotCache() {
        long now = System.currentTimeMillis();
        if (now <= lastBotCleanup + BOT_CACHE_TTL)
            return;
        lastBotCleanup = now;
        long cutoff = now - BOT_CACHE_TTL;
        seenMoving.entrySet().removeIf(en -> en.getValue() < cutoff);
        seenTeleport.entrySet().removeIf(en -> en.getValue() < cutoff);
    }

    // Breeze ek.a(): the team-mode setting (cu.N) defaults to NONE, so only the
    // friend list is consulted; everyone else is an enemy
    private Relationship relationship(EntityPlayer player) {
        if (isFriend(player.getName()))
            return Relationship.FRIEND;
        return Relationship.ENEMY;
    }

    private boolean isFriend(String name) {
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

    // Breeze d(): silent swap to the hotbar item with the most knockback
    private void doSilentSwap() {
        if (!silentSwap() || !smartMode())
            return;
        int slot = -1;
        float best = 0.0f;
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            int kb;
            // Breeze: EnchantmentHelper.func_77506_a(knockback.effectId, stack)
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
        syncCurrentPlayItem();
    }

    private void restoreWeapon() {
        if (prevSlot == -1)
            return;
        mc.thePlayer.inventory.currentItem = prevSlot;
        syncCurrentPlayItem();
        prevSlot = -1;
    }

    private static int leftClickCounter() {
        try {
            return leftClickCounterField == null ? 0 : leftClickCounterField.getInt(mc);
        } catch (Exception ex) {
            return 0;
        }
    }

    private static void syncCurrentPlayItem() {
        try {
            if (syncCurrentPlayItemMethod != null)
                syncCurrentPlayItemMethod.invoke(mc.playerController);
        } catch (Exception ignored) {
        }
    }

    // Breeze es.a(): local player must hold a sword, axe, or stick
    private boolean isHoldingWeapon() {
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack == null)
            return false;
        return stack.getItem() instanceof ItemSword
                || stack.getItem() instanceof ItemAxe
                || stack.getItem() == Items.stick;
    }

    // ------------------------------------------------------------- blink

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

    // send a controlled packet: batched into the blink queue when batching,
    // otherwise straight out
    private void emit(Packet<?> packet) {
        if (packet == null)
            return;
        if (batching()) {
            BlinkManager.getInstance().queueDirect(packet);
        } else {
            PacketUtils.sendPacketNoEvent(packet);
        }
    }

    // Breeze ef.W(mc): re-fire clickMouse so the attack packets are built fresh
    // on the restore tick, with the client-side attack prediction at the right
    // time (swing, crit, hit-slowdown) and the current mouseover re-targeted.
    // Breeze's w-branch calls EntityRenderer.getMouseOver(1.0f) first (dq.a(et),
    // field_71460_t.func_78473_a) - without it the re-fire aims at the stale
    // click-frame raycast and whiffs on a strafing target
    private void refireClick() {
        try {
            mc.entityRenderer.getMouseOver(1.0f);
            if (clickMouseMethod != null)
                clickMouseMethod.invoke(mc);
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ C03

    // Breeze a(et): I.a.a(du.class).c() - Breeze reaches its own module manager
    // (I.a = ag) for the SilentAim instance and arms its click processing. the
    // SUIM equivalent is Myau's AimAssist (myau.G) via MyauModuleManager; it has
    // no arm flag (state-driven, runs while enabled, AimAssistHook re-applies
    // the rotation after it), so the lookup is the port of the call
    private void armAimAssist() {
        manager.findModule("AimAssist");
    }

    // et.a(float, float): snap onto the mouse lattice chained from the previous
    // sent rotation
    private static float quantizeRotation(float target, float prev) {
        double d = (target - prev) / ROTATION_GCD;
        return (float) (prev + Math.rint(d) * (double) ROTATION_GCD);
    }

    // forward a rotating C03 with its yaw/pitch chained onto the lattice;
    // returns the original packet untouched when it is already on it
    private Packet<?> chainRotation(C03PacketPlayer orig) {
        float yaw = orig.getYaw();
        float pitch = orig.getPitch();
        if (Float.isNaN(lastSentYaw)) {
            lastSentYaw = yaw;
            lastSentPitch = pitch;
            return orig;
        }
        yaw = quantizeRotation(yaw, lastSentYaw);
        pitch = quantizeRotation(pitch, lastSentPitch);
        if (yaw == orig.getYaw() && pitch == orig.getPitch()) {
            lastSentYaw = yaw;
            lastSentPitch = pitch;
            return orig;
        }
        lastSentYaw = yaw;
        lastSentPitch = pitch;
        return copyRotation(orig, yaw, pitch);
    }

    private static Packet<?> copyRotation(C03PacketPlayer orig, float yaw, float pitch) {
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

    // the intercepted C03 carries the rotation Myau's RotationManager applied
    // (including AimAssist's server-only aim, like Breeze's et), so compose the
    // flick on top of it - the point of Breeze arming SilentAim. a bare C03
    // carries no yaw, so fall back to the client rotation
    private Packet<?> buildFlickPacket(C03PacketPlayer orig) {
        float baseYaw = orig.getRotating() ? orig.getYaw() : mc.thePlayer.rotationYaw;
        float basePitch = orig.getRotating() ? orig.getPitch() : mc.thePlayer.rotationPitch;
        return buildRotationPacket(orig, baseYaw + flickOffset, basePitch);
    }

    // force the real yaw back into the C03 so the server rotation snaps to the
    // client's even when vanilla sent a bare C03 or a position-only C04
    private Packet<?> buildRestorePacket(C03PacketPlayer orig) {
        float baseYaw = orig.getRotating() ? orig.getYaw() : mc.thePlayer.rotationYaw;
        float basePitch = orig.getRotating() ? orig.getPitch() : mc.thePlayer.rotationPitch;
        return buildRotationPacket(orig, baseYaw, basePitch);
    }

    private Packet<?> buildRotationPacket(C03PacketPlayer orig, float yaw, float pitch) {
        if (!Float.isNaN(lastSentYaw)) {
            yaw = quantizeRotation(yaw, lastSentYaw);
            pitch = quantizeRotation(pitch, lastSentPitch);
        }
        lastSentYaw = yaw;
        lastSentPitch = pitch;
        return copyRotation(orig, yaw, pitch);
    }

    private enum Relationship {
        FRIEND,
        TEAM,
        ENEMY
    }
}
