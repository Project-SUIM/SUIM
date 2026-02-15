package coffee.axle.suim.clickgui.util;

import coffee.axle.suim.clickgui.ClickGuiConfig;
import coffee.axle.suim.clickgui.render.font.CFontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Provides methods for rendering text.
 *
 * @author Aton
 */
public final class FontUtil {

    /**
     * @deprecated Use static convenience methods where available, e.g.
     *             {@code FontUtil.stringWidth(text)}.
     */
    @Deprecated
    public static final FontUtil INSTANCE = new FontUtil();

    private static final String RESOURCE_DOMAIN = "suim";
    private static final Pattern FORMATTING_CODE_PATTERN = Pattern.compile("\u00A7[0-9a-fk-or]",
            Pattern.CASE_INSENSITIVE);

    private final Minecraft mc = Minecraft.getMinecraft();
    private FontRenderer fontRenderer = mc.fontRendererObj;
    private CFontRenderer customFontRenderer;

    private FontUtil() {
    }

    private FontRenderer getFont() {
        if (fontRenderer == null) {
            fontRenderer = mc.fontRendererObj;
        }
        return fontRenderer;
    }

    // --------------- property-style getters ---------------

    public int getFontHeight() {
        return getFont().FONT_HEIGHT;
    }

    private boolean isFont() {
        return ClickGuiConfig.isCustomFontEnabled();
    }

    // --------------- setup ---------------

    public void setupFontUtils() {
        try {
            java.io.InputStream stream = mc.getResourceManager()
                    .getResource(new ResourceLocation(RESOURCE_DOMAIN, "Roboto-Regular.ttf"))
                    .getInputStream();
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            Font font = Font.createFont(Font.TRUETYPE_FONT, stream);
            ge.registerFont(font);

            customFontRenderer = new CFontRenderer(font.deriveFont(Font.PLAIN, 19f));
            fontRenderer = mc.fontRendererObj;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --------------- string width ---------------

    public int getStringWidth(String text) {
        return getStringWidth(text, isFont(), 1.0);
    }

    public int getStringWidth(String text, boolean customFont) {
        return getStringWidth(text, customFont, 1.0);
    }

    public int getStringWidth(String text, boolean customFont, double scale) {
        String stripped = stripControlCodes(text);
        int width;
        if (customFont && customFontRenderer != null) {
            width = (int) customFontRenderer.getStringWidth(stripped);
        } else {
            width = getFont().getStringWidth(stripped);
        }
        return (int) (width * scale);
    }

    public double getStringWidthDouble(String text) {
        return getStringWidthDouble(text, isFont(), 1.0);
    }

    public double getStringWidthDouble(String text, boolean customFont) {
        return getStringWidthDouble(text, customFont, 1.0);
    }

    public double getStringWidthDouble(String text, boolean customFont, double scale) {
        String stripped = stripControlCodes(text);
        double width;
        if (customFont && customFontRenderer != null) {
            width = customFontRenderer.getStringWidth(stripped);
        } else {
            width = getFont().getStringWidth(stripped);
        }
        return width * scale;
    }

    public int getSplitHeight(String text, int wrapWidth) {
        int dy = 0;
        for (String ignored : getFont().listFormattedStringToWidth(text, wrapWidth)) {
            dy += getFont().FONT_HEIGHT;
        }
        return dy;
    }

    public int getFontHeight(double scale) {
        return (int) (getFontHeight() * scale);
    }

    // --------------- core draw helper ---------------

    private void drawText(String text, double x, double y, int color, boolean customFont, double scale) {
        drawText(text, x, y, color, customFont, scale, false);
    }

    private void drawText(String text, double x, double y, int color, boolean customFont, double scale,
            boolean shadow) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0);
        GlStateManager.scale(scale, scale, 1.0);

        if (customFont && customFontRenderer != null) {
            customFontRenderer.drawString(text, 0.0, 0.0, color, shadow);
        } else {
            getFont().drawString(text, 0f, 0f, color, shadow);
        }

        GlStateManager.popMatrix();
    }

