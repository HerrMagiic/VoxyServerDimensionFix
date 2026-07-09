# Voxy Dynamic Area Fix

A Fabric client addon for [Voxy](https://modrinth.com/mod/voxy) that lets you keep separate, named sets of distant-terrain (LOD) data per server area instead of Voxy treating an entire server as one single world.

Voxy normally caches a server's distant terrain in one shared storage bucket per world/dimension. On servers with many unrelated areas on the same dimension (hub worlds, minigame lobbies, instanced zones, etc.), that means chunks from completely different places all get mixed into the same cache, and switching between areas can show stale or "ghost" terrain that doesn't belong there. This addon adds an in-game screen for saving, loading, renaming, and deleting distinct named storage buckets per server, so each area's LOD data stays isolated from the others.

![alt text](image.png)

**This mod was written by an AI coding assistant (Claude, via Claude Code)** in collaboration with the repository owner, who directed the design and tested every change in-game.

## Features

- Detects the connected server's IP and derives a default per-dimension storage key automatically.
- An in-game screen (own keybind) to save, load, rename, and delete named per-server storage areas.
- Forces a Voxy renderer reload whenever the active storage area changes.

## Installation

1. Build the mod with `gradle build`.
2. Place the generated JAR (`build/libs/voxy-dynamic-addon-*.jar`) into your Fabric `mods` folder.
3. Ensure `Voxy` and Fabric API are also installed, matching the versions declared in `build.gradle`.

## Usage

Bind `Open Voxy Dynamic Areas` (under the "Voxy Dynamic" category in Controls) to open the screen while connected to a server. From there:

- Type a name and click **Save Current As** to save the currently-detected area under that name and activate it immediately.
- Click **Load** next to a saved name to switch to it. A chat message announces a short countdown (a few seconds) before the switch actually takes effect, giving you a window to click Load on a different save instead if you picked the wrong one. Once it takes effect, that save stays active — and keeps capturing whatever you explore — until you change dimension, disconnect, or activate something else.
- Click **Rename** to change a save's display name without touching its stored LOD data (the underlying storage bucket doesn't change, only the label).
- Click **Delete** to remove a saved name (this also deactivates it first if it was the active one).
- Click **Use Default Storage** to immediately stop using any named save and fall back to the normal per-dimension storage.

An active named save is **session-only**: it's never remembered across joins, and it automatically clears the moment you change dimension (e.g. Overworld → Nether) — falling back to normal per-dimension storage — so wandering away from a save can't silently attribute the wrong area's chunks to it. The currently active name is marked with `●` in the list.

## Configuration

Named saves are stored in `config/voxy_dynamic_addon.json`, per server IP:

```json
{
  "namedAreaOverrides": {
    "play.hypixel.net": {
      "skyblock": "skyblock",
      "bedwars": "bedwars"
    }
  }
}
```

## Notes

- This addon is client-only.
- Each named save gets its own storage bucket based on the name you gave it when saving, so different names never share LOD data.
- Without any active named save, the current dimension is used as the storage key by default.
