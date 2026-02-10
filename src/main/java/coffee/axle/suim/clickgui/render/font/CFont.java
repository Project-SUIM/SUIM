package coffee.axle.suim.clickgui.render.font;

import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class CFont {
    private float imgSize = 4096.0f;
    protected CharData[] charData = new CharData[256];
    private Font font;
    private boolean antiAlias;
    private boolean fractionalMetrics;
    protected int fontHeight = -1;
    protected int charOffset = 0;
    protected DynamicTexture tex;

    public CFont(Font font, boolean antiAlias, boolean fractionalMetrics) {
        this.font = font;
        this.antiAlias = antiAlias;
        this.fractionalMetrics = fractionalMetrics;
        this.tex = setupTexture(font, antiAlias, fractionalMetrics, charData);
    }

    public Font getFont() {
        return font;
    }

    public void setFont(Font value) {
        this.font = value;
        this.tex = setupTexture(value, antiAlias, fractionalMetrics, charData);
    }

    public boolean getAntiAlias() {
        return antiAlias;
    }

    public void setAntiAlias(boolean value) {
        if (this.antiAlias != value) {
            this.antiAlias = value;
            this.tex = setupTexture(font, value, fractionalMetrics, charData);
        }
    }

    public boolean getFractionalMetrics() {
        return fractionalMetrics;
    }

    public void setFractionalMetrics(boolean value) {
        if (this.fractionalMetrics != value) {
            this.fractionalMetrics = value;
            this.tex = setupTexture(font, antiAlias, value, charData);
        }
    }

    public CharData[] getCharData() {
        return charData;
    }

    public int getFontHeight() {
        return fontHeight;
    }

    public int getCharOffset() {
        return charOffset;
    }

    public DynamicTexture getTex() {
        return tex;
    }

    public DynamicTexture setupTexture(Font font, boolean antiAlias, boolean fractionalMetrics, CharData[] chars) {
        BufferedImage img = generateFontImage(font, antiAlias, fractionalMetrics, chars);
        try {
            return new DynamicTexture(img);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private BufferedImage generateFontImage(Font font, boolean antiAlias, boolean fractionalMetrics, CharData[] chars) {
        int imgSizeInt = (int) imgSize;
        BufferedImage bufferedImage = new BufferedImage(imgSizeInt, imgSizeInt, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = (Graphics2D) bufferedImage.getGraphics();
        graphics.setFont(font);
        graphics.setColor(new Color(255, 255, 255, 0));
        graphics.fillRect(0, 0, imgSizeInt, imgSizeInt);
        graphics.setColor(Color.WHITE);
        graphics.setRenderingHint(
                RenderingHints.KEY_FRACTIONALMETRICS,
                fractionalMetrics ? RenderingHints.VALUE_FRACTIONALMETRICS_ON
                        : RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                antiAlias ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        java.awt.FontMetrics fontMetrics = graphics.getFontMetrics();
        int charHeight = 0;
        int positionX = 0;
        int positionY = 1;

        for (int index = 0; index < chars.length; index++) {
            char c = (char) index;
            CharData charDatum = new CharData();
            java.awt.geom.Rectangle2D dimensions = fontMetrics.getStringBounds(String.valueOf(c), graphics);
            charDatum.width = dimensions.getBounds().width + 8;
            charDatum.height = dimensions.getBounds().height;

            if (positionX + charDatum.width >= imgSizeInt) {
                positionX = 0;
                positionY += charHeight;
                charHeight = 0;
            }

            if (charDatum.height > charHeight) {
                charHeight = charDatum.height;
            }

            charDatum.storedX = positionX;
            charDatum.storedY = positionY;

            if (charDatum.height > fontHeight) {
                fontHeight = charDatum.height;
            }

            chars[index] = charDatum;
            graphics.drawString(String.valueOf(c), positionX + 2, positionY + fontMetrics.getAscent());
            positionX += charDatum.width;
        }

        return bufferedImage;
    }

    public void drawChar(CharData[] chars, char c, float x, float y) throws ArrayIndexOutOfBoundsException {
        CharData charData = chars[(int) c];
        if (charData == null)
            return;
        drawQuad(x, y, (float) charData.width, (float) charData.height,
                (float) charData.storedX, (float) charData.storedY,
                (float) charData.width, (float) charData.height);
    }

    private void drawQuad(float x2, float y2, float width, float height,
            float srcX, float srcY, float srcWidth, float srcHeight) {
        float renderSRCX = srcX / imgSize;
        float renderSRCY = srcY / imgSize;
        float renderSRCWidth = srcWidth / imgSize;
        float renderSRCHeight = srcHeight / imgSize;

        GL11.glTexCoord2f(renderSRCX + renderSRCWidth, renderSRCY);
        GL11.glVertex2d((double) (x2 + width), (double) y2);
        GL11.glTexCoord2f(renderSRCX, renderSRCY);
        GL11.glVertex2d((double) x2, (double) y2);
        GL11.glTexCoord2f(renderSRCX, renderSRCY + renderSRCHeight);
        GL11.glVertex2d((double) x2, (double) (y2 + height));
        GL11.glTexCoord2f(renderSRCX, renderSRCY + renderSRCHeight);
        GL11.glVertex2d((double) x2, (double) (y2 + height));
        GL11.glTexCoord2f(renderSRCX + renderSRCWidth, renderSRCY + renderSRCHeight);
        GL11.glVertex2d((double) (x2 + width), (double) (y2 + height));
        GL11.glTexCoord2f(renderSRCX + renderSRCWidth, renderSRCY);
        GL11.glVertex2d((double) (x2 + width), (double) y2);
    }

    public static class CharData {
        public int width = 0;
        public int height = 0;
        public int storedX = 0;
        public int storedY = 0;
    }
}





