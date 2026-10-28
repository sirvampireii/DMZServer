# Tier × level matrix audit (1–150000)

Fail-closed checks for unlock tiers T1–T7 across the DMZ level cap.

## 1) Cost anchors

- ✅ T1 @ lvl 1 = 1× Copper — 1× Copper
- ✅ T7 @ lvl 150000 = 100× Netherite — 100× Netherite (10000000)
- ✅ stock bases T1..T7
- ✅ anchor 150000

## 2) Buy-cost ladder T1<T2<…<T7 at every level

- ✅ cost T-ladder mono across 150000 levels — 0.62s
- ✅ cost non-decreasing with level (≤150k) — ok
- ✅ past-anchor clamp T7 200k==150k — 10000000 vs 10000000
- ✅ past-anchor clamp T1 200k==150k — 6700 vs 6700

### Sample costs

| level | T1 | T2 | T3 | T4 | T5 | T6 | T7 |
|------:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 1× Copper | 5× Copper | 15× Copper | 5× Iron | 15× Iron | 5× Gold | 15× Gold |
| 500 | 1× Copper | 5× Copper | 15× Copper | 51× Copper | 16× Iron | 52× Iron | 16× Gold |
| 1000 | 1× Copper | 5× Copper | 16× Copper | 53× Copper | 16× Iron | 53× Iron | 16× Gold |
| 5000 | 1× Copper | 7× Copper | 2× Iron | 67× Copper | 21× Iron | 68× Iron | 21× Gold |
| 10000 | 2× Copper | 9× Copper | 27× Copper | 9× Iron | 27× Iron | 9× Gold | 27× Gold |
| 50000 | 19× Copper | 94× Copper | 29× Iron | 95× Iron | 29× Gold | 95× Gold | 29× Emerald |
| 100000 | 36× Iron | 18× Gold | 54× Gold | 18× Emerald | 54× Emerald | 18× Diamond | 54× Diamond |
| 150000 | 67× Gold | 34× Emerald | 1× Netherite | 34× Diamond | 10× Netherite | 34× Netherite | 100× Netherite |

## 3) Unlock DMZ level gates

- ✅ stock REQUIRED matches UnlockTier — {1: 1, 2: 500, 3: 1000, 4: 5000, 5: 10000, 6: 50000, 7: 100000}
- ✅ T1 unlock level ≥ prior — 1 ≥ 0
- ✅ T1 unlocked at lvl 1 — level≥1 or prestige≥1
- ✅ T2 unlock level ≥ prior — 500 ≥ 1
- ✅ T2 locked below lvl 500 — gate=500
- ✅ T2 unlocked at lvl 500 — level≥500 or prestige≥2
- ✅ T3 unlock level ≥ prior — 1000 ≥ 500
- ✅ T3 locked below lvl 1000 — gate=1000
- ✅ T3 unlocked at lvl 1000 — level≥1000 or prestige≥3
- ✅ T4 unlock level ≥ prior — 5000 ≥ 1000
- ✅ T4 locked below lvl 5000 — gate=5000
- ✅ T4 unlocked at lvl 5000 — level≥5000 or prestige≥4
- ✅ T5 unlock level ≥ prior — 10000 ≥ 5000
- ✅ T5 locked below lvl 10000 — gate=10000
- ✅ T5 unlocked at lvl 10000 — level≥10000 or prestige≥5
- ✅ T6 unlock level ≥ prior — 50000 ≥ 10000
- ✅ T6 locked below lvl 50000 — gate=50000
- ✅ T6 unlocked at lvl 50000 — level≥50000 or prestige≥6
- ✅ T7 unlock level ≥ prior — 100000 ≥ 50000
- ✅ T7 locked below lvl 100000 — gate=100000
- ✅ T7 unlocked at lvl 100000 — level≥100000 or prestige≥7
- ✅ T7 unlock ≤ 150k cap
- ✅ T6 unlock ≤ 150k cap

## 4) Combat ladder (saiyan warrior) × level bands × T1–T7

