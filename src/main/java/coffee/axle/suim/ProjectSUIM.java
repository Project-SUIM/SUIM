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

import java.util.Collections;
import java.util.List;

@Mod(modid = "suim", useMetadata = true)
public class ProjectSUIM {

    private static volatile GuiScreen pendingDisplay = null;
    private static volatile ProjectSUIM instance;

    public static ProjectSUIM getInstance() {
        return instance;
    }

    public static void setDisplay(GuiScreen screen) {
        pendingDisplay = screen;
    }

    public static final String MOD_NAME = "SUIM";
    public static final String MOD_NAME_LONG = "Soci\u00E9t\u00E9 des Utilitaires et Injections pour Myau";
    public static final String LOG_PREFIX = "[SUIM]";

    public static final String CHAT_PREFIX = "\u00A75[\u00A7dProject SUIM\u00A75]\u00A7r";
    public static final String SHORT_PREFIX = "\u00A75[\u00A7dSUIM\u00A75]\u00A7r";
    public static final String RESOURCE_DOMAIN = "suim";

    private static volatile java.io.File configPath;

    public static java.io.File getConfigPath() {
        if (configPath == null) {
            try {
                configPath = new java.io.File(Minecraft.getMinecraft().mcDataDir,
                        "config/" + RESOURCE_DOMAIN);
            } catch (Exception e) {
                MyauLogger.error("CONFIG_PATH", e);
                configPath = new java.io.File("config/" + RESOURCE_DOMAIN);
            }
        }
        return configPath;
    }

    private final Minecraft mc = Minecraft.getMinecraft();

    private volatile FeatureManager featureManager;
    private volatile boolean initialized = false;
    private volatile boolean initFailed = false;
    private int tickCount = 0;
    private int initAttempts = 0;
    private static final int MAX_INIT_ATTEMPTS = 3;
    private static final int INIT_START_TICK = 100;
    private static final int INIT_TIMEOUT_TICK = 200;

    public FeatureManager getFeatureManager() {
        return featureManager;
    }

    public boolean isInitialized() {
        return initialized;
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        instance = this;
        try {
            MyauLogger.setPrefix(LOG_PREFIX);
            MyauLogger.log("INIT_START");

            MinecraftForge.EVENT_BUS.register(new PlayerMotionHandler());
            MinecraftForge.EVENT_BUS.register(new coffee.axle.suim.eventbus.EventBusBootstrap());
            MinecraftForge.EVENT_BUS.register(this);
            MyauLogger.info("Registered event handlers");
        } catch (Throwable t) {
            MyauLogger.log("CRITICAL_ERROR");
            t.printStackTrace();
        }
    }

    @Mod.EventHandler
    public void postInit(FMLLoadCompleteEvent event) {
        // Font init deferred to first use — creating GL textures during FML load
        // conflicts with the second resource reload and causes native heap corruption.
    }

    @SubscribeEvent
    public void onGuiDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiMainMenu)) return;
        try {
            net.minecraft.client.gui.FontRenderer fr = mc.fontRendererObj;
            if (fr == null) return;

            List<String> brandings = FMLCommonHandler.instance().getBrandings(true);
            if (brandings == null) brandings = Collections.emptyList();
            int brandingCount = 0;
            for (String b : brandings) {
                if (b != null && !b.isEmpty())
                    brandingCount++;
            }
            int brandingHeight = brandingCount * (fr.FONT_HEIGHT + 1);
            int lineHeight = fr.FONT_HEIGHT + 1;
            int yPos = event.gui.height - 10
                    - brandingHeight - (2 * lineHeight);

            FeatureManager fm = featureManager;
            int featureCount = 0;
            int myauModuleCount = 0;
            if (fm != null) {
                try {
                    featureCount = fm.getFeatures() != null ? fm.getFeatures().size() : 0;
                } catch (Exception ignored) {}
                try {
                    myauModuleCount = fm.getManager() != null ? fm.getManager().getModuleCount() : 0;
                } catch (Exception ignored) {}
            }

            String status = initFailed ? " \u00A7c[INIT FAILED]" : (!initialized ? " \u00A7e[LOADING]" : "");
            fr.drawStringWithShadow(
                    MOD_NAME + " (" + featureCount + ") Modules" + status,
                    2F, (float) yPos, 0xFFFFFF);

            fr.drawStringWithShadow(
                    "Myau Modules: " + myauModuleCount,
                    2F, (float) (yPos + lineHeight), 0xFFFFFF);
        } catch (Throwable t) {
            MyauLogger.error("GUI_DRAW", new Exception(t));
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        try {
            if (event.phase == TickEvent.Phase.START) {
                GuiScreen display = pendingDisplay;
                if (display != null) {
                    pendingDisplay = null;
                    try {
                        mc.displayGuiScreen(display);
                    } catch (Throwable t) {
                        MyauLogger.error("DISPLAY_GUI", new Exception(
                                "Failed to display " + display.getClass().getSimpleName(), t));
                    }
                }
            }

            if (initialized || initFailed || event.phase != TickEvent.Phase.END) return;

            tickCount++;
            if (tickCount < INIT_START_TICK) return;

            if (featureManager == null) {
                try {
                    MyauLogger.info("Initializing FeatureManager (attempt " + (initAttempts + 1) + "/" + MAX_INIT_ATTEMPTS + ")...");
                    featureManager = new FeatureManager();
                } catch (Throwable t) {
                    MyauLogger.error("FM_CREATE", new Exception("FeatureManager constructor failed", t));
                    handleInitFailure();
                    return;
                }
            }

            boolean success;
            try {
                success = featureManager.initializeAll();
            } catch (Throwable t) {
                MyauLogger.error("FM_INIT", new Exception("FeatureManager.initializeAll() threw", t));
                handleInitFailure();
                return;
            }

            if (success) {
                try {
                    GuiModuleManager guiModMgr = new GuiModuleManager(
                            featureManager.getManager(),
                            featureManager.getPropertyManager());
                    guiModMgr.populate(featureManager.getFeatures());
                    guiModMgr.populateFromMyau();
                } catch (Throwable t) {
                    MyauLogger.error("GUI_MOD_MGR", new Exception("GuiModuleManager setup failed", t));
                }
                MyauLogger.log("FM_SUCCESS");
                initialized = true;
            } else if (tickCount >= INIT_TIMEOUT_TICK) {
                handleInitFailure();
            }
        } catch (Throwable t) {
            MyauLogger.error("TICK_HANDLER", new Exception("Unhandled error in tick handler", t));
        }
    }

    private void handleInitFailure() {
        initAttempts++;
        if (initAttempts >= MAX_INIT_ATTEMPTS) {
            MyauLogger.log("FM_NO_TUNA");
            MyauLogger.info("Initialization permanently failed after " + MAX_INIT_ATTEMPTS + " attempts");
            initFailed = true;
        } else {
            MyauLogger.logMore("FM_NO_TUNA", "retrying (" + initAttempts + "/" + MAX_INIT_ATTEMPTS + ")");
            featureManager = null;
            tickCount = 0;
        }
    }
}