    // --------------- drawAlignedString ---------------

    public void drawAlignedString(String text, double x, double y) {
        drawAlignedString(text, x, y, Alignment.LEFT, VAlignment.CENTRE, ColorUtil.textcolor, isFont(), 1.0);
    }

    public void drawAlignedString(String text, double x, double y, Alignment alignment) {
        drawAlignedString(text, x, y, alignment, VAlignment.CENTRE, ColorUtil.textcolor, isFont(), 1.0);
    }

    public void drawAlignedString(String text, double x, double y, Alignment alignment, VAlignment vAlignment) {
        drawAlignedString(text, x, y, alignment, vAlignment, ColorUtil.textcolor, isFont(), 1.0);
    }

    public void drawAlignedString(String text, double x, double y, Alignment alignment, VAlignment vAlignment,
            int color) {
        drawAlignedString(text, x, y, alignment, vAlignment, color, isFont(), 1.0);
    }

    public void drawAlignedString(String text, double x, double y, Alignment alignment, VAlignment vAlignment,
            int color, boolean customFont) {
        drawAlignedString(text, x, y, alignment, vAlignment, color, customFont, 1.0);
    }

    public void drawAlignedString(String text, double x, double y, Alignment alignment, VAlignment vAlignment,
            int color, boolean customFont, double scale) {
        double posX;
        switch (alignment) {
            case CENTRE:
                posX = x - (getStringWidth(text, customFont, scale) * scale / 2.0);
                break;
            case RIGHT:
                posX = x - getStringWidth(text, customFont, scale);
                break;
            default:
                posX = x;
                break;
        }

        double posY;
        switch (vAlignment) {
            case CENTRE:
                posY = y - (getFontHeight(scale) / 2.0);
                break;
            case BOTTOM:
                posY = y - getFontHeight(scale);
                break;
            default:
                posY = y;
                break;
        }

        drawText(text, posX, posY, color, customFont, scale);
    }

    // --------------- drawString (double coords) ---------------

    public void drawString(String text, double x, double y) {
        drawString(text, x, y, ColorUtil.textcolor, isFont(), 1.0, false);
    }

    public void drawString(String text, double x, double y, int color) {
        drawString(text, x, y, color, isFont(), 1.0, false);
    }

    public void drawString(String text, double x, double y, int color, boolean customFont) {
        drawString(text, x, y, color, customFont, 1.0, false);
    }

    public void drawString(String text, double x, double y, int color, boolean customFont, double scale) {
        drawString(text, x, y, color, customFont, scale, false);
    }

    public void drawString(String text, double x, double y, int color, boolean customFont, double scale,
            boolean shadow) {
        drawText(text, x, y, color, customFont, scale, shadow);
    }

    // --------------- drawString (int coords) ---------------

    public void drawString(String text, int x, int y) {
        drawString(text, (double) x, (double) y, ColorUtil.textcolor, isFont(), 1.0, false);
    }

    public void drawString(String text, int x, int y, int color) {
        drawString(text, (double) x, (double) y, color, isFont(), 1.0, false);
    }

    public void drawString(String text, int x, int y, int color, boolean customFont) {
        drawString(text, (double) x, (double) y, color, customFont, 1.0, false);
    }

    public void drawString(String text, int x, int y, int color, boolean customFont, double scale) {
        drawString(text, (double) x, (double) y, color, customFont, scale, false);
    }

    // --------------- drawStringWithShadow ---------------

    public void drawStringWithShadow(String text, double x, double y) {
        drawStringWithShadow(text, x, y, ColorUtil.textcolor, isFont(), 1.0);
    }

    public void drawStringWithShadow(String text, double x, double y, int color) {
        drawStringWithShadow(text, x, y, color, isFont(), 1.0);
    }

    public void drawStringWithShadow(String text, double x, double y, int color, boolean customFont) {
        drawStringWithShadow(text, x, y, color, customFont, 1.0);
    }

    public void drawStringWithShadow(String text, double x, double y, int color, boolean customFont, double scale) {
        drawText(text, x, y, color, customFont, scale, true);
    }

