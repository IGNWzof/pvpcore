package dev.pvpcoreparty;

import dev.rean.pvpcore.model.Arena;
import dev.rean.pvpcore.model.Kit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public final class MatchManager {
    private final PvPCoreParty plugin;
    private final Map<UUID, TeamMatch> byPlayer = new HashMap<>();
    private final Set<TeamMatch> active = new HashSet<>();

    public MatchManager(PvPCoreParty plugin) { this.plugin = plugin; }

    public TeamMatch get(UUID id) { return byPlayer.get(id); }
    public boolean inMatch(UUID id) { return byPlayer.containsKey(id); }

    public boolean start(Party a, Party b, Kit kit) {
        List<UUID> red = new ArrayList<>(a.members());
        List<UUID> blue = new ArrayList<>(b.members());
        List<UUID> all = new ArrayList<>(red);
        all.addAll(blue);
        for (UUID id : all) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) return false;
            if (plugin.core().duelManager().isInMatch(id) || byPlayer.containsKey(id)) return false;
        }
        Optional<Arena> arena = plugin.core().arenaManager().findAvailable();
        if (arena.isEmpty()) return false;
        Arena ar = arena.get();
        plugin.core().arenaManager().markUsed(ar);
        for (UUID id : all) plugin.core().queueManager().leaveAllQuiet(id);
        TeamMatch m = new TeamMatch(plugin, this, ar, kit, red, blue);
        active.add(m);
        for (UUID id : all) byPlayer.put(id, m);
        m.begin();
        return true;
    }

    /** Called by TeamMatch when it is fully finished. */
    void unregister(TeamMatch m, Collection<UUID> players) {
        active.remove(m);
        for (UUID id : players) byPlayer.remove(id, m);
    }

    /** A player quit / left their party while possibly in a match. */
    public void handleLeave(UUID id) {
        TeamMatch m = byPlayer.get(id);
        if (m != null) m.handleQuit(id);
    }

    public void endAll() {
        for (TeamMatch m : new ArrayList<>(active)) m.forceEnd();
    }
}
