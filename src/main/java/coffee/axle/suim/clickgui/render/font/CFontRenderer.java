package coffee.axle.suim.clickgui.render.font;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.awt.Font;

public class CFontRenderer extends CFont {

    private final CharData[] boldChars = new CharData[256];
    private final CharData[] italicChars = new CharData[256];
    private final CharData[] boldItalicChars = new CharData[256];
    private final int[] colorCode = new int[32];
    private final String colourCodes = "0123456789abcdefklmnor";
    private DynamicTexture texBold;
    private DynamicTexture texItalic;
    private DynamicTexture texItalicBold;

    public CFontRenderer(Font font) {
        super(font, true, false);
        setupMinecraftColourCodes();
        setupBoldItalicIDs();
    }

    public int drawStringWithShadow(String text, double x, double y, int color) {
        float shadowWidth = drawString(text, x + 0.5, y + 0.8999999761581421, new Color(20, 20, 20).getRGB(), true,
                8.3f);
        return (int) Math.max(shadowWidth, drawString(text, x, y, color, false, 8.3f));
    }

    public void drawString(String text, double x, double y, int color, boolean shadow) {
        if (shadow) {
            drawStringWithShadow(text, x, y, color);
        } else {
            drawString(text, x, y, color, false, 8.3f);
        }
    }

    public float drawString(String text, double x, double y, int color, boolean shadow, float kerning) {
        double x1 = (x - 1) * 2;
        double y1 = (y - 2) * 2;
        if (text.isEmpty())
            return 0.0f;

        Color colour = new Color(color, true);
        if (colour.getRed() == 255 && colour.getGreen() == 255 && colour.getBlue() == 255 && colour.getAlpha() == 32) {
            colour = new Color(255, 255, 255);
        }
        if (colour.getAlpha() < 4) {
            colour = new Color(colour.getRed(), colour.getBlue(), colour.getGreen(), 255);
        }
        if (shadow) {
            colour = new Color(colour.getRed() / 4, colour.getGreen() / 4, colour.getBlue() / 4, colour.getAlpha());
        }

        CharData[] currentData = charData;
        boolean bold = false;
        boolean italic = false;
        boolean strikethrough = false;
        boolean underline = false;

        GL11.glPushMatrix();
        GlStateManager.scale(0.5, 0.5, 0.5);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        GlStateManager.color(colour.getRed() / 255f, colour.getGreen() / 255f, colour.getBlue() / 255f,
                colour.getAlpha() / 255f);
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(tex.getGlTextureId());
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex.getGlTextureId());

        int index = 0;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (character == '\u00A7') {
                int colorIndex = 21;
                try {
                    colorIndex = colourCodes.indexOf(text.charAt(index + 1));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (colorIndex < 16) {
                    bold = false;
                    italic = false;
                    underline = false;
                    strikethrough = false;
                    GlStateManager.bindTexture(tex.getGlTextureId());
                    currentData = charData;
                    int adjustedIndex = colorIndex < 0 ? 15 : colorIndex;
                    int codeColor = colorCode[shadow ? adjustedIndex + 16 : adjustedIndex];
                    GlStateManager.color(
                            (codeColor >> 16 & 0xFF) / 255.0f,
                            (codeColor >> 8 & 0xFF) / 255.0f,
                            (codeColor & 0xFF) / 255.0f,
                            (float) colour.getAlpha());
                } else if (colorIndex == 17) {
                    bold = true;
                    if (italic) {
                        GlStateManager.bindTexture(texItalicBold.getGlTextureId());
                        currentData = boldItalicChars;
                    } else {
                        GlStateManager.bindTexture(texBold.getGlTextureId());
                        currentData = boldChars;
                    }
                } else if (colorIndex == 18) {
                    strikethrough = true;
                } else if (colorIndex == 19) {
                    underline = true;
                } else if (colorIndex == 20) {
                    italic = true;
                    if (bold) {
                        GlStateManager.bindTexture(texItalicBold.getGlTextureId());
                        currentData = boldItalicChars;
                    } else {
                        GlStateManager.bindTexture(texItalic.getGlTextureId());
                        currentData = italicChars;
                    }
                } else {
                    bold = false;
                    italic = false;
                    underline = false;
                    strikethrough = false;
                    GlStateManager.color(colour.getRed() / 255f, colour.getGreen() / 255f, colour.getBlue() / 255f,
                            colour.getAlpha() / 255f);
                    GlStateManager.bindTexture(tex.getGlTextureId());
                    currentData = charData;
                }
                index += 2;
            } else {
                if ((int) character < currentData.length) {
                    GL11.glBegin(GL11.GL_TRIANGLES);
                    drawChar(currentData, character, (float) x1, (float) y1);
                    GL11.glEnd();

                    if (strikethrough) {
                        drawLine(
                                x1,
                                y1 + currentData[(int) character].height / 2.0,
                                x1 + currentData[(int) character].width - 8.0,
                                y1 + currentData[(int) character].height / 2.0,
                                1.0f);
                    }
                    if (underline) {
                        drawLine(
                                x1,
                                y1 + currentData[(int) character].height - 2.0,
                                x1 + currentData[(int) character].width - 8.0,
                                y1 + currentData[(int) character].height - 2.0,
                                1.0f);
                    }
                    x1 += currentData[(int) character].width - kerning + charOffset;
                }
                index++;
            }
        }

        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glPopMatrix();
        return (float) x1 / 2.0f;
    }

    public double getStringWidth(String text) {
        if (text == null) {
            return 0.0;
        }

        float width = 0.0f;
        CharData[] currentData = charData;

        int index = 0;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (character == '\u00A7') {
                index++;
            } else if ((int) character < currentData.length) {
                width += currentData[(int) character].width - 8.3f + charOffset;
            }
            index++;
        }

        return (double) (width / 2.0f);
    }

    private void setupBoldItalicIDs() {
        texBold = setupTexture(getFont().deriveFont(Font.BOLD), getAntiAlias(), getFractionalMetrics(), boldChars);
        texItalic = setupTexture(getFont().deriveFont(Font.ITALIC), getAntiAlias(), getFractionalMetrics(),
                italicChars);
        texItalicBold = setupTexture(getFont().deriveFont(Font.BOLD | Font.ITALIC), getAntiAlias(),
                getFractionalMetrics(), boldItalicChars);
    }

    private void drawLine(double x2, double y2, double x1, double y1, float width) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(width);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2d(x2, y2);
        GL11.glVertex2d(x1, y1);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private void setupMinecraftColourCodes() {
        for (int index = 0; index < 32; index++) {
            int noClue = (index >> 3 & 0x1) * 85;
            int red = (index >> 2 & 0x1) * 170 + noClue;
            int green = (index >> 1 & 0x1) * 170 + noClue;
            int blue = (index & 0x1) * 170 + noClue;

            if (index == 6)
                red += 85;
            if (index >= 16) {
                red /= 4;
                green /= 4;
                blue /= 4;
            }
            colorCode[index] = ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
        }
    }
}