    // --------------- drawCenteredString ---------------

    public void drawCenteredString(String text, double x, double y) {
        drawCenteredString(text, x, y, ColorUtil.textcolor, isFont(), 1.0);
    }

    public void drawCenteredString(String text, double x, double y, int color) {
        drawCenteredString(text, x, y, color, isFont(), 1.0);
    }

    public void drawCenteredString(String text, double x, double y, int color, boolean customFont) {
        drawCenteredString(text, x, y, color, customFont, 1.0);
    }

    public void drawCenteredString(String text, double x, double y, int color, boolean customFont, double scale) {
        drawText(text, x - getStringWidth(text, customFont) * scale / 2, y, color, customFont, scale);
    }

    // --------------- drawCenteredStringWithShadow ---------------

    public void drawCenteredStringWithShadow(String text, double x, double y) {
        drawCenteredStringWithShadow(text, x, y, ColorUtil.textcolor, isFont(), 1.0);
    }

    public void drawCenteredStringWithShadow(String text, double x, double y, int color) {
        drawCenteredStringWithShadow(text, x, y, color, isFont(), 1.0);
    }

    public void drawCenteredStringWithShadow(String text, double x, double y, int color, boolean customFont) {
        drawCenteredStringWithShadow(text, x, y, color, customFont, 1.0);
    }

    public void drawCenteredStringWithShadow(String text, double x, double y, int color, boolean customFont,
            double scale) {
        drawText(text, x - getStringWidth(text, customFont) * scale / 2, y, color, customFont, scale, true);
    }

    // --------------- drawTotalCenteredString ---------------

    public void drawTotalCenteredString(String text, double x, double y) {
        drawTotalCenteredString(text, x, y, ColorUtil.textcolor, isFont(), 1.0);
    }

    public void drawTotalCenteredString(String text, double x, double y, int color) {
        drawTotalCenteredString(text, x, y, color, isFont(), 1.0);
    }

    public void drawTotalCenteredString(String text, double x, double y, int color, boolean customFont) {
        drawTotalCenteredString(text, x, y, color, customFont, 1.0);
    }

    public void drawTotalCenteredString(String text, double x, double y, int color, boolean customFont, double scale) {
        drawText(text, x - getStringWidth(text, customFont) * scale / 2, y - getFontHeight(scale) / 2.0, color,
                customFont, scale);
    }

    // --------------- drawTotalCenteredStringWithShadow ---------------

    public void drawTotalCenteredStringWithShadow(String text, double x, double y) {
        drawTotalCenteredStringWithShadow(text, x, y, ColorUtil.textcolor, isFont(), 1.0);
    }

    public void drawTotalCenteredStringWithShadow(String text, double x, double y, int color) {
        drawTotalCenteredStringWithShadow(text, x, y, color, isFont(), 1.0);
    }

    public void drawTotalCenteredStringWithShadow(String text, double x, double y, int color, boolean customFont) {
        drawTotalCenteredStringWithShadow(text, x, y, color, customFont, 1.0);
    }

    public void drawTotalCenteredStringWithShadow(String text, double x, double y, int color, boolean customFont,
            double scale) {
        drawText(text, x - getStringWidth(text, customFont) * scale / 2, y - getFontHeight(scale) / 2.0, color,
                customFont, scale, true);
    }

    // --------------- drawWrappedText ---------------

    public void drawWrappedText(String text, double x, double y, double width) {
        drawWrappedText(text, x, y, width, ColorUtil.textcolor, isFont(), 1.0);
    }

    public void drawWrappedText(String text, double x, double y, double width, int color) {
        drawWrappedText(text, x, y, width, color, isFont(), 1.0);
    }

    public void drawWrappedText(String text, double x, double y, double width, int color, boolean customFont) {
        drawWrappedText(text, x, y, width, color, customFont, 1.0);
    }

