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

            String currentDate = new SimpleDateFormat("MMMM dd, yyyy").format(new Date());

            ChatComponentText root = new ChatComponentText("");
            root.appendSibling(new ChatComponentText(color("\n&f&m----------------------------------------\n")));
            root.appendSibling(new ChatComponentText(color("&fMyau Client &7(&aver: &b" + version + "&7 | &5" + username
                    + "&7) &7- &d" + currentDate + "\n\n")));

            ChatComponentText prefix = new ChatComponentText(color("&7» &fClient Name: "));
            ChatComponentText clickableName = new ChatComponentText(color(clientName));
            ChatStyle style = new ChatStyle();
            style.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ChatComponentText("§eClick to change §fthe client name")));
            style.setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, ".setclientname "));
            clickableName.setChatStyle(style);
            prefix.appendSibling(clickableName);
            prefix.appendSibling(new ChatComponentText("\n"));
            root.appendSibling(prefix);

            root.appendSibling(new ChatComponentText(color("&7» &fLoaded Modules: &a" + moduleCount + "\n")));
            root.appendSibling(new ChatComponentText(color("&7» &fLoaded Commands: &b" + commandCount + "\n")));
            root.appendSibling(new ChatComponentText(color("&7» &fSaved Configs: &6" + configCount + "\n")));
            root.appendSibling(new ChatComponentText(color("&f&m----------------------------------------")));

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

                StringBuilder newName = new StringBuilder();
                for (int i = 1; i < args.size(); i++) {
                    if (i > 1) {
                        newName.append(" ");
                    }
                    newName.append(args.get(i));
                }

                setClientName(newName.toString());
            } catch (Exception e) {
                manager.sendMessage("&cFailed to handle client name command: " + e.getMessage());
                MyauLogger.error("ClientInfo:setclientname", e);
            }
        });
    }

    private void setClientName(String newName) {
        try {
            String formattedName = newName.replace("&", "§").replaceAll("\\s+$", "");
            if (!formattedName.endsWith("§r ")) {
                formattedName = formattedName + "§r ";
            }

            manager.setClientName(formattedName);
            saveClientName(formattedName);
            manager.sendMessage("&aClient name updated to: " + formattedName.replace("§", "&"));
        } catch (Exception e) {
            manager.sendMessage("&cFailed to set client name: " + e.getMessage());
            MyauLogger.error("ClientInfo:setName", e);
        }
    }

    private void saveClientName(String name) {
        try {
            File parent = CONFIG_FILE.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            Files.write(CONFIG_FILE.toPath(), name.getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            MyauLogger.error("ClientInfo:save", e);
        }
    }

    private void loadClientName() {
        try {
            if (!CONFIG_FILE.exists()) {
                return;
            }

            String savedName = new String(Files.readAllBytes(CONFIG_FILE.toPath()));
            if (!savedName.isEmpty()) {
                manager.setClientName(savedName);
            }
        } catch (Exception e) {
            MyauLogger.error("ClientInfo:load", e);
        }
    }

    private String color(String text) {
        return text.replace('&', '§');
    }
}
