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
 * Mixin to override FriendCommand (myau.y) to support multiple usernames
 */
@SuppressWarnings("unchecked")
@Pseudo
@Mixin(targets = "myau.y", remap = false)
public class MixinFriendCommand {

    @Overwrite
    public void j(ArrayList<String> args) {
        try {
            Class<?> myauClass = Class.forName(MyauMappings.CLASS_MAIN);
            java.lang.reflect.Field friendManagerField = myauClass.getDeclaredField(MyauMappings.FIELD_FRIEND_MANAGER);
            friendManagerField.setAccessible(true);
            Object friendManager = friendManagerField.get(null);

            java.lang.reflect.Field clientNameField = myauClass.getDeclaredField(MyauMappings.FIELD_CLIENT_NAME);
            clientNameField.setAccessible(true);
            String clientName = (String) clientNameField.get(null);

            Class<?> chatUtilClass = Class.forName(MyauMappings.CLASS_CHAT_UTIL);
            java.lang.reflect.Method sendFormattedMethod = chatUtilClass
                    .getMethod(MyauMappings.METHOD_CHAT_SEND_FORMATTED, String.class);
            java.lang.reflect.Method sendRawMethod = chatUtilClass.getMethod(MyauMappings.METHOD_CHAT_SEND_RAW,
                    String.class);

            java.lang.reflect.Field playersField = friendManager.getClass().getSuperclass()
                    .getDeclaredField(MyauMappings.FIELD_PLAYER_LIST);
            playersField.setAccessible(true);

            ArrayList<String> playersList = (ArrayList<String>) playersField.get(friendManager);

            java.lang.reflect.Field fileField = friendManager.getClass().getSuperclass()
                    .getDeclaredField(MyauMappings.FIELD_PLAYER_FILE);
            fileField.setAccessible(true);
            File file = (File) fileField.get(friendManager);

            if (args.size() >= 2) {
                String subCommand = args.get(1).toLowerCase();
                switch (subCommand) {
                    case "a":
                    case "add":
                        if (args.size() < 3) {
                            sendFormattedMethod.invoke(null,
                                    String.format("%sUsage: .%s add <&oname&r> [&oname&r] ...&r",
                                            clientName, args.get(0).toLowerCase()));
                            return;
                        }
                        for (String name : args.subList(2, args.size())) {
                            if (playersList.contains(name)) {
                                sendFormattedMethod.invoke(null,
                                        String.format("%s&o%s&r is already in your friend list&r", clientName, name));
                            } else {
                                playersList.add(name);
                                sendFormattedMethod.invoke(null,
                                        String.format("%sAdded &o%s&r to your friend list&r", clientName, name));
                            }
                        }
                        saveToFile(file, playersList);
                        return;

                    case "r":
                    case "remove":
                        if (args.size() < 3) {
                            sendFormattedMethod.invoke(null,
                                    String.format("%sUsage: .%s remove <&oname&r> [&oname&r] ...&r",
                                            clientName, args.get(0).toLowerCase()));
                            return;
                        }
                        for (String name : args.subList(2, args.size())) {
                            if (!playersList.contains(name)) {
                                sendFormattedMethod.invoke(null,
                                        String.format("%s&o%s&r is not in your friend list&r", clientName, name));
                            } else {
                                playersList.remove(name);
                                sendFormattedMethod.invoke(null,
                                        String.format("%sRemoved &o%s&r from your friend list&r", clientName, name));
                            }
                        }
                        saveToFile(file, playersList);
                        return;

                    case "l":
                    case "list":
                        if (playersList.isEmpty()) {
                            sendFormattedMethod.invoke(null, String.format("%sNo friends&r", clientName));
                            return;
                        }
                        sendFormattedMethod.invoke(null, String.format("%sFriends:&r", clientName));

                        for (String friend : playersList) {
                            sendRawMethod.invoke(null, String.format("   §o%s§r", friend));
                        }
                        return;

                    case "c":
                    case "clear":
                        playersList.clear();
                        saveToFile(file, playersList);
                        sendFormattedMethod.invoke(null, String.format("%sCleared your friend list&r", clientName));
                        return;

                    default:
                        if (args.size() == 2) {
                            java.lang.reflect.Method isFriendMethod = friendManager.getClass().getSuperclass()
                                    .getDeclaredMethod(MyauMappings.METHOD_PLAYER_LIST_CONTAINS, String.class);
                            isFriendMethod.setAccessible(true);
                            boolean isFriend = (Boolean) isFriendMethod.invoke(friendManager, args.get(1));

                            ArrayList<String> newArgs = new ArrayList<>();
                            newArgs.add(args.get(0));
                            newArgs.add(isFriend ? "remove" : "add");
                            newArgs.add(args.get(1));
                            j(newArgs);
                            return;
                        }
                }
            }
            sendFormattedMethod.invoke(null,
                    String.format("%sUsage: .%s <&oa(dd)&r/&or(emove)&r/&ol(ist)&r/&oc(lear)&r>&r",
                            clientName, args.get(0).toLowerCase()));

        } catch (Exception e) {
            MyauLogger.error("MixinFriendCommand", e);
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
            MyauLogger.error("MixinFriendCommand:save", e);
        }
    }
}