    public void drawWrappedText(String text, double x, double y, double width, int color, boolean customFont,
            double scale) {
        List<String> lines = wrapText(text, width, customFont, scale);
        for (int i = 0; i < lines.size(); i++) {
            drawText(lines.get(i), x, y + getFontHeight(scale) * i, color, customFont, scale);
        }
    }

    public void drawWrappedText(String text, int x, int y, int width) {
        drawWrappedText(text, (double) x, (double) y, (double) width, ColorUtil.textcolor, isFont(), 1.0);
    }

    public void drawWrappedText(String text, int x, int y, int width, int color) {
        drawWrappedText(text, (double) x, (double) y, (double) width, color, isFont(), 1.0);
    }

    public void drawWrappedText(String text, int x, int y, int width, int color, boolean customFont) {
        drawWrappedText(text, (double) x, (double) y, (double) width, color, customFont, 1.0);
    }

    public void drawWrappedText(String text, int x, int y, int width, int color, boolean customFont, double scale) {
        drawWrappedText(text, (double) x, (double) y, (double) width, color, customFont, scale);
    }

    // --------------- wrapText ---------------

    public List<String> wrapText(String text, double maxWidth) {
        return wrapText(text, maxWidth, isFont(), 1.0);
    }

    public List<String> wrapText(String text, double maxWidth, boolean customFont) {
        return wrapText(text, maxWidth, customFont, 1.0);
    }

