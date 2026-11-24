# LM native HUD, part 2: moving repeated chat to the HUD (proposal)

Status: **approved 2026-11-24, to build as LM 4.7.75.** Owner's answers: all seven elements; the fight pill also
covers spar sessions and dojo war bouts; the coin toast sums over a 5-second window; the server boost chip is shown. Source: `docs/LM_FULL_REVIEW_2026-11.md` section B, ranked by how often each line fires and how little the
player can do with it.

## Rules (same as the 4.7.72 HUD)

- **The server owns everything.** Same text and numbers as the chat line or subtitle it replaces, from the same
  function. The HUD only draws.
- **Who gets it.** LM clients that list `hud` and have the HUD switch ON. KaiPath, older clients and HUD OFF keep
  today's chat lines exactly.
- **No doubling.** A HUD player gets the HUD element instead of the chat line, never both.
- **Lag rules.** Baked chrome, text laid out on arrival, sends only on change.
- **Placement.** Every new element joins the existing zones: the top-centre stack, the right-edge cards and the
  bottom-right meter row. The 4.7.73 placement rules apply, and the probe's layout check covers every element at
  GUI 2/3/4.

## Proposed elements

| # | Element | Replaces (HUD players only) | Zone | Updates | Click |
|---|---|---|---|---|---|
| 1 | **Fight pill**: both names, damage score, time left, and "Too far apart - 23m, get within 16m" in red while out of range | Rival challenge score line every 15 s, "Too far apart" every 3 s to both fighters, `RivalChallengeManager.java:504-518` | top stack | 1/s while the fight runs | Rival page |
| 2 | **Spectator pill**: the same pill for a spectator, with "Watching" | `[Spec] A 123 vs B 456 (42s)` every 2 s, `RivalSpectator.java:102` | top stack | 1/s | none |
| 3 | **Spar chip**: "Perfect Training", combo count, "Recover in 6s" countdown | "Perfect Training active" every 2.5 s, "Combo broken" up to every 1.5 s, the one-shot recover line whose number goes stale (`SparringSystem.java:1136,1216,1271`) | bottom-right row, beside Momentum | on change; countdown 1/s | Sparring page |
| 4 | **Potential meter**: "Potential 34/50" and "Switch training methods" while the streak blocks gains | the subtitle sent on every hit after a 5-hit streak, `PotentialProgression.java:263` | bottom-right row | on change | none |
| 5 | **Coin toast**: "+12 Ancient Coins", summed over 5 s so a kill streak is one card | "Dropped X Ancient Coins" on every kill (when that chat setting is on), `AncientCoinEconomy.java:579` | right-edge cards | per 5 s window | none |
| 6 | **Elite sense**: a small "Elite nearby" chip while one is within 48 blocks | the chat line every 45 s, `TitleSense.java:65-77` | top stack | on change | none |
| 7 | **Server boost chip**: "TP x1.5 - 42m left" while a global TP or mastery boost runs | nothing today (only the purchase and end broadcasts). The broadcasts stay in chat | top stack, last | 1/min | Prestige shop |

**Stays chat**:
- spar session summaries, streak lines;
- Proving Grounds claims, match reports;
- prestige receipts, title unlocks;
- race, android and spirit confirmations;
- dojo war bout announcements;
- the boost purchase and end broadcasts.

**Dropped**: the healing-caps line sent at every login (`HealCap.java:40`). The player can't act on it, so it moves
to the character stats page.

**Copy fixes that ride along**:
- `enderman`/`shulker` become mob names (`EndDimensionStrength.java:2573`).
- `difficultyChosen` is no longer shown (`RaceLock.java:146`).
- `(cooldown)` is dropped (`ShadowDummyLimiter.java:219`).
- `[countdown]` reads "Starting in 5s" (`RivalSpectator.java:101`).
- The full-set Dragon Ball warning moves from chat to the action bar like its sibling (`DragonBallSetHold.java:81`).

## Wire and proof

- **Wire.** The same message 4 (`NativeHudPacket`) with new element ids. A client that does not know an id ignores
  it, so no protocol change is needed. `LmHud.ELEMENTS` grows; the client gets one slot per element.
- **Probe.** For each element on the LM client, a scripted check that the HUD text equals the chat text, and that a
  HUD player gets no chat line. On KaiPath, the chat lines are unchanged. The layout verdict covers all elements
  drawn at once at GUI 2/3/4. The mock set `hud` grows to show every element.

## Decisions for the owner

1. **Which elements:** all seven, or a subset? Elements 1 and 3 are the biggest chat savers.
2. **Fight pill during spars and dojo war bouts:** the 4.7.72 proposal left spar sessions and war clocks out. Should
   element 1 also cover spar sessions and war bouts (timer, opponent, score), or only rival challenges?
3. **Coin toast window:** 5 s, or a running total that stays while you keep killing?
4. **Server boost chip:** show it, or leave boosts to chat?
