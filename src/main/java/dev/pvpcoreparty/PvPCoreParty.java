package dev.pvpcoreparty;

import dev.rean.pvpcore.PvPCorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class PvPCoreParty extends JavaPlugin {
    private PvPCorePlugin core;
    private PartyManager parties;
    private MatchManager matches;
    private PartyMenus menus;
    private PartyItems items;
    private MatchGuard guard;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        core = PvPCorePlugin.getInstance();
        if (core == null) {
            getLogger().severe("PvPCore is not loaded. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        matches = new MatchManager(this);
        parties = new PartyManager(this);
        menus = new PartyMenus(this);
        items = new PartyItems(this);
        PartyCommand cmd = new PartyCommand(this);
        getCommand("party").setExecutor(cmd);
        getCommand("party").setTabCompleter(cmd);
        getCommand("partychat").setExecutor(cmd);
        getServer().getPluginManager().registerEvents(new PartyListener(this), this);
        guard = new MatchGuard(this);
        getServer().getPluginManager().registerEvents(guard, this);
    }

    @Override
    public void onDisable() {
        if (matches != null) matches.endAll();
        if (guard != null) guard.shutdown();
    }

    public PvPCorePlugin core() { return core; }
    public PartyManager parties() { return parties; }
    public MatchManager matches() { return matches; }
    public PartyMenus menus() { return menus; }
    public PartyItems items() { return items; }

    public static Component color(String legacy) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy);
    }

    /** Legacy-colored component with italics turned off (for item names/lore). */
    public static Component plain(String legacy) {
        return color(legacy).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    public void send(CommandSender to, String legacy) {
        to.sendMessage(color(getConfig().getString("prefix", "") + legacy));
    }
}
