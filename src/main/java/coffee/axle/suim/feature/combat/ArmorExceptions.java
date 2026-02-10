package coffee.axle.suim.feature.combat;

import coffee.axle.suim.feature.Feature;

import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * Armor Exceptions for KillAura
 * Prevents targeting players wearing specific armor items
 */
@SuppressWarnings("unused")

public class ArmorExceptions extends Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static ArmorExceptions INSTANCE = null;

    private Object killAuraModule;
    private Object armorExceptionsProperty;
    private Set<String> cachedExceptions = new HashSet<>();
    private int cacheUpdateCounter = 0;
    private static final int CACHE_UPDATE_INTERVAL = 20;

    private boolean initialized = false;
    private String lastExceptionString = "";

    @Override
    public String getName() {
        return "KillAura:armor-exceptions";
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");

            killAuraModule = manager.findModule("KillAura");

            if (killAuraModule == null) {
                MyauLogger.log(getName(), "MODULE_NOT_FOUND");
                return false;
            }

            armorExceptionsProperty = creator.createStringProperty("filter-armor", "none");

            if (!creator.injectPropertyAfter(killAuraModule, armorExceptionsProperty, "sort")) {
                MyauLogger.log(getName(), "PROPERTY_INJECT_FAIL");
                return false;
            }

            initialized = true;
            INSTANCE = this;
            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;

        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Check if entity should be excluded based on armor
     * Called from mixin
     */
    public boolean shouldExcludeEntity(EntityLivingBase entity) {
        if (!initialized) {
            return false;
        }

        try {
            cacheUpdateCounter++;
            if (cacheUpdateCounter >= CACHE_UPDATE_INTERVAL) {
                updateExceptionsCache();
                cacheUpdateCounter = 0;
            }

            if (cachedExceptions.isEmpty()) {
                return false;
            }

            // (0=boots, 1=legs, 2=chest, 3=helm)
            for (int i = 0; i < 4; i++) {
                ItemStack armorStack = entity.getCurrentArmor(i);
                if (armorStack != null && armorStack.getItem() instanceof ItemArmor) {
                    String itemName = getItemName(armorStack);

                    if (isException(itemName)) {
                        return true;
                    }
                }
            }

            return false;

        } catch (Exception e) {
            return false;
        }
    }

    private void updateExceptionsCache() {
        try {
            String exceptionsValue = (String) properties.getPropertyValue(armorExceptionsProperty);
            if (exceptionsValue == null)
                exceptionsValue = "none";

            if (!exceptionsValue.equals(lastExceptionString)) {
                cachedExceptions.clear();

                if (!exceptionsValue.trim().isEmpty() && !exceptionsValue.equalsIgnoreCase("none")) {
                    String[] items = exceptionsValue.toLowerCase().split(",");
                    for (String item : items) {
                        String cleaned = item.trim().replaceAll("[_\\s]", "");
                        if (!cleaned.isEmpty()) {
                            cachedExceptions.add(cleaned);
                        }
                    }
                }

                lastExceptionString = exceptionsValue;
            }

        } catch (Exception e) {
            cachedExceptions.clear();
        }
    }

    private boolean isException(String itemName) {
        if (cachedExceptions.isEmpty() || itemName == null || itemName.isEmpty()) {
            return false;
        }

        String normalizedItem = itemName.replaceAll("[_\\s]", "");

        for (String exc : cachedExceptions) {
            if (normalizedItem.contains(exc)) {
                return true;
            }
        }

        return false;
    }

    private String getItemName(ItemStack stack) {
        try {
            String unlocalizedName = stack.getItem().getUnlocalizedName(stack).toLowerCase();
            String baseName = unlocalizedName.replaceFirst("^(item\\.|tile\\.)", "");
            return baseName;
        } catch (Exception e) {
            return "";
        }
    }

    public static ArmorExceptions getInstance() {
        return INSTANCE;
    }
}





