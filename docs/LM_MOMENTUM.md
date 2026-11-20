# Difficulty Momentum and the Difficulty guide (LM 4.7.59)

## Momentum

- **What counts:** a hostile the difficulty system scaled (`MobScaling.isAdPainted`, the population the
  balance telemetry measures). Chamber wave foes never count; they keep the 4.7.45 per-kill mastery grant.
- **Banking:** the player behind the kill (the killer, or the owner of the projectile or summon) banks
  `momentumSecondsPerKill` seconds (10), stacking up to `momentumMaxBankSeconds` (300). The timer counts down
  in real time. Each player banks only from their own kills, party members included.
- **Effect:** DragonMineZ grants form mastery per landed hit. While a player has time banked, the hit mastery
  DMZ grants them for hitting a difficulty mob is multiplied by `momentumMasteryMultiplier` (1.5 = +50%).
  Training, sparring, hits on any other target, hit-taken mastery and the chamber kill grant are unchanged.
  Hooked through the existing hit-mastery mixins (`StatsEventsHitTakenMasteryMixin`,
  `PotionEffectHelperHitTakenMasteryMixin`).
- **Lifetime:** per character and session-only. Cleared on logout and on a DMZ Ultra character switch.
- **Config:** `momentumEnabled`, `momentumSecondsPerKill`, `momentumMaxBankSeconds`,
  `momentumMasteryMultiplier` in `legacymechanics.json` (staff editor section "Momentum").
- **Visible:** time banked and a bar on the guide's Step 3 ("Your tier"); the tier detail page explains it
  in one line. There is no HUD element.

## Difficulty guide

| Page | What it shows |
|---|---|
| Step 1 of 3, What is Difficulty (`main`) | One sentence, three rows (stronger enemies, bigger rewards, change anytime), your tier, Next |
| Step 2 of 3, Pick your level (`tiers`) | One button per tier: `You are here`, `Recommended`, dim `Locked` (still opens its detail page) |
| Step 3 of 3, Your tier (`your_tier`) | Tier, Difficulty on/off, any free trial, Momentum with a bar; the tier tools (change tier, on/off, clear, titles, rival teams, coin messages, staff tools) |
| Tier detail (`tier_ask:<t>`) | Before/after enemy damage, enemy health and TP, mastery gain, cost, the requirement in plain words, Confirm, Try one fight free |

- Back is in the left footer slot on every page. Summons keeps its 4.7.57 behaviour, with the same header
  and Back chrome.
- **Recommended:** the highest tier whose requirement the player meets; with no tier active, Tier 1.
- **Before/after:** enemies scale to a share of your stats, so damage and health show that share against
  your tier now. TP per kill follows the beaten enemy's health (DMZ pays 2 + 0.25 x max HP), so it moves by
  the same ratio. Mastery gain is the Momentum boost.

## Try one fight free

- **What it does:** applies a tier the player meets the requirement for, at no cost, for 5 minutes. Then the
  previous tier (or no tier) comes back by itself. A once-a-second server check does the revert, with a
  chat note.
- **Limit:** once per tier per character, recorded in that character's LM record.
- **During the trial:** buying, switching down or clearing keeps the player's choice and ends the trial with
  no revert. Momentum banks normally.
