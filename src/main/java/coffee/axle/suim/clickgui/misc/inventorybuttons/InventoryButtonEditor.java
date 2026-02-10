package coffee.axle.suim.clickgui.misc.inventorybuttons;

import coffee.axle.suim.config.InventoryButtonsConfig;
import coffee.axle.suim.clickgui.util.MiscElementHelper;
import coffee.axle.suim.clickgui.util.ColorUtil;
import coffee.axle.suim.clickgui.util.FontUtil;
import coffee.axle.suim.clickgui.misc.elements.impl.MiscElementTextField;
import coffee.axle.suim.clickgui.render.HUDRenderUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.io.IOException;
import java.util.List;

/**
 * Screen for editing inventory buttons: assign commands and icons.
 */
public class InventoryButtonEditor extends GuiScreen {

    private static final ResourceLocation INVENTORY = new ResourceLocation(
            "minecraft:textures/gui/container/inventory.png");

    private static final int INV_WIDTH = 176;
    private static final int INV_HEIGHT = 166;

    private int invX;
    private int invY;

    private InventoryButton editingButton = null;

    private final double editorWidth = 150.0;
    private final double editorHeight = 78.0;
    private int editorX = 0;
    private int editorY = 0;

    private final MiscElementTextField commandTextField;
    private final MiscElementTextField iconTextField;

    public InventoryButtonEditor() {
        commandTextField = MiscElementHelper.createTextField(0, 0, 136.0, 16.0, 0, "", "");
        iconTextField = MiscElementHelper.createTextField(0, 0, 136.0, 16.0, 0, "", "");
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.drawDefaultBackground();

        invX = width / 2 - INV_WIDTH / 2;
        invY = height / 2 - INV_HEIGHT / 2;

        GlStateManager.pushMatrix();
        GlStateManager.enableDepth();
        GlStateManager.color(1f, 1f, 1f, 1f);

        mc.getTextureManager().bindTexture(INVENTORY);
        HUDRenderUtils.INSTANCE.drawTexturedRect(
                (float) invX, (float) invY,
                (float) INV_WIDTH, (float) INV_HEIGHT,
                0f, INV_WIDTH / 256f, 0f, INV_HEIGHT / 256f, GL11.GL_NEAREST);

        List<InventoryButton> allButtons = InventoryButtonsConfig.INSTANCE.getAllButtons();
        for (InventoryButton button : allButtons) {
            int bx = invX + button.getX();
            int by = invY + button.getY();
            Color c, bC;
            if (editingButton == button) {
                c = InventoryButton.colour.brighter();
                bC = InventoryButton.borderColour.brighter();
            } else {
                c = InventoryButton.colour;
                bC = InventoryButton.borderColour;
            }
            button.render(invX, invY, c, bC);
            if (!button.isActive() && !button.isEquipment()) {
                FontUtil.INSTANCE.drawTotalCenteredString("+", bx + 8.0, by + 8.0);
            }
        }

        if (editingButton != null) {
            int bx = invX + editingButton.getX();
            int by = invY + editingButton.getY();
            editorX = bx + 8 - (int) editorWidth / 2;
            editorY = by + 20;

            GlStateManager.pushMatrix();
            GlStateManager.translate(0.0, 0.0, 50.0);
            HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                    editorX, editorY, editorWidth, editorHeight,
                    5.0, 1.0,
                    ColorUtil.INSTANCE.getBgColor(), ColorUtil.INSTANCE.getOutlineColor());

            FontUtil.INSTANCE.drawString("Command", editorX + 7, editorY + 7, 0xffa0a0a0);

            commandTextField.setPrependText(
                    commandTextField.getText().startsWith("/") ? "" : "\u00a77/\u00a7r");
            commandTextField.setX(editorX + 7.0);
            commandTextField.setY(editorY + 19.0);
            commandTextField.render(mouseX, mouseY);

            FontUtil.INSTANCE.drawString("Icon", editorX + 7, editorY + 43, 0xffa0a0a0);
            iconTextField.setX(editorX + 7.0);
            iconTextField.setY(editorY + 55.0);
            iconTextField.render(mouseX, mouseY);

            GlStateManager.popMatrix();
        }

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0)
            return;

        if (editingButton != null && isHoveringEditor(mouseX, mouseY)) {
            if (commandTextField.mouseClicked(mouseX, mouseY, mouseButton)) {
                editingButton.setCommand(commandTextField.getText());
            }
            if (iconTextField.mouseClicked(mouseX, mouseY, mouseButton)) {
                editingButton.setIcon(iconTextField.getText());
            }
        }

        if (isHoveringEditor(mouseX, mouseY))
            return;

        List<InventoryButton> allButtons = InventoryButtonsConfig.INSTANCE.getAllButtons();
        for (InventoryButton button : allButtons) {
            if (!button.isEquipment() && button.isHovered(mouseX - invX, mouseY - invY)) {
                if (editingButton == button) {
                    editingButton = null;
                } else {
                    editingButton = button;

                    commandTextField.setFocused(true);
                    commandTextField.setText(editingButton.getCommand());

                    iconTextField.setText(editingButton.getIcon());
                    iconTextField.setFocused(false);

                    InventoryButtonsConfig.INSTANCE.save();
                }
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (editingButton != null) {
            if (commandTextField.keyTyped(typedChar, keyCode)) {
                editingButton.setCommand(commandTextField.getText());
            }
            if (iconTextField.keyTyped(typedChar, keyCode)) {
                editingButton.setIcon(iconTextField.getText());
            }
            if (keyCode == Keyboard.KEY_ESCAPE) {
                editingButton = null;
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        InventoryButtonsConfig.INSTANCE.save();
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private boolean isHoveringEditor(int mouseX, int mouseY) {
        return mouseX >= editorX && mouseX <= editorX + editorWidth
                && mouseY >= editorY && mouseY <= editorY + editorHeight;
    }
}





