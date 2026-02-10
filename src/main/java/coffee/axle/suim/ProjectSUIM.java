package coffee.axle.suim;

import coffee.axle.suim.feature.FeatureManager;
import coffee.axle.suim.clickgui.module.GuiModuleManager;
import coffee.axle.suim.handlers.PlayerMotionHandler;
import coffee.axle.suim.clickgui.util.FontUtil;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraft.client.gui.GuiScreen;

import java.util.List;

@Mod(modid = "suim", useMetadata = true)
public class ProjectSUIM {

    private static GuiScreen pendingDisplay = null;

    public static void setDisplay(GuiScreen screen) {
        pendingDisplay = screen;
    }

    public static final String MOD_NAME = "d'Myau";
    public static final String MOD_NAME_LONG = "Soci\u00E9t\u00E9 des Utilitaires et Injections d'Myau";
    public static final String LOG_PREFIX = "[d'Myau]";

    public static final String CHAT_PREFIX = "\u00A75[\u00A7dProject SUIM\u00A75]\u00A7r";
    public static final String SHORT_PREFIX = "\u00A75[\u00A7dSUIM\u00A75]\u00A7r";
    public static final String RESOURCE_DOMAIN = "suim";

    private static final java.io.File CONFIG_PATH = new java.io.File(Minecraft.getMinecraft().mcDataDir,
            "config/" + RESOURCE_DOMAIN);

    public static java.io.File getConfigPath() {
        return CONFIG_PATH;
    }

    private final Minecraft mc = Minecraft.getMinecraft();

    private FeatureManager featureManager;
    private boolean initialized = false;
    private int tickCount = 0;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MyauLogger.setPrefix(LOG_PREFIX);
        MyauLogger.log("INIT_START");

        MinecraftForge.EVENT_BUS.register(new PlayerMotionHandler());
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void postInit(FMLLoadCompleteEvent event) {
        FontUtil.INSTANCE.setupFontUtils();
    }

    @SubscribeEvent
    public void onGuiDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event.gui instanceof GuiMainMenu) {
            net.minecraft.client.gui.FontRenderer fr = mc.fontRendererObj;

            List<String> brandings = FMLCommonHandler.instance().getBrandings(true);
            int brandingCount = 0;
            for (String b : brandings) {
                if (b != null && !b.isEmpty())
                    brandingCount++;
            }
            int brandingHeight = brandingCount * (fr.FONT_HEIGHT + 1);
            int lineHeight = fr.FONT_HEIGHT + 1;
            int yPos = event.gui.height - 10
                    - brandingHeight - (2 * lineHeight);

            int featureCount = featureManager != null
                    ? featureManager.getFeatures().size()
                    : 0;
            fr.drawStringWithShadow(
                    MOD_NAME + " (" + featureCount + ") Modules",
                    2F, (float) yPos, 0xFFFFFF);

            int myauModuleCount = featureManager != null
                    ? featureManager.getManager().getModuleCount()
                    : 0;
            fr.drawStringWithShadow(
                    "Myau Modules: " + myauModuleCount,
                    2F, (float) (yPos + lineHeight), 0xFFFFFF);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START && pendingDisplay != null) {
            mc.displayGuiScreen(pendingDisplay);
            pendingDisplay = null;
        }

        if (!initialized && event.phase == TickEvent.Phase.END) {
            tickCount++;
            if (tickCount >= 100) {
                featureManager = new FeatureManager();
                boolean success = featureManager.initializeAll();
                if (success) {
                    MyauLogger.log("FM_SUCCESS");

                    GuiModuleManager guiModMgr = new GuiModuleManager(
                            featureManager.getManager(),
                            featureManager.getPropertyManager());
                    guiModMgr.populate(featureManager.getFeatures());
                    guiModMgr.populateFromMyau();

                    initialized = true;
                } else if (tickCount >= 200) {
                    MyauLogger.log("FM_NO_TUNA");
                    tickCount = 0;
                }
            }
        }
    }
}
