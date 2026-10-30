# Mohist M1: animation but no damage

## Fix v2.12.27 — DragonMineZ 2.2 port (Oct 2026)

`mods/dmz_mohist_melee_fix-2.12.27.jar` — built against `mods/dragonminez-2.2.jar` (CurseForge file 9282248). It needs DragonMineZ 2.2 (`mods.toml` asks for `[2.2,)`) and will not load on 2.1.3.

- **Party quest guard.** 2.2 removed the party quest merge (`PartyManager.syncQuestProgress`, `onPlayerLogin`, `PlayerQuestData.mergeQuestStateFrom`, `QuestProgress.mergeForwardFrom`). Quests now complete per player through `completeQuest`, and party members join a quest only when it is available to them. `joinParty`, `joinPartyForFusion`, `reconcile` (the 2.2 login path), `syncPartyDifficulty`, `kickMember`, and `leaveParty(ServerPlayer, boolean)` each run inside a guard. During that call a quest can reach `SUCCESS` (`QuestProgress.setStatus`) only for a player who had it `ACCEPTED`. Afterwards, any completion that appeared and was not earned or accepted is stripped. Unearned reward claims are still cancelled. Nothing wipes saga progress on leave, login, or death. Completion credit now goes to the completing player only; 2.2 posts the completion event once per player.
- **Ghost party heal** runs after the login `reconcile` and after the full `leaveParty` overload. The 1-arg `leaveParty` is a stub that delegates, so every party hook is pinned by descriptor and runs once.
- **Master NPC disables.** `handlePopo(ServerPlayer, int)`, `handleGuru(StatsData, int)`, and `handleGero(ServerPlayer, StatsData, int)` return `boolean` now; the hooks set `false`. The packet gate (`lambda$handle$0`) still cancels before DMZ's range checks, `perform()`, and `reply()`. It now sends the failed dialogue result itself. The player sees the reason ("… is disabled on this server.") as the NPC's line, and the option stays usable. Without that result the 2.2 dialogue screen keeps the action pending.
- **Melee packet.** 2.2's `CombatAttackRequestC2S.handle` records minigame actions, stores the arrival aim, and hands the hit to `MeleeWindupTracker`. The fix no longer cancels `handle`. It queues its Mohist repairs ahead of DMZ's work, and the stale-ID rescue now hangs off `processAttackRequest`, so it runs only when DMZ actually strikes after the windup.
- **Stat reset.** `resetPlayerProgress` now also scales or clears growth stats and clears story resets. The HEAD/RETURN anchors still match, and this fix has no wipe of its own for those, so nothing is cleared twice.
- **Priceless costs.** 2.2's `computeTpCost` returns `-1` for any negative or missing price, and both purchase and upgrade stop on a cost below zero. The 2.1.3 `Math.max` redirects are gone from the (still unregistered) `UpdateSkillPricelessMixin`. The registered `setSkillLevel` max-level refresh stays.
- **Old Kai Precision skip is off.** 2.2 moved the stage list to `MinigameLogics.CHALLENGE_STAGES`, which the server checks stage by stage. A skip on only one side fails the challenge at stage 4, and the client modpack does not ship this jar. The mixin is retargeted but not registered.
- **Console.** Per-event lines (repairs, blocks, self-test passes) log at DEBUG. Set `debugLogging=true` in `config/dmz_mohist_melee_fix-debug.properties` (re-read every 30 seconds) or start with `-Ddmz_mohist_melee_fix.debug=true` to see them in the console. Self-test failures are warnings.
- **Build audits.** `build.sh` now scans the jar for missing classes and inner classes, checks every mixin config class is packaged, checks mixin helpers are private, and checks every mixin target, descriptor, callback type, handler signature, and `@At` against the DragonMineZ jar (`scripts/audit_mixin_targets.py`).

Requires a full server restart, on DragonMineZ 2.2.

