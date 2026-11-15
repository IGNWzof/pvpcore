package dev.pvpcoreparty;

import dev.rean.pvpcore.model.Kit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public final class PartyManager {
    private record Invite(Party party, long expires) {}
    private record FightRequest(Party from, String kitId, long expires) {}

    private final PvPCoreParty plugin;
    private final Map<UUID, Party> byPlayer = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Map<UUID, FightRequest> fights = new HashMap<>(); // key = challenged party leader

    public PartyManager(PvPCoreParty plugin) { this.plugin = plugin; }

    public Party get(UUID id) { return byPlayer.get(id); }

    public void broadcast(Party party, String legacy) {
        for (UUID id : party.members()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) plugin.send(p, legacy);
        }
    }

    public Party create(Player p) {
        if (byPlayer.containsKey(p.getUniqueId())) { plugin.send(p, "&cYou are already in a party."); return null; }
        Party party = new Party(p.getUniqueId());
        byPlayer.put(p.getUniqueId(), party);
        plugin.send(p, "&aParty created. Use &e/party invite <player>&a.");
        return party;
    }

    public void invite(Player leader, Player target) {
        Party party = get(leader.getUniqueId());
        if (party == null) party = create(leader);
        if (party == null) return;
        if (!party.isLeader(leader.getUniqueId())) { plugin.send(leader, "&cOnly the party leader can invite."); return; }
        if (target.equals(leader)) { plugin.send(leader, "&cYou cannot invite yourself."); return; }
        if (get(target.getUniqueId()) != null) { plugin.send(leader, "&c" + target.getName() + " is already in a party."); return; }
        if (party.size() >= plugin.getConfig().getInt("max-party-size", 4)) { plugin.send(leader, "&cYour party is full."); return; }
        long timeout = plugin.getConfig().getInt("invite-timeout-seconds", 60) * 1000L;
        invites.put(target.getUniqueId(), new Invite(party, System.currentTimeMillis() + timeout));
        plugin.send(leader, "&aInvited &e" + target.getName() + "&a.");
        plugin.send(target, "&e" + leader.getName() + " &ainvited you to their party.");
        target.sendMessage(Component.text("   [CLICK TO ACCEPT]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/party accept"))
                .hoverEvent(HoverEvent.showText(Component.text("/party accept"))));
    }

    public void accept(Player p) {
        Invite inv = invites.remove(p.getUniqueId());
        if (inv == null || inv.expires() < System.currentTimeMillis()) { plugin.send(p, "&cNo valid party invite."); return; }
        if (get(p.getUniqueId()) != null) { plugin.send(p, "&cLeave your current party first."); return; }
        Party party = inv.party();
        if (get(party.leader()) != party) { plugin.send(p, "&cThat party no longer exists."); return; }
        if (party.size() >= plugin.getConfig().getInt("max-party-size", 4)) { plugin.send(p, "&cThat party is full."); return; }
        party.members().add(p.getUniqueId());
        byPlayer.put(p.getUniqueId(), party);
        broadcast(party, "&e" + p.getName() + " &ajoined the party.");
    }

    public void deny(Player p) {
        if (invites.remove(p.getUniqueId()) != null) plugin.send(p, "&7Invite declined.");
        else plugin.send(p, "&cNo party invite.");
    }

    /** Removes a player from their party (leave/kick/quit). */
    public void removeMember(UUID id, String reason) {
        Party party = byPlayer.remove(id);
        if (party == null) return;
        party.members().remove(id);
        plugin.matches().handleLeave(id);
        Player p = Bukkit.getPlayer(id);
        String name = p != null ? p.getName() : "A player";
        if (party.size() == 0) return;
        if (party.isLeader(id)) party.setLeader(party.members().iterator().next());
        Player newLeader = Bukkit.getPlayer(party.leader());
        broadcast(party, "&e" + name + " &c" + reason + ". &7Leader: &e" + (newLeader != null ? newLeader.getName() : "?"));
        if (party.size() == 1) { // a party of one is pointless
            UUID last = party.leader();
            byPlayer.remove(last);
            party.members().clear();
            Player lp = Bukkit.getPlayer(last);
            if (lp != null) plugin.send(lp, "&7Your party was disbanded.");
        }
    }

    public void leave(Player p) {
        if (get(p.getUniqueId()) == null) { plugin.send(p, "&cYou are not in a party."); return; }
        removeMember(p.getUniqueId(), "left the party");
        plugin.send(p, "&7You left the party.");
    }

    public void kick(Player leader, String name) {
        Party party = get(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) { plugin.send(leader, "&cOnly the party leader can kick."); return; }
        Player t = Bukkit.getPlayerExact(name);
        if (t == null || !party.has(t.getUniqueId()) || t.equals(leader)) { plugin.send(leader, "&cThat player is not in your party."); return; }
        removeMember(t.getUniqueId(), "was kicked");
        plugin.send(t, "&cYou were kicked from the party.");
    }

    public void promote(Player leader, String name) {
        Party party = get(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) { plugin.send(leader, "&cOnly the party leader can promote."); return; }
        Player t = Bukkit.getPlayerExact(name);
        if (t == null || !party.has(t.getUniqueId())) { plugin.send(leader, "&cThat player is not in your party."); return; }
        party.setLeader(t.getUniqueId());
        broadcast(party, "&e" + t.getName() + " &ais now the party leader.");
    }

    public void disband(Player leader) {
        Party party = get(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) { plugin.send(leader, "&cOnly the party leader can disband."); return; }
        broadcast(party, "&cThe party was disbanded.");
        for (UUID id : new ArrayList<>(party.members())) {
            byPlayer.remove(id);
            plugin.matches().handleLeave(id);
        }
        party.members().clear();
    }

    public void list(Player p) {
        Party party = get(p.getUniqueId());
        if (party == null) { plugin.send(p, "&cYou are not in a party."); return; }
        StringBuilder sb = new StringBuilder("&aParty (&e" + party.size() + "&a): ");
        for (UUID id : party.members()) {
            Player m = Bukkit.getPlayer(id);
            String n = m != null ? m.getName() : Bukkit.getOfflinePlayer(id).getName();
            sb.append(party.isLeader(id) ? "&6★" : "&f").append(n).append("&7, ");
        }
        plugin.send(p, sb.toString());
    }

    public void chat(Player p, String msg) {
        Party party = get(p.getUniqueId());
        if (party == null) { plugin.send(p, "&cYou are not in a party."); return; }
        broadcast(party, "&f" + p.getName() + "&7: &f" + msg);
    }

    // ---------- party vs party ----------

    public void fight(Player sender, String targetName, String kitId) {
        Party mine = get(sender.getUniqueId());
        if (mine == null || !mine.isLeader(sender.getUniqueId())) { plugin.send(sender, "&cOnly a party leader can challenge another party."); return; }
        Player target = Bukkit.getPlayerExact(targetName);
        Party theirs = target == null ? null : get(target.getUniqueId());
        if (theirs == null) { plugin.send(sender, "&cThat player is not in a party."); return; }
        if (theirs == mine) { plugin.send(sender, "&cThat is your own party."); return; }
        if (plugin.getConfig().getBoolean("require-equal-size", true) && mine.size() != theirs.size()) {
            plugin.send(sender, "&cParties must be the same size (&e" + mine.size() + "v" + theirs.size() + "&c)."); return;
        }
        if (inMatch(mine) || inMatch(theirs)) { plugin.send(sender, "&cA party is already in a match."); return; }
        Kit kit = resolveKit(kitId);
        if (kit == null) { plugin.send(sender, "&cKit not found."); return; }
        Player theirLeader = Bukkit.getPlayer(theirs.leader());
        if (theirLeader == null) { plugin.send(sender, "&cThat party's leader is offline."); return; }
        long timeout = plugin.getConfig().getInt("fight-timeout-seconds", 60) * 1000L;
        fights.put(theirs.leader(), new FightRequest(mine, kit.id(), System.currentTimeMillis() + timeout));
        plugin.send(sender, "&aChallenge sent to &e" + theirLeader.getName() + "&a's party. Kit: &e" + kit.displayName());
        plugin.send(theirLeader, "&e" + sender.getName() + "&a's party (&e" + mine.size() + "v" + theirs.size() + "&a) challenged you. Kit: &e" + kit.displayName());
        theirLeader.sendMessage(Component.text("   [CLICK TO ACCEPT]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/party acceptfight")));
    }

    public void acceptFight(Player p) {
        Party mine = get(p.getUniqueId());
        if (mine == null || !mine.isLeader(p.getUniqueId())) { plugin.send(p, "&cOnly a party leader can accept."); return; }
        FightRequest req = fights.remove(p.getUniqueId());
        if (req == null || req.expires() < System.currentTimeMillis()) { plugin.send(p, "&cNo valid challenge."); return; }
        Party from = req.from();
        if (get(from.leader()) != from || from.size() == 0) { plugin.send(p, "&cThat party no longer exists."); return; }
        if (plugin.getConfig().getBoolean("require-equal-size", true) && from.size() != mine.size()) {
            plugin.send(p, "&cParty sizes changed, challenge cancelled."); return;
        }
        if (inMatch(from) || inMatch(mine)) { plugin.send(p, "&cA party is already in a match."); return; }
        Kit kit = resolveKit(req.kitId());
        if (kit == null) { plugin.send(p, "&cKit no longer exists."); return; }
        if (!plugin.matches().start(from, mine, kit)) {
            broadcast(from, "&cCould not start the match (no free arena or a player is busy).");
            broadcast(mine, "&cCould not start the match (no free arena or a player is busy).");
        }
    }

    public void denyFight(Player p) {
        if (fights.remove(p.getUniqueId()) != null) plugin.send(p, "&7Challenge declined.");
        else plugin.send(p, "&cNo challenge.");
    }

    private boolean inMatch(Party party) {
        for (UUID id : party.members()) if (plugin.matches().inMatch(id)) return true;
        return false;
    }

    private Kit resolveKit(String id) {
        if (id == null || id.isBlank()) id = plugin.getConfig().getString("default-kit", "");
        if (id == null || id.isBlank()) return plugin.core().kitManager().getFirstOrNull();
        return plugin.core().kitManager().get(id);
    }
}