    public List<String> wrapText(String text, double maxWidth, boolean customFont, double scale) {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();

        for (String word : text.split(" ")) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (getStringWidthDouble(testLine, customFont, scale) <= maxWidth) {
                currentLine = new StringBuilder(testLine);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                }
                currentLine = new StringBuilder(word);
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    // --------------- getTruncatedText ---------------

    public String getTruncatedText(String text, double width) {
        return getTruncatedText(text, width, "...");
    }

    public String getTruncatedText(String text, double width, String ellipsis) {
        if (getWidth(text) <= width) {
            return text;
        }
        String truncated = text;
        while (getWidth(truncated) + getWidth(ellipsis) > width && !truncated.isEmpty()) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        return truncated + ellipsis;
    }

    // --------------- static string utilities ---------------

    public static String capitalizeWords(String text) {
        String[] words = text.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0)
                sb.append(' ');
            String w = words[i];
            if (!w.isEmpty()) {
                sb.append(w.substring(0, 1).toUpperCase(Locale.getDefault()));
                sb.append(w.substring(1).toLowerCase(Locale.getDefault()));
            }
        }
        return sb.toString();
    }

    public String trimToWidth(String text, int width) {
        return getFont().trimStringToWidth(text, width);
    }

    public static double getWidth(String text) {
        return INSTANCE.getStringWidthDouble(text);
    }

    // --------------- internal helpers ---------------

    private static String stripControlCodes(String text) {
        if (text == null)
            return "";
        return FORMATTING_CODE_PATTERN.matcher(text).replaceAll("");
    }

    // --------------- static convenience methods (delegate to INSTANCE)
    // ---------------

    /** Delegates to {@link #setupFontUtils()}. */
    public static void setup() {
        INSTANCE.setupFontUtils();
    }

    /** Delegates to {@link #getStringWidth(String)}. */
    public static int stringWidth(String text) {
        return INSTANCE.getStringWidth(text);
    }

    /** Delegates to {@link #getStringWidth(String, boolean)}. */
    public static int stringWidth(String text, boolean customFont) {
        return INSTANCE.getStringWidth(text, customFont);
    }

    /** Delegates to {@link #getStringWidth(String, boolean, double)}. */
    public static int stringWidth(String text, boolean customFont, double scale) {
        return INSTANCE.getStringWidth(text, customFont, scale);
    }

    /** Delegates to {@link #getStringWidthDouble(String)}. */
    public static double stringWidthD(String text) {
        return INSTANCE.getStringWidthDouble(text);
    }

    /** Delegates to {@link #getFontHeight()}. */
    public static int fontHeight() {
        return INSTANCE.getFontHeight();
    }

    /** Delegates to {@link #getFontHeight(double)}. */
    public static int fontHeight(double scale) {
        return INSTANCE.getFontHeight(scale);
    }

    /** Delegates to {@link #getSplitHeight(String, int)}. */
    public static int splitHeight(String text, int wrapWidth) {
        return INSTANCE.getSplitHeight(text, wrapWidth);
    }

    /** Delegates to {@link #drawString(String, double, double)}. */
    public static void draw(String text, double x, double y) {
        INSTANCE.drawString(text, x, y);
    }

    /** Delegates to {@link #drawString(String, double, double, int)}. */
    public static void draw(String text, double x, double y, int color) {
        INSTANCE.drawString(text, x, y, color);
    }

    /** Delegates to {@link #drawStringWithShadow(String, double, double)}. */
    public static void drawShadow(String text, double x, double y) {
        INSTANCE.drawStringWithShadow(text, x, y);
    }

    /** Delegates to {@link #drawStringWithShadow(String, double, double, int)}. */
    public static void drawShadow(String text, double x, double y, int color) {
        INSTANCE.drawStringWithShadow(text, x, y, color);
    }

    /** Delegates to {@link #drawCenteredString(String, double, double)}. */
    public static void drawCentered(String text, double x, double y) {
        INSTANCE.drawCenteredString(text, x, y);
    }

    /** Delegates to {@link #drawCenteredString(String, double, double, int)}. */
    public static void drawCentered(String text, double x, double y, int color) {
        INSTANCE.drawCenteredString(text, x, y, color);
    }

    /**
     * Delegates to {@link #drawCenteredStringWithShadow(String, double, double)}.
     */
    public static void drawCenteredShadow(String text, double x, double y) {
        INSTANCE.drawCenteredStringWithShadow(text, x, y);
    }

    /**
     * Delegates to
     * {@link #drawCenteredStringWithShadow(String, double, double, int)}.
     */
    public static void drawCenteredShadow(String text, double x, double y, int color) {
        INSTANCE.drawCenteredStringWithShadow(text, x, y, color);
    }

    /** Delegates to {@link #drawTotalCenteredString(String, double, double)}. */
    public static void drawTotalCentered(String text, double x, double y) {
        INSTANCE.drawTotalCenteredString(text, x, y);
    }

    /**
     * Delegates to {@link #drawTotalCenteredString(String, double, double, int)}.
     */
    public static void drawTotalCentered(String text, double x, double y, int color) {
        INSTANCE.drawTotalCenteredString(text, x, y, color);
    }

    /**
     * Delegates to {@link #drawAlignedString(String, double, double, Alignment)}.
     */
    public static void drawAligned(String text, double x, double y, Alignment alignment) {
        INSTANCE.drawAlignedString(text, x, y, alignment);
    }

    /**
     * Delegates to
     * {@link #drawAlignedString(String, double, double, Alignment, VAlignment, int)}.
     */
    public static void drawAligned(String text, double x, double y, Alignment alignment, VAlignment vAlignment,
            int color) {
        INSTANCE.drawAlignedString(text, x, y, alignment, vAlignment, color);
    }

    /** Delegates to {@link #drawWrappedText(String, double, double, double)}. */
    public static void drawWrapped(String text, double x, double y, double width) {
        INSTANCE.drawWrappedText(text, x, y, width);
    }

    /**
     * Delegates to {@link #drawWrappedText(String, double, double, double, int)}.
     */
    public static void drawWrapped(String text, double x, double y, double width, int color) {
        INSTANCE.drawWrappedText(text, x, y, width, color);
    }

    /** Delegates to {@link #wrapText(String, double)}. */
    public static List<String> wrap(String text, double maxWidth) {
        return INSTANCE.wrapText(text, maxWidth);
    }

    /** Delegates to {@link #getTruncatedText(String, double)}. */
    public static String truncate(String text, double width) {
        return INSTANCE.getTruncatedText(text, width);
    }

    /** Delegates to {@link #trimToWidth(String, int)}. */
    public static String trim(String text, int width) {
        return INSTANCE.trimToWidth(text, width);
    }
}