## Fix v2.12.25 — flow shockwaves leave terrain alone (Oct 2026)

`mods/dmz_mohist_melee_fix-2.12.25.jar`

`FlowShockwaveNoDestroyMixin` cancels `FlowShockwaveService.destroyRing`. The shockwave visuals and knockback still play. This jar also keeps the 2.12.24 priceless-mixin removal and the 2.12.23 grab load-order fix.

Requires a full server restart.

## Fix v2.12.24 — priceless skill mixin unregistered (Oct 2026)

`mods/dmz_mohist_melee_fix-2.12.24.jar`

`UpdateSkillPricelessMixin` injected into Dragon Mine Z `UpdateSkillC2S` and called `com.dbzlegacy.mohistmelee.PricelessPurchaseGuard`. That class lives in this mod, and Dragon Mine Z's classloader cannot see it — the same isolation that broke `MajinAbsorptionStore` when it sat next to a mixin. `SkillsBlockPricelessLevelMixin` and `SkillBlockPricelessAddLevelMixin` call the same class from `Skills` and `Skill`, so those three are not registered. The other melee, stat-reset, and Mohist mixins stay. The priceless menu guard is off until that logic is inlined into the mixin or moved somewhere Dragon Mine Z can load.

Requires a full server restart.

## Fix v2.12.23 — GrabService mixin no longer loads too early (Oct 2026)

`mods/dmz_mohist_melee_fix-2.12.23.jar`

`OptionalNoeaMixinPlugin` was calling `Class.forName("com.butterjaffa.noeabosses.GrabService")` while Mixin was still reading its config. That defines `GrabService` before the transformer is ready, so `NoeaGrabServiceRequestMixin` is skipped and the log says the target was loaded too early. The plugin now checks for the class file and does not define it. The mixin names `GrabService` as a string and its inject method does not reference Noea types. `GrabService.setEnabled` still runs from the server-started event, after mixins have applied.

`NoeaGrabActionPacketMixin` is unchanged. Requires a full server restart.

## Fix v2.12.22 — hard-disable Noea experimental grab (Oct 2026)

`mods/dmz_mohist_melee_fix-2.12.22.jar`

When **Dragon Block Noea** (`noeabosses`) is installed, this jar:

- Cancels all `GrabActionC2SPacket` handling and `GrabService.request` (Z grab/throw).
- Calls `GrabService.setEnabled(server, false)` on start and every ~10s so `/noea experimental grab on` cannot re-enable it.

Requires a **full server restart** after replacing the melee fix jar (Forge mixins).

## Fix v2.12.21 — handwear + Apotheosis crash (Sep 2026)

`mods/dmz_mohist_melee_fix-2.12.21.jar`

Live crashes when attacking with **DMZ Revamp wristbands** (curio handwear) plus **Apotheosis affix** gear (e.g. Simply More **Blood Harvester**, other uniques) were a **recursive `LivingHurtEvent` loop**: `HandwearCombatEvents.runApotheosisHandwearAttackHooks` → `EnchantmentHelper.doPostAttack` → Thunderstruck (etc.) → hurt again → same hook → `StackOverflowError`.

**Mitigation:** `HandwearApotheosisReentrancyMixin` skips nested handwear Apotheosis hooks on the same thread. Restart required after jar swap.

Upstream fix still belongs in **dmzrevamp** (`HandwearCombatEvents` should not call post-attack affixes from inside `LivingHurt` without a reentrancy guard).

## Fix v2.12.19

`mods/dmz_mohist_melee_fix-2.12.19.jar`

https://github.com/anthonycasio92-debug/DMZServer/raw/cursor/saga-guard-purge-fix-c766/mods/dmz_mohist_melee_fix-2.12.19.jar

