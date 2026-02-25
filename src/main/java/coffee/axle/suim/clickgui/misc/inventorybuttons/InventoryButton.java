package coffee.axle.suim.clickgui.misc.inventorybuttons;

import coffee.axle.suim.clickgui.util.ChatUtils;
import coffee.axle.suim.clickgui.render.HUDRenderUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.awt.Color;

/**
 * Represents a single button rendered inside the inventory GUI.
 */
public class InventoryButton {

    public static final Color colour = new Color(139, 139, 139, 155);
    public static final Color borderColour = new Color(250, 250, 250, 155);

    private final int x;
    private final int y;
    private String command;
    private String icon;
    private final boolean isEquipment;

    public InventoryButton(int x, int y, String command, String icon, boolean isEquipment) {
        this.x = x;
        this.y = y;
        this.command = command;
        this.icon = icon;
        this.isEquipment = isEquipment;
    }

    public InventoryButton(int x, int y) {
        this(x, y, "", "", false);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public boolean isEquipment() {
        return isEquipment;
    }

    public boolean isActive() {
        return command != null && !command.isEmpty();
    }

    public void action() {
        ChatUtils.INSTANCE.commandAny(command);
    }

    public void render(double xOff, double yOff) {
        render(xOff, yOff, colour, borderColour);
    }

    public void render(double xOff, double yOff, Color c, Color bC) {
        GlStateManager.pushMatrix();
        HUDRenderUtils.INSTANCE.drawRoundedBorderedRect(
                x + xOff, y + yOff, 16.0, 16.0, 3.0, 2.0, c, bC);

        if (icon != null && !icon.isEmpty()) {
            Item item = Item.getByNameOrId(icon.toLowerCase());
            if (item != null) {
                ItemStack itemStack = new ItemStack(item);
                HUDRenderUtils.INSTANCE.drawItemStackWithText(itemStack, x + xOff, y + yOff);
            }
        }

        GlStateManager.popMatrix();
    }

    public boolean isHovered(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + 16 && mouseY >= y && mouseY <= y + 16;
    }
}
