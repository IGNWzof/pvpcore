# PvPCoreParty (addon for PvPCore 1.0.0)

Build:   mvn -q package        (needs JDK 21 + internet for paper-api)
Output:  target/PvPCoreParty-1.0.0.jar  -> put in plugins/ next to PvPCore-1_0_0.jar

Commands
  /party create | invite <p> | accept | deny | leave | kick <p> | promote <p> | disband | list
  /party chat <msg>  or  /pc <msg>
  /party fight <player-in-other-party> [kit]   -> party vs party team battle (2v2, 3v3, 4v4)
  /party acceptfight | denyfight

Config: plugins/PvPCoreParty/config.yml (party size, rounds-to-win, equal-size rule, default kit)
