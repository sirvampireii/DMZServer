# LegacyMechanics concept audit (2.3.131)

Fail-closed checks against the player's stated balance concept.

## 1) Tier ladder (even build)

- ✅ stock percents 21→200%
- ✅ stock tankDamageHealthRatio 0.28 — got 0.28
- ✅ even T1→T5 hitFrac rises ≥1.4× — T1=0.129 T5=0.353
- ✅ even T5→T7 hitFrac rises ≥1.10× — T5=0.353 T7=0.430
- ✅ even T5 unprotected ≥25% bag (KP recommended) — hitFrac=0.353
- ✅ even T7 unprotected ≥39% bag — hitFrac=0.430

## 2) Dump builds feel the ladder (not shrug)

- ✅ vit_dump T5 ≥ 28% bag — hitFrac=0.353
- ✅ vit_dump T5 ≥ 58% of even — 0.353 vs even 0.353
- ✅ vit_dump T7 > T1 ×1.8 — T1=0.020 T7=0.430
- ✅ vit_dump T5 post-DEF ≥ 10% live — postDef~=0.124
- ✅ res_dump T5 ≥ 28% bag — hitFrac=0.353
- ✅ res_dump T5 ≥ 58% of even — 0.353 vs even 0.353
- ✅ res_dump T7 > T1 ×1.8 — T1=0.065 T7=0.430
- ✅ res_dump T5 post-DEF ≥ 10% live — postDef~=0.124
- ✅ str_dump T5 ≥ 28% bag — hitFrac=0.353
- ✅ str_dump T5 ≥ 58% of even — 0.353 vs even 0.353
- ✅ str_dump T7 > T1 ×1.8 — T1=0.068 T7=0.430
- ✅ str_dump T5 post-DEF ≥ 10% live — postDef~=0.124
- ✅ pwr_dump T5 ≥ 28% bag — hitFrac=0.353
- ✅ pwr_dump T5 ≥ 58% of even — 0.353 vs even 0.353
- ✅ pwr_dump T7 > T1 ×1.8 — T1=0.068 T7=0.430
- ✅ pwr_dump T5 post-DEF ≥ 10% live — postDef~=0.124
- ✅ tank class T5 ≥ 28% bag — hitFrac=0.353
- ✅ tank class T5 ≥ 58% of even — tank=0.353 even=0.353

## 3) Skills matter

- ✅ KP10 reduces painted + post-mit pressure at T5 — none=0.353 kpPre=0.318 afterKp=0.286
- ✅ Ki Infusion sponges more pack HP — none=874 inf=1119
- ✅ Potential Unlock sponges when transformed — androidforms.ssdroid4 none=1506 pu=1686
- ✅ KP still saves on god form — pre=0.414 afterKp=0.337

## 4) God forms do not out-tank (incl. DMZ DEF-cancel)

- ✅ god-form T5 post-DEF ≥12% (androidforms.ssdroid4) — pre=0.414 postDef~=0.145 ×54.0
- ✅ SSJG T5 post-DEF ≥12% live — pre=0.414 postDef~=0.145
- ✅ SSJG T7 clears cancel or landing ≥30% — wouldCancel=True dmg=423 flatMit=2442 landingFrac=0.440
- ✅ SSJG T7 landing safety-net ≥30% bag — landingFrac=0.440
- ✅ SSJB T5 post-DEF ≥12% live — pre=0.414 postDef~=0.145
- ✅ SSJB T7 clears cancel or landing ≥30% — wouldCancel=True dmg=423 flatMit=4775 landingFrac=0.440
- ✅ SSJB T7 landing safety-net ≥30% bag — landingFrac=0.440

## 5) Melee AD parity (feature gates)

- ✅ painted melee shock/slam
- ✅ Awakened+ chase speed
- ✅ Adaptive AI speed from Enhanced+

## 6) Version / formula revision

- ✅ VERSION 4.6.236
- ✅ Fabled race skill bridge removed
- ✅ formula revision 45
- ✅ KP hit-cap relief wired
- ✅ DEF/enchant paint relief
- ✅ paintEase cap-path split
- ✅ hpFloorStrength present
- ✅ T1–T3 god-form floors (1.0.12 rollback)
- ✅ paintEase DMZ level ramp
- ✅ counter strength from T4
- ✅ T7 incoming soft-cap via profile
- ✅ T5 soft-cap 44%
- ✅ T6 soft-cap 48%
- ✅ T3 soft-cap ≤ T4
- ✅ T4 soft-cap ≤ T5
- ✅ T5 soft-cap ≤ T6
- ✅ T6 soft-cap ≤ T7
- ✅ landFrac ladder progressive T4<T5<T6<T7
- ✅ god soft-cap ladder T3≤T4 — T3=0.323 T4=0.377
- ✅ god soft-cap ladder T4≤T5 — T4=0.377 T5=0.414
- ✅ god soft-cap ladder T5≤T6 — T5=0.414 T6=0.452
- ✅ god soft-cap ladder T6≤T7 — T6=0.452 T7=0.565
- ✅ god landing T5 < T6 (buy matters) — T5=0.360 T6=0.400
- ✅ god landing T4 < T5 — T4=0.211 T5=0.360
- ✅ god landing ≤ soft-cap T5 — landing=0.360
- ✅ god landing ≤ soft-cap T6 — landing=0.400
- ✅ combat telemetry present
- ✅ god-form landing T1≥3% — landingFrac=0.034
- ✅ god-form landing T5≥22% — landingFrac=0.360
- ✅ god-form landing T7≥30% — landingFrac=0.440
- ✅ god-form landing T7>T1×2.5 — T1=0.034 T7=0.440

## 7) DMZ passthrough NBT clear (all tiers)

- ✅ NBT clear unconditional on AD mob hits (2.3.164)
- ✅ onDamageDone has no activeTier gate before NBT clear
- ✅ god-form KP10: T1–T7 can hit DMZ cancel passthrough — tiers=[1, 2, 3, 4, 5, 6, 7]

## Sample numbers (saiyan warrior)

| Build | T1 | T5 | T5 post-DEF | T7 | vs even T5 |
|-------|---:|---:|------------:|---:|-----------:|
| even | 0.129 | 0.353 | 0.124 | 0.430 | 1.00× |
| vit_dump | 0.020 | 0.353 | 0.124 | 0.430 | 1.00× |
| res_dump | 0.065 | 0.353 | 0.124 | 0.430 | 1.00× |
| str_dump | 0.068 | 0.353 | 0.124 | 0.430 | 1.00× |
| pwr_dump | 0.068 | 0.353 | 0.124 | 0.430 | 1.00× |
| tank class | — | 0.353 | 0.124 | — | 1.00× |

| Form | hitFrac | post-DEF | softHits |
|------|--------:|---------:|---------:|
| androidforms.ssdroid4 | 0.414 | 0.145 | 0.79 |

**Result:** PASS — 72 ok, 0 error(s).
