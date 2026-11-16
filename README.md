# PvPCoreParty (addon for PvPCore 1.0.0)

Build:   unzip, cd PvPCoreParty, then: mvn -q package   (PvPCore jar is already in libs/)
Output:  target/PvPCoreParty-1.0.0.jar  -> put in plugins/ next to PvPCore-1_0_0.jar

Commands
  /party create | invite <p> | accept | deny | leave | kick <p> | promote <p> | disband | list
  /party chat <msg>  or  /pc <msg>
  /party fight <player-in-other-party> [kit]   -> party vs party team battle (2v2, 3v3, 4v4)
  /party acceptfight | denyfight

Config: plugins/PvPCoreParty/config.yml (party size, rounds-to-win, equal-size rule, default kit)

v1.1: party horn (goat horn) in hotbar slot 2 -> opens the Party menu (create / invite / members / party battle / leave / disband).
Disband (barrier) asks for confirmation. While in a party you cannot queue (queue item/GUI and /queue are blocked)
and cannot join another party until you leave your current one.

v1.2:
- Invite GUI removed: invite with /party invite <player> or /p invite <player> (invite expires after 60s, see config).
- Disband and Leave ask for confirmation: click the CAKE in the sub-menu (also /party leave and /party disband).
- If the party leader leaves/quits the party is disbanded; if a plain member leaves only that member is removed.
- Server-wide (PvPCore duels AND party battles): during countdown / between rounds / after the win nothing can be
  used, thrown, dropped, placed or eaten. Ender pearls vanish when the owner quits, when the round/match ends, and on server restart.
