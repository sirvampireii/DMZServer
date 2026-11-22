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
- **Visible:** time banked and a bar on Difficulty Home and the guide's Step 3 ("Your tier"); the tier detail page explains it
  in one line. There is no HUD element.

## Difficulty guide

| Page | What it shows |
|---|---|
| Step 1 of 3, What is Difficulty (`guide`) | One sentence, three rows (stronger enemies, bigger rewards, change anytime), your tier, "This guide shows once" line, Next |
| Step 2 of 3, Pick your level (`guide_tiers`) | One button per tier: `You are here`, `Recommended`, dim `Locked` (still opens its detail page), Next |
| Step 3 of 3, Your tier (`guide_your_tier`) | Tier, Difficulty on/off, any free trial, Momentum with a bar; "Got it - take me to Difficulty"; the tier tools |
| Difficulty Home (`main`, after the guide) | Same rows and tier tools as Step 3 under a plain "Your tier" header, plus "How difficulty works" (replays the guide) |
| Tiers (`tiers`, after the guide) | The same tier list under a plain "Pick your level" header, Back to Home |
| Tier detail (`tier_ask:<t>`) | Before/after enemy damage, enemy health and TP, mastery gain, cost, the requirement in plain words, Confirm, Try one fight free |

- Back is in the left footer slot on every page; on the guide steps Skip guide sits in the right slot. Summons
  keeps its 4.7.57 behaviour, with the same header and Back chrome (Back to Step 1 during the guide, Back to
  Home after it).
- **Shows once (4.7.63):** per account, stored in the LM player record under `lm_difficulty_tutorial`
  (account UUID, so every character slot shares it; relog, slot switch and race change never reset it). Not
  done: `main`, `tiers` and `your_tier` open guide Steps 1, 2 and 3. "Got it" on Step 3 or Skip guide on any
  step marks it done and opens Home; closing the menu does not. Done: `main` and `your_tier` open Home, `tiers`
  the plain list. The `guide*` ids always open the guide, so "How difficulty works" replays it any time without
  clearing the mark. Routing is server side: the LM client and the KaiPath bridge behave the same.
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
