# Scaling × form-band audit

Validates combat scaling against the 2.3.171 telemetry band taxonomy.

## 1) Java ↔ Python band thresholds

- ✅ base gate ≤1.12 — py=1.12 java=1.12
- ✅ awakened <6 (MEGA_FORM_START) — py=6.0 java=6.0
- ✅ super <15 — py=15.0 java=15.0
- ✅ ultra <22 — py=22.0 java=22.0
- ✅ divine <50 — py=50.0 java=50.0
- ✅ enhancement <80 (MEGA_FORM_TARGET) — py=80.0 java=80.0
- ✅ Java logs formBand via BalanceTelemetry
- ✅ T3 pierce at formBoost≥6 (super+)
- ✅ Mega-form path at formBoost≥6

## 2) Sim ladder per band (saiyan warrior, even build)

| band | form | fb | T3 hit | T5 hit | T7 hit | T5 land | T7 land |
|------|------|---:|-------:|-------:|-------:|--------:|--------:|
| base | `(no transform)` | 1.0 | 0.261 | 0.353 | 0.430 | 0.219 | 0.217 |
- ✅ sim base T5 ≥ concept min — 0.353
- ✅ sim base T7 ≥ concept min — 0.430
| awakened | `ssgrades.supersaiyangrade2` | 5.6 | 0.287 | 0.389 | 0.473 | 0.229 | 0.225 |
| super | `legendaryforms.ssjhybrid` | 12.5 | 0.339 | 0.397 | 0.483 | 0.233 | 0.229 |
- ✅ sim super T5 hitFrac ≤ soft-cap+0.02 — 0.397 cap=0.44
- ✅ sim super T7 hitFrac ≤ soft-cap+0.02 — 0.483 cap=0.6
| ultra | `androidforms.ssdroid2` | 21.8 | 0.265 | 0.358 | 0.436 | 0.214 | 0.212 |
- ✅ sim ultra T5 hitFrac ≤ soft-cap+0.02 — 0.358 cap=0.44
- ✅ sim ultra T7 hitFrac ≤ soft-cap+0.02 — 0.436 cap=0.6
| divine | `godforms.supersaiyanblue` | 49.1 | 0.339 | 0.414 | 0.565 | 0.241 | 0.236 |
- ✅ sim divine T5 hitFrac ≤ soft-cap+0.02 — 0.414 cap=0.44
- ✅ sim divine T7 hitFrac ≤ soft-cap+0.02 — 0.565 cap=0.6
- ✅ sim divine T5 post-DEF ≥12% — postDef~=0.145
- ✅ sim divine T5 landing ≥ concept min — land=0.241
- ❌ sim divine T7 landing ≥ concept min: land=0.236
| enhancement | `androidforms.ssdroid4` | 54.0 | 0.339 | 0.414 | 0.565 | 0.219 | 0.216 |
- ✅ sim enhancement T5 hitFrac ≤ soft-cap+0.02 — 0.414 cap=0.44
- ✅ sim enhancement T7 hitFrac ≤ soft-cap+0.02 — 0.565 cap=0.6
- ✅ sim enhancement T5 post-DEF ≥12% — postDef~=0.145
- ❌ sim enhancement T5 landing ≥ concept min: land=0.219
- ❌ sim enhancement T7 landing ≥ concept min: land=0.216

- ✅ divine sim T3≤T4≤T5≤T6≤T7 hitFrac — T3=0.339 T5=0.414 T7=0.565
- ❌ divine sim T5 landing < T7 landing: T5=0.241 T7=0.236
- ✅ divine T5 hitFrac ≥ base T5 ×0.95 — divine=0.414 base=0.353

## 3) Live telemetry vs sim (post-Sep 3 slice)

_No recent telemetry found — sim-only checks above._

## 4) Pack form catalog per band

- ✅ saiyan catalog has base forms (or synthetic) — n=0
- ✅ saiyan catalog has awakened forms — n=7
- ✅ saiyan catalog has super forms — n=4
- ✅ saiyan catalog has ultra forms — n=5
- ✅ saiyan catalog has divine forms — n=5
- ✅ saiyan catalog has enhancement forms — n=1
- ✅ saiyan catalog has apex forms (optional) — n=0

## Result

**ADVISORY** — 4 issue(s) (see live vs sim if mixed-jar):
- ❌ sim divine T7 landing ≥ concept min: land=0.236
- ❌ sim enhancement T5 landing ≥ concept min: land=0.219
- ❌ sim enhancement T7 landing ≥ concept min: land=0.216
- ❌ divine sim T5 landing < T7 landing: T5=0.241 T7=0.236
