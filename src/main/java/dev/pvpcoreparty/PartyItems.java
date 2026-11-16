package dev.pvpcoreparty;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** The party horn that sits in hotbar slot 2 of the lobby. */
public final class PartyItems {
    public static final int SLOT = 1; // slot 2 (0-indexed)

    private final PvPCoreParty plugin;
    private final NamespacedKey key;

    public PartyItems(PvPCoreParty plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "party_item");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    public ItemStack create() {
        ItemStack it = new ItemStack(Material.GOAT_HORN);
        ItemMeta m = it.getItemMeta();
        m.displayName(PvPCoreParty.plain("&d🎺 &5Party &7(Right Click)"));
        m.lore(List.of(PvPCoreParty.plain("&7Create or manage your party."),
                PvPCoreParty.plain("&7Invite friends and fight other parties.")));
        m.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public boolean isPartyItem(ItemStack it) {
        return it != null && it.getType() != Material.AIR && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Keeps the horn in slot 2 for players who are in lobby mode (they hold PvPCore's queue item in slot 1). */
    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = p.getInventory();
            ItemStack first = inv.getItem(0);
            boolean lobby = first != null && first.getType() != Material.AIR
                    && plugin.core().lobbyItems().isLobbyItem(first)
                    && !plugin.matches().inMatch(p.getUniqueId())
                    && !plugin.core().duelManager().isInMatch(p.getUniqueId());
            ItemStack cur = inv.getItem(SLOT);
            boolean hasHorn = isPartyItem(cur);
            if (lobby && !hasHorn && (cur == null || cur.getType() == Material.AIR)) {
                inv.setItem(SLOT, create());
            } else if (!lobby && hasHorn) {
                inv.setItem(SLOT, null);
            }
        }
    }
}
