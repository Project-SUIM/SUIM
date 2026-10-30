package coffee.axle.suim.feature.render;

import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.property.Setting;
import net.minecraft.block.Block;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static coffee.axle.suim.ProjectSUIM.mc;

public class ItemESPExtras extends Feature {
    private static final Frustum frustum = new Frustum();

    @Setting(existing = true) private boolean autoScale = true;
    @Setting(existing = true) private boolean emeralds = true;
    @Setting(existing = true) private boolean diamonds = true;
    @Setting(existing = true) private boolean gold = true;
    @Setting(existing = true) private boolean iron = true;
    @Setting(after = "item-count") private boolean nameTags = false;

    public ItemESPExtras() {
        super("ItemESP:NameTags");
    }

    @Override
    protected String targetModule() {
        return "ItemESP";
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (mc.theWorld == null || mc.getRenderViewEntity() == null
                || !isEnabled() || !nameTags) {
            return;
        }
        LinkedHashMap<Group, ItemStack> items = new LinkedHashMap<>();
        for (Object obj : mc.theWorld.loadedEntityList) {
            if (!(obj instanceof EntityItem)) continue;
            EntityItem item = (EntityItem) obj;
            if (item.ticksExisted < 3 || (!item.ignoreFrustumCheck && !inFrustum(item, event.partialTicks))) continue;
            ItemStack stack = item.getEntityItem();
            if (stack == null || stack.stackSize <= 0 || !show(Item.getIdFromItem(stack.getItem()))) continue;
            double x = item.lastTickPosX + (item.posX - item.lastTickPosX) * event.partialTicks;
            double y = item.lastTickPosY + (item.posY - item.lastTickPosY) * event.partialTicks;
            double z = item.lastTickPosZ + (item.posZ - item.lastTickPosZ) * event.partialTicks;
            Group group = new Group(Item.getIdFromItem(stack.getItem()), x, y, z);
            ItemStack previous = items.get(group);
            if (previous == null) {
                items.put(group, stack.copy());
            } else {
                previous.stackSize += stack.stackSize;
            }
        }
        List<Map.Entry<Group, ItemStack>> entries = new ArrayList<>(items.entrySet());
        entries.sort(Comparator.comparingInt(e -> priority(e.getKey().itemId)));
        for (Map.Entry<Group, ItemStack> entry : entries) {
            drawNameTag(entry.getKey(), entry.getValue());
        }
    }

    private boolean show(int id) {
        return emeralds && isEmerald(id)
                || diamonds && isDiamond(id)
                || gold && isGold(id)
                || iron && isIron(id);
    }

    private void drawNameTag(Group group, ItemStack stack) {
        Entity view = mc.getRenderViewEntity();
        double distance = view.getDistance(group.x, group.y, group.z);
        double scaleDistance = Math.max(6.0, autoScale ? distance : 6.0);
        double size = 0.5 + 0.375 * ((scaleDistance - 6.0) / 28.0);
        double x = group.x - mc.getRenderManager().viewerPosX;
        double y = group.y - mc.getRenderManager().viewerPosY;
        double z = group.z - mc.getRenderManager().viewerPosZ;
        String name = stack.getDisplayName();
        String label = stack.stackSize > 1 ? name + " x" + stack.stackSize : name;
        int color = color(Item.getIdFromItem(stack.getItem())).getRGB();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + size * 0.5, z);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX,
                mc.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F, 0.0F, 0.0F);
        double scale = -0.04375 - 0.0328125 * ((scaleDistance - 6.0) / 28.0);
        GlStateManager.scale(scale, scale, 1.0);
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        FontRenderer font = mc.fontRendererObj;
        int width = font.getStringWidth(label);
        float textX = -(width / 2.0F - 0.5F);
        float textY = -(font.FONT_HEIGHT / 2 - 0.5F);
        // four black copies behind the real text as the outline
        font.drawString(label, textX + 1.0F, textY, 0, false);
        font.drawString(label, textX - 1.0F, textY, 0, false);
        font.drawString(label, textX, textY + 1.0F, 0, false);
        font.drawString(label, textX, textY - 1.0F, 0, false);
        font.drawString(label, textX, textY, color, false);
        GlStateManager.enableDepth();
        GlStateManager.resetColor();
        GlStateManager.popMatrix();
    }

    private boolean inFrustum(Entity entity, float partialTicks) {
        Entity view = mc.getRenderViewEntity();
        if (view.getDistanceToEntity(entity) > 512.0F) return false;
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        AxisAlignedBB bounds = entity.getEntityBoundingBox().expand(0.125, 0.125, 0.125)
                .offset(x - entity.posX, y - entity.posY, z - entity.posZ);
        frustum.setPosition(view.posX, view.posY, view.posZ);
        return frustum.isBoundingBoxInFrustum(bounds);
    }

    private static Color color(int id) {
        if (isEmerald(id)) return new Color(0x55FF55);
        if (isDiamond(id)) return new Color(0x55FFFF);
        if (isGold(id)) return new Color(0xFFFF55);
        return new Color(isIron(id) ? 0xFFFFFF : 0xAAAAAA);
    }

    private static int priority(int id) {
        if (isEmerald(id)) return 4;
        if (isDiamond(id)) return 3;
        if (isGold(id)) return 2;
        return isIron(id) ? 1 : 0;
    }

    private static boolean isEmerald(int id) {
        Item item = Item.getItemById(id);
        Block block = Block.getBlockFromItem(item);
        return item == Items.emerald || block == Blocks.emerald_block || block == Blocks.emerald_ore;
    }

    private static boolean isDiamond(int id) {
        Item item = Item.getItemById(id);
        Block block = Block.getBlockFromItem(item);
        return item == Items.diamond || item == Items.diamond_sword || item == Items.diamond_pickaxe
                || item == Items.diamond_shovel || item == Items.diamond_axe || item == Items.diamond_hoe
                || item == Items.diamond_helmet || item == Items.diamond_chestplate
                || item == Items.diamond_leggings || item == Items.diamond_boots
                || block == Blocks.diamond_block || block == Blocks.diamond_ore;
    }

    private static boolean isGold(int id) {
        Item item = Item.getItemById(id);
        Block block = Block.getBlockFromItem(item);
        return item == Items.gold_ingot || item == Items.gold_nugget || item == Items.golden_apple
                || block == Blocks.gold_block || block == Blocks.gold_ore;
    }

    private static boolean isIron(int id) {
        Item item = Item.getItemById(id);
        Block block = Block.getBlockFromItem(item);
        return item == Items.iron_ingot || block == Blocks.iron_block || block == Blocks.iron_ore;
    }

    private static class Group {
        final int itemId;
        final double x, y, z;
        private final int hash;

        Group(int itemId, double x, double y, double z) {
            this.itemId = itemId;
            this.x = x;
            this.y = y;
            this.z = z;
            hash = Objects.hash(itemId, (int) x, (int) y, (int) z);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Group)) return false;
            Group other = (Group) obj;
            return itemId == other.itemId && (int) x == (int) other.x
                    && (int) y == (int) other.y && (int) z == (int) other.z;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
