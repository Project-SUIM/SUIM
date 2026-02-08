package coffee.axle.suim.feature.command;

import coffee.axle.suim.feature.Feature;

import coffee.axle.suim.util.MyauLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

/**
 * .client Command
 * 
 * @maybsomeday
 */
public class ClientInfoCommand extends Feature {
    private static final File CONFIG_FILE = new File("config/Myau/clientname.txt");

    @Override
    public String getName() {
        return "Command:ClientInfo";
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");

            ArrayList<String> commandNames = new ArrayList<>(Arrays.asList("client", "info"));
            creator.registerCommand(commandNames, this::handleCommand);

            registerSetClientNameCommand();

            loadClientName();

            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;
        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    private void handleCommand(ArrayList<String> args) {
        try {
            String clientName = manager.getClientName();
            String version = manager.getClientVersion();
            String username = manager.getUsername();

            int moduleCount = manager.getModuleCount();
            int commandCount = manager.getCommandCount();
            int configCount = manager.getConfigCount();

            SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM dd, yyyy");
            String currentDate = dateFormat.format(new Date());

            StringBuilder headerAndPre = new StringBuilder();
            headerAndPre.append("\n&f&m----------------------------------------\n");
            headerAndPre.append("&fMyau Client &7(&aver: &b")
                    .append(version)
                    .append("&7 | &5")
                    .append(username)
                    .append("&7) &7- &d")
                    .append(currentDate)
                    .append("\n");
            headerAndPre.append("\n");

            String clientLinePrefix = "&7» &fClient Name: ";
            String clientLineName = clientName;

            StringBuilder footer = new StringBuilder();
            footer.append("&7» &fLoaded Modules: &a").append(moduleCount).append("\n");
            footer.append("&7» &fLoaded Commands: &b").append(commandCount).append("\n");
            footer.append("&7» &fSaved Configs: &6").append(configCount).append("\n");
            footer.append("&f&m----------------------------------------");

            ChatComponentText root = new ChatComponentText("");

            root.appendSibling(new ChatComponentText(headerAndPre.toString().replace("&", "§")));

            ChatComponentText namePrefix = new ChatComponentText(clientLinePrefix.replace("&", "§"));

            ChatComponentText clickableName = new ChatComponentText(clientLineName.replace("&", "§"));
            ChatStyle style = new ChatStyle();
            style.setChatHoverEvent(
                    new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new ChatComponentText("§eClick to change §fthe client name")));
            style.setChatClickEvent(
                    new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, ".setclientname "));
            clickableName.setChatStyle(style);

            namePrefix.appendSibling(clickableName);
            namePrefix.appendSibling(new ChatComponentText("\n"));
            root.appendSibling(namePrefix);
            root.appendSibling(new ChatComponentText(footer.toString().replace("&", "§")));

            if (Minecraft.getMinecraft().thePlayer != null) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(root);
            }
        } catch (Exception e) {
            manager.sendMessage("&cError retrieving client info: " + e.getMessage());
            MyauLogger.error("ClientInfo:handler", e);
        }
    }

    private void registerSetClientNameCommand() throws Exception {
        ArrayList<String> cmd = new ArrayList<>(Arrays.asList("setclientname"));
        creator.registerCommand(cmd, args -> {
            try {
                if (args.size() <= 1) {
                    manager.sendMessage("&cUsage: &f.setclientname &7<new client name>");
                    manager.sendMessage("&7Example: &f.setclientname &7[&cM&6y&ea&au&7]");
                    return;
                }

                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < args.size(); i++) {
                    if (i > 1)
                        sb.append(" ");
                    sb.append(args.get(i));
                }

                setClientName(sb.toString());
            } catch (Exception e) {
                manager.sendMessage("&cFailed to handle client name command: " + e.getMessage());
                MyauLogger.error("ClientInfo:version", e);
            }
        });
    }

    private void setClientName(String newName) {
        try {
            String base = newName.replace("&", "§");
            String formattedName = base;
            if (!formattedName.endsWith("§r ")) {
                formattedName = formattedName.replaceAll("\\s+$", "");
                formattedName = formattedName + "§r ";
            }

            manager.setClientName(formattedName);

            saveClientName(formattedName);
            String echo = formattedName.replace("§", "&");
            manager.sendMessage("&aClient name updated to: " + echo);
        } catch (Exception e) {
            manager.sendMessage("&cFailed to set client name: " + e.getMessage());
            MyauLogger.error("ClientInfo:init", e);
        }
    }

    private void saveClientName(String name) {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            Files.write(CONFIG_FILE.toPath(), name.getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            MyauLogger.error("Failed to save client name", e);
        }
    }

    private void loadClientName() {
        try {
            if (!CONFIG_FILE.exists()) {
                return;
            }

            String savedName = new String(Files.readAllBytes(CONFIG_FILE.toPath()));
            if (savedName.isEmpty()) {
                return;
            }

            manager.setClientName(savedName);
        } catch (Exception e) {
            MyauLogger.error("Failed to load client name", e);
        }
    }
}