| band | even T1→T5 | even T5→T7 | god T3≤…≤T7 | god land≤soft |
|-----:|-----------:|-----------:|:-----------:|:-------------:|
- ✅ even T1→T5 ≥1.4× (level-invariant combat) — 0.129→0.353
- ✅ even T5→T7 ≥1.10× — 0.353→0.430
- ✅ god hitFrac mono T3→T7 — T3=0.323 / T4=0.377 / T5=0.414 / T6=0.452 / T7=0.565
- ✅ god landing ≤ soft-cap all tiers
- ✅ stock tier% 21→200%
- ✅ god T5 ≥28% bag — 0.414
- ✅ god T7 ≥40% bag — 0.565
- ✅ god land T5 < T6 — 0.329<0.379
| 1 | OK | OK | OK | OK |
- ✅ lvl 1: eligible tiers match gates — T[1]
- ✅ lvl 1: cost mono — T1=1× Copper, T2=5× Copper, T3=15× Copper, T4=5× Iron, T5=15× Iron, T6=5× Gold, T7=15× Gold
| 500 | OK | OK | OK | OK |
- ✅ lvl 500: eligible tiers match gates — T[1, 2]
- ✅ lvl 500: cost mono — T1=1× Copper, T2=5× Copper, T3=15× Copper, T4=51× Copper, T5=16× Iron, T6=52× Iron, T7=16× Gold
| 1000 | OK | OK | OK | OK |
- ✅ lvl 1000: eligible tiers match gates — T[1, 2, 3]
- ✅ lvl 1000: cost mono — T1=1× Copper, T2=5× Copper, T3=16× Copper, T4=53× Copper, T5=16× Iron, T6=53× Iron, T7=16× Gold
| 5000 | OK | OK | OK | OK |
- ✅ lvl 5000: eligible tiers match gates — T[1, 2, 3, 4]
- ✅ lvl 5000: cost mono — T1=1× Copper, T2=7× Copper, T3=2× Iron, T4=67× Copper, T5=21× Iron, T6=68× Iron, T7=21× Gold
| 10000 | OK | OK | OK | OK |
- ✅ lvl 10000: eligible tiers match gates — T[1, 2, 3, 4, 5]
- ✅ lvl 10000: cost mono — T1=2× Copper, T2=9× Copper, T3=27× Copper, T4=9× Iron, T5=27× Iron, T6=9× Gold, T7=27× Gold
| 50000 | OK | OK | OK | OK |
- ✅ lvl 50000: eligible tiers match gates — T[1, 2, 3, 4, 5, 6]
- ✅ lvl 50000: cost mono — T1=19× Copper, T2=94× Copper, T3=29× Iron, T4=95× Iron, T5=29× Gold, T6=95× Gold, T7=29× Emerald
| 100000 | OK | OK | OK | OK |
- ✅ lvl 100000: eligible tiers match gates — T[1, 2, 3, 4, 5, 6, 7]
- ✅ lvl 100000: cost mono — T1=36× Iron, T2=18× Gold, T3=54× Gold, T4=18× Emerald, T5=54× Emerald, T6=18× Diamond, T7=54× Diamond
| 110000 | OK | OK | OK | OK |
- ✅ lvl 110000: eligible tiers match gates — T[1, 2, 3, 4, 5, 6, 7]
- ✅ lvl 110000: cost mono — T1=64× Iron, T2=32× Gold, T3=96× Gold, T4=32× Emerald, T5=96× Emerald, T6=32× Diamond, T7=96× Diamond
| 120000 | OK | OK | OK | OK |
- ✅ lvl 120000: eligible tiers match gates — T[1, 2, 3, 4, 5, 6, 7]
- ✅ lvl 120000: cost mono — T1=115× Iron, T2=58× Gold, T3=18× Emerald, T4=58× Emerald, T5=18× Diamond, T6=58× Diamond, T7=18× Netherite
| 130000 | OK | OK | OK | OK |
- ✅ lvl 130000: eligible tiers match gates — T[1, 2, 3, 4, 5, 6, 7]
- ✅ lvl 130000: cost mono — T1=21× Gold, T2=104× Gold, T3=31× Emerald, T4=104× Emerald, T5=31× Diamond, T6=104× Diamond, T7=31× Netherite
| 140000 | OK | OK | OK | OK |
- ✅ lvl 140000: eligible tiers match gates — T[1, 2, 3, 4, 5, 6, 7]
- ✅ lvl 140000: cost mono — T1=38× Gold, T2=19× Emerald, T3=56× Emerald, T4=19× Diamond, T5=56× Diamond, T6=19× Netherite, T7=56× Netherite
| 150000 | OK | OK | OK | OK |
- ✅ lvl 150000: eligible tiers match gates — T[1, 2, 3, 4, 5, 6, 7]
- ✅ lvl 150000: cost mono — T1=67× Gold, T2=34× Emerald, T3=1× Netherite, T4=34× Diamond, T5=10× Netherite, T6=34× Netherite, T7=100× Netherite

## 5) All races × all forms × T1–T7

Combat is level-invariant; buy-cost scan above covers levels 1–150k.
Each form checked at mastery 0% + 100% (Base once). Soft-cap + no zero dmg.

- ✅ discovered ≥8 stock races — 10: ancient_saiyan, bioandroid, frostdemon, human, majin, monkey, namekian, saiyan, sento_saiyan, viltrumite
| race | forms | cells | soft≤cap | dmg>0 | peak mono T3→T7 | peak T5≥28% |
|------|------:|------:|:--------:|:-----:|:---------------:|:-----------:|
| ancient_saiyan | 3 | 49 | OK | OK | OK | OK |
| bioandroid | 11 | 161 | OK | OK | OK | OK |
| frostdemon | 14 | 203 | OK | OK | OK | OK |
| human | 14 | 203 | OK | OK | OK | OK |
| majin | 9 | 133 | OK | OK | OK | OK |
| monkey | 6 | 91 | OK | OK | OK | OK |
| namekian | 8 | 119 | OK | OK | OK | OK |
| saiyan | 22 | 315 | OK | OK | OK | OK |
| sento_saiyan | 11 | 161 | OK | OK | OK | OK |
| viltrumite | 8 | 119 | OK | OK | OK | OK |
- ✅ race/form soft-cap all cells (1554) — 0.04s
- ✅ race/form mobDmg > 0 all cells — ok
- ✅ race peak form ladders / floors — 10 peaks ok

### Android forms coverage

- ✅ human has android form entries — 5 forms
- ✅ saiyan has android form entries — 5 forms
- ✅ frostdemon has android form entries — 5 forms
- ✅ viltrumite has android form entries — 4 forms
- ✅ bioandroid has no androidforms upgrade group — android-named=0

**Result:** PASS — 72 ok, 0 error(s).
