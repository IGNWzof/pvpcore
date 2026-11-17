# PvPCoreParty (addon for PvPCore 1.0.0)

Build:
    unzip PvPCoreParty.zip
    cd PvPCoreParty
    mvn -q package
Output: target/PvPCoreParty-1.0.0.jar  -> put in plugins/ next to PvPCore-1_0_0.jar
(PvPCore-1_0_0.jar is already bundled under libs/.)

## Commands
/party create
/party invite <player>   (/party add, /p invite)   - expires after invite-timeout-seconds (default 60s)
/party accept | deny
/party leave             - opens a confirm menu (click the cake). "/party leave confirm" skips it.
/party kick <player> | promote <player>
/party disband           - opens a confirm menu (click the cake). "/party disband confirm" skips it.
/party list | /party chat <msg> (or /pc <msg>) | /party menu
/party battle            - opens the gamemode select menu (Team Battle / Party FFA)
/party fight <player in other party> [kit]   - challenge another party to a Team Battle
/party acceptfight | denyfight
/party ffa [kit]         - start a Party FFA with your own members directly (skips the menu)

## Party item (hotbar slot 2)
Whenever a player is in the PvPCore lobby, slot 2 holds a party item next to PvPCore's own queue item:
- Not in a party, or a regular member -> a GOAT HORN (right-click opens the party menu: create/join/leave/members).
- The party LEADER -> a LECTERN instead. Only the leader ever receives it, and it is the only way to reach the
  full leader menu (invite, members, Party Battle, disband). The item swaps automatically the moment someone
  becomes/stops being a leader. Neither item can be dropped, moved to another slot, or used for anything else,
  and it disappears once the player enters a duel or a party battle.

## Party rules
- Leaving a party or disbanding always goes through a confirm menu with a CAKE the player must click.
- Party LEADER leaves/disconnects -> the whole party is disbanded. Normal MEMBER leaves/is kicked/disconnects
  -> only that member is removed, the party continues.
- While in a party you cannot queue (PvPCore's queue item, its GUI, and /queue are all blocked).
- You cannot accept an invite or join another party while already in one, or while in a match.

## Party Battle - gamemode select (like mcpvp.club's)
The leader opens /party battle (or the Lectern -> Party Battle) and picks a mode:
- **Team Battle**: challenge another party. Both parties get a RED vs BLUE title, split into teams the same
  way PvPCore splits duelists, fight in a PvPCore arena with a chosen kit, single round - first team fully
  eliminated loses. No friendly fire.
- **Party FFA**: every member of YOUR OWN party fights each other (needs gamemodes.ffa.min-players, default 2).
  Single round, last player standing wins, "FREE FOR ALL" title shown to everyone.
Both modes:
  - use PvPCore's own arenas (arenaManager) and kits (kitManager, including each player's personal kit layout),
  - show a title/subtitle at the start and at the end (winner announcement), the same way PvPCore shows titles
    for its own duels,
  - regenerate the arena through PvPCore's own regenArenaAsync() once the match ends - exactly like a normal
    PvPCore duel does, instead of a custom implementation.
  - Kit list is configurable: config.yml's gamemodes.team.kits / gamemodes.ffa.kits - leave empty to show every
    PvPCore kit (same list the PvPCore queue menu uses), or list specific kit ids to restrict that menu.

NOTE / limitation: PvPCore's Arena class only exposes two fixed spawn points (red/blue). Team Battle uses them
directly. Party FFA (which can have more than 2 players) spreads players in a small circle around the midpoint
of those two spawns since there's no "FFA spawn ring" in PvPCore's arena data - this is a best-effort stand-in,
not a real PvPCore feature, so test it with your actual arenas and tell me if spawns end up too close/clipped.
I could not find a "door" API on PvPCore's compiled Arena/DuelMatch classes to copy (no source, class files
only) - players are instead frozen in place during the countdown (same effect: you can't leave your spawn
until the fight starts), just not an actual door block opening.

## Server-wide match rules (PvPCore 1v1 duels AND party battles, both modes)
- During the countdown, between rounds, and after a match ends: no using/throwing/dropping/placing/breaking/
  eating anything.
- Ender pearls only exist while their owner is actually fighting: they vanish if the owner disconnects (the
  match ends the same way it normally would), when the round/match ends, and any pearl that survived a server
  restart is removed right after the server finishes starting.

Config: plugins/PvPCoreParty/config.yml
    prefix, max-party-size, invite-timeout-seconds, fight-timeout-seconds, require-equal-size, default-kit,
    gamemodes.team.kits, gamemodes.ffa.kits, gamemodes.ffa.min-players
