# GoAFK
## Summary
A server side fabric mod that allows the player to replace themselves with an AFK anchor that simulates the world in the same way the player does.
## Features
- AFK anchors, represented in game by a name tag and particles, force loads chunks and simulates vanilla mob spawning as if the player was there.
- /afk - replaces player with AFK anchor in online world. Rejoining removes the anchor. Available to all players.
- /afk anchor add - Add an anchor at a position in world with an optional name. Unnamed anchors are given their coordinates as their default name.
- /afk anchor remove - Remove an anchor by name (position if unnamed), or all of them.
- All /afk subcommands are op only.
- AFK anchors survive server restarts.
## Limitations
- AFK anchors do not prevent the server from pausing. To disable server pause, set pause-when-empty-seconds in server.properties to -1.
- Phantoms don't target AFK anchors. They want real meat.