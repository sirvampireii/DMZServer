# DMZ Ultimate Key — DragonMineZ 2.2 port

DUK's source is not in this repo. The port is built from the live DUK 0.2.20 jar
(`base/dmzultimatekey-0.2.20.jar`, SHA-256 `32466987…beae`), with only the classes that DMZ 2.2
breaks rebuilt from decompiled source.

```
bash tools/dmz-ultimatekey-port/build.sh      # -> mods/dmzultimatekey-0.3.0.jar
```

## How the jar is made

1. `src/` holds CFR output for the classes that change, with hand fixes where CFR got the Java wrong.
   Compiled against DMZ 2.1.3, these sources match the 0.2.20 bytecode method by method (the first
   commit on the port branch), so the rebuild changes nothing but the port.
2. `build.sh` compiles `src/` against DMZ 2.2. `patcher/AtWiden.java` gives javac the same view of
   Minecraft that Forge's access transformer gives the game. It is a compile input only.
3. `patcher/BytecodePatches.java` patches client classes that cannot be compiled here, because there
   is no client Minecraft jar (PreviewModel).
4. `pack.py` swaps the rebuilt classes into the base jar and sets the version and DragonMineZ range
   in `mods.toml` and `MANIFEST.MF`. Every other entry is copied byte for byte.

## Audits (all run by build.sh, all must pass)

- `compare_bytecode.py`: every rebuilt method matches 0.2.20 except the ones listed in
  `port-changes.txt`.
- `audit_api_refs.py`: every DragonMineZ class, field, and method the jar links resolves in DMZ 2.2.
  `api-allow.txt` lists the one Forge-patched member it cannot see.
- `audit_duk_jar.py`: metadata agrees (version, DMZ range, mod id, displayTest, mixin configs and the
  empires plugin), and every DragonMineZ class a mixin targets exists.

## What changed for DMZ 2.2

| Break in 0.2.20 | Port |
| --- | --- |
| `mods.toml` DMZ range `[2.1.3,2.2)` | `[2.2,2.3)` |
| `HairManager` code methods and `Character.setHairBase/SSJ*` removed | `FactionBodies` uses `HairCodec` and `Character.setHairStyle(HairStyleSlot, …)`; 2.1 hair codes still decode; a copy from a player takes the full hair set |
| `revertToBaseForm` now returns boolean and has a 3-arg overload | `FormMechanics.revert` calls `revertToBaseForm(p, d, false)`, as DMZ's own revert paths do |
| `GrantWishC2S(String, List)` removed | `VillainSchemes.wish` sends `GrantWishC2S(dragonId, wishScreen, picks, List.of())` for the dragon it found |
| `TaiyokenCastC2S`, `TaiyokenHandler` removed (Taiyoken is an evasion now) | agents cast evasions through `EvasionCastC2S`; `DmzActions.useTechnique` casts evasions through `EvasionAttackHandler.cast`; `PilotSpectatorMixin` targets `EvasionCastC2S` |
| `ConfigManager.applySpecificSyncedConfig` returns boolean (was void) | `PreviewModel` call sites retargeted, result dropped (bytecode patch) |

Checked and unchanged: the regen event accessors (`getAmount`/`setAmount`/`getPlayer`) and the
quest/form event `isCanceled`/`setCanceled` methods all resolve in 2.2. The empires mixins target
Noea and are gated by `EmpiresMixinPlugin`, which turns them off when `noeabosses` is absent.
