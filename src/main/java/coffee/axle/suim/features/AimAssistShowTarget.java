package coffee.axle.suim.features;

import coffee.axle.suim.hooks.MyauHook;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * AimAssist Show Target
 * 
 * @maybsomeday
 */
public class AimAssistShowTarget implements Feature {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private final MyauHook hook = MyauHook.getInstance();

    private Object aimAssistModule;
    private Object showTargetProperty;

    private Method isValidTargetMethod;
    private Method isInReachMethod;
    private Method isEnabledMethod;
    private Object hudColorProperty;
    private Object hudColorHurtProperty;
    private Field hurtTimeField;

    private static final int NONE = 0;
    private static final int DEFAULT = 1;
    private static final int HUD = 2;

    private EntityLivingBase cachedTarget = null;
    private int lastTargetCheck = 0;
    private static final int TARGET_CHECK_INTERVAL = 2;

    private Object boxOutlineWidth;

    @Override
    public String getName() {
        return "AimAssist:ShowTarget";
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");

            aimAssistModule = hook.findModule("AimAssist");
            if (aimAssistModule == null) {
                MyauLogger.log(getName(), "MODULE_NOT_FOUND");
                return false;
            }

            showTargetProperty = hook.createEnumProperty(
                    "show-target",
                    0,
                    new String[] { "NONE", "DEFAULT", "HUD" });

            boxOutlineWidth = hook.createFloatProperty("target-box-width", 2.0f, 1.0f, 5.0f);

            if (!hook.injectPropertyAfter(aimAssistModule, showTargetProperty, "aim-bone")) {
                if (!hook.registerPropertiesToModule(aimAssistModule, showTargetProperty)) {
                    MyauLogger.log(getName(), "PROPERTY_INJECT_FAIL");
                    return false;
                }
            }

            hook.registerPropertiesToModule(aimAssistModule, boxOutlineWidth);
            cacheReflectionData();

            MinecraftForge.EVENT_BUS.register(this);

            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;

        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    private void cacheReflectionData() {
        try {
            Class<?> moduleClass = aimAssistModule.getClass();

            for (Method method : moduleClass.getDeclaredMethods()) {
                method.setAccessible(true);
                Class<?>[] params = method.getParameterTypes();

                if (params.length == 1 && params[0] == Entity.class) {
                    if (method.getReturnType() == boolean.class) {
                        String methodName = method.getName();
                        if (methodName.length() <= 2) {
                            if (isValidTargetMethod == null) {
                                isValidTargetMethod = method;
                            } else if (isInReachMethod == null) {
                                isInReachMethod = method;
                            }
                        }
                    }
                }
            }

            for (Method method : moduleClass.getDeclaredMethods()) {
                method.setAccessible(true);
                if (method.getParameterCount() == 0 && method.getReturnType() == boolean.class) {
                    String methodName = method.getName();
                    if (methodName.equals("p") || methodName.length() == 1) {
                        isEnabledMethod = method;
                        break;
                    }
                }
            }

            try {
                Class<?> hudClass = Class.forName("myau.L3");

                Object hudInstance = null;
                for (Field f : hudClass.getDeclaredFields()) {
                    f.setAccessible(true);
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        Object val = f.get(null);
                        if (val != null && hudClass.isInstance(val)) {
                            hudInstance = val;
                            break;
                        }
                    }
                }

                if (hudInstance != null) {
                    hudColorProperty = hook.findProperty(hudInstance, "color");
                    hudColorHurtProperty = hook.findProperty(hudInstance, "hurt-color");
                }
            } catch (Exception ignored) {
            }

            try {
                hurtTimeField = EntityLivingBase.class.getDeclaredField("hurtTime");
                hurtTimeField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                try {
                    for (Field f : EntityLivingBase.class.getDeclaredFields()) {
                        if (f.getType() == int.class) {
                            f.setAccessible(true);
                            hurtTimeField = f;
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }
            }

        } catch (Exception e) {
            MyauLogger.error("Failed to cache reflection data", e);
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!hook.isModuleEnabled(aimAssistModule))
            return;
        if (mc.thePlayer == null || mc.theWorld == null)
            return;

        int mode = getShowTargetMode();
        if (mode == NONE)
            return;

        EntityLivingBase target = findTargetOptimized();
        if (target == null)
            return;

        drawEntityBox(target, mode, event.partialTicks);
    }

    private EntityLivingBase findTargetOptimized() {
        int currentTick = mc.thePlayer.ticksExisted;
        if (currentTick - lastTargetCheck < TARGET_CHECK_INTERVAL && cachedTarget != null) {
            if (cachedTarget.isEntityAlive() && isValidTarget(cachedTarget)) {
                return cachedTarget;
            }
        }

        lastTargetCheck = currentTick;
        cachedTarget = null;

        double closestDistance = Double.MAX_VALUE;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase))
                continue;
            if (entity == mc.thePlayer)
                continue;

            EntityLivingBase living = (EntityLivingBase) entity;
            if (!living.isEntityAlive())
                continue;

            if (!isValidTarget(living))
                continue;
            if (!isInReach(living))
                continue;

            double distance = mc.thePlayer.getDistanceToEntity(living);
            if (distance < closestDistance) {
                closestDistance = distance;
                cachedTarget = living;
            }
        }

