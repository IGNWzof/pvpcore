package dev.pvpcoreparty;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.Bukkit;

public final class PartyListener implements Listener {
    private final PvPCoreParty plugin;

    public PartyListener(PvPCoreParty plugin) { this.plugin = plugin; }

    private TeamMatch match(Player p) { return plugin.matches().get(p.getUniqueId()); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        TeamMatch m = match(p);
        if (m == null) return;
        if (m.state() != TeamMatch.State.FIGHTING || !m.isAlive(p.getUniqueId())) { e.setCancelled(true); return; }
        if (e instanceof EntityDamageByEntityEvent de) {
            Player damager = null;
            if (de.getDamager() instanceof Player dp) damager = dp;
            else if (de.getDamager() instanceof org.bukkit.entity.Projectile proj) {
                ProjectileSource src = proj.getShooter();
                if (src instanceof Player sp) damager = sp;
            }
            if (damager != null && !damager.equals(p) && m.sameTeam(damager.getUniqueId(), p.getUniqueId())) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        TeamMatch m = match(p);
        if (m == null) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.setKeepInventory(false);
        e.deathMessage(null);
        m.handleDeath(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && p.isDead()) p.spigot().respawn();
        }, 1L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        TeamMatch m = match(p);
        if (m == null) return;
        Location loc = m.spectatorSpawn();
        if (loc != null) e.setRespawnLocation(loc);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) m.makeSpectator(p); }, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.parties().removeMember(e.getPlayer().getUniqueId(), "disconnected");
        plugin.matches().handleLeave(e.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        TeamMatch m = match(e.getPlayer());
        if (m == null || m.state() != TeamMatch.State.COUNTDOWN) return;
        Location from = e.getFrom(), to = e.getTo();
        if (to != null && (from.getX() != to.getX() || from.getZ() != to.getZ())) {
            e.setTo(from.clone().setDirection(to.getDirection()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        TeamMatch m = match(e.getPlayer());
        if (m == null) return;
        if (!m.kit().canBreakBlock(e.getBlock().getType())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        TeamMatch m = match(p);
        if (m == null || m.kit().naturalRegen()) return;
        if (e.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED
                || e.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (match(e.getPlayer()) == null || plugin.core().settings().allowCommandsInMatch()) return;
        String cmd = e.getMessage().substring(1).split(" ")[0].toLowerCase();
        if (cmd.equals("party") || cmd.equals("p") || cmd.equals("pc") || cmd.equals("partychat")) return;
        e.setCancelled(true);
        plugin.send(e.getPlayer(), "&cYou can't use commands during a team battle.");
    }
}
