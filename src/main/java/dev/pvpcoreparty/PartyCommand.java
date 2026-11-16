package dev.pvpcoreparty;

import dev.rean.pvpcore.model.Kit;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public final class PartyCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("create", "invite", "accept", "deny", "leave", "kick",
            "promote", "disband", "list", "chat", "fight", "acceptfight", "denyfight", "menu", "help");

    private final PvPCoreParty plugin;

    public PartyCommand(PvPCoreParty plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        PartyManager pm = plugin.parties();
        if (cmd.getName().equalsIgnoreCase("partychat")) {
            if (args.length == 0) { plugin.send(p, "&cUsage: /pc <message>"); return true; }
            pm.chat(p, String.join(" ", args));
            return true;
        }
        if (args.length == 0) { plugin.menus().openMain(p); return true; }
        switch (args[0].toLowerCase()) {
            case "create" -> pm.create(p);
            case "invite", "add" -> {
                if (args.length < 2) { plugin.send(p, "&cUsage: /party invite <player>"); break; }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) plugin.send(p, "&cPlayer not found."); else pm.invite(p, t);
            }
            case "accept", "join" -> pm.accept(p);
            case "deny" -> pm.deny(p);
            case "leave" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) pm.leave(p);
                else plugin.menus().openConfirm(p);
            }
            case "kick" -> { if (args.length < 2) plugin.send(p, "&cUsage: /party kick <player>"); else pm.kick(p, args[1]); }
            case "promote" -> { if (args.length < 2) plugin.send(p, "&cUsage: /party promote <player>"); else pm.promote(p, args[1]); }
            case "menu" -> plugin.menus().openMain(p);
            case "disband" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) pm.disband(p);
                else plugin.menus().openConfirm(p);
            }
            case "list" -> pm.list(p);
            case "chat" -> { if (args.length < 2) plugin.send(p, "&cUsage: /party chat <message>"); else pm.chat(p, String.join(" ", Arrays.copyOfRange(args, 1, args.length))); }
            case "fight", "duel" -> {
                if (args.length < 2) { plugin.send(p, "&cUsage: /party fight <player in other party> [kit]"); break; }
                pm.fight(p, args[1], args.length > 2 ? args[2] : null);
            }
            case "acceptfight" -> pm.acceptFight(p);
            case "denyfight" -> pm.denyFight(p);
            default -> help(p);
        }
        return true;
    }

    private void help(Player p) {
        plugin.send(p, "&e/party create &7| &e/party invite <p> &7| &e/party accept &7| &e/party leave");
        plugin.send(p, "&e/party kick|promote <p> &7| &e/party menu &7| &e/party disband &7| &e/party list &7| &e/pc <msg>");
        plugin.send(p, "&e/party fight <player> [kit] &7- challenge another party (team battle)");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("party")) return List.of();
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase())) out.add(s);
        } else if (args.length == 2 && List.of("invite", "kick", "promote", "fight").contains(args[0].toLowerCase())) {
            for (Player o : Bukkit.getOnlinePlayers()) if (o.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(o.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("fight")) {
            for (Kit k : plugin.core().kitManager().list()) if (k.id().toLowerCase().startsWith(args[2].toLowerCase())) out.add(k.id());
        }
        return out;
    }
}
