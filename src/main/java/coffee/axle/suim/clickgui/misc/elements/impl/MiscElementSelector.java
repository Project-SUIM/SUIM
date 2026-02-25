package coffee.axle.suim.clickgui.misc.elements.impl;

import coffee.axle.suim.clickgui.util.Alignment;
import coffee.axle.suim.clickgui.util.ColorUtil;
import coffee.axle.suim.clickgui.util.FontUtil;
import coffee.axle.suim.clickgui.util.VAlignment;
import coffee.axle.suim.clickgui.misc.elements.MiscElement;
import coffee.axle.suim.clickgui.misc.elements.MiscElementStyle;
import coffee.axle.suim.clickgui.render.HUDRenderUtils;

import java.awt.Color;
import java.util.List;

/**
 * A selector element that displays a list of options and lets the user pick
 * one.
 * Supports vertical (dropdown) and horizontal (grid) orientations.
 */
public class MiscElementSelector extends MiscElement {

    public enum Orientation {
        Vertical, Horizontal
    }

    private final List<String> options;
    private final Orientation orientation;
    private final int optionsPerRow;
    private final int horizontalPadding;

    private String _selected;
    private String _lastSelected;
    private boolean extended = false;
    private int index;

    public MiscElementSelector(MiscElementStyle style, String defaultOption,
            List<String> options, Orientation orientation,
            int optionsPerRow, int horizontalPadding) {
        super(style);
        this.options = options;
        this.orientation = orientation;
        this.optionsPerRow = optionsPerRow;
        this.horizontalPadding = horizontalPadding;

        int idx = -1;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equalsIgnoreCase(defaultOption)) {
                idx = i;
                break;
            }
        }
        this.index = Math.max(idx, 0);
        this._selected = options.isEmpty() ? defaultOption : options.get(this.index);
        this._lastSelected = this._selected;
    }

    public MiscElementSelector(MiscElementStyle style, String defaultOption, List<String> options) {
        this(style, defaultOption, options, Orientation.Vertical, 5, 0);
    }

    public int getIndex() {
        return index;
    }

    private void setIndex(int value) {
        if (options.isEmpty())
            return;
        this.index = Math.max(0, Math.min(value, options.size() - 1));
        this._selected = options.get(this.index);
        this._lastSelected = this._selected;
    }

    public String getSelected() {
        return _selected;
    }

    public void setSelected(String value) {
        this._lastSelected = value;
        this._selected = value;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equalsIgnoreCase(value)) {
                setIndex(i);
                return;
            }
        }
    }

    public String getLastSelected() {
        return _lastSelected;
    }

    public List<String> getOptions() {
        return options;
    }

    public boolean isExtended() {
        return extended;
    }

    public boolean isSelected(String option) {
        return _selected.equalsIgnoreCase(option);
    }

    @Override
    public void render(int mouseX, int mouseY) {
        Color highlightColor = (extended || isHovered(mouseX, mouseY))
                ? getOutlineColour()
                : getOutlineHoverColour();

        if (orientation == Orientation.Vertical) {
            renderVertical(mouseX, mouseY, highlightColor);
        } else {
            renderHorizontal(mouseX, mouseY, highlightColor);
        }
    }

    private double[] calculateTextPosition() {
        Alignment align = style.getAlignment();
        VAlignment vAlign = style.getVAlignment();
        double hPadding = style.getTextPadding();
        double vPadding = style.getVTextPadding();

        double tx;
        switch (align) {
            case LEFT:
                tx = getX() + hPadding;
                break;
            case RIGHT:
                tx = getX() + getWidth() - hPadding;
                break;
            default:
                tx = getX() + getWidth() / 2;
                break;
        }
        double ty;
        switch (vAlign) {
            case TOP:
                ty = getY() + vPadding;
                break;
            case BOTTOM:
                ty = getY() + getHeight() - vPadding - FontUtil.INSTANCE.getFontHeight();
                break;
            default:
                ty = getY() + getHeight() / 2;
                break;
        }
        return new double[] { tx, ty };
    }

    private void renderVertical(int mouseX, int mouseY, Color highlightColor) {
        double totalHeight = extended ? getHeight() * (options.size() + 1) : getHeight();
        HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                getX(), getY(), getWidth(), totalHeight,
                getRadius(), getThickness(), getColour(), highlightColor);

        double[] pos = calculateTextPosition();
        double textX = pos[0];
        double textY = pos[1];

        FontUtil.INSTANCE.drawAlignedString(
                _selected, textX, textY,
                style.getAlignment(), style.getVAlignment(),
                style.getTextColour().getRGB());

        if (extended) {
            for (int i = 0; i < options.size(); i++) {
                String option = options.get(i);
                double optionY = getY() + getHeight() * (i + 1);
                double tY = textY + getHeight() * (i + 1);

                if (isSelected(option) || isHovered(mouseX, mouseY, 0.0, optionY - getY())) {
                    HUDRenderUtils.INSTANCE.drawRoundedRect(
                            getX(), optionY, getWidth(), getHeight(),
                            getRadius(), highlightColor);
                }

                String label = option.isEmpty() ? option
                        : Character.toUpperCase(option.charAt(0)) + option.substring(1);
                FontUtil.INSTANCE.drawAlignedString(
                        label, textX, tY,
                        style.getAlignment(), style.getVAlignment(),
                        style.getTextColour().getRGB());
            }
        }
    }

    private void renderHorizontal(int mouseX, int mouseY, Color highlightColor) {
        double[] pos = calculateTextPosition();
        double textX0 = pos[0];
        double textY0 = pos[1];

        for (int i = 0; i < options.size(); i++) {
            String option = options.get(i);
            int column = i % optionsPerRow;
            int row = i / optionsPerRow;
            double optionX = getX() + column * (getWidth() + horizontalPadding);
            double optionY = getY() + row * (getHeight() + horizontalPadding);
            double textX = textX0 + column * (getWidth() + horizontalPadding);
            double textY = textY0 + row * (getHeight() + horizontalPadding);

            HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                    optionX, optionY, getWidth(), getHeight(),
                    getRadius(), getThickness(), getColour(),
                    isSelected(option) ? highlightColor : ColorUtil.INSTANCE.getOutlineColor());

            if (isSelected(option) || isHovered(mouseX, mouseY, optionX - getX(), optionY - getY())) {
                HUDRenderUtils.INSTANCE.drawRoundedOutline(
                        optionX, optionY, getWidth(), getHeight(),
                        getRadius(), getThickness(), highlightColor);
            }

            String label = option.isEmpty() ? option
                    : Character.toUpperCase(option.charAt(0)) + option.substring(1);
            FontUtil.INSTANCE.drawAlignedString(
                    label, textX, textY,
                    style.getAlignment(), style.getVAlignment(),
                    style.getTextColour().getRGB());
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            if (orientation == Orientation.Vertical) {
                if (!extended && isHovered(mouseX, mouseY)) {
                    setIndex(index + 1 > options.size() - 1 ? 0 : index + 1);
                    return true;
                }
                if (extended) {
                    for (int i = 0; i < options.size(); i++) {
                        double optionY = getY() + getHeight() * (i + 1);
                        if (isHovered(mouseX, mouseY, 0.0, optionY - getY())) {
                            setIndex(i);
                            extended = false;
                            return true;
                        }
                    }
                }
            } else {
                for (int i = 0; i < options.size(); i++) {
                    int column = i % optionsPerRow;
                    int row = i / optionsPerRow;
                    double optionX = getX() + column * (getWidth() + horizontalPadding);
                    double optionY = getY() + row * (getHeight() + horizontalPadding);
                    if (isHovered(mouseX, mouseY, optionX - getX(), optionY - getY())) {
                        setIndex(i);
                        return true;
                    }
                }
            }
        } else if (mouseButton == 1 && orientation == Orientation.Vertical && isHovered(mouseX, mouseY)) {
            extended = !extended;
            return true;
        }
        return false;
    }
}
