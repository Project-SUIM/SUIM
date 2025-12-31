package coffee.axle.suim

import coffee.axle.suim.util.MyauLogger
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiMainMenu
import net.minecraftforge.client.event.GuiScreenEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

@Mod(modid = "suim", useMetadata = true)
class ProjectSUIM {
    companion object {
        const val MOD_NAME = "d'Myau"
        const val MOD_NAME_LONG = "Société des Utilitaires et Injections d'Myau" // when will we need this?
        const val LOG_PREFIX = "[d'Myau]"
    }

    private var featureManager: FeatureManager? = null
    private var initialized = false
    public var mc = Minecraft.getMinecraft()
    private var tickCount = 0

    @Mod.EventHandler
    fun init(event: FMLInitializationEvent) {
        MyauLogger.setPrefix(LOG_PREFIX)
        MyauLogger.log("INIT_START")
        MinecraftForge.EVENT_BUS.register(this)
    }
    @SubscribeEvent
    fun onGuiDraw(event: GuiScreenEvent.DrawScreenEvent.Post) {
        if (event.gui is GuiMainMenu) {
            val fr = mc.fontRendererObj

            val brandings = FMLCommonHandler.instance().getBrandings(true)
            val brandingCount = brandings.count { !it.isNullOrEmpty() }
            val brandingHeight = brandingCount * (fr.FONT_HEIGHT + 1)
            val lineHeight = fr.FONT_HEIGHT + 1
            val yPos = event.gui.height - 10 - brandingHeight - (2 * lineHeight)

            fr.drawStringWithShadow("$MOD_NAME (" + featureManager?.features?.count()+ ") Modules", 2F, yPos.toFloat(), 0xFFFFFF)
            
            val myauModuleCount = featureManager?.manager?.moduleCount ?: 0
            fr.drawStringWithShadow("Myau Modules: $myauModuleCount", 2F, (yPos + lineHeight).toFloat(), 0xFFFFFF)
        }
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (!initialized && event.phase == TickEvent.Phase.END) {
            tickCount++
            if (tickCount >= 100) {
                featureManager = FeatureManager()
                val success = featureManager!!.initializeAll()
                if (success) {
                    MyauLogger.log("FM_SUCCESS")
                    initialized = true
                } else if (tickCount >= 200) {
                    MyauLogger.log("FM_NO_TUNA")
                    tickCount = 0
                }
            }
        }
    }
}
