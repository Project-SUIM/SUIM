package coffee.axle.suim.mixin;

import coffee.axle.suim.hooks.MyauMappings;
import coffee.axle.suim.util.MyauLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import java.util.ArrayList;
import java.io.FileWriter;
import java.io.File;

/**
 * Mixin to override TargetCommand (myau.KA) to support multiple usernames
 */
@SuppressWarnings("unchecked")
@Pseudo
@Mixin(targets = "myau.KA", remap = false)
public class MixinTargetCommand {

    @Overwrite
    public void J(ArrayList<String> args, long unused) {
        try {
            Class<?> myauClass = Class.forName(MyauMappings.CLASS_MAIN);
            java.lang.reflect.Field targetManagerField = myauClass.getDeclaredField(MyauMappings.FIELD_TARGET_MANAGER);
            targetManagerField.setAccessible(true);
            Object targetManager = targetManagerField.get(null);

            java.lang.reflect.Field clientNameField = myauClass.getDeclaredField(MyauMappings.FIELD_CLIENT_NAME);
            clientNameField.setAccessible(true);
            String clientName = (String) clientNameField.get(null);

            Class<?> chatUtilClass = Class.forName(MyauMappings.CLASS_CHAT_UTIL);
            java.lang.reflect.Method sendFormattedMethod = chatUtilClass
                    .getMethod(MyauMappings.METHOD_CHAT_SEND_FORMATTED, String.class, long.class);
            java.lang.reflect.Method sendRawMethod = chatUtilClass.getMethod(MyauMappings.METHOD_CHAT_SEND_RAW,
                    String.class, long.class);

            java.lang.reflect.Field playersField = targetManager.getClass().getSuperclass()
                    .getDeclaredField(MyauMappings.FIELD_PLAYER_LIST);
            playersField.setAccessible(true);
            ArrayList<String> playersList = (ArrayList<String>) playersField.get(targetManager);

            java.lang.reflect.Field fileField = targetManager.getClass().getSuperclass()
                    .getDeclaredField(MyauMappings.FIELD_PLAYER_FILE);
            fileField.setAccessible(true);
            File file = (File) fileField.get(targetManager);

            if (args.size() >= 2) {
                String subCommand = args.get(1).toLowerCase();
                switch (subCommand) {
                    case "a":
                    case "add":
                        if (args.size() < 3) {
                            sendFormattedMethod.invoke(null,
                                    String.format("%sUsage: .%s add <&oname&r> [&oname&r] ...&r",
                                            clientName, args.get(0).toLowerCase()),
                                    0L);
                            return;
                        }
                        for (String name : args.subList(2, args.size())) {
                            if (playersList.contains(name)) {
                                sendFormattedMethod.invoke(null,
                                        String.format("%s&o%s&r is already in your enemy list&r", clientName, name),
                                        0L);
                            } else {
                                playersList.add(name);
                                sendFormattedMethod.invoke(null,
                                        String.format("%sAdded &o%s&r to your enemy list&r", clientName, name), 0L);
                            }
                        }
                        saveToFile(file, playersList);
                        return;

                    case "r":
                    case "remove":
                        if (args.size() < 3) {
                            sendFormattedMethod.invoke(null,
                                    String.format("%sUsage: .%s remove <&oname&r> [&oname&r] ...&r",
                                            clientName, args.get(0).toLowerCase()),
                                    0L);
                            return;
                        }
                        for (String name : args.subList(2, args.size())) {
                            if (!playersList.contains(name)) {
                                sendFormattedMethod.invoke(null,
                                        String.format("%s&o%s&r is not in your enemy list&r", clientName, name), 0L);
                            } else {
                                playersList.remove(name);
                                sendFormattedMethod.invoke(null,
                                        String.format("%sRemoved &o%s&r from your enemy list&r", clientName, name), 0L);
                            }
                        }
                        saveToFile(file, playersList);
                        return;

                    case "l":
                    case "list":
                        if (playersList.isEmpty()) {
                            sendFormattedMethod.invoke(null, String.format("%sNo enemies&r", clientName), 0L);
                            return;
                        }
                        sendFormattedMethod.invoke(null, String.format("%sEnemies:&r", clientName), 0L);

                        for (String player : playersList) {
                            sendRawMethod.invoke(null, String.format("   §o%s§r", player), 0L);
                        }
                        return;

                    case "c":
                    case "clear":
                        playersList.clear();
                        saveToFile(file, playersList);
                        sendFormattedMethod.invoke(null, String.format("%sCleared your enemy list&r", clientName), 0L);
                        return;
                }
            }
            sendFormattedMethod.invoke(null,
                    String.format("%sUsage: .%s <&oa(dd)&r/&or(emove)&r/&ol(ist)&r/&oc(lear)&r>&r",
                            clientName, args.get(0).toLowerCase()),
                    0L);

        } catch (Exception e) {
            MyauLogger.error("MixinTargetCommand", e);
        }
    }

    private void saveToFile(File file, ArrayList<String> players) {
        try {
            FileWriter writer = new FileWriter(file);
            for (String player : players) {
                writer.write(player + "\n");
            }
            writer.close();
        } catch (Exception e) {
            MyauLogger.error("MixinTargetCommand:save", e);
        }
    }
}





