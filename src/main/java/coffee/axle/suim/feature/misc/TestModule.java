package coffee.axle.suim.feature.misc;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.property.Setting;
import coffee.axle.suim.render.AimDotTracker;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.render.TestModuleRenderer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;

public class TestModule extends Feature {

    @Setting(name = "debug-aim-dots") private boolean aimDots = false;
    @Setting private boolean evilWeirdBoxTestThing = false;
    @Setting private boolean useMixin = false;

    public TestModule() {
        super("TestModule", GuiCategory.MISC);
    }

    @Override
    protected void init() throws Exception {
        ArrayList<String> commandNames = new ArrayList<>();
        commandNames.add("testmodule");
        creator.registerCommand(commandNames, this::handleCommand);
    }

    @Override
    protected void onEnable() {
        TestModuleRenderer.setModuleEnabled(true);
        MyauLogger.info("TestModule enabled!");
    }

    @Override
    protected void onDisable() {
        TestModuleRenderer.setModuleEnabled(false);
        AimDotTracker.clear();
        MyauLogger.info("TestModule disabled!");
    }

    private void handleCommand(ArrayList<String> args) {
        if (args.isEmpty()) {
            boolean useMixin = this.useMixin;
            manager.sendMessage("&7Usage: &f.testmodule <use-mixin> <true/false>");
            manager.sendMessage("&7Current use-mixin: &f" + useMixin);
            manager.sendMessage("&7Box color: " + (useMixin ? "&aGREEN (mixin)" : "&5PURPLE (direct)"));
            return;
        }

        String subCommand = args.get(0).toLowerCase();
        if (subCommand.equals("use-mixin") && args.size() > 1) {
            boolean value = args.get(1).equalsIgnoreCase("true");
            setSaved("use-mixin", value);
            TestModuleRenderer.setMixinEnabled(value);
            manager.sendMessage("&aSet use-mixin to: &f" + value);
            manager.sendMessage("&7Box will now be: " + (value ? "&aGREEN (mixin)" : "&5PURPLE (direct)"));
        } else {
            manager.sendMessage("&cUnknown option: &f" + subCommand);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START)
            return;

        TestModuleRenderer.setModuleEnabled(isEnabled());
        TestModuleRenderer.setMixinEnabled(useMixin);
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (!isEnabled())
            return;
        if (!aimDots)
            return;
        if (event.target == null)
            return;

        AimDotTracker.recordHit(event.target);
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        TestModuleRenderer.renderDirectBox(event.partialTicks);

        if (isEnabled() && aimDots) {
            AimDotTracker.renderDots(event.partialTicks);
        }
    }

    @Override
    public void disable() {
        try {
            TestModuleRenderer.setModuleEnabled(false);
            AimDotTracker.clear();
            if (getModuleInstance() != null) {
                manager.setModuleEnabled(getModuleInstance(), false);
            }
        } catch (Exception e) {
            MyauLogger.error("TestModule:onDisable", e);
        }
    }
}





