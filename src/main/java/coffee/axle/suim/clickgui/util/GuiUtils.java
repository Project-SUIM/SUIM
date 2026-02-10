package coffee.axle.suim.clickgui.util;

import coffee.axle.suim.clickgui.util.FontUtil;
import coffee.axle.suim.clickgui.render.HUDRenderUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Static utility methods ported from Kotlin extension functions in GuiUtils.kt.
 */
public final class GuiUtils {

    public static final GuiUtils INSTANCE = new GuiUtils();

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Pattern FORMATTING_CODE_PATTERN = Pattern.compile("(?i)\u00A7[0-9a-fk-or]");

    private GuiUtils() {
    }

    // ────────────────────────────────────────────────────────────────────────
    // String utilities
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Removes Minecraft formatting control codes (§-codes) from the string.
     */
    public static String noControlCodes(String text) {
        if (text == null)
            return "";
        return FORMATTING_CODE_PATTERN.matcher(text).replaceAll("");
    }

    /**
     * Strips rank prefixes (e.g. "[MVP+] ") from the string after removing control
     * codes.
     */
    public static String stripRank(String text) {
        if (text == null)
            return "";
        return noControlCodes(text).replaceAll("\\[[\\w+\\-]+] ", "").trim();
    }

    /**
     * Returns {@code true} if {@code value} equals any of the given
     * {@code options}.
     */
    public static boolean equalsOneOf(Object value, Object... options) {
        for (Object option : options) {
            if (Objects.equals(value, option))
                return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if {@code text} contains any of the given
     * {@code options}.
     */
    public static boolean containsOneOf(String text, Collection<String> options, boolean ignoreCase) {
        for (String option : options) {
            if (ignoreCase) {
                if (text.toLowerCase().contains(option.toLowerCase()))
                    return true;
            } else {
                if (text.contains(option))
                    return true;
            }
        }
        return false;
    }

    public static boolean containsOneOf(String text, Collection<String> options) {
        return containsOneOf(text, options, false);
    }

    /**
     * Safe substring that clamps indices to valid range.
     */
    public static String substringSafe(String text, int start, int end) {
        int safeStart = Math.max(0, Math.min(start, text.length()));
        int safeEnd = Math.max(safeStart, Math.min(end, text.length()));
        return text.substring(safeStart, safeEnd);
    }

    /**
     * Removes a range from the string, automatically swapping from/to if needed.
     */
    public static String removeRangeSafe(String text, int from, int to) {
        int f = Math.min(from, to);
        int t = Math.max(from, to);
        return text.substring(0, f) + text.substring(t);
    }

    /**
     * Drops {@code amount} characters starting at index {@code at}.
     */
    public static String dropAt(String text, int at, int amount) {
        return removeRangeSafe(text, at, at + amount);
    }

    // ────────────────────────────────────────────────────────────────────────
    // ItemStack utilities
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Gets the lore lines from an ItemStack.
     */
    public static List<String> getLore(ItemStack stack) {
        if (stack == null || stack.getTagCompound() == null)
            return Collections.emptyList();
        NBTTagCompound display = stack.getTagCompound().getCompoundTag("display");
        if (display == null)
            return Collections.emptyList();
        NBTTagList loreTag = display.getTagList("Lore", 8);
        if (loreTag == null)
            return Collections.emptyList();
        List<String> result = new ArrayList<>(loreTag.tagCount());
        for (int i = 0; i < loreTag.tagCount(); i++) {
            result.add(loreTag.getStringTagAt(i));
        }
        return result;
    }

    /**
     * Gets tooltip lines for an ItemStack.
     */
    public static List<String> getTooltip(ItemStack stack, boolean advanced) {
        return stack.getTooltip(mc.thePlayer, advanced);
    }

    public static List<String> getTooltip(ItemStack stack) {
        return getTooltip(stack, false);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Event utilities
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Posts an event to the Forge event bus, catching and logging any exceptions.
     *
     * @return {@code true} if the event was cancelled.
     */
    public static boolean postAndCatch(Event event) {
        try {
            return MinecraftForge.EVENT_BUS.post(event);
        } catch (Throwable t) {
            t.printStackTrace();
            ChatStyle style = new ChatStyle();
            StackTraceElement[] elements = t.getStackTrace();
            StringBuilder sb = new StringBuilder();
            sb.append("```");
            sb.append(t.toString()).append("\n");
            int limit = Math.min(elements.length, 10);
            for (int i = 0; i < limit; i++) {
                sb.append(elements[i].toString()).append("\n");
            }
            sb.append("```");
            style.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/od copy " + sb));
            style.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ChatComponentText("\u00A76Click to copy the error to your clipboard.")));
            String errorName = t.getClass().getSimpleName();
            ChatUtils.modMessage(" Caught an " + errorName + " at " + event.getClass().getSimpleName()
                    + ". \u00A7cPlease click this message to copy and send it in the Odin discord!");
            return event.isCanceled();
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Formatting utilities
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Removes both § and &amp; formatting codes from a string.
     */
    public static String removeFormatting(String text) {
        return text.replaceAll("[\u00A7&][0-9a-fk-or]", "");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Distance utilities
    // ────────────────────────────────────────────────────────────────────────

    public static double distanceToPlayer(double x, double y, double z) {
        double dx = mc.getRenderManager().viewerPosX - x;
        double dy = mc.getRenderManager().viewerPosY - y;
        double dz = mc.getRenderManager().viewerPosZ - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static double distanceToPlayer(int x, int y, int z) {
        double dx = mc.getRenderManager().viewerPosX - x;
        double dy = mc.getRenderManager().viewerPosY - y;
        double dz = mc.getRenderManager().viewerPosZ - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Player utilities
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the given player is not null, not the local player,
     * and not an NPC (UUID version != 2).
     */
    public static boolean isOtherPlayer(EntityPlayer player) {
        return player != null && player != mc.thePlayer && player.getUniqueID().version() != 2;
    }

    // ────────────────────────────────────────────────────────────────────────
    // NBT / Skyblock utilities
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Gets the unformatted display name of an ItemStack.
     */
    public static String getUnformattedName(ItemStack stack) {
        if (stack == null)
            return "";
        return noControlCodes(stack.getDisplayName());
    }

    /**
     * Gets the ExtraAttributes NBT compound from an ItemStack.
     */
    public static NBTTagCompound getExtraAttributes(ItemStack stack) {
        if (stack == null)
            return null;
        return stack.getSubCompound("ExtraAttributes", false);
    }

    /**
     * Gets the Skyblock item ID from an ItemStack's ExtraAttributes.
     */
    public static String getSkyblockID(ItemStack stack) {
        NBTTagCompound extra = getExtraAttributes(stack);
        if (extra == null)
            return "";
        return extra.getString("id");
    }

    /**
     * Gets the Skyblock UUID from an ItemStack's ExtraAttributes.
     */
    public static String getSkyblockUUID(ItemStack stack) {
        NBTTagCompound extra = getExtraAttributes(stack);
        if (extra == null)
            return "";
        return extra.getString("uuid");
    }

    /**
     * Gets the rarity string from the last line of lore, or {@code null}.
     */
    public static String getRarityOrNull(ItemStack stack) {
        if (stack == null)
            return null;
        NBTTagCompound display = stack.getSubCompound("display", false);
        if (display == null)
            return null;
        NBTTagList loreTag = display.getTagList("Lore", 8);
        if (loreTag == null || loreTag.tagCount() == 0)
            return null;
        String lastLine = loreTag.getStringTagAt(loreTag.tagCount() - 1);
        if (lastLine == null)
            return null;
        if (lastLine.contains("COMMON"))
            return "COMMON";
        if (lastLine.contains("UNCOMMON"))
            return "UNCOMMON";
        if (lastLine.contains("RARE"))
            return "RARE";
        if (lastLine.contains("EPIC"))
            return "EPIC";
        if (lastLine.contains("LEGENDARY"))
            return "LEGENDARY";
        if (lastLine.contains("MYTHIC"))
            return "MYTHIC";
        if (lastLine.contains("DIVINE"))
            return "DIVINE";
        return null;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Thread utilities
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Runs the given {@link Runnable} on the Minecraft main thread.
     */
    public static void runOnMCThread(Runnable run) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!mc.isCallingFromMinecraftThread()) {
            mc.addScheduledTask(run);
        } else {
            run.run();
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Roman numeral utilities
    // ────────────────────────────────────────────────────────────────────────

    private static final Map<Character, Integer> ROMAN_MAP = new HashMap<>();
    static {
        ROMAN_MAP.put('I', 1);
        ROMAN_MAP.put('V', 5);
        ROMAN_MAP.put('X', 10);
        ROMAN_MAP.put('L', 50);
        ROMAN_MAP.put('C', 100);
        ROMAN_MAP.put('D', 500);
        ROMAN_MAP.put('M', 1000);
    }

    public static int romanToInt(String s) {
        if (s == null || s.isEmpty())
            return 0;
        int result = 0;
        for (int i = 0; i < s.length() - 1; i++) {
            Integer current = ROMAN_MAP.get(s.charAt(i));
            Integer next = ROMAN_MAP.get(s.charAt(i + 1));
            int cur = current != null ? current : 0;
            int nxt = next != null ? next : 0;
            result += (cur < nxt) ? -cur : cur;
        }
        Integer last = ROMAN_MAP.get(s.charAt(s.length() - 1));
        result += last != null ? last : 0;
        return result;
    }

    private static final int[] INT_TO_ROMAN_VALUES = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
    private static final String[] INT_TO_ROMAN_SYMBOLS = { "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V",
            "IV", "I" };

    public static String intToRoman(int num) {
        StringBuilder sb = new StringBuilder();
        int remaining = num;
        for (int i = 0; i < INT_TO_ROMAN_VALUES.length; i++) {
            while (remaining >= INT_TO_ROMAN_VALUES[i]) {
                sb.append(INT_TO_ROMAN_SYMBOLS[i]);
                remaining -= INT_TO_ROMAN_VALUES[i];
            }
        }
        return sb.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Number formatting
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Formats a double with the specified number of decimal places, with grouping
     * separators.
     */
    public static String formatDouble(double value, int decimals) {
        return String.format("%,." + decimals + "f", value);
    }

    /**
     * Formats a coin value with suffixes (k, m, b).
     */
    public static String formatCoins(double value) {
        String formatted;
        if (value < 1e3) {
            formatted = String.format("%.2f", value);
        } else if (value < 1e6) {
            formatted = String.format("%.2fk", value / 1e3);
        } else if (value < 1e9) {
            formatted = String.format("%.2fm", value / 1e6);
        } else {
            formatted = String.format("%.2fb", value / 1e9);
        }
        // Remove trailing .00 / .0
        if (formatted.contains(".")) {
            // Find suffix (non-digit at end)
            int suffixStart = formatted.length();
            while (suffixStart > 0 && !Character.isDigit(formatted.charAt(suffixStart - 1))) {
                suffixStart--;
            }
            String numberPart = formatted.substring(0, suffixStart);
            String suffix = formatted.substring(suffixStart);
            if (numberPart.endsWith(".00")) {
                numberPart = numberPart.substring(0, numberPart.length() - 3);
            } else if (numberPart.endsWith(".0")) {
                numberPart = numberPart.substring(0, numberPart.length() - 2);
            }
            formatted = numberPart + suffix;
        }
        return formatted;
    }

    /**
     * Formats an enchantment name and tier into a colored display string.
     */
    public static String formatEnchantment(String enchantName, int tier) {
        String colour = enchantName.toUpperCase().startsWith("ULTIMATE") ? "\u00A79\u00A7d\u00A7l" : "\u00A79";
        String enchantment = enchantName.toUpperCase().contains("WISE")
                ? enchantName
                : enchantName.replaceAll("(?i)ULTIMATE_", "");
        return colour + capitalizeWords(enchantment.replace("_", " ")) + " " + intToRoman(tier);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Collection utilities
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Safely gets an element from a collection by index.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getSafe(Collection<T> collection, int index) {
        if (collection == null || index < 0)
            return null;
        try {
            List<T> list = (collection instanceof List) ? (List<T>) collection : new ArrayList<>(collection);
            if (index >= list.size())
                return null;
            return list.get(index);
        } catch (Exception e) {
            return null;
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Render utilities
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Renders text at specified position with scale and color.
     */
    public static void renderText(String text, double x, double y, double scale, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();

        GlStateManager.scale(scale, scale, scale);

        int fontHeight = FontUtil.INSTANCE.getFontHeight();
        double yOffset = y - fontHeight;
        String[] lines = text.split("\n");
        for (String line : lines) {
            yOffset += (int) (fontHeight * scale);
            FontUtil.INSTANCE.drawStringWithShadow(
                    line,
                    Math.round(x / scale),
                    Math.round(yOffset / scale),
                    color);
        }

        GlStateManager.popMatrix();
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();
    }

    /**
     * Renders text centered on screen.
     */
    public static void renderText(String text, double scale, int color) {
        net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(mc);
        int fontHeight = FontUtil.INSTANCE.getFontHeight();
        double x = sr.getScaledWidth_double() / 2.0 - FontUtil.INSTANCE.getStringWidth(text) / 2.0;
        double y = sr.getScaledHeight_double() / 2.0 + fontHeight;
        renderText(text, x, y, scale, color);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Profiler utilities
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Profiles the specified runnable with the specified name.
     */
    public static void profile(String name, Runnable func) {
        startProfile(name);
        func.run();
        endProfile();
    }

    /**
     * Starts a Minecraft profiler section with "SUIM: " prefix.
     */
    public static void startProfile(String name) {
        mc.mcProfiler.startSection("SUIM: " + name);
    }

    /**
     * Ends the current Minecraft profiler section.
     */
    public static void endProfile() {
        mc.mcProfiler.endSection();
    }

    // ────────────────────────────────────────────────────────────────────────
    // JSON / ItemStack conversion
    // ────────────────────────────────────────────────────────────────────────

    private static final Map<String, ItemStack> itemStackCache = new HashMap<>();

    /**
     * Converts a JsonObject to an ItemStack, with optional caching.
     */
    public static ItemStack toItemStack(JsonObject json, boolean useCache, boolean copyStack) {
        if (json == null)
            return new ItemStack(Items.painting, 1, 10);

        String internalName = json.has("internalname") ? json.get("internalname").getAsString() : null;
        if (internalName == null)
            return new ItemStack(Items.painting, 1, 10);

        boolean cacheEnabled = useCache && !"_".equals(internalName);

        if (cacheEnabled) {
            ItemStack cached = itemStackCache.get(internalName);
            if (cached != null) {
                return copyStack ? cached.copy() : cached;
            }
        }

        String itemId = json.has("itemid") ? json.get("itemid").getAsString() : "minecraft:stone";
        ItemStack stack = new ItemStack((Item) Item.itemRegistry.getObject(new ResourceLocation(itemId)));

        if (json.has("count")) {
            stack.stackSize = json.get("count").getAsInt();
        }

        if (stack.getItem() == null) {
            stack = new ItemStack(Item.getItemFromBlock(Blocks.stone), 0, 255);
        } else {
            if (json.has("damage")) {
                stack.setItemDamage(json.get("damage").getAsInt());
            }

            if (json.has("nbttag")) {
                try {
                    NBTTagCompound tag = JsonToNBT.getTagFromJson(json.get("nbttag").getAsString());
                    stack.setTagCompound(tag);
                } catch (NBTException ignored) {
                }
            }

            if (json.has("lore")) {
                NBTTagCompound tag = stack.getTagCompound();
                if (tag == null)
                    tag = new NBTTagCompound();
                NBTTagCompound display = tag.getCompoundTag("display");
                if (display == null)
                    display = new NBTTagCompound();
                display.setTag("Lore", processLore(json.get("lore").getAsJsonArray()));
                tag.setTag("display", display);
                stack.setTagCompound(tag);
            }
        }

        if (cacheEnabled) {
            itemStackCache.put(internalName, stack);
        }
        return copyStack ? stack.copy() : stack;
    }

    public static ItemStack toItemStack(JsonObject json) {
        return toItemStack(json, true, false);
    }

    /**
     * Processes a JsonArray of lore strings into an NBTTagList, filtering recipe
     * click lines.
     */
    public static NBTTagList processLore(JsonArray loreArray) {
        NBTTagList nbtLore = new NBTTagList();
        for (int i = 0; i < loreArray.size(); i++) {
            String lineStr = loreArray.get(i).getAsString();
            if (!lineStr.contains("Click to view recipes!") && !lineStr.contains("Click to view recipe!")) {
                nbtLore.appendTag(new NBTTagString(lineStr));
            }
        }
        return nbtLore;
    }

    /**
     * Parses a JSON string into a JsonObject.
     */
    public static JsonObject toJsonObject(String text) {
        return new JsonParser().parse(text).getAsJsonObject();
    }

    /**
     * Converts an ItemStack to a JsonObject representation.
     */
    public static JsonObject toJson(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null)
            tag = new NBTTagCompound();

        String[] lore = new String[0];
        if (tag.hasKey("display", 10)) {
            NBTTagCompound display = tag.getCompoundTag("display");
            if (display.hasKey("Lore", 9)) {
                NBTTagList list = display.getTagList("Lore", 8);
                lore = new String[list.tagCount()];
                for (int i = 0; i < list.tagCount(); i++) {
                    lore[i] = list.getStringTagAt(i);
                }
            }
        }

        String displayName = stack.getDisplayName();
        if (displayName.endsWith(" Recipes")) {
            stack.setStackDisplayName(displayName.substring(0, displayName.length() - 8));
        }

        if (lore.length > 0) {
            String lastLine = lore[lore.length - 1];
            if (lastLine.contains("Click to view recipes!") || lastLine.contains("Click to view recipe!")) {
                String[] trimmedLore = new String[Math.max(0, lore.length - 2)];
                System.arraycopy(lore, 0, trimmedLore, 0, trimmedLore.length);
                lore = trimmedLore;
            }
        }

        JsonObject json = new JsonObject();
        json.addProperty("itemid", stack.getItem().getRegistryName());
        json.addProperty("displayname", stack.getDisplayName());
        json.addProperty("nbttag", tag.toString());
        json.addProperty("damage", stack.getItemDamage());

        JsonArray jsonLore = new JsonArray();
        for (String line : lore) {
            jsonLore.add(new JsonPrimitive(line));
        }
        json.add("lore", jsonLore);

        return json;
    }

    // ────────────────────────────────────────────────────────────────────────
    // String capitalization helper
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Capitalizes the first letter of each word in a string.
     */
    public static String capitalizeWords(String text) {
        if (text == null || text.isEmpty())
            return text;
        String[] words = text.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0)
                sb.append(' ');
            String word = words[i];
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    sb.append(word.substring(1).toLowerCase());
                }
            }
        }
        return sb.toString();
    }
}