        return cachedTarget;
    }

    private boolean isValidTarget(Entity entity) {
        if (isValidTargetMethod == null)
            return true;
        try {
            return (Boolean) isValidTargetMethod.invoke(aimAssistModule, entity);
        } catch (Exception e) {
            return true;
        }
    }

    private boolean isInReach(Entity entity) {
        if (isInReachMethod == null)
            return true;
        try {
            return (Boolean) isInReachMethod.invoke(aimAssistModule, entity);
        } catch (Exception e) {
            return true;
        }
    }

    private void drawEntityBox(EntityLivingBase entity, int mode, float partialTicks) {
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks
                - mc.getRenderManager().viewerPosX;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks
                - mc.getRenderManager().viewerPosY;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks
                - mc.getRenderManager().viewerPosZ;

        float halfWidth = entity.width / 2.0F;
        AxisAlignedBB box = new AxisAlignedBB(
                x - halfWidth, y, z - halfWidth,
                x + halfWidth, y + entity.height, z + halfWidth);

        Color color = calculateHUDColor(entity, mode);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();

        float lineWidth = 2.0f;
        try {
            lineWidth = (Float) hook.getPropertyValue(boxOutlineWidth);
        } catch (Exception ignored) {
        }
        GL11.glLineWidth(lineWidth);

        float r = color.getRed() / 255.0F;
        float g = color.getGreen() / 255.0F;
        float b = color.getBlue() / 255.0F;
        float a = color.getAlpha() / 255.0F;

        GlStateManager.color(r, g, b, a);
        RenderGlobal.drawSelectionBoundingBox(box);

        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private Color calculateHUDColor(EntityLivingBase entity, int mode) {
        if (mode == DEFAULT) {
            return new Color(255, 0, 0, 255);
        }

        boolean isHurt = false;
        try {
            if (hurtTimeField != null) {
                int hurtTime = hurtTimeField.getInt(entity);
                isHurt = hurtTime > 0;
            } else {
                isHurt = entity.hurtTime > 0;
            }
        } catch (Exception e) {
            isHurt = entity.hurtTime > 0;
        }

        if (isHurt && hudColorHurtProperty != null) {
            try {
                Object colorValue = hook.getPropertyValue(hudColorHurtProperty);
                if (colorValue instanceof Integer) {
                    return new Color((Integer) colorValue, true);
                } else if (colorValue instanceof Color) {
                    return (Color) colorValue;
                }
            } catch (Exception ignored) {
            }
            return new Color(255, 0, 0, 255);
        }

        if (hudColorProperty != null) {
            try {
                Object colorValue = hook.getPropertyValue(hudColorProperty);
                if (colorValue instanceof Integer) {
                    return new Color((Integer) colorValue, true);
                } else if (colorValue instanceof Color) {
                    return (Color) colorValue;
                }
            } catch (Exception ignored) {
            }
        }

        return new Color(0, 255, 0, 255);
    }

    private int getShowTargetMode() {
        try {
            Object value = hook.getPropertyValue(showTargetProperty);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (Exception ignored) {
        }
        return NONE;
    }

    @Override
    public void disable() {
        cachedTarget = null;
    }
}