### Saga guard safety fix (2.12.19)
- **Removed** wipe-on-leave / wipe-on-login / wipe-on-death purge (was erasing legitimate saga progress)
- Death/respawn now **carries over** personal earn marks (same UUID) instead of re-bootstrapping empty
- Party merge only strips completions **newly borrowed in that merge**, never pre-existing progress
- Reward claims still blocked for quests the player did not personally earn
- True co-op still works if you were `ACCEPTED` on the quest when it completed


### Personal saga earn guard (2.12.17)
- Party sync can no longer copy already-`SUCCESS` quests onto a player who did not earn them and was not actively on the quest
- `QuestRewardClaimEvent` cancels claims unless the player personally earned that quest (solo turn-in or present in party at completion)
- Saga/quest resets clear personal earn marks so reset → rejoin-friend cannot reclaim TP/rewards
- First login bootstraps earn marks from currently completed quests (grandfather existing progress)

### Skill set maxLevel refresh (2.12.11)
- `/dmzskill set` refreshes skill `maxLevel` from `skills.json` costs before applying the level

### Instant Transmission to players disabled (2.12.10)
- Blocks `InstantTransmissionTravelToPlayerC2S` (party + external players)
- IT menu only lists masters; master teleport still works

### Stat reset / race selection (2.12.9)
- Vanilla DMZ clears `hasCreatedCharacter` on every reset → client race select (`forceCharacterCreation`)
- Soft `/dmzstats reset 100 true` still keeps stats/% + skills through recreate
- Percentage snapshot math + self `/dmzstats reset <%> [keepSkills]` still patched

### Melee (same as working 2.12.0)
- Decode cap raise 64→256 then truncate to 64
- Handle mixin unlocks only **stale** strike locks, then `processAttackRequest`, then server-side AABB rescue
- Persistent-data hit-time gate via `PersistentDataAccess` + `CombatEvent.DMZ_LAST_HIT_TARGET_TIME_TAG`
- No damage redirects / `setHealth` / `ki_damage` rewrites

### Stat reset (hardened)
- Record intentional primary writes (public setters + `setAttributeBaseValue`), including 0
- On `resetPlayerProgress`: cancel delayed teleport follow-ups, suppress restore (~10s), adopt/zero snapshot after
- Full resets force a zero snapshot; percentage resets adopt live post-reset values

### Disabled master NPC actions (server)
- **Shadow spar:** `NPCActionC2S("popo", 1)` cancelled. Player minigame summons unchanged.
- **Guru potential unlock:** `NPCActionC2S("guru", 1)` cancelled.
- **Dr. Gero android conversion:** `NPCActionC2S("gero", 1)` / `handleGero` cancelled.

### Priceless skills/forms stay unbuyable (server) — hardened in 2.12.16
- Cancels DMZ `UpdateSkillC2S.lambda$handle$0` for PURCHASE/UPGRADE when cost is `-1`
- **SDU 3.0.5+:** also cancels `DmzSkills.buyStackSkill` (own `BuyStackSkillC2S` path that did `Math.max(0, -1)` and free-unlocked stack forms on spam/double-click)
- Preserves negatives in DMZ `computeTpCost`
- Guards `Skills.setSkillLevel` / `Skill.addLevel` during menu purchase packets only
- Log lines: `blocked priceless …` / `blocked priceless sdu buyStackSkill …`


### Old Kai UltimateChallenge (client)
- Hardcoded stages: Control → Gravity → Memory → **Precision** → Rhythm
- Client mixin drops Precision → Control → Gravity → Memory → Rhythm (still level 5 each)
- **Install this jar on clients** as well as the server, or players still see Precision

### Install

1. Only **2.12.19** in server `mods/` (client too if using Precision skip)
2. Delete older `dmz_mohist_melee_fix-*.jar`
3. Restart — server log: `v2.12.19`
4. Full reset → race select: `/dmzstats reset` (requires `gameplay.forceCharacterCreation=true`)
5. Soft-reset example: `/dmzstats reset 100 true` (self) or `/dmzstats reset <player> 100 true`
