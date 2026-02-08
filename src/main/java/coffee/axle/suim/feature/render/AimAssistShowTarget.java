package coffee.axle.suim.feature.render;

import coffee.axle.suim.feature.Feature;

import coffee.axle.suim.util.HudUtils;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * AimAssist Show Target
 *
 * @maybsomeday
 */
public class AimAssistShowTarget extends Feature {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private Object aimAssistModule;
    private Object showTargetProperty;

    private Object weaponsOnlyProperty;
    private Object allowToolsProperty;

    private Object boxOutlineWidth;

    private Method isValidTargetMethod;
    private Method isInReachMethod;
    private Field hurtTimeField;

    private EntityPlayer cachedTarget;
    private int targetUpdateCounter = 0;
    private static final int TARGET_UPDATE_INTERVAL = 10;

    private final Color defaultColorHurt = new Color(16733525);
    private final Color defaultColorNormal = new Color(5635925);

    @Override
    public String getName() {
        return "AimAssist:ShowTarget";
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");

            aimAssistModule = manager.findModule("AimAssist");
            if (aimAssistModule == null) {
                MyauLogger.log(getName(), "MODULE_NOT_FOUND");
                return false;
            }

            HudUtils.getInstance().initialize();

            weaponsOnlyProperty = manager.findProperty(
                    aimAssistModule, "weapons-only");
            allowToolsProperty = manager.findProperty(
                    aimAssistModule, "allow-tools");

            showTargetProperty = creator.createEnumProperty(
                    "show-target",
                    0,
                    new String[] { "NONE", "DEFAULT", "HUD", "BOX" });

            boxOutlineWidth = creator.createFloatProperty(
                    "target-box-width", 2.0f, 1.0f, 5.0f);

            if (!creator.injectPropertyAfter(
                    aimAssistModule, showTargetProperty, "aim-bone")) {
                creator.registerProperties(aimAssistModule, showTargetProperty);
            }

            creator.registerProperties(aimAssistModule, boxOutlineWidth);

            cacheAimAssistMethods();
            cacheHurtTimeField();

            MinecraftForge.EVENT_BUS.register(this);

            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;

        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    private void cacheAimAssistMethods() {
        for (Method m : aimAssistModule.getClass().getDeclaredMethods()) {
            if (m.getParameterCount() == 1
                    && m.getParameterTypes()[0].equals(Entity.class)
                    && m.getReturnType().equals(boolean.class)) {
                m.setAccessible(true);
                if (isValidTargetMethod == null) {
                    isValidTargetMethod = m;
                } else if (isInReachMethod == null) {
                    isInReachMethod = m;
                }
            }
        }

    }

    private void cacheHurtTimeField() {
        try {
            hurtTimeField = EntityLivingBase.class.getDeclaredField("hurtTime");
        } catch (NoSuchFieldException e) {
            try {
                hurtTimeField = EntityLivingBase.class.getDeclaredField(
                        "field_70737_aN");
            } catch (NoSuchFieldException ignored) {
            }
        }

        if (hurtTimeField != null) {
            hurtTimeField.setAccessible(true);
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        try {
            if (!manager.isModuleEnabled(aimAssistModule)) {
                cachedTarget = null;
                return;
            }

            if (mc.thePlayer == null || mc.theWorld == null) {
                cachedTarget = null;
                return;
            }

            if (!checkWeaponRequirement()) {
                cachedTarget = null;
                return;
            }

            Integer mode = (Integer) properties.getPropertyValue(showTargetProperty);
            if (mode == null || mode == 0)
                return;

            targetUpdateCounter++;
            if (targetUpdateCounter >= TARGET_UPDATE_INTERVAL) {
                cachedTarget = findTargetOptimized();
                targetUpdateCounter = 0;
            }

            if (cachedTarget == null
                    || cachedTarget.isDead
                    || cachedTarget.getHealth() <= 0) {
                cachedTarget = null;
                return;
            }

            Color color;
            if (mode == 1 || mode == 3) {
                color = getDefaultColorFast(cachedTarget);
            } else {
                color = HudUtils.getInstance()
                        .getHudColor(defaultColorNormal);
            }

            if (mode == 3) {
                drawEntityOutlineBox(cachedTarget, color, event.partialTicks);
            } else {
                drawEntityBox(cachedTarget, color, event.partialTicks);
            }

        } catch (Exception ignored) {
        }
    }

    private boolean checkWeaponRequirement() {
        try {
            Boolean weaponsOnly = false;
            if (weaponsOnlyProperty != null) {
                weaponsOnly = (Boolean) properties.getPropertyValue(weaponsOnlyProperty);
            }

            if (weaponsOnly == null || !weaponsOnly) {
                return true;
            }

            ItemStack heldItem = mc.thePlayer.getHeldItem();
            if (heldItem == null) {
                return false;
            }

            if (heldItem.getItem() instanceof ItemSword) {
                return true;
            }

            Boolean allowTools = false;
            if (allowToolsProperty != null) {
                allowTools = (Boolean) properties.getPropertyValue(allowToolsProperty);
            }

            if (allowTools != null
                    && allowTools
                    && heldItem.getItem() instanceof ItemTool) {
                return true;
            }

            return false;

        } catch (Exception e) {
            return true;
        }
    }

    private EntityPlayer findTargetOptimized() {
        try {
            EntityPlayer closest = null;
            double closestDist = Double.MAX_VALUE;

            for (Object entity : mc.theWorld.loadedEntityList) {
                if (!(entity instanceof EntityPlayer))
                    continue;

                EntityPlayer player = (EntityPlayer) entity;
                if (player == mc.thePlayer)
                    continue;

                if (!invokeQuietly(
                        isValidTargetMethod, aimAssistModule, player)) {
                    continue;
                }

                double dist = mc.thePlayer.getDistanceToEntity(player);
                if (dist < closestDist) {
                    closest = player;
                    closestDist = dist;
                }
            }

            if (closest != null && isInReachMethod != null) {
                if (!invokeQuietly(
                        isInReachMethod, aimAssistModule, closest)) {
                    return null;
                }
            }

            return closest;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean invokeQuietly(
            Method method, Object instance, Object... args) {
        try {
            return (Boolean) method.invoke(instance, args);
        } catch (Exception e) {
            return false;
        }
    }

    private Color getDefaultColorFast(EntityPlayer target) {
        try {
            int hurtTime = hurtTimeField.getInt(target);
            return hurtTime > 0 ? defaultColorHurt : defaultColorNormal;
        } catch (Exception e) {
            return defaultColorNormal;
        }
    }

    private void drawEntityOutlineBox(
            EntityPlayer entity, Color color, float partialTicks) {
        double x = entity.lastTickPosX
                + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY
                + (entity.posY - entity.lastTickPosY) * partialTicks;
        double z = entity.lastTickPosZ
                + (entity.posZ - entity.lastTickPosZ) * partialTicks;

        AxisAlignedBB box = entity.getEntityBoundingBox()
                .expand(0.1f, 0.1f, 0.1f)
                .offset(x - entity.posX, y - entity.posY, z - entity.posZ)
                .offset(
                        -mc.getRenderManager().viewerPosX,
                        -mc.getRenderManager().viewerPosY,
                        -mc.getRenderManager().viewerPosZ);

        float lineWidth = 2.0f;
        try {
            lineWidth = (Float) properties.getPropertyValue(boxOutlineWidth);
        } catch (Exception ignored) {
        }

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();

        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        RenderGlobal.drawOutlinedBoundingBox(
                box,
                color.getRed(), color.getGreen(),
                color.getBlue(), color.getAlpha());

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);

        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void drawEntityBox(
            EntityPlayer entity, Color color, float partialTicks) {
        double x = entity.lastTickPosX
                + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY
                + (entity.posY - entity.lastTickPosY) * partialTicks;
        double z = entity.lastTickPosZ
                + (entity.posZ - entity.lastTickPosZ) * partialTicks;

        AxisAlignedBB box = entity.getEntityBoundingBox()
                .expand(0.1f, 0.1f, 0.1f)
                .offset(x - entity.posX, y - entity.posY, z - entity.posZ)
                .offset(
                        -mc.getRenderManager().viewerPosX,
                        -mc.getRenderManager().viewerPosY,
                        -mc.getRenderManager().viewerPosZ);

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(
                color.getRed() / 255.0f,
                color.getGreen() / 255.0f,
                color.getBlue() / 255.0f,
                0.15f);

        // Bottom
        GL11.glVertex3d(box.minX, box.minY, box.minZ);
        GL11.glVertex3d(box.maxX, box.minY, box.minZ);
        GL11.glVertex3d(box.maxX, box.minY, box.maxZ);
        GL11.glVertex3d(box.minX, box.minY, box.maxZ);

        // Top
        GL11.glVertex3d(box.minX, box.maxY, box.minZ);
        GL11.glVertex3d(box.minX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.minZ);

        // Front
        GL11.glVertex3d(box.minX, box.minY, box.minZ);
        GL11.glVertex3d(box.minX, box.maxY, box.minZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.minZ);
        GL11.glVertex3d(box.maxX, box.minY, box.minZ);

        // Back
        GL11.glVertex3d(box.minX, box.minY, box.maxZ);
        GL11.glVertex3d(box.maxX, box.minY, box.maxZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.minX, box.maxY, box.maxZ);

        // Left
        GL11.glVertex3d(box.minX, box.minY, box.minZ);
        GL11.glVertex3d(box.minX, box.minY, box.maxZ);
        GL11.glVertex3d(box.minX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.minX, box.maxY, box.minZ);

        // Right
        GL11.glVertex3d(box.maxX, box.minY, box.minZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.minZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.maxX, box.minY, box.maxZ);

        GL11.glEnd();

        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    @Override
    public void disable() {
        cachedTarget = null;
    }
}
