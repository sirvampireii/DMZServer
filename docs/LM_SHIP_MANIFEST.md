# Legacy Mechanics — owner ship manifest

This file is the **source of truth for requests you already approved**.  
CI/build/deploy run `tools/dmz-adaptive-difficulty/sim/audit_ship_manifest.py` — **deploy is blocked if any item fails**.

When you ask for a behavior change, add a row here and a matching check in `audit_ship_manifest.py` in the **same PR** that implements it.

| ID | Request (summary) | Introduced | Audit |
|----|-------------------|------------|--------|
| cnpc-ui-scale | CNPC menus fit the GUI scale Minecraft actually uses (Auto, which is also what a manual 4 or 5 becomes when the screen cannot show a tighter step). Width and height share one layout scale. Label text is not shrunk a second time | 4.5.135+ | manifest §ui scale |
| terminal-donator | `/terminal` opens for donators (`legacymechanics.skillcheck`) or players with `cmi.customalias.terminal` | 4.5.130+ | manifest §terminal |
| held-overhaul-sync | Overhaul prestige count matches the LM held wallet, 1:1 from 0 to 10. A missing wallet is filled from faction, the DMZ prestige skill, or the current Overhaul count before that sync | 4.5.129+ | manifest §held overhaul |
| farming-building-removed | Farming TP and Building TP are not in LegacyMechanics. Harvesting crops and placing blocks do not award training points from this mod | 4.5.128+ | manifest §farming building |
| lightman-terminal | `/terminal` opens the Lightman's Currency network terminal. The CMI CustomAlias that force-cast the Fabled Terminal skill is retired | 4.5.127+ | manifest §terminal |
| fabled-removed | Fabled bridges are gone. Prestige, spar, and Overhaul count use the LM held wallet. Race lock does not reset a character for a missing skill | 4.5.126+ | manifest §fabled removed |
| class-stamina-config-scale | LegacyMechanics does not rewrite fighting-class stat scaling. Stamina and ki stay on the DragonMineZ and dmzrevamp coefficient the client bar already uses, so the server pool and the bar match. The mod applies no mixins off the dedicated server. LegacyMechanics does not multiply `getMaxStamina` or `getMaxEnergy` | 4.5.143+ | `audit_overhaul_scale_delegation.py` |
| natural-potential-only | Flight, sprint/jump, and the old meditation trainer are deleted, not left as off flags. Potential Unlock still levels from sparring | 4.5.148+ | manifest §natural skills |
| living-world-meditation | Meditation levels only while the player is in Living World's meditation state. Time counts when two players meditate within the detection radius of each other, or when one meditating player is within that radius of two living NPCs that are meditating. One NPC does not count. Standing nearby without meditating does not count. A player partner is full time. Extra players do not add more. Two living NPCs credit 75% of that time. Each held prestige adds 10% on top. Overlapping partners credit one sample, and a nearby player wins over NPCs. Defaults: radius 16, ten per-level times totaling 31 hours 51 minutes before those rates | 4.5.153+ | manifest §natural skills |
| dead-slash-commands | `/progression meditation` and `/enddragon spawn` are not commands. Difficulty GUI End Dragon summon is on (T4–T7, Ancient Netherite). Natural End dragon spawn stays off. Admins clear dragons with `/enddragon clear`. `/progression android remove` removes the caller's Android upgrade | 4.5.154+ | manifest §commands |
| skillcheck-player-npc | A Skill Check NPC opens the menu for every player. Slash `/skillcheck` still needs the donator permission | 4.5.155+ | manifest §access |
| skillcheck-one-tab | Skill Check shows every tracked skill on one Skills tab. Natural and Saga are not separate tabs | 4.5.145+ | manifest §access |
| skillcheck-simple-lines | Skill rows show the current level and Locked, Unlocked, or Max. Potential Unlock keeps its spar progress, soft cap, and Piccolo text | 4.5.146+ | manifest §access |
| spar-rival-cap | Spar closeness bonus raises the per-hit TP cap, so a rival multiplier still adds training points after other bonuses fill the old cap | 4.5.147+ | manifest §spar |
| death-tp-penalty | Dying gives the TP gain effect at level -3 for 10 minutes, the same effect the global TP boost uses. That level is a bonus of -0.5, so DragonMineZ's own effect multiplier is x0.5. A red bar shows Effect: x0.5 for the timer, because the character-stats line is computed on the client and stock DragonMineZ hides a negative level. The timer is copied onto the respawned player, and a later death refreshes it to 10 minutes. Leaving the End through the return portal does not start it | 4.5.137+ | manifest §death tp |
| prestige-need | First 4 prestiges need 20k each. From the 5th: 50k with 0 held, 100k with 1+ held | 4.5.121+ | `audit_prestige_need_ladder.py`, manifest §prestige |
| prestige-gui | Main prestige hub: **Need/Held** from wallet; **no Cap** on main; no cap breakthrough button on main (CNPC/chest/CMI) | 4.5.64+ | `audit_prestige_gui_flow.py`, manifest §prestige |
| skillcheck-gate | **Skill Check** = `legacymechanics.skillcheck` only; **no NPC/session bypass**; staff use **Skills** admin | 4.5.68+ | manifest §access |
| staff-admin-gate | **Staff Admin** / `/skills` / progression staff pages = staff/op only | 4.5.68+ | manifest §access |
| rival-challenge-pending | Rival **Challenge → Duel requests** board; tap row → accept/decline/cancel (CNPC + chest + CMI) | 4.5.59+ (merged 4.5.69) | manifest §rival |
| rival-challenge-persist | Duel requests **persist** in `rivalry-v4.json`; **not** cleared on logout | 4.5.79+ | manifest §rival |
| rival-challenge-reopen | After send, CNPC reopens **Duel requests** (not generic Challenge hub) | 4.5.79+ | manifest §rival |
| rival-declare-vs-duel | **Declare invites** (Actions) labeled separately from **Duel requests** (Challenge) | 4.5.79+ | manifest §rival |
| dojo-war-offline | Dojo war declare via **`uuid:` master** (target may be offline) | 4.5.79+ | manifest §spar |
| dojo-war-pending-hub | CNPC Dojo War hub: **War pending** only (no hub Accept/Decline) | 4.5.79+ | manifest §spar |
| spar-training-bonds | Mentor hub **Training bonds** + recruit/master wording (CNPC + chest/CMI) | 4.5.79+ | manifest §spar |
| cnpc-notice-visible | CNPC flash notice band readable (not grey-only) | 4.5.79+ | manifest §cnpc |
| cnpc-info-readable | CNPC info blocks + subtitles use same contrast as flash notices (`readableInfoLine`) | 4.5.86+ | manifest §cnpc |
| difficulty-tier-personal-gate | CNPC tier ladder locked when personal difficulty OFF | 4.5.78+ | manifest §difficulty |
| difficulty-team-personal-gate | Team scaling locked when AD off / whitelist / personal OFF (CNPC + chest + CMI) | 4.5.87+ | manifest §difficulty |
| build-backend-ship-overlay | `build.sh` overlays rival/spar backend classes shipped in 4.5.79+ | 4.5.79+ | manifest §build |
| rival-declare-pending | Rival **Actions → Pending** + `pending_decide:` (declare mutual confirm) | 4.5.55+ | manifest §rival |
| spar-pending | Spar **Mentor Pending** + `pending_decide:`; dojo war pending decide | 2.3.142+ | manifest §spar |
| spar-no-end-session | Spar main GUI: **no End Session** button (use normal flow / `/spar end`) | 4.5.63+ | manifest §spar |
| cnpc-defer-reopen | CNPC buttons defer reopen via `afterGuiClosed` (fix flash/crash after actions) | 4.5.54+ | manifest §cnpc |
| gui-menus | All LM hub systems wired (CNPC + chest + CMI where applicable) | ongoing | `audit_gui_menus_comprehensive.py` |
| android-saiyan-eligible | Dr. Gero Android convert allowed for **Saiyan** (and variants) when `androidforms` configured — no hard deny list | 4.5.84+ | manifest §android |
| android-self-convert | Players may **convert themselves** (staff converts others); CNPC must not say Saiyan blocked. From 4.6.237 the Character Services Android tab shows only what applies: an eligible race (androidforms configured) sees Convert for `1 + level/100` Gold Ancient Coins (charged before converting, refunded if the conversion fails, existing staff/bypass waiver only); an upgraded Android sees only Remove, always free; any other race sees the eligible-race list and no button; a Bio-Android is told they already are one. Staff converting or removing another player stays free and shows only the applicable button. `/progression android` and `/androidify` are unchanged | 4.5.88+ (4.6.237) | manifest §android |
| cnpc-empty-label-ids | CNPC empty-list placeholders use `ID_EMPTY_PLACEHOLDER` not flash id 50 | 4.5.88+ | manifest §cnpc |
| cnpc-boost-preset-ids | TP boost preset buttons use `ID_BOOST_PRESET_BASE` not flash band 50–59 | 4.5.89+ | manifest §cnpc |
| rival-challenge-cnpc-reopen | Rival challenge CNPC: defer reopen after actions; duration grid on `ID_GRID_BASE`; accept/decline/cancel match uuid or name | 4.5.92+ | manifest §rival |
| rival-challenge-accept-range | Accept duel from GUI without standing within range; fight starts when both players are in range after countdown | 4.5.93+ | manifest §rival |
| progression-no-flag-gui | Progression GUIs have no All Flags / subflags boards or per-module toggle tiles | 4.5.90+ | manifest §progression |
| cnpc-preview-live-player | Main-system CNPC menus: character preview uses **live player sync** (inventory-style) before Gecko clone fallback | 4.5.84+ | manifest §cnpc |
| prestige-cnpc-tier-ids | Prestige **Tiers** grid must not reuse CNPC flash widget ids (50+) — avoids menu errors after buy/turn-in | 4.5.85+ | manifest §prestige |
| prestige-turnin-payout | Turning in prestiges for favor uses `prestige-shop.json` → `exchange`: a `payoutOverrides` entry for that exact amount wins; otherwise the payout is the amount plus the group bonus (each full group of `bonusGroupSize` in one turn-in adds one more than the last group: with the default 3, turning in 3 gives +1, 6 gives +3, 9 gives +6) | 4.6.229-2+ | manifest §prestige |
| ultra-pickers-paged | Ultra pickers page instead of cutting off the list. Page size stays the old cap (10 online players; 8 cards; 4 / 6 / 8 on the Rival lists). Prev (dim) shows from page 2 and Next only while more items remain, with a "Page X of Y" line. Every item lands on exactly one page in its original order. On the dojo war-request and bond-invite pages the request lines and their buttons come from the same page, so every request shown can be acted on. Rival's player, challenge and spectate pickers show the same "Page X of Y" line (Spectate is built on the shared paged picker). A rival's profile opened from page 2+ of the Rival tab or the rival list returns to that page of the Rival tab. Back from a picker's confirm or decide page returns to the page the player picked from. The top-10 boards stay unpaged | 4.6.231+ (4.6.233) | manifest §ui paging |
| config-editor-save-safety | Staff config editor: every save first copies each settings file into a `backups` folder beside it (time-stamped, with a counter when two saves land in the same millisecond; the newest 20 per file are kept), on top of the once-per-session copy. After saving, Review shows a report: what was saved, by whom and when, which tabs reloaded and are live, any tab that failed to reload (in red, with what to do), and the backup. Each staged change on Review also says who last changed that setting and when, from the config audit log. A refused save names the setting and what to do next. Enum settings get an Options page listing every allowed value; list and map entries get Remove / Keep entry (staged until save). If another staff member saves a file you have unsaved changes for, Review warns and offers Reload and reapply; a save that would overwrite a setting they changed is refused with how to fix it. File formats, field names, defaults, closed modules, and staff gating are unchanged | 4.6.232+ (4.6.233) | manifest §config editor |
| no-auto-race-tail | LM never adds the Saiyan race tail as a body part. The race tail comes only from DMZ's own race layer (the character's tail flag), so other players see one tail, not two. A tail piece is sent only for a form that names one or a shop tail (tail_*) the player equipped; a chosen shop tail still hides the race tail while worn and lets it come back when taken off. The stuck-tail wipe for non-tail races is unchanged, and tail-family races keep their tail flags | 4.6.234+ | manifest §body parts |
| lm-revive | `/lm revive` revives the player who runs it (donator node `legacymechanics.skillcheck`; staff may use it too). `/lm revive <player>` revives a named online player and is staff-only (op 2, Bukkit op, or `difficulty.admin`). No op and no `dmzrevive.*` node is needed: LM calls DMZ's own revive directly, so DMZ's revive and its messages are unchanged. If DMZ's revive cannot be reached, the command says revive is not available and does nothing. No top-level `/revive` or `/rev` is registered | 4.6.236+ | manifest §commands |
| nemesis-mode-no-menu-toggle | Character Services Home has no Nemesis spawn line, description, or button, and the toggle action is gone. Each player's stored nemesis mode still gates their nemesis spawns exactly as before (OFF still allows the manual menu summon) | 4.6.236+ | manifest §rival |
| titles-persist | Unlocked difficulty titles stay unlocked: the equipped title, mastery, claimed Title Score milestones, and challenge progress (including mutations seen) are saved and loaded with the player, so a relog, restart, or prestige never drops them and nothing has to be re-earned or re-equipped. Every title has a one-line description on its detail page. Prestige itself (level ladder reset, tiers, requirements) is unchanged | 4.6.238+ | manifest §titles |
| version-handshake | Forge `VERSION`, `mods.toml`, and GUI `plugin.yml` source are the **same version**. `plugins/` does not ship `LegacyMechanicsGUI-*.jar` | always | `audit_gui_abi.py`, manifest §version |

