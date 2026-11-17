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

    public PartyManager(PvPCoreParty plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 20L, 20L);
    }

    private String nameOf(UUID id) {
        Player o = Bukkit.getPlayer(id);
        if (o != null) return o.getName();
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n != null ? n : "?";
    }

    private boolean valid(Party party) { return byPlayer.get(party.leader()) == party; }

    /** Drops expired invites / challenges and tells the people involved. */
    private void expire() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Invite>> it = invites.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Invite> en = it.next();
            if (en.getValue().expires() >= now) continue;
            it.remove();
            Party party = en.getValue().party();
            Player target = Bukkit.getPlayer(en.getKey());
            Player leader = Bukkit.getPlayer(party.leader());
            if (target != null) plugin.send(target, "&7The party invite from &e" + (leader != null ? leader.getName() : "?") + " &7expired.");
            if (leader != null && valid(party)) plugin.send(leader, "&7Your invite to &e" + (target != null ? target.getName() : "?") + " &7expired.");
        }
        Iterator<Map.Entry<UUID, FightRequest>> fi = fights.entrySet().iterator();
        while (fi.hasNext()) {
            Map.Entry<UUID, FightRequest> en = fi.next();
            if (en.getValue().expires() >= now) continue;
            fi.remove();
            Party from = en.getValue().from();
            Player l = Bukkit.getPlayer(from.leader());
            if (l != null && valid(from)) plugin.send(l, "&7Your party battle challenge expired.");
        }
    }

    /** Called when a player disconnects: forget invites/challenges addressed to them. */
    public void forget(UUID id) {
        invites.remove(id);
        fights.remove(id);
    }

    public Party get(UUID id) { return byPlayer.get(id); }

    public void broadcast(Party party, String legacy) {
        for (UUID id : party.members()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) plugin.send(p, legacy);
        }
    }

    public Party create(Player p) {
        if (byPlayer.containsKey(p.getUniqueId())) { plugin.send(p, "&cYou are already in a party. Leave it first."); return null; }
        if (plugin.core().duelManager().isInMatch(p.getUniqueId()) || plugin.matches().inMatch(p.getUniqueId())) {
            plugin.send(p, "&cYou can't create a party during a match."); return null;
        }
        plugin.core().queueManager().leaveAllQuiet(p.getUniqueId());
        Party party = new Party(p.getUniqueId());
        byPlayer.put(p.getUniqueId(), party);
        plugin.send(p, "&aParty created. Use &e/party invite <player>&a.");
        plugin.title(p, "&d&lPARTY", "&7/party invite <player> to add friends");
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
        plugin.send(target, "&e" + leader.getName() + " &ainvited you to their party. &7(expires in " + plugin.getConfig().getInt("invite-timeout-seconds", 60) + "s)");
        target.sendMessage(Component.text("   [CLICK TO ACCEPT]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/party accept"))
                .hoverEvent(HoverEvent.showText(Component.text("/party accept"))));
    }

    public void accept(Player p) {
        Invite inv = invites.remove(p.getUniqueId());
        if (inv == null || inv.expires() < System.currentTimeMillis()) { plugin.send(p, "&cNo valid party invite."); return; }
        if (get(p.getUniqueId()) != null) { plugin.send(p, "&cLeave your current party first."); return; }
        if (plugin.core().duelManager().isInMatch(p.getUniqueId()) || plugin.matches().inMatch(p.getUniqueId())) {
            plugin.send(p, "&cYou can't join a party during a match."); return;
        }
        Party party = inv.party();
        if (get(party.leader()) != party) { plugin.send(p, "&cThat party no longer exists."); return; }
        if (party.size() >= plugin.getConfig().getInt("max-party-size", 4)) { plugin.send(p, "&cThat party is full."); return; }
        plugin.core().queueManager().leaveAllQuiet(p.getUniqueId());
        party.members().add(p.getUniqueId());
        byPlayer.put(p.getUniqueId(), party);
        broadcast(party, "&e" + p.getName() + " &ajoined the party.");
        plugin.title(p, "&d&lPARTY", "&7You joined &e" + nameOf(party.leader()) + "&7's party");
    }

    public void deny(Player p) {
        if (invites.remove(p.getUniqueId()) != null) plugin.send(p, "&7Invite declined.");
        else plugin.send(p, "&cNo party invite.");
    }

    /** Breaks the party up. {@code forfeiting} are the players who also drop out of an ongoing team battle. */
    private void dissolve(Party party, Collection<UUID> forfeiting, String message) {
        broadcast(party, message);
        List<UUID> ids = new ArrayList<>(party.members());
        for (UUID id : ids) byPlayer.remove(id);
        party.members().clear();
        invites.values().removeIf(i -> i.party() == party);
        fights.values().removeIf(f -> f.from() == party);
        fights.remove(party.leader());
        for (UUID id : forfeiting) plugin.matches().handleLeave(id);
    }

    /**
     * A player leaves / is kicked / disconnects. If it is the leader the whole party is disbanded,
     * if it is just a member only that member is removed.
     */
    public void removeMember(UUID id, String reason) {
        Party party = byPlayer.get(id);
        if (party == null) return;
        Player p = Bukkit.getPlayer(id);
        String name = p != null ? p.getName() : "A player";
        if (party.isLeader(id)) {
            dissolve(party, List.of(id), "&e" + name + " &c" + reason + ". &cThe party was disbanded.");
            return;
        }
        byPlayer.remove(id);
        party.members().remove(id);
        plugin.matches().handleLeave(id);
        broadcast(party, "&e" + name + " &c" + reason + ".");
    }

    /** Leave (members) or disband (leader). Menus/commands ask for confirmation before calling this. */
    public void leave(Player p) {
        Party party = get(p.getUniqueId());
        if (party == null) { plugin.send(p, "&cYou are not in a party."); return; }
        if (party.isLeader(p.getUniqueId())) { disband(p); return; }
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
        dissolve(party, new ArrayList<>(party.members()), "&cThe party was disbanded.");
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

    // ---------- party FFA (own members only) ----------

    public void startFFA(Player leader, String kitId) {
        Party party = get(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) { plugin.send(leader, "&cOnly the party leader can start a party battle."); return; }
        int min = Math.max(2, plugin.getConfig().getInt("gamemodes.ffa.min-players", 2));
        if (party.size() < min) { plugin.send(leader, "&cYou need at least &e" + min + " &cparty members for FFA."); return; }
        if (inMatch(party)) { plugin.send(leader, "&cYour party is already in a match."); return; }
        Kit kit = resolveKit(kitId);
        if (kit == null) { plugin.send(leader, "&cKit not found."); return; }
        if (!plugin.matches().startFFA(party, kit)) {
            broadcast(party, "&cCould not start the FFA (no free arena, or a player is offline/busy).");
        }
    }

    /** Kit ids allowed for a gamemode's menu ("team" or "ffa"); empty config list = every PvPCore kit. */
    public List<Kit> allowedKits(String mode) {
        List<String> ids = plugin.getConfig().getStringList("gamemodes." + mode + ".kits");
        List<Kit> all = plugin.core().kitManager().list();
        if (ids == null || ids.isEmpty()) return all;
        List<Kit> out = new ArrayList<>();
        for (Kit k : all) if (ids.contains(k.id())) out.add(k);
        return out.isEmpty() ? all : out;
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

    // ---------- helpers for menus ----------

    public Collection<Party> allParties() { return new LinkedHashSet<>(byPlayer.values()); }

    /** Name of the leader who invited this player (valid invite only), else null. */
    public String pendingInviteFrom(UUID id) {
        Invite i = invites.get(id);
        if (i == null || i.expires() < System.currentTimeMillis() || get(i.party().leader()) != i.party()) return null;
        Player l = Bukkit.getPlayer(i.party().leader());
        return l != null ? l.getName() : null;
    }

    /** Name of the leader who challenged this party leader (valid challenge only), else null. */
    public String pendingFightFrom(UUID leaderId) {
        FightRequest r = fights.get(leaderId);
        if (r == null || r.expires() < System.currentTimeMillis() || get(r.from().leader()) != r.from()) return null;
        Player l = Bukkit.getPlayer(r.from().leader());
        return l != null ? l.getName() : null;
    }

    public boolean inMatch(UUID id) { return plugin.matches().inMatch(id); }
}
