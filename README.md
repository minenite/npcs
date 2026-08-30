# npcs

WarZ civilian player-NPCs for the Minenite network. They use the **DirtyCivilian** skin, show in the tab list, wander like people (not A* bots), hold a loaded pistol, talk through a small local LLM, aim back if you put a gun on them, and leave a lootable corpse when they die.

## Command

```
/npc spawn civilian
/npc remove near
/npc remove all
/npc list
```

Ops only (`npcs.admin`).

## Loadout

Civilians roll a random WarZ pistol plus a seated mag and a spare. Needs WarzPlugin on the same server.

## Talk

Chat goes through Ollama at `127.0.0.1:11434` (`phi3:mini` by default). Each civilian has a written personality and a tight system prompt so lines stay short, in-world, and never “AI assistant.” If Ollama is down they still speak from that personality’s lines.

## Corpse

Death uses the same Paper `Mannequin` sleeping body as WarZ player corpses: skin, worn pistol, loot chest.
