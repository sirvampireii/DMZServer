# Race / class apply audit

## Flow matrix (intended)

| Flow | Snapshot timing | Class apply | Sync |
|------|-----------------|-------------|------|
| Paid class change | Before commit | `/dmzclass` via `onPaidClassChange` | Stats sync |
| Race auto-remap | Before race+class commit | `onServicesRaceChangeApplied` | dmzclass + transform limits + appearance |
| Race + class picker | At race change + UpdateCharacter HEAD | On recustomize complete | same |
| 0% race wipe | CreateCharacter HEAD | `onCharacterCreated` → race hook | same |

## DMZ reference (`/dmzclass`)

- `isValidClass` → `getAllClasses().contains(lowercase)`
- LM sets the class id and clamps pools down. It does not call `restoreMultiplierGains`.

## Static checks

**FAIL** — issues:
- Mixin targets lambda handle not handle return
