package coffee.axle.suim.features;

import coffee.axle.suim.hooks.MyauHook;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;

public class KillAuraDisableOnDeath implements Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final MyauHook hook = MyauHook.getInstance();

    private Object killAuraModule;
    private Object disableOnDeathProperty;

    private Field displayedTitleField;
    private boolean hasTriggered = false;

    @Override
    public String getName() {
        return "KillAura:DisableOnDeath";
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");
            killAuraModule = hook.findModule("KillAura");
            if (killAuraModule == null) {
                MyauLogger.log(getName(), "MODULE_NOT_FOUND");
                return false;
            }

            disableOnDeathProperty = hook.createBooleanProperty("disable-on-death", false);
            if (!hook.injectPropertyAfter(killAuraModule, disableOnDeathProperty, "mode")) {
                return false;
            }

            try {
                displayedTitleField = GuiIngame.class.getDeclaredField("field_175201_x");
            } catch (NoSuchFieldException e) {
                displayedTitleField = GuiIngame.class.getDeclaredField("displayedTitle");
            }
            displayedTitleField.setAccessible(true);

            MinecraftForge.EVENT_BUS.register(this);

            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;

        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START)
            return;

        try {
            if (mc.thePlayer == null) {
                hasTriggered = false;
                return;
            }

            Boolean disableOnDeath = (Boolean) hook.getPropertyValue(disableOnDeathProperty);
            if (disableOnDeath == null || !disableOnDeath) {
                hasTriggered = false;
                return;
            }

            String currentTitle = (String) displayedTitleField.get(mc.ingameGUI);

            if (currentTitle != null && !hasTriggered) {
                String cleanTitle = currentTitle.replaceAll("§.", "").toUpperCase();

                if (cleanTitle.contains("YOU DIED")) {
                    if (hook.isModuleEnabled(killAuraModule)) {
                        hook.setModuleEnabled(killAuraModule, false);
                        hasTriggered = true;
                    }
                }
            }

            if (currentTitle == null || currentTitle.isEmpty()) {
                hasTriggered = false;
            }

        } catch (Exception e) {
        }
    }
}
