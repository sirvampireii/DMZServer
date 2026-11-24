# LM 4.7.72 build notes

Native HUD, Phase 2 of `docs/LM_NATIVE_HUD_PROPOSAL.md`, approved 2026-11-24 as written ("go ahead and make native
HUD"), with the proposal's own answers to its open decisions: Instinct cards replace the chat lines, clicks while
chat is open plus an unbound LM key, the Momentum meter on screen, exactly five elements. Merged to `main` as
2956a351 (branch `lm-4772-native-hud`, off 4.7.71 `main` b8901226). No gameplay, rate or price change.

## What shipped

- Native message 4 (`NativeHudPacket`), hello feature `hud`. Sent only to LM clients that list it and whose HUD switch
  is on (`LmHud.active`); KaiPath and older LM clients get nothing new and keep the boss bars and chat lines.
- Underdog pill (top centre): `ProvingGroundsBar.barParts` split into three lines (grounds; tier, champion and
  battles; role), Legendary in gold. Joined with " - " the lines read exactly as the boss bar. Click: Rival page.
- Rival Instinct cards (right edge, 45% down, 3 at most, 4 s, fade over the last half second): the chat line's text
  without the "Instinct" tag, a symbol per kind, same pulses and cooldowns (`RivalInstinct.deliver`). Click: that
  rival's profile (`go:rival|list_detail:<uuid>`). Instinct off: no cards and no chat, as before.
- TP notice (top centre): the boss bar's text in the notice's colour, one message per notice; the client drains the
  bar over `SHOW_MS` (3 s) and hides it.
- Death TP penalty (top centre, last row): `DeathTpPenalty.barText` and the bar's fraction, the 3-second announcement
  then the countdown, updated once a second. Click: Difficulty.
- Momentum meter (above the hotbar, right of centre), new: "Momentum m:ss" (`Momentum.clock`) and the bank against
  its cap, while time is banked and the player hit or was hit by a difficulty mob in the last 10 s. Click: Difficulty.
- HUD switch on the main menu's More tab ("HUD - ON/OFF", per account in `lm_hud`, default on). OFF hides every
  element and the bars and chat lines come back on the next update.
- Client (`HudOverlay`): chrome baked once per size, GUI scale, skin and accent at screen pixel density (soft shadow
  in Full, flat in Lite), text laid out on arrival or a skin change, no per-frame allocation; hand cursor and clicks
  while chat is open; Controls > Legacy Mechanics > "Open the newest HUD page" (unbound); F1 hides it.
- Build: the LM client step compiles against Minecraft's LWJGL 3.3.1 (compile only; the jar does not bundle it).

## Proof

Jar `LegacyMechanics-4.7.72.jar`, sha256 `43706983564df036cd383bb2229913a49c67b1230d327e2c0c0656459838c1d9`.

- `build.sh`: every audit passes; ship manifest has 10 new 4.7.72 checks; boot self-tests 18 OK including
  `native-hud` (packet round trip, clamps, the bar text rule) and the Momentum meter rules.
- Full matrix, every row PASS: 3666 layouts at GUI 2/3/4, 204 scripted checks, 0 failures; native everywhere; five
  skins at 60+ FPS; layout, scale-to-fit, look; KaiPath-only stage; one spec for both render paths (18 identical,
  386 by LM-client navigation, 8 by the color wheel, 0 other); requireLmClient.
- HUD checks, LM client stage: TP notice, penalty and its end, Underdog through the real grounds check (a rival link
  whose grounds sit under the probe player) and its exit, Instinct card, Instinct off, Momentum meter and its hide,
  HUD OFF brings the TP bar back, the More tab switch reads and saves; the real client reported drawing all five
  elements (`HUD CLIENT all drew underdog,tp,penalty,instinct,momentum: PASS`).
- HUD checks, KaiPath stage: nothing goes to the HUD; the TP, penalty and Underdog boss bars stay; Instinct stays chat.
- Mock (`-Duiprobe.mockset=hud`): the More tab with the switch, the HUD at GUI 2/3/4 and the same moment as boss bars
  and chat lines (12 shots).
- Kit: corpus refreshed from the KaiPath stage (93 menu families, now with the More tab's HUD switch);
  `LmCorpusTest` OK (607 buttons) with the other kit tests (KaiPath 1.5.6 jar read as the base, not modified).

## Gaps and follow-ups

- The top-centre stack starts at the top edge, where vanilla draws boss bars. HUD players no longer get LM's own bars,
  but another mod's boss bar (or a vanilla boss) would sit under the Underdog pill.
- Instinct cards start 45% down the right edge; at GUI 4 on small windows they can meet DMZ's quest tracker when it is
  tall. Not seen in the probe shots (tracker ends about 34% down at 1080p).
- Clicks need the chat screen open (the mouse is captured otherwise); the unbound key is the keyboard route.
- Spar session state and war bout clocks were left out by the proposal; see the full-mod review for candidates.
- First run fix during the build: the death penalty probe check counted the probe's stand-ins and could compare
  across a countdown second; it now filters to the probe player and accepts either side of the second.
