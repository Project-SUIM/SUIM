package coffee.axle.suim.clickgui.util;

import coffee.axle.suim.clickgui.util.Alignment;
import coffee.axle.suim.clickgui.util.ColorUtil;
import coffee.axle.suim.clickgui.util.VAlignment;
import coffee.axle.suim.clickgui.misc.elements.MiscElementStyle;
import coffee.axle.suim.clickgui.misc.elements.impl.MiscElementTextField;

import java.awt.Color;

/**
 * Factory for creating Kotlin MiscElement instances from Java code.
 * Avoids the need for Kotlin DSL syntax when constructing elements.
 */
public final class MiscElementHelper {

    private MiscElementHelper() {
    }

    public static MiscElementTextField createTextField(
            double x, double y, double w, double h,
            int maxLength, String placeholder, String prependText) {
        MiscElementStyle style = new MiscElementStyle(
                "", x, y, w, h, 3.0, 1.0,
                new Color(ColorUtil.textcolor),
                ColorUtil.INSTANCE.getBgColor().darker(),
                ColorUtil.INSTANCE.getOutlineColor(),
                ColorUtil.INSTANCE.getClickGUIColor(),
                Alignment.CENTRE, VAlignment.CENTRE, 5.0, 0.0);
        return new MiscElementTextField(
                style, maxLength, placeholder, 0, prependText);
    }

    public static MiscElementTextField createTextField(
            double x, double y, double w, double h,
            int maxLength, String placeholder, String prependText,
            Color colour) {
        MiscElementStyle style = new MiscElementStyle(
                "", x, y, w, h, 3.0, 1.0,
                new Color(ColorUtil.textcolor),
                colour,
                ColorUtil.INSTANCE.getOutlineColor(),
                ColorUtil.INSTANCE.getClickGUIColor(),
                Alignment.CENTRE, VAlignment.CENTRE, 5.0, 0.0);
        return new MiscElementTextField(
                style, maxLength, placeholder, 0, prependText);
    }
}





