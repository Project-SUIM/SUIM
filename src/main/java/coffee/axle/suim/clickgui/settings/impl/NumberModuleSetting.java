package coffee.axle.suim.clickgui.settings.impl;

import coffee.axle.suim.clickgui.settings.ModuleSetting;
import coffee.axle.suim.clickgui.settings.SettingType;
import coffee.axle.suim.clickgui.settings.SettingVisibility;
import coffee.axle.suim.hooks.ModulePropertyManager;
import coffee.axle.suim.hooks.MyauModuleManager;

/**
 * Number setting backed by a Myau float or int property.
 * Stores all values as double internally; reads from Myau as float.
 */
public class NumberModuleSetting extends ModuleSetting<Double> {

    private final Object myauProp;
    private final MyauModuleManager mgr;
    private final ModulePropertyManager propMgr;
    private final double defaultValue;
    private final double min;
    private final double max;
    private final double increment;
    private final boolean isInteger;
    private final String unit;

    public NumberModuleSetting(
            String name,
            String description,
            Object myauProp,
            MyauModuleManager mgr,
            ModulePropertyManager propMgr,
            double min,
            double max,
            double increment,
            boolean isInteger,
            String unit) {
        this(name, description, myauProp, mgr, propMgr, min, max, increment,
                isInteger, unit, SettingVisibility.VISIBLE);
    }

    public NumberModuleSetting(
            String name,
            String description,
            Object myauProp,
            MyauModuleManager mgr,
            ModulePropertyManager propMgr,
            double min,
            double max,
            double increment,
            boolean isInteger,
            String unit,
            SettingVisibility visibility) {
        super(name, description, SettingType.NUMBER, visibility);
        this.myauProp = myauProp;
        this.mgr = mgr;
        this.propMgr = propMgr;
        this.min = min;
        this.max = max;
        this.increment = increment;
        this.isInteger = isInteger;
        this.unit = unit;

        if (isInteger) {
            this.defaultValue = propMgr.getInt(myauProp, 0);
        } else {
            this.defaultValue = propMgr.getFloat(myauProp, 0f);
        }
    }

    @Override
    public Double getValue() {
        if (isInteger) {
            return (double) propMgr.getInt(myauProp, (int) defaultValue);
        }
        return (double) propMgr.getFloat(myauProp, (float) defaultValue);
    }

    @Override
    public void setValue(Double value) {
        try {
            double clamped = Math.max(min, Math.min(max, value));
            if (isInteger) {
                mgr.setPropertyValue(myauProp, (int) clamped);
            } else {
                mgr.setPropertyValue(myauProp, (float) clamped);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public Double getDefault() {
        return defaultValue;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getIncrement() {
        return increment;
    }

    public boolean isInteger() {
        return isInteger;
    }

    public String getUnit() {
        return unit;
    }
}





