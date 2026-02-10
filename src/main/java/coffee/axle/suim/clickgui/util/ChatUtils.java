package coffee.axle.suim.clickgui.util;

import coffee.axle.suim.clickgui.ClickGuiConfig;
import coffee.axle.suim.hooks.MyauHook;
import coffee.axle.suim.hooks.MyauModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.ClientCommandHandler;

import java.util.regex.Pattern;

/**
 * A collection of utility functions for creating and sending or displaying chat
 * messages.
 * <p>
 * Use {@link #chatMessage(String, boolean)} to put messages in chat which are
 * only visible locally.
 * Use {@link #modMessage(Object, String, ChatStyle)} for client-side messages
 * with the mod prefix.
 * Use {@link #sendChat(String)} for sending a player message to the server.
 * Use {@link #command(String, boolean)} to execute commands either client-side
 * or send them to the server.
 */
public final class ChatUtils {

    public static final ChatUtils INSTANCE = new ChatUtils();

    private static final Minecraft mc = Minecraft.getMinecraft();

    // Color string constants
    public static final String BLACK = EnumChatFormatting.BLACK.toString();
    public static final String DARK_BLUE = EnumChatFormatting.DARK_BLUE.toString();
    public static final String DARK_GREEN = EnumChatFormatting.DARK_GREEN.toString();
    public static final String DARK_AQUA = EnumChatFormatting.DARK_AQUA.toString();
    public static final String DARK_RED = EnumChatFormatting.DARK_RED.toString();
    public static final String DARK_PURPLE = EnumChatFormatting.DARK_PURPLE.toString();
    public static final String GOLD = EnumChatFormatting.GOLD.toString();
    public static final String GRAY = EnumChatFormatting.GRAY.toString();
    public static final String DARK_GRAY = EnumChatFormatting.DARK_GRAY.toString();
    public static final String BLUE = EnumChatFormatting.BLUE.toString();
    public static final String GREEN = EnumChatFormatting.GREEN.toString();
    public static final String AQUA = EnumChatFormatting.AQUA.toString();
    public static final String RED = EnumChatFormatting.RED.toString();
    public static final String LIGHT_PURPLE = EnumChatFormatting.LIGHT_PURPLE.toString();
    public static final String YELLOW = EnumChatFormatting.YELLOW.toString();
    public static final String WHITE = EnumChatFormatting.WHITE.toString();
    public static final String OBFUSCATED = EnumChatFormatting.OBFUSCATED.toString();
    public static final String BOLD = EnumChatFormatting.BOLD.toString();
    public static final String STRIKETHROUGH = EnumChatFormatting.STRIKETHROUGH.toString();
    public static final String UNDERLINE = EnumChatFormatting.UNDERLINE.toString();
    public static final String ITALIC = EnumChatFormatting.ITALIC.toString();
    public static final String RESET = EnumChatFormatting.RESET.toString();

    /**
     * Pattern to replace formatting codes with &amp; with the § equivalent.
     * Matches all "&amp;" directly followed by one of 0-9, a-f, k-o, r
     * (case-insensitive).
     */
    private static final Pattern formattingCodePattern = Pattern.compile("(?i)&(?=[0-9A-FK-OR])");

    private ChatUtils() {
    }

    /**
     * Replaces chat formatting codes using "&amp;" as escape character with "§".
     * Example: "&amp;aText &amp;r" returns "§aText §r".
     */
    private static String reformatString(String text) {
        return formattingCodePattern.matcher(text).replaceAll("\u00A7");
    }

    /**
     * Gets the selected mod prefix from {@link ClickGuiConfig}.
     */
    public static String getPrefix() {
        switch (ClickGuiConfig.getPrefixStyleIndex()) {
            case 0:
                return getMyauPrefix();
            case 1:
                return getMyauShortPrefix();
            default:
                return reformatString(ClickGuiConfig.getCustomPrefix());
        }
    }

    private static String getMyauPrefix() {
        try {
            MyauHook hook = MyauHook.getInstance();
            if (hook == null)
                return "\u00A77[\u00A7cMyau\u00A77]\u00A7r ";
            MyauModuleManager mgr = new MyauModuleManager(hook);
            String name = mgr.getClientName();
            return name != null ? name : "\u00A77[\u00A7cMyau\u00A77]\u00A7r ";
        } catch (Exception ignored) {
            return "\u00A77[\u00A7cMyau\u00A77]\u00A7r ";
        }
    }

    private static String getMyauShortPrefix() {
        try {
            MyauHook hook = MyauHook.getInstance();
            if (hook == null)
                return "\u00A77[\u00A7cMyau\u00A77]\u00A7r ";
            MyauModuleManager mgr = new MyauModuleManager(hook);
            String name = mgr.getClientName();
            if (name != null && name.contains("]")) {
                int bracket = name.lastIndexOf(']');
                String inner = name.substring(name.indexOf('[') + 1, bracket);
                String firstChar = inner.replaceAll("\u00A7.", "").substring(0, 1);
                return name.substring(0, name.indexOf('[') + 1)
                        + inner.substring(0, inner.lastIndexOf(firstChar) + 1)
                        + name.substring(bracket) + " ";
            }
            return "\u00A77[\u00A7cM\u00A77]\u00A7r ";
        } catch (Exception ignored) {
            return "\u00A77[\u00A7cM\u00A77]\u00A7r ";
        }
    }

