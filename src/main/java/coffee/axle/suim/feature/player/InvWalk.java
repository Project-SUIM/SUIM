package coffee.axle.suim.feature.player;

import coffee.axle.suim.events.ReceivePacketEvent;
import coffee.axle.suim.events.SendPacketEvent;
import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.ShopDetectionUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.server.S2DPacketOpenWindow;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@SuppressWarnings({ "unused" })
public class InvWalk extends Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private Object moduleInstance;
    private Object shopOnlyProperty;
    private Object delayTicksProperty;
    private Object keepSprintProperty;

    private boolean clicked = false;
    private int ticksSinceClick = 0;

    private KeyBinding[] keybinds;

    @Override
    public String getName() {
        return "InvWalk+";
    }

    @Override
    public GuiCategory getGuiCategory() {
        return GuiCategory.PLAYER;
    }

    @Override
    public boolean initialize() {
        try {
            moduleInstance = createModule();
            creator.injectModule(moduleInstance, InvWalk.class);

            shopOnlyProperty = creator.createBooleanProperty("shop-only", false);
            creator.injectPropertyAfter(moduleInstance, shopOnlyProperty, "enabled");

            delayTicksProperty = creator.createIntegerProperty("delay-ticks", 5, 0, 10);
            creator.injectPropertyAfter(moduleInstance, delayTicksProperty, "shop-only");

            keepSprintProperty = creator.createBooleanProperty("keep-sprint", true);
            creator.injectPropertyAfter(moduleInstance, keepSprintProperty, "delay-ticks");

            keybinds = new KeyBinding[]{
                    mc.gameSettings.keyBindForward,
                    mc.gameSettings.keyBindLeft,
                    mc.gameSettings.keyBindRight,
                    mc.gameSettings.keyBindBack,
                    mc.gameSettings.keyBindJump,
                    mc.gameSettings.keyBindSprint,
                    mc.gameSettings.keyBindSneak
            };

            manager.reloadModuleCommand();
            MinecraftForge.EVENT_BUS.register(this);
            manager.registerModuleCallbacks(moduleInstance,
                    this::reset,
                    this::releaseAll);

            return true;
        } catch (Exception e) {
            MyauLogger.error("InvWalk+:init", e);
            return false;
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        if (!manager.isModuleEnabled(moduleInstance)) return;
        if (mc.thePlayer == null) return;

        if (!(mc.currentScreen instanceof GuiContainer)) {
            clicked = false;
            return;
        }

        if (properties.getBoolean(shopOnlyProperty, false) && !ShopDetectionUtils.isInShopGui()) return;

        int delay = properties.getInt(delayTicksProperty, 5);

        boolean keepSprint = properties.getBoolean(keepSprintProperty, true);

        if (!clicked || ticksSinceClick >= delay) {
            for (KeyBinding kb : keybinds) {
                KeyBinding.setKeyBindState(kb.getKeyCode(), isKeyDown(kb));
            }
            if (keepSprint && isKeyDown(mc.gameSettings.keyBindForward)) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
            }
        } else {
            ticksSinceClick++;
            for (KeyBinding kb : keybinds) {
                KeyBinding.setKeyBindState(kb.getKeyCode(), false);
            }
        }
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (!manager.isModuleEnabled(moduleInstance)) return;
        if (!(mc.currentScreen instanceof GuiContainer)) return;

        if (e.getPacket() instanceof C0EPacketClickWindow) {
            clicked = true;
            ticksSinceClick = 0;
            for (KeyBinding kb : keybinds) {
                KeyBinding.setKeyBindState(kb.getKeyCode(), false);
            }
        }
    }

    @SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (!manager.isModuleEnabled(moduleInstance)) return;

        if (e.getPacket() instanceof S2DPacketOpenWindow) {
            clicked = false;
            for (KeyBinding kb : keybinds) {
                if (isKeyDown(kb)) {
                    KeyBinding.setKeyBindState(kb.getKeyCode(), true);
                }
            }
        }
    }

    private boolean isKeyDown(KeyBinding kb) {
        int code = kb.getKeyCode();
        return code > 0 && org.lwjgl.input.Keyboard.isKeyDown(code);
    }

    private void reset() {
        clicked = false;
        ticksSinceClick = 0;
    }

    private void releaseAll() {
        clicked = false;
        ticksSinceClick = 0;
        if (keybinds != null) {
            for (KeyBinding kb : keybinds) {
                KeyBinding.setKeyBindState(kb.getKeyCode(), false);
            }
        }
    }
}
