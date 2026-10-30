# DMZ Ultimate Key — DragonMineZ 2.2 port, empires layer on DMZ Ultra

DUK's source is not in this repo. The port is built from the live DUK 0.2.20 jar
(`base/dmzultimatekey-0.2.20.jar`, SHA-256 `32466987…beae`). Only the classes that DMZ 2.2 breaks are
rebuilt from decompiled source. The empires layer is moved off Noea and onto DMZ Ultra 0.4.0.

```
DMZULTRA_JAR=<dmzultra-0.4.x.jar> bash tools/dmz-ultimatekey-port/build.sh   # -> mods/dmzultimatekey-0.3.1.jar
DMZULTRA_JAR=… KAIPATH_JAR=… bash tools/dmz-ultimatekey-port/selftest/run.sh # empires self-tests, offline
```

The Ultra and KaiPath jars are read only, as compile and audit inputs. Neither is bundled or changed.

## How the jar is made

1. `src/` holds CFR output for the classes that change, with hand fixes where CFR got the Java wrong.
   Compiled against DMZ 2.1.3, these sources match the 0.2.20 bytecode method by method (the first
   commit on the port branch), so the rebuild changes nothing but the port.
   `src/…/empires/ultra/` is new: DUK-owned facades over DMZ Ultra's public API (see below).
2. `build.sh` compiles `src/` against DMZ 2.2 and Ultra 0.4.0. `patcher/AtWiden.java` gives javac the
   same view of Minecraft that Forge's access transformer gives the game. It is a compile input only.
3. `patcher/BytecodePatches.java` patches client classes that cannot be compiled here, because there
   is no client Minecraft jar (PreviewModel).
4. `pack.py` swaps the rebuilt classes into the base jar, adds the facades, and sets the version, the
   DragonMineZ range and the optional DMZ Ultra dependency in `mods.toml` and `MANIFEST.MF`.
5. `patcher/NoeaToUltra.java` remaps every Noea class name in the jar with `noea-to-ultra.txt`, and
   makes the mixin and gate changes listed below.

## Audits (all run by build.sh, all must pass)

- `compare_bytecode.py`: every rebuilt method matches 0.2.20 except the ones listed in
  `port-changes.txt`.
- `audit_api_refs.py`, three times: every class, field and method the jar links resolves in DMZ 2.2,
  in DMZ Ultra 0.4.0, and in DUK's own facades. `api-allow.txt` lists the one Forge-patched member it
  cannot see.
- `audit_duk_jar.py`: metadata agrees (version, DMZ range, the optional Ultra dependency, mod id,
  displayTest, mixin configs and the empires plugin). Every DragonMineZ/Ultra class a mixin targets
  exists, and no entry names Noea (`com/butterjaffa`).

`selftest/run.sh` runs the empires self-tests that the server runs on start, on a plain JVM. The
ported jar passes all 3,296 assertions, the same count as 0.2.20 on Noea.

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
quest/form event `isCanceled`/`setCanceled` methods all resolve in 2.2.

## Empires layer: Noea → DMZ Ultra

The empires layer now turns on when `dmzultra` is loaded (with KaiPath, CustomNPCs, FTB Teams and
Lightman's Currency), instead of `noeabosses`. Without Ultra it logs "Empires is off (needs dmzultra)"
and stays dormant. Nothing outside the empires layer touches Ultra.

| Noea | Now |
| --- | --- |
| `DeepSpaceSavedData` (planet state) | Ultra `space/catalogue/SpaceSavedData` |
| `CanonicalSolarSystem`, `DeepSpaceEvents`, `DeepSpaceTravel`, `DeepSpaceEntities`, `DeepSpaceNetwork`, `DeepSpacePlanetBuilder`, `DeepSpaceEncounterService`, `entity/DeepSpacePlanetEntity` | the same-named Ultra classes under `com/dmzultra/space/…` |
| `DivineTechniques`, `HakaiGrabService`, `SparringMatchService` | Ultra `space/shim/…` |
| `DestroyerRank` | `empires/ultra/DestroyerRank` (same constants; read from Ultra's `DestroyerHost.Rank`) |
| `DestroyerRoleSavedData` | `empires/ultra/DestroyerRoles`: a read-only view of Ultra's Destroyer Path (seat holders, apprentices, online candidates) |
| `DestroyerRoleService` | `empires/ultra/DestroyerOffice`: rank/universe from `Hosts.destroyer()`, energy cap from `DestroyerRules` |
| `V095ServerPolicySnapshot` | `empires/ultra/SpaceRules` (`deepSpaceEnabled` from Ultra's `SpacePolicy`) |
| `AtmosphereTravelService.active` | `UltraTravel.active` = Ultra `SpaceHost.crossing` |
| `V095CustomDefinitionRegistry`, `SonicFlightService`, `TournamentOfPowerMatchService` | no Ultra equivalent: empty / no-op / false |
| planet destruction ledger mixin (`DeepSpaceEvents`) | retargeted to Ultra `GalaxyWave2` (same methods and call sites) |

Dormant because Ultra has no equivalent:

- **Destroyer terms and succession.** Ultra's Destroyer Path owns the seats and has no API to appoint
  or remove someone. The live term maintenance is switched off, and admin "end term" replies that the
  seats are changed in Ultra. DUK still reads who holds which seat.
- **Bounty hakai hooks.** The three mixins on Noea's `DivineImmortalityEvents.forceKill/eraseAuthorized`,
  `HakaiGrabService.start(player, bool)` and `DestroyerRoleService.canDamageWithHakaiProjectile` are
  removed. Ultra handles hakai on players through its own Grand Priest discipline.
- **Admin-defined custom planets** (Noea's V095 registry): the list is empty.

Agent looks: Ultra's `FormParts` is client-side and resolves a look from the DMZ character of every
player entity on the client, and DUK agents are players. DUK has nothing to call for this; the test
server confirms it.
