package coffee.axle.suim.feature.render;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.util.GameLocation;
import coffee.axle.suim.util.MyauLogger;
import coffee.axle.suim.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class MurderMystery extends Feature {

    private static final Minecraft mc = Minecraft.getMinecraft();
    // just all the bullshit atp
    private static final String[] KNIFE_ITEMS = {
            "iron_sword", "stone_sword", "iron_shovel", "stick", "wooden_axe", "wooden_sword",
            "deadbush", "reeds", "stone_shovel", "blaze_rod", "diamond_shovel", "quartz",
            "pumpkin_pie", "golden_pickaxe", "leather", "name_tag", "coal", "flint", "bone",
            "carrot", "golden_carrot", "cookie", "diamond_axe", "double_plant",
            "prismarine_shard", "cooked_beef", "netherbrick", "cooked_chicken", "record_blocks",
            "golden_hoe", "golden_sword", "diamond_sword", "diamond_hoe", "shears", "fish",
            "dye", "bread", "boat", "speckled_melon", "book", "sapling", "ender_chest",
            "golden_axe", "diamond_pickaxe", "golden_shovel"
    };

    private final Set<UUID> murderers = new HashSet<>();
    private final Set<UUID> detectives = new HashSet<>();
    private boolean inGame = false;
    private int heroTicks = -1;
    private int alertCooldown = 0;

    private Object moduleInstance;
    private Object alertProperty;
    private Object espProperty;
    private Object tracersProperty;
    private Object bowTracersProperty;
    private Object revealNamesProperty;
    private Object tracersInnocentsProperty;

    private static MurderMystery instance;

    private static final int INNOCENTS_NONE = 0;
    private static final int INNOCENTS_MURDERER = 1;
    private static final int INNOCENTS_ALWAYS = 2;

    @Override
    public String getName() {
        return "MurderMystery";
    }

    @Override
    public GuiCategory getGuiCategory() {
        return GuiCategory.RENDER;
    }

    @Override
    public boolean initialize() {
        try {
            instance = this;
            moduleInstance = createModule();
            creator.injectModule(moduleInstance, getClass());

            alertProperty = creator.createBooleanProperty("alert", true);
            espProperty = creator.createBooleanProperty("esp", true);
            tracersProperty = creator.createBooleanProperty("tracers", true);
            bowTracersProperty = creator.createBooleanProperty("bow-tracers", true);
            revealNamesProperty = creator.createBooleanProperty("reveal-names", true);

            String[] innocentsModes = { "NONE", "MURDERER", "ALWAYS" };
            tracersInnocentsProperty = creator.createEnumProperty("tracers-innocents", INNOCENTS_MURDERER, innocentsModes);

            creator.registerProperties(moduleInstance, alertProperty, espProperty,
                    tracersProperty, bowTracersProperty, revealNamesProperty, tracersInnocentsProperty);

            manager.registerModuleCallbacks(moduleInstance, this::onEnable, this::onDisable);
            manager.reloadModuleCommand();

            MinecraftForge.EVENT_BUS.register(this);
            return true;
        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
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
        inGame = false;
        heroTicks = -1;
        alertCooldown = 0;
        murderers.clear();
        detectives.clear();
    }

    @SubscribeEvent
    public void onWorldJoin(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) reset();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (alertCooldown > 0) alertCooldown--;
        if (!manager.isModuleEnabled(moduleInstance)) return;

        inGame = isMurderGame();
        if (!inGame) {
            if (!murderers.isEmpty() || !detectives.isEmpty()) reset();
            return;
        }

        if (heroTicks > 0) {
            heroTicks--;
            if (heroTicks == 0) {
                reset();
                heroTicks = -1;
            }
        }

        scanHeldItems();
        revealTeams();

        if (heroTicks < 0 && !murderers.isEmpty()) {
            boolean allDead = true;
            for (UUID id : murderers) {
                EntityPlayer p = playerByUuid(id);
                if (p != null && p.isEntityAlive() && p.deathTime == 0) {
                    allDead = false;
                    break;
                }
            }
            if (allDead) heroTicks = 200;
        }
    }

    private EntityPlayer playerByUuid(UUID id) {
        Entity e = mc.theWorld.getPlayerEntityByUUID(id);
        return e instanceof EntityPlayer ? (EntityPlayer) e : null;
    }

    private boolean isKnife(Item item) {
        String name = item.getRegistryName() == null ? "" : item.getRegistryName();
        name = name.replaceFirst("minecraft:", "");
        for (String k : KNIFE_ITEMS) if (k.equals(name)) return true;
        return false;
    }

    private void scanHeldItems() {
        for (Object o : mc.theWorld.playerEntities) {
            if (!(o instanceof EntityPlayer)) continue;
            EntityPlayer p = (EntityPlayer) o;
            if (p == mc.thePlayer || p.isPlayerSleeping() || p.deathTime > 0) continue;
            String name = strippedName(p);
            if (name.isEmpty()) continue;

            ItemStack held = p.getHeldItem();
            Item item = held == null ? null : held.getItem();

            if (item != null && isKnife(item)) {
                detectives.remove(p.getUniqueID());
                if (murderers.add(p.getUniqueID()) && alertCooldown <= 0) {
                    alert("&c" + name + " &7is the &4murderer&7!");
                    alertCooldown = 40;
                }
            } else if (item == Items.bow && !detectives.contains(p.getUniqueID())) {
                // anyone with 10 gold can buy a bow, so no detective cap
                detectives.add(p.getUniqueID());
                if (alertCooldown <= 0) {
                    alert("&b" + name + " &7has a &9bow&7!");
                    alertCooldown = 40;
                }
            }
        }
    }

    // we are holding a sword in mm = hero, everyone who was a murderer is no longer
    private boolean isHero() {
        ItemStack held = mc.thePlayer.getHeldItem();
        return held != null && held.getItem() != null && isKnife(held.getItem());
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent e) {
        if (!inGame || !manager.isModuleEnabled(moduleInstance)) return;
        String msg = e.message.getUnformattedText();
        if (msg.contains("You are the murderer!") || msg.contains("You are the MURDERER!")) {
            reset();
            murderers.add(mc.thePlayer.getUniqueID());
            alert("&7You are the &4murderer&7!");
        }
    }

    // render ESP/tracers for the murderer (and the dropped bow)
    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!inGame || !manager.isModuleEnabled(moduleInstance)) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        boolean esp = properties.getBoolean(espProperty, true);
        boolean tracers = properties.getBoolean(tracersProperty, true);
        boolean bowTracers = properties.getBoolean(bowTracersProperty, true);
        if (!esp && !tracers && !bowTracers) return;

        boolean hero = isHero();
        float partialTicks = event.partialTicks;

        Entity view = mc.getRenderViewEntity();
        if (view == null) return;
        Vec3 start = tracerStart(partialTicks);

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glLineWidth(1.5f);

        for (Object o : mc.theWorld.playerEntities) {
            if (!(o instanceof EntityPlayer)) continue;
            EntityPlayer p = (EntityPlayer) o;
            if (p == mc.thePlayer || p.deathTime > 0) continue;

            boolean murderer = murderers.contains(p.getUniqueID());
            if (murderer) {
                if (esp) drawBox(p, partialTicks, 1.0f, 0.2f, 0.2f);
                if (tracers) drawTracer(start, p, partialTicks, 1.0f, 0.2f, 0.2f);
            } else if (detectives.contains(p.getUniqueID())) {
                // a detective cannot be shot with their own bow, so the hero
                // does not need them flagged
                if (!hero && (esp || tracers)) {
                    if (esp) drawBox(p, partialTicks, 0.2f, 0.5f, 1.0f);
                    if (tracers) drawTracer(start, p, partialTicks, 0.2f, 0.5f, 1.0f);
                }
            }
        }

        if (bowTracers && !hero) {
            for (Object oi : mc.theWorld.loadedEntityList) {
                if (!(oi instanceof EntityItem)) continue;
                EntityItem drop = (EntityItem) oi;
                if (drop.getEntityItem() == null || drop.getEntityItem().getItem() != Items.bow) continue;
                drawTracer(start, drop.posX, drop.posY + 0.5, drop.posZ, 1.0f, 0.85f, 0.0f);
            }
        }

        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void drawBox(EntityPlayer p, float partialTicks, float r, float g, float b) {
        Vec3 pos = Utils.interpolate(p, partialTicks);
        double x = pos.xCoord;
        double y = pos.yCoord;
        double z = pos.zCoord;
        float h = p.height;
        float w = p.width / 2.0f + 0.1f;
        float a = 1.0f;

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glColor4f(r, g, b, a);
        GL11.glVertex3d(x - w, y, z - w);
        GL11.glVertex3d(x + w, y, z - w);
        GL11.glVertex3d(x + w, y, z + w);
        GL11.glVertex3d(x - w, y, z + w);
        GL11.glEnd();
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glColor4f(r, g, b, a);
        GL11.glVertex3d(x - w, y + h, z - w);
        GL11.glVertex3d(x + w, y + h, z - w);
        GL11.glVertex3d(x + w, y + h, z + w);
        GL11.glVertex3d(x - w, y + h, z + w);
        GL11.glEnd();
        GL11.glBegin(GL11.GL_LINES);
        GL11.glColor4f(r, g, b, a);
        GL11.glVertex3d(x - w, y, z - w);
        GL11.glVertex3d(x - w, y + h, z - w);
        GL11.glVertex3d(x + w, y, z - w);
        GL11.glVertex3d(x + w, y + h, z - w);
        GL11.glVertex3d(x + w, y, z + w);
        GL11.glVertex3d(x + w, y + h, z + w);
        GL11.glVertex3d(x - w, y, z + w);
        GL11.glVertex3d(x - w, y + h, z + w);
        GL11.glEnd();
    }

    private void drawTracer(Vec3 start, EntityPlayer p, float partialTicks, float r, float g, float b) {
        drawTracer(start,
                Utils.lerp(p.posX, p.lastTickPosX, partialTicks),
                Utils.lerp(p.posY, p.lastTickPosY, partialTicks) + p.getEyeHeight() * 0.8,
                Utils.lerp(p.posZ, p.lastTickPosZ, partialTicks), r, g, b);
    }

    private void drawTracer(Vec3 start, double x, double y, double z, float r, float g, float b) {
        GL11.glBegin(GL11.GL_LINES);
        GL11.glColor4f(r, g, b, 0.8f);
        GL11.glVertex3d(start.xCoord, start.yCoord, start.zCoord);
        GL11.glVertex3d(x, y, z);
        GL11.glEnd();
    }

    private Vec3 tracerStart(float partialTicks) {
        Entity view = mc.getRenderViewEntity();
        Vec3 dir;
        if (mc.gameSettings.thirdPersonView == 0) {
            dir = new Vec3(0, 0, 1)
                    .rotatePitch((float) -Math.toRadians(Utils.lerp(view.rotationPitch, view.prevRotationPitch, partialTicks)))
                    .rotateYaw((float) -Math.toRadians(Utils.lerp(view.rotationYaw, view.prevRotationYaw, partialTicks)));
        } else {
            dir = new Vec3(0, 0, 0);
        }
        return new Vec3(
                dir.xCoord + Utils.lerp(view.posX, view.lastTickPosX, partialTicks),
                dir.yCoord + Utils.lerp(view.posY, view.lastTickPosY, partialTicks) + view.getEyeHeight(),
                dir.zCoord + Utils.lerp(view.posZ, view.lastTickPosZ, partialTicks));
    }

    // verdict for MixinTracers, filtering myau's own Tracers module.
    // null = let myau decide, FALSE = hide, TRUE = force show
    public static Boolean tracerVerdict(EntityPlayer p) {
        MurderMystery m = instance;
        if (m == null || !m.inGame || p == null || mc.thePlayer == null) return null;
        if (!m.manager.isModuleEnabled(m.moduleInstance)) return null;

        if (m.murderers.contains(p.getUniqueID()) || m.detectives.contains(p.getUniqueID())) {
            // we draw our own colored lines for these when tracers is on
            return m.properties.getBoolean(m.tracersProperty, true) ? Boolean.FALSE : null;
        }

        int mode = m.properties.getInt(m.tracersInnocentsProperty, INNOCENTS_MURDERER);
        if (mode == INNOCENTS_NONE) return Boolean.FALSE;
        if (mode == INNOCENTS_MURDERER) {
            // innocents only once we got the sword or picked up a murder item
            return (m.murderers.contains(mc.thePlayer.getUniqueID()) || m.isHero()) ? null : Boolean.FALSE;
        }
        return null;
    }

    private boolean isMurderGame() {
        if (GameLocation.isKnown()) {
            return GameLocation.inGame("MURDER_MYSTERY");
        }
        // sidebar fallback for when the location packet has not landed yet
        Scoreboard sb = mc.theWorld.getScoreboard();
        if (sb == null) return false;
        ScoreObjective obj = sb.getObjectiveInDisplaySlot(1);
        if (obj == null) return false;
        // the title is the objective displayname on hypixel, not a score line
        if (!stripCodes(obj.getDisplayName()).contains("MURDER")) return false;
        for (Score s : sb.getSortedScores(obj)) {
            String line = stripCodes(ScorePlayerTeam.formatPlayerName(
                    sb.getPlayersTeam(s.getPlayerName()), s.getPlayerName()));
            if (line.contains("Innocents Left:") || line.contains("Innocents:")) return true;
        }
        return false;
    }

    private String strippedName(EntityPlayer p) {
        if (p.getTeam() instanceof ScorePlayerTeam) {
            return stripCodes(ScorePlayerTeam.formatPlayerName((ScorePlayerTeam) p.getTeam(), p.getName()));
        }
        return stripCodes(p.getName());
    }

    // reveal-names: force murderer/detective team tags visible so myau
    // nametags and vanilla tab show a colored prefix
    void revealTeams() {
        boolean reveal = properties.getBoolean(revealNamesProperty, true);
        for (Object o : mc.theWorld.playerEntities) {
            if (!(o instanceof EntityPlayer)) continue;
            EntityPlayer p = (EntityPlayer) o;
            if (!(p.getTeam() instanceof ScorePlayerTeam)) continue;
            ScorePlayerTeam team = (ScorePlayerTeam) p.getTeam();
            String prefix = murderers.contains(p.getUniqueID()) ? "\u00a74[M] \u00a7c"
                    : detectives.contains(p.getUniqueID()) ? "\u00a79[B] \u00a7b" : null;
            if (prefix == null) continue;
            team.setNameTagVisibility(Team.EnumVisible.ALWAYS);
            if (reveal) team.setNamePrefix(prefix);
        }
    }

    private void alert(String text) {
        if (!properties.getBoolean(alertProperty, true)) return;
        manager.sendMessage("&7MurderMystery: &r" + text);
    }

    private static final Pattern FORMAT_CODES = Pattern.compile("\u00a7.");

    private static String stripCodes(String s) {
        return s == null ? "" : FORMAT_CODES.matcher(s).replaceAll("");
    }

}
