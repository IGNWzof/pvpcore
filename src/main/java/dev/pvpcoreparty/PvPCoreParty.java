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
        PartyCommand cmd = new PartyCommand(this);
        getCommand("party").setExecutor(cmd);
        getCommand("party").setTabCompleter(cmd);
        getCommand("partychat").setExecutor(cmd);
        getServer().getPluginManager().registerEvents(new PartyListener(this), this);
    }

    @Override
    public void onDisable() {
        if (matches != null) matches.endAll();
    }

    public PvPCorePlugin core() { return core; }
    public PartyManager parties() { return parties; }
    public MatchManager matches() { return matches; }

    public static Component color(String legacy) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy);
    }

    public void send(CommandSender to, String legacy) {
        to.sendMessage(color(getConfig().getString("prefix", "") + legacy));
    }
}
