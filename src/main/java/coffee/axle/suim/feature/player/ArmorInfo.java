package coffee.axle.suim.feature.player;

import coffee.axle.suim.events.ReceivePacketEvent;
import coffee.axle.suim.feature.Feature;
import coffee.axle.suim.feature.GuiCategory;
import coffee.axle.suim.property.Setting;
import coffee.axle.suim.util.MyauLogger;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.server.S04PacketEntityEquipment;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import static coffee.axle.suim.ProjectSUIM.mc;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ArmorInfo extends Feature {

    private static final int MAX_PROT = 4;
    private static final int MAX_FF = 2;

    public enum MODE { UPDATE, DAMAGE, BOTH }

    public ArmorInfo() {
        super("ArmorInfo", GuiCategory.PLAYER);
    }

    /** Fixes Myau's nametags with our protection level calculation */
    @Setting boolean fixNametags = true;
    /** prints assumed to chat once per second, I guess */
    @Setting(name = "send-chat") boolean printPredicted = true;
    @Setting(name = "mode") MODE mode = MODE.BOTH;
    @Setting(name = "debug") boolean debug = false;

    private static ArmorInfo instance;

    private final Map<String, Integer> teamProt = new HashMap<String, Integer>();
    private final Map<String, Integer> teamFeather = new HashMap<String, Integer>();

    private final Map<String, String> stackOwners = new HashMap<String, String>();

    private final Map<String, String[]> lastGear = new HashMap<String, String[]>();

    private static final java.util.regex.Pattern PLAYER_UPGRADE =
            java.util.regex.Pattern.compile(
                    "(\\w+) purchased Reinforced Armor ([IVX]+)");

    // damage-mode per-victim sampling
    private static class Sample {
        final float expectedUnenchanted;
        final long at;
        float seenHealth;
        Sample(float expected, long at, float hp) {
            this.expectedUnenchanted = expected;
            this.at = at;
            this.seenHealth = hp;
        }
    }
    private final Map<String, Sample> pendingHits = new HashMap<String, Sample>();


    private final Map<String, boolean[]> refreshSlots = new HashMap<String, boolean[]>();

    private final Map<String, int[]> lastEnchants = new HashMap<String, int[]>();
    private World lastWorld;

    @Override
    protected void init() {
        instance = this;
    }

    public static ArmorInfo getInstance() {
        return instance;
    }

    private boolean wantUpdate() {
        return mode == MODE.UPDATE || mode == MODE.BOTH;
    }

    private boolean wantDamage() {
        return mode == MODE.DAMAGE || mode == MODE.BOTH;
    }

    private void dbg(String s) {
        if (debug) MyauLogger.log("ArmorInfo", s);
    }

    @SubscribeEvent
    public void onChat(net.minecraftforge.client.event.ClientChatReceivedEvent e) {
        if (mc.thePlayer == null) return;
        String text = EnumChatFormatting.getTextWithoutFormattingCodes(
                e.message.getUnformattedText());

        java.util.regex.Matcher solo = PLAYER_UPGRADE.matcher(text);
        if (solo.find()) {
            String buyer = solo.group(1);
            EntityPlayer player = playerByName(buyer);
            String color = player == null ? null : teamKey(player);

            if (color == null) {
                if (buyer.equals(mc.thePlayer.getName())) {
                    color = teamKey(mc.thePlayer);
                } else {
                    return;
                }
            }
            if (color == null) return;
            int level = Math.min(roman(solo.group(2)), MAX_PROT);
            Integer cur = teamProt.get(color);
            if (cur == null || level > cur) {
                teamProt.put(color, level);
                announce(color, "protection " + level);
            }
        }
    }

    private static int roman(String s) {
        switch (s) {
            case "I": return 1;
            case "II": return 2;
            case "III": return 3;
            default: return 4;
        }
    }

    @SubscribeEvent
    public void onReceive(ReceivePacketEvent e) {
        if (!wantUpdate() || mc.thePlayer == null || mc.theWorld == null) return;
        if (!(e.getPacket() instanceof S04PacketEntityEquipment)) return;
        S04PacketEntityEquipment packet = (S04PacketEntityEquipment) e.getPacket();
        Entity entity = mc.theWorld.getEntityByID(packet.getEntityID());
        if (!(entity instanceof EntityPlayer)) return;
        int slot = packet.getEquipmentSlot() - 1;
        if (slot < 0 || slot > 3) return;

        EntityPlayer player = (EntityPlayer) entity;
        String name = player.getName();
        ItemStack stack = packet.getItemStack();
        String[] gear = lastGear.get(name);
        if (gear == null) {
            gear = new String[4];
            for (int i = 0; i < 4; i++) {
                gear[i] = fingerprint(player.inventory.armorInventory[i]);
            }
            lastGear.put(name, gear);
        }

        String print = fingerprint(stack);
        if (print.equals("0")) return;
        String old = gear[slot];
        gear[slot] = print;
        if (stack.getItem() instanceof ItemArmor) {
            stackOwners.put(print, name);
        }
        if (!print.equals(old) && !old.equals("0") && debug) {
            dbg(name + " slot " + slot + ": gear changed " + old + " -> " + print);
        }
        if (print.equals(old)) {

            if (!stack.hasEffect()) return;
            int ench = stack.getEnchantmentTagList() == null
                    ? 0 : stack.getEnchantmentTagList().tagCount();
            int[] counts = lastEnchants.get(name);
            if (counts == null) {
                counts = new int[4];
                lastEnchants.put(name, counts);
            }
            if (ench != counts[slot]) {
                dbg(name + " slot " + slot + ": ench count "
                        + counts[slot] + " -> " + ench);
                counts[slot] = ench;
            }
            boolean[] slots = refreshSlots.get(name);
            if (slots == null) {
                slots = new boolean[4];
                refreshSlots.put(name, slots);
            }
            slots[slot] = true;
        }
    }

    private void onGearRefreshed(EntityPlayer player, int slot) {
        String team = teamKey(player);
        if (team == null) return;

        if (slot == 0) {
            Integer ffCur = teamFeather.get(team);
            int ffNew = Math.min((ffCur == null ? 0 : ffCur) + 1, MAX_FF);
            if (ffNew != (ffCur == null ? 0 : ffCur)) {
                teamFeather.put(team, ffNew);
                announce(team, "feather falling " + ffNew);
                dbg("feather " + team + " -> " + ffNew);
            }
        } else if (slot == 1 || slot == 2) {
            Integer protCur = teamProt.get(team);
            int protNew = Math.min((protCur == null ? 0 : protCur) + 1, MAX_PROT);
            if (protNew != (protCur == null ? 0 : protCur)) {
                teamProt.put(team, protNew);
                announce(team, "protection " + protNew);
                dbg("prot " + team + " -> " + protNew);
            }
        }
    }

    private void announce(String teamColor, String what) {
        if (!printPredicted || mc.thePlayer == null) return;
        EnumChatFormatting color = null;
        for (EnumChatFormatting f : EnumChatFormatting.values()) {
            if (f.isColor() && f.toString().equals("\u00a7" + teamColor)) {
                color = f;
                break;
            }
        }
        String label = color == null ? teamName(teamColor) : color + teamName(teamColor);
        mc.thePlayer.addChatMessage(new ChatComponentText(
                EnumChatFormatting.GRAY + "[ArmorInfo] " + label
                        + EnumChatFormatting.GRAY + " " + what));
    }


    @SubscribeEvent
    public void onAttack(AttackEntityEvent e) {
        if (!wantDamage() || e.entity != mc.thePlayer) return;
        if (!(e.target instanceof EntityPlayer)) return;
        EntityPlayer victim = (EntityPlayer) e.target;
        float expected = unenchantedDamage(victim);
        if (expected <= 0) return;
        if (fireballNear(victim)) {
            dbg(victim.getName() + ": fireball nearby, skip");
            return;
        }
        pendingHits.put(victim.getName(), new Sample(expected,
                System.currentTimeMillis(),
                victim.getHealth() + victim.getAbsorptionAmount()));
        dbg("hit " + victim.getName() + " expected " + expected);
    }

    private boolean fireballNear(EntityPlayer victim) {
        for (Object o : mc.theWorld.loadedEntityList) {
            if (o instanceof EntityFireball) {
                EntityFireball fb = (EntityFireball) o;
                if (fb.getDistanceSqToEntity(victim) < 36.0) return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public void onTick(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent e) {
        if (e.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // new game/world: forget everything
        if (mc.theWorld != lastWorld) {
            lastWorld = mc.theWorld;
            teamProt.clear();
            teamFeather.clear();
            stackOwners.clear();
            lastGear.clear();
            pendingHits.clear();
            refreshSlots.clear();
            lastEnchants.clear();
            dbg("world changed, reset");
        }

        if (!refreshSlots.isEmpty()) {
            for (Map.Entry<String, boolean[]> entry : refreshSlots.entrySet()) {
                boolean[] slots = entry.getValue();
                String listed = (slots[0] ? "boots " : "") + (slots[1] ? "legs " : "")
                        + (slots[2] ? "chest " : "") + (slots[3] ? "helm" : "");
                EntityPlayer player = playerByName(entry.getKey());
                if (player == null) {
                    dbg(entry.getKey() + ": left render, " + listed);
                    continue;
                }
                if (slots[3]) {
                    dbg(entry.getKey() + ": helmet in refresh (" + listed + "), burst ignored");
                    continue;
                }
                if (slots[0] && slots[1] && slots[2]) {
                    onGearRefreshed(player, 1);
                } else if (slots[0] && !slots[1] && !slots[2]) {
                    onGearRefreshed(player, 0);
                } else {
                    dbg(entry.getKey() + ": odd pattern " + listed);
                }
            }
            refreshSlots.clear();
        }

        Iterator<Map.Entry<String, Sample>> it = pendingHits.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Sample> entry = it.next();
            Sample sample = entry.getValue();
            if (System.currentTimeMillis() - sample.at > 2000) {
                it.remove();
                continue;
            }
            EntityPlayer victim = playerByName(entry.getKey());
            if (victim == null) continue;
            float hp = victim.getHealth() + victim.getAbsorptionAmount();
            if (hp == sample.seenHealth) continue;
            float lost = sample.seenHealth - hp;
            if (lost > 0) {
                applyDamageMode(victim, sample.expectedUnenchanted, lost);
            }
            it.remove();
        }
    }


    private void applyDamageMode(EntityPlayer victim, float expected, float actual) {
        if (wasFallingRecently(victim)) {
            dbg(victim.getName() + ": falling, skip");
            return;
        }
        float armor = armorReduction(victim);
        float unenchanted = expected * (1.0f - armor);
        if (unenchanted <= 0.5f) {
            dbg(victim.getName() + ": base damage too low to read");
            return;
        }
        if (actual >= unenchanted) {

            dbg(victim.getName() + ": full damage, no prot read");
            return;
        }
        float ratio = actual / unenchanted;
        int epf = (int) Math.round((1.0f - ratio) / 0.04f);
        epf = Math.max(0, Math.min(20, epf));

        int prot = Math.min(epf, MAX_PROT);
        String team = teamKey(victim);
        String key = team == null ? victim.getName() : team;
        Integer cur = teamProt.get(key);
        if (cur == null || prot > cur) {
            teamProt.put(key, prot);
            announce(key, "protection " + prot + " (damage read)");
            dbg("damage mode: " + key + " prot -> " + prot + " (epf " + epf + ")");
        }
    }

    private float unenchantedDamage(EntityPlayer victim) {
        ItemStack weapon = mc.thePlayer.getHeldItem();
        if (weapon == null) return -1;
        if (weapon.getItem() instanceof ItemSword) {
            float dmg = ((ItemSword) weapon.getItem()).getDamageVsEntity()
                    + EnchantmentHelper.getModifierForCreature(
                            weapon, victim.getCreatureAttribute()) + 1.0f;
            boolean crit = mc.thePlayer.fallDistance > 0.0f
                    && !mc.thePlayer.onGround
                    && !mc.thePlayer.isOnLadder()
                    && !mc.thePlayer.isInWater()
                    && !mc.thePlayer.isPotionActive(net.minecraft.potion.Potion.jump);
            return crit ? dmg * 1.5f : dmg;
        }
        if (weapon.getDisplayName() != null
                && weapon.getDisplayName().toLowerCase().contains("stick")) {
            return 1.0f;
        }
        return -1;
    }

    private float armorReduction(EntityPlayer victim) {
        int armorValue = 0;
        for (int slot = 0; slot < 4; slot++) {
            ItemStack armor = victim.inventory.armorInventory[slot];
            if (armor != null && armor.getItem() instanceof ItemArmor) {
                armorValue += ((ItemArmor) armor.getItem()).damageReduceAmount;
            }
        }
        return Math.min(0.8f, armorValue * 0.04f);
    }

    private boolean wasFallingRecently(EntityPlayer victim) {
        return victim.fallDistance > 0.0f || !victim.onGround;
    }


    public static ArrayList<String> labelsFor(ItemStack stack) {
        ArmorInfo self = instance;
        if (self == null || !self.fixNametags || stack == null) return null;
        if (!(stack.getItem() instanceof ItemArmor)) return null;
        String owner = self.stackOwners.get(fingerprint(stack));
        if (owner == null) return null;
        EntityPlayer player = self.playerByName(owner);
        if (player == null) return null;
        String team = self.teamKey(player);
        if (team == null) return null;

        Integer prot = self.teamProt.get(team);
        Integer ff = self.teamFeather.get(team);
        if (prot == null && ff == null) return null;

        boolean isBoots = ((ItemArmor) stack.getItem()).armorType == 3;
        ArrayList<String> labels = new ArrayList<String>();
        if (prot != null && prot > 0) {
            labels.add(EnumChatFormatting.GRAY + "Prot " + prot);
        }
        if (isBoots && ff != null && ff > 0) {
            labels.add(EnumChatFormatting.GRAY + "FF " + ff);
        }
        if (labels.isEmpty()) return null;
        return labels;
    }


    private static String fingerprint(ItemStack stack) {
        if (stack == null) return "0";
        return stack.getItem().hashCode() + ":" + stack.getItemDamage()
                + ":" + stack.stackSize;
    }

    private EntityPlayer playerByName(String name) {
        if (name == null) return null;
        for (Object o : mc.theWorld.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (p.getName().equals(name)) return p;
        }
        return null;
    }

    private String teamKey(EntityPlayer player) {
        if (player == null) return null;
        try {
            if (player.getTeam() instanceof net.minecraft.scoreboard.ScorePlayerTeam) {
                String color = mostFrequentColor(
                        ((net.minecraft.scoreboard.ScorePlayerTeam) player.getTeam()).getColorPrefix());
                if (color != null) return color;
            }
            return mostFrequentColor(player.getDisplayName().getUnformattedText());
        } catch (Exception e) {
            return null;
        }
    }

    private static String teamName(String colorCode) {
        for (EnumChatFormatting format : EnumChatFormatting.values()) {
            if (format.toString().equals("\u00a7" + colorCode)) {
                String name = format.getFriendlyName();
                return name == null ? colorCode : name;
            }
        }
        return colorCode;
    }

    private static String mostFrequentColor(String text) {
        if (text == null) return null;
        Map<Character, Integer> counts = new HashMap<Character, Integer>();
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) == '\u00a7') {
                char code = text.charAt(i + 1);
                for (EnumChatFormatting format : EnumChatFormatting.values()) {
                    if (format.isColor() && format.toString().equals("\u00a7" + code)) {
                        Integer count = counts.get(code);
                        counts.put(code, count == null ? 1 : count + 1);
                        break;
                    }
                }
            }
        }
        char best = 0;
        int bestCount = 0;
        for (Map.Entry<Character, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best == 0 ? null : String.valueOf(best);
    }
}