## Deploy process (do not skip)

1. `bash tools/dmz-adaptive-difficulty/build.sh` — must pass all audits including **ship manifest**.
2. `LM_DEPLOY_VERSION=x.y.z DEPLOY_LIVE_CONFIRM=LIVE LM_SKIP_GUI=1 bash scripts/deploy-lm-live.sh` — script re-runs manifest audit before upload. Forge only.
3. **Full server restart** after Forge jar change.
4. Confirm live: only one `LegacyMechanics-*.jar` in `mods/`, and no `LegacyMechanicsGUI-*.jar` in `plugins/`.

## Why regressions happened (2026-09)

- **Rival challenge pending** lived on open PR #150 and was **never merged to `main`** while prestige/skillcheck fixes shipped on other branches → live got 4.5.68 **without** challenge pending UX.
- **4.5.79:** duel requests were memory-only and cleared on logout; dojo war required online masters — fixed + manifest rows above.
- **Prestige runtime crash (4.5.74):** source had `heldCountForNeed` but **jar build** did not overlay `PrestigeSystem.class` onto the consolidated base — fixed 4.5.75+ (`build.sh` + manifest `javap` check).
- **Prevention:** manifest audit + deploy gate; merge manifest row when feature ships; do not deploy from a branch that fails `audit_ship_manifest.py`.
- **Branch matrix:** `tools/dmz-adaptive-difficulty/sim/audit_branch_manifest_matrix.py` runs in `build.sh`. It **fails** if `main` is behind any `origin/cursor/*-c766` tip or if a scanned feature branch **regresses** markers present on the best branch. After merging to `main`, fast-forward or delete stale cursor branches so the matrix stays green.
