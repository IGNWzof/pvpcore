package dev.pvpcoreparty;

import dev.rean.pvpcore.model.Arena;
import dev.rean.pvpcore.model.Kit;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** A rounds-based red-vs-blue team fight (2v2, 3v3, 4v4...) played in a PvPCore arena with a PvPCore kit. */
public final class TeamMatch {
    public enum State { COUNTDOWN, FIGHTING, BETWEEN, ENDED }

    private final PvPCoreParty plugin;
    private final MatchManager manager;
    private final Arena arena;
    private final Kit kit;
    private final List<UUID> red;
    private final List<UUID> blue;
    private final Set<UUID> present = new HashSet<>(); // still in the match
    private final Set<UUID> alive = new HashSet<>();   // alive this round
    private final Set<UUID> everyone = new HashSet<>();
    private final int firstTo;
    private int redWins, blueWins;
    private State state = State.BETWEEN;
    private BukkitTask task;

    TeamMatch(PvPCoreParty plugin, MatchManager manager, Arena arena, Kit kit, List<UUID> red, List<UUID> blue) {
        this.plugin = plugin;
        this.manager = manager;
        this.arena = arena;
        this.kit = kit;
        this.red = red;
        this.blue = blue;
        present.addAll(red);
        present.addAll(blue);
        everyone.addAll(present);
        this.firstTo = Math.max(1, plugin.getConfig().getInt("rounds-to-win", 3));
    }

    public State state() { return state; }
    public Kit kit() { return kit; }
    public boolean isRed(UUID id) { return red.contains(id); }
    public boolean sameTeam(UUID a, UUID b) { return (isRed(a) && isRed(b)) || (blue.contains(a) && blue.contains(b)); }
    public boolean isAlive(UUID id) { return alive.contains(id); }
    public Location spectatorSpawn() { return arena.redSpawn(); }

    // ---------------- flow ----------------

    void begin() {
        broadcast("&aTeam battle: &c" + red.size() + " &7vs &9" + blue.size() + " &7| Kit: &e" + kit.displayName()
                + " &7| Arena: &e" + arena.displayName() + " &7| First to &e" + firstTo);
        startRound();
    }

    private void startRound() {
        if (state == State.ENDED) return;
        alive.clear();
        for (UUID id : present) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            alive.add(id);
            prep(p, isRed(id) ? arena.redSpawn() : arena.blueSpawn());
        }
        state = State.COUNTDOWN;
        int[] left = { Math.max(1, plugin.core().settings().countdownSeconds()) };
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != State.COUNTDOWN) { task.cancel(); return; }
            if (left[0] <= 0) {
                state = State.FIGHTING;
                bar("&a&lFIGHT!");
                task.cancel();
                return;
            }
            bar("&e" + left[0]);
            left[0]--;
        }, 0L, 20L);
    }

    private void prep(Player p, Location spawn) {
        p.setGameMode(kit.gameMode() != null ? kit.gameMode() : GameMode.SURVIVAL);
        applyKit(p);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max != null ? max.getValue() : 20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        for (var e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        if (spawn != null) p.teleport(spawn);
    }

    private void applyKit(Player p) {
        Kit k = plugin.core().kitManager().getEffectiveKit(p.getUniqueId(), kit); // respects player's personal kit layout
        if (k == null) k = kit;
        PlayerInventory inv = p.getInventory();
        inv.clear();
        ItemStack[] c = k.contents();
        if (c != null) for (int i = 0; i < Math.min(c.length, 36); i++) inv.setItem(i, c[i] == null ? null : c[i].clone());
        ItemStack[] a = k.armor();
        if (a != null) {
            ItemStack[] copy = new ItemStack[a.length];
            for (int i = 0; i < a.length; i++) copy[i] = a[i] == null ? null : a[i].clone();
            inv.setArmorContents(copy);
        }
        ItemStack off = k.offhand();
        if (off != null) inv.setItemInOffHand(off.clone());
    }

    /** Called from the death listener. */
    public void handleDeath(Player victim) {
        if (state != State.FIGHTING || !alive.remove(victim.getUniqueId())) return;
        Player killer = victim.getKiller();
        broadcast("&7" + (isRed(victim.getUniqueId()) ? "&c" : "&9") + victim.getName() + " &7was eliminated"
                + (killer != null ? " by &f" + killer.getName() : "") + "&7.");
        checkRoundEnd();
    }

    public void makeSpectator(Player p) {
        if (state != State.ENDED && !alive.contains(p.getUniqueId())) p.setGameMode(GameMode.SPECTATOR);
    }

    void handleQuit(UUID id) {
        if (!present.remove(id)) return;
        alive.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (p != null) restore(p);
        manager.unregister(this, List.of(id));
        if (state == State.ENDED) return;
        boolean redLeft = red.stream().anyMatch(present::contains);
        boolean blueLeft = blue.stream().anyMatch(present::contains);
        if (!redLeft || !blueLeft) {
            finish(redLeft);
        } else if (state == State.FIGHTING) {
            checkRoundEnd();
        }
    }

    private void checkRoundEnd() {
        if (state != State.FIGHTING) return;
        boolean redAlive = red.stream().anyMatch(alive::contains);
        boolean blueAlive = blue.stream().anyMatch(alive::contains);
        if (redAlive && blueAlive) return;
        boolean redWon = redAlive;
        if (redWon) redWins++; else blueWins++;
        broadcast((redWon ? "&c&lRED" : "&9&lBLUE") + " &fwon the round! &7(&c" + redWins + " &7- &9" + blueWins + "&7)");
        if (redWins >= firstTo || blueWins >= firstTo) {
            finish(redWon);
        } else {
            state = State.BETWEEN;
            Bukkit.getScheduler().runTaskLater(plugin, this::startRound, 60L);
        }
    }

    private void finish(boolean redWon) {
        if (state == State.ENDED) return;
        state = State.ENDED;
        if (task != null) task.cancel();
        broadcast("&e&lMATCH OVER! " + (redWon ? "&c&lRED" : "&9&lBLUE") + " &ewins &7(&c" + redWins + " &7- &9" + blueWins + "&7)");
        Bukkit.getScheduler().runTaskLater(plugin, this::cleanup, 60L);
    }

    public void forceEnd() {
        if (state == State.ENDED) { cleanup(); return; }
        state = State.ENDED;
        if (task != null) task.cancel();
        cleanup();
    }

    private boolean cleaned;

    private void cleanup() {
        if (cleaned) return;
        cleaned = true;
        for (UUID id : new ArrayList<>(present)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) restore(p);
        }
        manager.unregister(this, everyone);
        try {
            plugin.core().arenaManager().regenArenaAsync(arena, () -> arena.setInUse(false));
        } catch (Throwable t) {
            arena.setInUse(false);
        }
    }

    private void restore(Player p) {
        p.setGameMode(GameMode.SURVIVAL);
        p.getInventory().clear();
        for (var e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        if (!p.isDead()) p.setHealth(max != null ? max.getValue() : 20.0);
        p.setFoodLevel(20);
        p.setFireTicks(0);
        Location lobby = plugin.core().settings().mainSpawn();
        if (lobby != null) p.teleport(lobby);
        plugin.core().lobbyItems().giveLobbyItems(p);
    }

    // ---------------- helpers ----------------

    private void broadcast(String legacy) {
        for (UUID id : everyone) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) plugin.send(p, legacy);
        }
    }

    private void bar(String legacy) {
        for (UUID id : present) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendActionBar(PvPCoreParty.color(legacy));
        }
    }
}
