package coffee.axle.suim.clickgui.misc.elements.impl;

import coffee.axle.suim.clickgui.util.FontUtil;
import coffee.axle.suim.clickgui.misc.elements.MiscElement;
import coffee.axle.suim.clickgui.misc.elements.MiscElementStyle;
import coffee.axle.suim.clickgui.render.HUDRenderUtils;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.awt.Color;

/**
 * Full-featured text field element with cursor, selection, scrolling, and
 * clipboard support.
 */
public class MiscElementTextField extends MiscElement {

    private int cursorPos = 0;
    private int selectionPos = 0;
    private int scrollOffset = 0;
    private boolean isFocused = false;

    private String text;
    private int maxLength;
    private String placeholder;
    private int options;
    private String prependText;

    private long lastClickTime = 0L;
    private int clicks = 0;

    public MiscElementTextField(MiscElementStyle style, int maxLength,
            String placeholder, int options, String prependText) {
        super(style);
        this.maxLength = maxLength;
        this.placeholder = placeholder;
        this.options = options;
        this.prependText = prependText;
        this.text = style.getValue();
    }

    public MiscElementTextField(MiscElementStyle style) {
        this(style, 0, "", 0, "");
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isFocused() {
        return isFocused;
    }

    public void setFocused(boolean focused) {
        this.isFocused = focused;
    }

    public void setIsFocused(boolean focused) {
        this.isFocused = focused;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public String getPrependText() {
        return prependText;
    }

    public void setPrependText(String prependText) {
        this.prependText = prependText;
    }

    private String getFullText() {
        return prependText + text;
    }

    private String getLengthText() {
        return maxLength != 0 ? text.length() + "/" + maxLength : "";
    }

    private double getAvailableWidth() {
        return getWidth() - FontUtil.INSTANCE.getStringWidth(getLengthText()) - 8.0;
    }

    private int getSelectionLeft() {
        return Math.max(0, Math.min(Math.min(selectionPos, cursorPos), text.length()));
    }

    private int getSelectionRight() {
        return Math.max(0, Math.min(Math.max(selectionPos, cursorPos), text.length()));
    }

    private String getSelectedText() {
        return text.substring(getSelectionLeft(), getSelectionRight());
    }

    private void deleteSelection() {
        int from = getSelectionLeft();
        int to = getSelectionRight();
        if (from == to)
            return;
        text = text.substring(0, from) + text.substring(to);
        cursorPos = from;
        selectionPos = from;
    }

    private int getPreviousWord() {
        int pos = cursorPos;
        if (pos == 0)
            return pos;

        // Skip whitespace
        while (pos > 0 && Character.isWhitespace(text.charAt(pos - 1))) {
            pos--;
        }
        if (pos > 0) {
            char ch = text.charAt(pos - 1);
            if (Character.isLetterOrDigit(ch)) {
                while (pos > 0 && Character.isLetterOrDigit(text.charAt(pos - 1))) {
                    pos--;
                }
            } else {
                while (pos > 0 && text.charAt(pos - 1) == ch) {
                    pos--;
                }
            }
        }
        return pos;
    }

    private int getNextWord() {
        int pos = cursorPos;
        if (pos >= text.length())
            return pos;

        // Skip whitespace
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
        if (pos < text.length()) {
            char ch = text.charAt(pos);
            if (Character.isLetterOrDigit(ch)) {
                while (pos < text.length() && Character.isLetterOrDigit(text.charAt(pos))) {
                    pos++;
                }
            } else {
                while (pos < text.length() && text.charAt(pos) == ch) {
                    pos++;
                }
            }
        }
        return pos;
    }

    private void updateScrollOffset() {
        double textWidth = getAvailableWidth() - FontUtil.INSTANCE.getStringWidth(prependText);

        if (cursorPos < scrollOffset) {
            scrollOffset = cursorPos;
        }

        while (scrollOffset < cursorPos) {
            double currentWidth = FontUtil.INSTANCE.getStringWidth(text.substring(scrollOffset, cursorPos));
            if (currentWidth <= textWidth)
                break;
            scrollOffset++;
        }

        while (scrollOffset > 0) {
            double newWidth = FontUtil.INSTANCE.getStringWidth(text.substring(scrollOffset - 1));
            if (newWidth >= textWidth)
                break;
            scrollOffset--;
        }

        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, text.length() - 1)));
    }

    private int getCursorPosFromMouse(int mouseX) {
        double relX = Math.max(0.0, mouseX - getX() - 5 - FontUtil.INSTANCE.getStringWidth(prependText));
        double width = 0.0;
        int pos = scrollOffset;

        if (text.isEmpty())
            return 0;

        int limit = Math.min(scrollOffset + (int) getAvailableWidth(), text.length());
        for (int i = scrollOffset; i < limit; i++) {
            double charWidth = FontUtil.INSTANCE.getStringWidth(String.valueOf(text.charAt(i)));
            if (width + charWidth / 2 > relX)
                return pos;
            width += charWidth;
            pos++;
        }
        return Math.min(pos, text.length());
    }

    private void selectWord() {
        int start = cursorPos;
        int end = cursorPos;
        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1)))
            start--;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end)))
            end++;
        selectionPos = start;
        cursorPos = end;
        updateScrollOffset();
    }

    private boolean isAllowedCharacter(char c) {
        return c >= ' ' && c != 127;
    }

    private void writeText(String string) {
        for (char c : string.toCharArray()) {
            writeChar(c);
        }
    }

    private void writeChar(char c) {
        if (!isAllowedCharacter(c))
            return;
        if (selectionPos != cursorPos)
            deleteSelection();
        if (maxLength != 0 && text.length() >= maxLength)
            return;

        if (c == '\r')
            c = '\n';
        text = safeSubstring(text, 0, cursorPos) + c + text.substring(cursorPos);
        cursorPos++;
        selectionPos = cursorPos;
    }

    private static String safeSubstring(String s, int start, int end) {
        int safeStart = Math.max(0, Math.min(start, s.length()));
        int safeEnd = Math.max(safeStart, Math.min(end, s.length()));
        return s.substring(safeStart, safeEnd);
    }

    private static String dropAt(String s, int at, int amount) {
        int from, to;
        if (amount < 0) {
            from = Math.max(0, at + amount);
            to = at;
        } else {
            from = at;
            to = Math.min(s.length(), at + amount);
        }
        if (from >= to)
            return s;
        return s.substring(0, from) + s.substring(to);
    }

    private static String trimToWidth(String s, double maxWidth) {
        StringBuilder sb = new StringBuilder();
        double w = 0;
        for (int i = 0; i < s.length(); i++) {
            double cw = FontUtil.INSTANCE.getStringWidth(String.valueOf(s.charAt(i)));
            if (w + cw > maxWidth)
                break;
            w += cw;
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    @Override
    public void render(int mouseX, int mouseY) {
        HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                getX(), getY(), getWidth(), getHeight(),
                getRadius(), getThickness(), getColour(),
                isFocused ? getOutlineHoverColour() : getOutlineColour());

        double yPos = getY() + (getHeight() - 8) / 2;
        String fullText = getFullText();
        String visibleText = trimToWidth(
                scrollOffset < fullText.length() ? fullText.substring(scrollOffset) : "",
                getAvailableWidth());

        // Cursor
        if (isFocused && System.currentTimeMillis() % 1000 > 500) {
            int cursorOffset = Math.max(0, prependText.length() + cursorPos - scrollOffset);
            double cursorX = getX() + 5 + FontUtil.INSTANCE.getStringWidth(
                    safeSubstring(visibleText, 0, cursorOffset));
            HUDRenderUtils.INSTANCE.drawRoundedRect(cursorX, yPos - 1, 1.0, 10.0, 1.0, Color.WHITE);
        }

        // Selection highlight
        String selectedText = getSelectedText();
        if (!selectedText.isEmpty()) {
            int start = Math.max(getSelectionLeft() + prependText.length(), scrollOffset);
            int end = Math.min(getSelectionRight() + prependText.length(),
                    scrollOffset + (int) getAvailableWidth());
            if (start < end) {
                double unselectedWidth = FontUtil.INSTANCE.getStringWidth(
                        fullText.substring(scrollOffset, start));
                double selectionWidth = FontUtil.INSTANCE.getStringWidth(
                        fullText.substring(start, end));
                HUDRenderUtils.INSTANCE.drawRoundedRect(
                        getX() + 5 + unselectedWidth, yPos - 1,
                        selectionWidth, 10.0, 0.1,
                        new Color(110, 180, 255, 150));
            }
        }

        // Text or placeholder
        if (!visibleText.isEmpty()) {
            FontUtil.INSTANCE.drawString(visibleText, getX() + 5, yPos, style.getTextColour().getRGB());
        } else if (text.isEmpty()) {
            FontUtil.INSTANCE.drawString(
                    placeholder,
                    getX() + 5 + FontUtil.INSTANCE.getStringWidth(prependText),
                    yPos, Color.LIGHT_GRAY.getRGB());
        }

        // Length counter
        String lengthText = getLengthText();
        if (!lengthText.isEmpty()) {
            FontUtil.INSTANCE.drawString(
                    lengthText, getX() + getAvailableWidth() + 5, yPos,
                    text.length() == maxLength ? Color.RED.getRGB() : Color.LIGHT_GRAY.getRGB());
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!isHovered(mouseX, mouseY)) {
            isFocused = false;
            selectionPos = cursorPos;
            return false;
        }

        switch (mouseButton) {
            case 0: {
                long now = System.currentTimeMillis();
                clicks = (now - lastClickTime < 250) ? clicks + 1 : 1;
                lastClickTime = now;

                switch (clicks) {
                    case 1:
                        cursorPos = getCursorPosFromMouse(mouseX);
                        selectionPos = cursorPos;
                        break;
                    case 2:
                        selectWord();
                        break;
                    case 3:
                        cursorPos = text.length();
                        selectionPos = 0;
                        break;
                    default:
                        clicks = 0;
                        break;
                }
                break;
            }
            case 1: {
                text = "";
                cursorPos = 0;
                break;
            }
            default: {
                cursorPos = getCursorPosFromMouse(mouseX);
                selectionPos = cursorPos;
                break;
            }
        }

        isFocused = true;
        updateScrollOffset();
        return true;
    }

    @Override
    public void mouseClickMove(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
        if (!isFocused || !isHovered(mouseX, mouseY))
            return;
        selectionPos = getCursorPosFromMouse(mouseX);
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (!isFocused)
            return false;
        boolean isShift = GuiScreen.isShiftKeyDown();
        boolean isCtrl = GuiScreen.isCtrlKeyDown();

        if (isCtrl) {
            switch (keyCode) {
                case Keyboard.KEY_A:
                    cursorPos = text.length();
                    selectionPos = 0;
                    break;
                case Keyboard.KEY_C:
                    GuiScreen.setClipboardString(getSelectedText());
                    break;
                case Keyboard.KEY_V:
                    writeText(GuiScreen.getClipboardString());
                    break;
                case Keyboard.KEY_X:
                    GuiScreen.setClipboardString(getSelectedText());
                    deleteSelection();
                    break;
            }
        }

        switch (keyCode) {
            case Keyboard.KEY_ESCAPE:
                isFocused = false;
                break;
            case Keyboard.KEY_BACK:
                if (selectionPos != cursorPos) {
                    deleteSelection();
                } else if (isCtrl) {
                    int start = getPreviousWord();
                    if (start < cursorPos) {
                        text = dropAt(text, start, cursorPos - start);
                        cursorPos = start;
                        selectionPos = cursorPos;
                    }
                } else if (cursorPos > 0) {
                    text = dropAt(text, cursorPos, -1);
                    cursorPos--;
                    selectionPos = cursorPos;
                }
                break;
            case Keyboard.KEY_DELETE:
                if (selectionPos != cursorPos) {
                    deleteSelection();
                } else if (isCtrl) {
                    int end = getNextWord();
                    if (end > cursorPos) {
                        text = dropAt(text, cursorPos, end - cursorPos);
                    }
                } else if (cursorPos < text.length()) {
                    text = dropAt(text, cursorPos, 1);
                }
                break;
            case Keyboard.KEY_HOME:
                cursorPos = 0;
                if (!isShift)
                    selectionPos = cursorPos;
                break;
            case Keyboard.KEY_END:
                cursorPos = text.length();
                if (!isShift)
                    selectionPos = cursorPos;
                break;
            case Keyboard.KEY_RIGHT: {
                int newPos = isCtrl ? getNextWord() : cursorPos + 1;
                cursorPos = Math.min(newPos, text.length());
                if (!isShift)
                    selectionPos = cursorPos;
                break;
            }
            case Keyboard.KEY_LEFT: {
                int newPos = isCtrl ? getPreviousWord() : cursorPos - 1;
                cursorPos = Math.max(newPos, 0);
                if (!isShift)
                    selectionPos = cursorPos;
                break;
            }
            default:
                writeChar(typedChar);
                break;
        }

        updateScrollOffset();
        return true;
    }
}
