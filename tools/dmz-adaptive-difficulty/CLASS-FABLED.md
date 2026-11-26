# Fighting class Fabled + LuckPerms sync

When `enableClassPermissionSync` is on, LegacyMechanics:

1. **Discovers every fighting class** from:
   - `config/dragonminez/races/*/stats.json` → `classes` keys (union across all races)
   - Fabled classes **not** in group `race` or `prestige`
   - Optional overrides in `config/legacymechanics/class-fabled.json`
2. **Ensures Fabled skill stubs** exist for each class (`ClassSkillSync`):
   - Base skill: `plugins/Fabled/dynamic/skill/<Name>.yml` with `needs-permission: true`
   - Prestige marker: `<Name> Prestige` (for permanent LP lock after prestige buy)
3. **Grants LuckPerms** when the player picks that DMZ class (`ClassPermissionSync`):
   - `fabled.skill.<normalized-name>` (spaces → hyphens, lowercase)
   - Prestige lock: owning `<Class> Prestige` at L≥1 keeps the base permission forever

## Custom classes

Add a class to any race `stats.json`:

```json
"classes": {
  "shadow_knight": { "baseStats": { ... } }
}
```

On next boot / `/lm difficulty reload`:

- `class-fabled.json` gains a row (`shadow_knight` → Fabled skill **Shadow Knight**)
- YAML stubs are created if missing
- LP node: `fabled.skill.shadow-knight`

Override display / Fabled skill name in `class-fabled.json`:

```json
{
  "classes": [
    {
      "id": "shadow_knight",
      "fabledSkill": "Shadow Knight",
      "displayName": "Shadow Knight",
      "prestigeSkill": "Shadow Knight Prestige"
    }
  ]
}
```

Legacy ids without underscores (`martialartist`) still resolve via built-in map → **Martial Artist**.

## Toggle

`enableClassPermissionSync` in `config/legacymechanics.json` (same flag as legacy `DMZ Class Permission.js`).

## Telemetry

- `class_perm_grant` — LP node granted for current class
