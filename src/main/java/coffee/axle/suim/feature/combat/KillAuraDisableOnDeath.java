package coffee.axle.suim.feature.combat;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.property.Setting;
import net.minecraft.client.gui.GuiIngame;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import static coffee.axle.suim.ProjectSUIM.mc;

public class KillAuraDisableOnDeath extends Feature {

    @Setting(after = "mode") private boolean disableOnDeath = false;

    private Field displayedTitleField;
    private boolean hasTriggered = false;

    public KillAuraDisableOnDeath() {
        super("KillAura:DisableOnDeath");
    }

    @Override
    protected String targetModule() {
        return "KillAura";
    }

    @Override
    protected void init() throws Exception {
        try {
            displayedTitleField = field(GuiIngame.class, "field_175201_x");
        } catch (MappingException e) {
            displayedTitleField = field(GuiIngame.class, "displayedTitle");
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

            if (!disableOnDeath) {
                hasTriggered = false;
                return;
            }

            String currentTitle = (String) displayedTitleField.get(mc.ingameGUI);

            if (currentTitle != null && !hasTriggered) {
                String cleanTitle = currentTitle.replaceAll("§.", "").toUpperCase();

                if (cleanTitle.contains("YOU DIED")) {
                    if (isEnabled()) {
                        manager.setModuleEnabled(getModuleInstance(), false);
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





