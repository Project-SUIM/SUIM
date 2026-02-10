package coffee.axle.suim.clickgui.misc.elements.impl;

import coffee.axle.suim.clickgui.util.Alignment;
import coffee.axle.suim.clickgui.util.FontUtil;
import coffee.axle.suim.clickgui.misc.elements.MiscElement;
import coffee.axle.suim.clickgui.misc.elements.MiscElementStyle;
import coffee.axle.suim.clickgui.render.HUDRenderUtils;
import net.minecraft.client.renderer.GlStateManager;

/**
 * A clickable button element with text.
 */
public class MiscElementButton extends MiscElement {

    private final Runnable onClick;
    private final Runnable onHover;

    public MiscElementButton(MiscElementStyle style, Runnable onClick, Runnable onHover) {
        super(style);
        this.onClick = onClick != null ? onClick : () -> {
        };
        this.onHover = onHover != null ? onHover : () -> {
        };
    }

    public MiscElementButton(MiscElementStyle style, Runnable onClick) {
        this(style, onClick, () -> {
        });
    }

    @Override
    public void render(int mouseX, int mouseY) {
        GlStateManager.pushMatrix();
        HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                getX(), getY(), getWidth(), getHeight(),
                getRadius(), getThickness(),
                getColour(),
                isHovered(mouseX, mouseY) ? getOutlineHoverColour() : getOutlineColour());

        double textX;
        Alignment alignment = style.getAlignment();
        double hPadding = style.getTextPadding();
        switch (alignment) {
            case LEFT:
                textX = getX() + hPadding;
                break;
            case RIGHT:
                textX = getX() + getWidth() - hPadding;
                break;
            default: // CENTRE
                textX = getX() + getWidth() / 2;
                break;
        }
        double textY = getY() + getHeight() / 2;

        String displayText = FontUtil.INSTANCE.getTruncatedText(getValue(), getWidth() - 3.0);
        FontUtil.INSTANCE.drawAlignedString(displayText, textX, textY, alignment);

        if (isHovered(mouseX, mouseY)) {
            onHover.run();
        }
        GlStateManager.popMatrix();
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && isHovered(mouseX, mouseY)) {
            onClick.run();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }
}





