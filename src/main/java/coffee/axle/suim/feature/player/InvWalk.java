package coffee.axle.suim.feature.player;

import coffee.axle.suim.events.ReceivePacketEvent;
import coffee.axle.suim.events.SendPacketEvent;
import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.property.Setting;
import coffee.axle.suim.util.ShopDetectionUtils;
import coffee.axle.suim.util.Utils;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.server.S2DPacketOpenWindow;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import static coffee.axle.suim.ProjectSUIM.mc;

public class InvWalk extends Feature {

    private boolean clicked = false;
    private int ticksSinceClick = 0;

    @Setting(after = "enabled") private boolean shopOnly = false;
    @Setting(after = "shop-only", min = 0, max = 10) private int delayTicks = 5;
    @Setting(after = "delay-ticks") private boolean keepSprint = true;

    private KeyBinding[] keybinds;

    public InvWalk() {
        super("InvWalk+", GuiCategory.PLAYER);
    }

    @Override
    protected void init() throws Exception {
        keybinds = new KeyBinding[]{
                mc.gameSettings.keyBindForward,
                mc.gameSettings.keyBindLeft,
                mc.gameSettings.keyBindRight,
                mc.gameSettings.keyBindBack,
                mc.gameSettings.keyBindJump,
                mc.gameSettings.keyBindSprint,
                mc.gameSettings.keyBindSneak
        };
    }

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable() {
        releaseAll();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        if (!isEnabled()) return;
        if (mc.thePlayer == null) return;

        if (!(mc.currentScreen instanceof GuiContainer)) {
            clicked = false;
            return;
        }

        if (shopOnly && !ShopDetectionUtils.isInShopGui()) return;

        int delay = delayTicks;

        boolean keepSprint = this.keepSprint;

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
        if (!isEnabled()) return;
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
        if (!isEnabled()) return;

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
        return code != 0 && Utils.isKeyDown(code);
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
