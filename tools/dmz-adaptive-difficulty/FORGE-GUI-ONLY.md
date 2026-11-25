# Forge-only Legacy Mechanics GUI (CustomNPCs)

Legacy Mechanics **4.5.3+** can run with **only** `mods/LegacyMechanics-*.jar`. The Bukkit plugin
`LegacyMechanicsGUI` is **optional** and should be **removed** once CNPC UI is verified on live.

## Requirements

- **CustomNPCs** on server **and** player clients (GBPort build used in `build.sh`).
- `config/legacymechanics.json`:

```json
"guiBackend": "cnpc"
```

- `/lm admin reload` after config change.

## Player commands (Forge brigadier)

These register on the Forge mod. On Mohist, **remove** `plugins/LegacyMechanicsGUI-*.jar` so Bukkit
does not shadow `/lm`, `/lm difficulty`, `/lm rival`, `/lm spar`, `/lm prestige`, `/lm admin skills`, `/lm skillcheck`.

| Command | UI |
|---------|-----|
| `/lm`, `/legacymechanics` | CNPC hub |
| `/lm difficulty` | CNPC difficulty |
| `/lm rival`, `/lm spar`, `/lm prestige` | CNPC panels |
| `/lm admin skills` | Staff CNPC / SkillUnlock chat fallback |
| `/lm skillcheck` | Donator CNPC |
| `/lm progression` | Staff CNPC (player **Remove Android** via hub) |

## Staff test GUI

Same hub as players:

```
/lm admin testgui
```

Controlled by `enableStaffCnpcTestGui` in config (default `true`).

## Staff inspect (no plugin)

```
/lm admin inspect <player> [hub|difficulty|rival|spar|…]
/lm admin inspect clear
```

Opens CNPC while **`AdminInspectSessions`** routes data/actions to the subject.

## Cutover checklist

1. Build and deploy **LegacyMechanics-4.5.3.jar** (Forge only).
2. Set `guiBackend` to `cnpc`; reload config.
3. Smoke: `/lm`, hub buttons, `/lm admin testgui`, one action per system (tier, rival TP, prestige turn-in, race list).
4. Stop server; move `plugins/LegacyMechanicsGUI-*.jar` to recycle; start server.
5. Confirm `/lm` opens CNPC (if command unknown, Mohist still has another plugin owning `/lm`).
6. Optional: PlaceholderAPI `%legacymechanics_*%` — install a tiny bridge plugin later if needed; not required for GUI.

## Legacy plugin

See `tools/dmz-adaptive-difficulty-gui/DEPRECATED.md`. Chest/CMI/`/lmdo` paths remain in that repo for
rollback only; Forge menus no longer call them when `guiBackend=cnpc`.