    /**
     * Puts a message in chat client-side with the mod prefix.
     */
    public static void modMessage(Object message, String prefix, ChatStyle chatStyle) {
        ChatComponentText chatComponent = new ChatComponentText(prefix + " \u00A78\u00BB\u00A7r " + message);
        if (chatStyle != null) {
            chatComponent.setChatStyle(chatStyle);
        }
        runOnMCThread(() -> {
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(chatComponent);
            }
        });
    }

    /**
     * Puts a message in chat client-side with the default mod prefix and no chat
     * style.
     */
    public static void modMessage(Object message) {
        modMessage(message, getPrefix(), null);
    }

    /**
     * Puts a message in chat client-side with the specified prefix and no chat
     * style.
     */
    public static void modMessage(Object message, String prefix) {
        modMessage(message, prefix, null);
    }

    /**
     * Puts a message in chat client-side with the mod prefix, wrapping an
     * IChatComponent.
     */
    public static void modMessage(IChatComponent iChatComponent) {
        chatMessage(new ChatComponentText(getPrefix() + " ").appendSibling(iChatComponent));
    }

    /**
     * Print a message in chat <b>client-side</b>.
     *
     * @param text     The message text.
     * @param reformat Whether to replace "&amp;" formatting codes with "§".
     */
    public static void chatMessage(String text, boolean reformat) {
        IChatComponent message = new ChatComponentText(reformat ? reformatString(text) : text);
        chatMessage(message);
    }

    /**
     * Print a message in chat <b>client-side</b> with reformatting enabled.
     */
    public static void chatMessage(String text) {
        chatMessage(text, true);
    }

    /**
     * Print a message in chat <b>client-side</b>.
     */
    public static void chatMessage(IChatComponent iChatComponent) {
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(iChatComponent);
        }
    }

    /**
     * Print a developer message in chat (disabled; dev settings removed).
     */
    public static void devMessage(Object msg) {
        // Dev mode has been removed — no-op
    }

    /**
     * Print a debug message in chat (disabled; debug settings removed).
     */
    public static void debugMessage(Object msg) {
        // Debug mode has been removed — no-op
    }

    /**
     * <b>Send player message to the server</b>.
     * This mimics the player using the chat GUI to send the message.
     */
    public static void sendChat(String message) {
        if (mc.thePlayer != null) {
            mc.thePlayer.sendChatMessage(message);
        }
    }

    /**
     * Runs the specified command. Per default sends it client-side but has a
     * server-side option.
     * The input is assumed to <b>not</b> include the slash "/".
     */
    public static void command(String text, boolean clientSide) {
        if (clientSide && mc.thePlayer != null) {
            ClientCommandHandler.instance.executeCommand(mc.thePlayer, "/" + text);
        } else if (mc.thePlayer != null) {
            mc.thePlayer.sendChatMessage("/" + text);
        }
    }

    /**
     * Runs the specified command client-side.
     */
    public static void command(String text) {
        command(text, true);
    }

    /**
     * Runs the specified command. Tries client-side first; if it returns 0
     * (unrecognized), sends to server.
     */
    public static void commandAny(String text) {
        if (mc.thePlayer != null) {
            String cmd = text.startsWith("/") ? text.substring(1) : text;
            if (ClientCommandHandler.instance.executeCommand(mc.thePlayer, "/" + cmd) == 0) {
                mc.thePlayer.sendChatMessage("/" + cmd);
            }
        }
    }

    /**
     * Creates a new IChatComponent displaying {@code text} and showing
     * {@code hoverText} when hovered.
     * {@code hoverText} can include "\n" for new lines.
     */
    public static IChatComponent createHoverableText(String text, String hoverText) {
        IChatComponent message = new ChatComponentText(text);
        ChatStyle style = new ChatStyle();
        style.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(hoverText)));
        message.setChatStyle(style);
        return message;
    }

    /**
     * Creates a clickable IChatComponent with hover text and a run-command click
     * action.
     */
    public static IChatComponent createClickableText(String text, String hoverText, String action) {
        IChatComponent message = new ChatComponentText(text);
        ChatStyle style = new ChatStyle();
        style.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(hoverText)));
        style.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, action));
        message.setChatStyle(style);
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(message);
        }
        return message;
    }

    /**
     * Runs the given {@link Runnable} on the Minecraft main thread.
     * If already on the main thread, runs it immediately.
     */
    public static void runOnMCThread(Runnable run) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!mc.isCallingFromMinecraftThread()) {
            mc.addScheduledTask(run);
        } else {
            run.run();
        }
    }
}
