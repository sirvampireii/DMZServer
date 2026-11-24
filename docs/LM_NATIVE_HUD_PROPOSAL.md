# LM native HUD: proposal (LM 4.7.71 Part 2, Phase 1)

Status: **proposal, waiting for approval.** No HUD code is written until the owner approves this page (or a changed
version of it). Phase 2 builds exactly what is approved.

## What it replaces

LM shows five things in the vanilla presentation today. All of their state lives on the server and stays there.

| Element | Where it lives | What players see today | Updates |
|---|---|---|---|
| Underdog bonus (Proving Grounds) | `rival/ProvingGroundsBar.java` | A yellow boss bar while you stand in a Proving Grounds claim of one of your own rival pairs: `<grounds> - <Claimed / Dominant / Legendary> - Champion <name> - <n> battles - <role>`. The role is "No champion yet", "You are the champion" or "You are the underdog challenger". Display only; the bonuses themselves (TP x1.25, offense x1.12) are applied elsewhere. | once a second |
| Rival Instinct | `rival/RivalInstinct.java` | Chat lines tagged "Instinct", for mutual or nemesis rivals: arrived nearby, status (BP), is charging ki, transformation / aura surge, is fused, power surged. Per-event cooldowns. A per-player on/off toggle (`instinctEnabled`), defaulting to the config `rivalInstinct`. | pulsed, with cooldowns |
| TP notices | `util/TpBossNotify.java` | A short boss bar coloured by the notice, shown for 3 s (`SHOW_MS`) with the bar draining, with anti-spam cooldowns. | on each notice |
| Death TP penalty | `progression/tp/DeathTpPenalty.java` | One red boss bar per player: an announcement for 3 s (`ANNOUNCE_MS`), then `TP penalty - <m>m <s>s left` counting down the 10 minutes (`DURATION_MS`). | once a second |
| Momentum | `progression/Momentum.java` | Only inside the Difficulty menu (label + bar, `UltraDifficultyScreens.java:200`). Nothing while fighting. | n/a |

## Rules (both phases)

- **The server owns everything.** The client draws what the server sends and nothing else: the same strings the
  boss bar or chat line carries, and the same progress fraction. Every number on screen is identical to the bar or
  chat line it replaces, because it is the same text from the same function.
- **Who gets it.** Only LM clients whose hello lists `hud` (a new hello feature, like `pictures`). Everyone else,
  bridge, KaiPath and older LM clients, keeps the boss bars and chat lines exactly as today. Nobody loses
  information.
- **No doubling.** For a HUD client the server skips that player's boss bar or chat line for an element the HUD
  draws, and only that one.
- **A per-player switch.** A "HUD" setting in the hub's More tab, next to Menu skins: On (default for HUD clients) or
  Off. Off sends that player the boss bars and chat lines again. The Instinct on/off toggle keeps working as today
  in both modes; off hides Instinct everywhere.
- **The 4.7.61 lag rules.** The chrome (the rounded panel, shadow and accent edge) is baked once per size and skin
  into a cached texture, like `MenuBackground`. The text is laid out once, when a new state arrives, not every frame.
  No allocations per frame. Lite draws flat panels with no shadow, and Full draws the rounded panels with depth. The
  elements scale with the GUI and fit at GUI 2, 3 and 4 at 854 x 480 and up.

## The elements

Positions are in GUI pixels at GUI 3 on 1920 x 1080 (640 x 360), anchored to screen edges so they hold at every size.
DragonMineZ's own HUD keeps the top-left (stats) and the top-right (quest tracker), so LM uses the top centre and
the right edge below the quest tracker.

### 1. Underdog bonus (Rival accent, white)
- **Look:** a rounded pill, 200 x 24, in Saiyan Royal chrome with a white Rival edge. Top line: grounds name and
  tier, with the tier in gold for Legendary. Second line: "Champion <name> - <n> battles". When you are the
  challenger, the role ("You are the underdog challenger") shows as a white-edged tag on the right.
- **Position:** top centre, 6 px down, where the boss bar was.
- **Shows:** while the boss bar would show (you are inside one of your own pairs' grounds); hides when you leave.
  Same 1-second updates.
- **Click:** opens your Rival page (`rival` Home).

### 2. Rival Instinct (Rival accent, white)
- **Look:** toast cards, 150 x 22, each with a small symbol for its kind (arrive, status, charging, surge, fusion,
  power) and the same text as the chat line, e.g. "Goku is charging ki!". The rival's name is in gold, as in chat.
- **Position:** the right edge, under DMZ's quest tracker (about 45 % down), stacking downward, 3 at most. A fourth
  pushes out the oldest.
- **Shows:** each card for 4 s, then it fades, on the same pulses and cooldowns as today. The toggle off: no cards
  and no chat lines (as today).
- **Click:** opens the Rival page for that rival.

### 3. TP notices (the notice's own colour)
- **Look:** a slim pill, 180 x 14, with the notice text and a 2 px bar along its bottom edge draining over 3 s
  (`SHOW_MS`). Its colour comes from the notice, the same mapping as the boss bar colour.
- **Position:** top centre, under the underdog pill if that is showing, otherwise at the top.
- **Shows:** on each notice, with the same anti-spam cooldowns as today; a new notice replaces the current one.
- **Click:** none; it is a passing notice.

### 4. Death TP penalty (Difficulty accent, crimson)
- **Look:** a pill, 170 x 16, with a crimson edge: the 3-second announcement first, then "TP penalty - 9m 12s left"
  with a bar draining over the 10 minutes.
- **Position:** top centre, the last row of the top stack.
- **Shows:** from death until the penalty ends; same text and timing as the bar.
- **Click:** opens the Difficulty page.

### 5. Momentum (Difficulty accent, crimson): new on-screen, numbers from the menu
- **Look:** a compact meter, 90 x 10: "Momentum 0:42" with a bar. These are the same label, clock and fraction the
  Difficulty menu shows (`Momentum.clock`, banked time against the bank cap).
- **Position:** just above the hotbar, right of centre, clear of the XP bar and DMZ's bars.
- **Shows:** while you have Momentum banked and you were in a fight in the last 10 s (you hit or were hit by a
  qualifying mob); it hides 10 s after the fight ends, or when the bank is empty.
- **Click:** opens the Difficulty page.

### Clicks
The HUD can only be clicked while the mouse is free. The proposal: elements take clicks while the chat screen is
open, and the cursor turns into a hand over a clickable element. Optionally, an LM key (unbound by default) opens the
page of the newest element.

## The wire (Phase 2)

- **Message:** native message 4 (`MSG_HUD`), server to client. Each update names one element and carries its text
  lines (the exact strings the bar or chat line uses), a progress fraction, a colour, the page a click opens, and how
  long it lasts. "Hide" clears an element. It is sent only on change, at the same cadence as today's bars (1/s at
  most per element).
- The client keeps one state slot per element and draws it from cached layers.
- **KaiPath:** nothing changes on the bridge.

## The mock and the proof (Phase 2)

- **Mock:** UI probe mock mode gets a demo toggle: HUD on draws all five elements together on the real client at
  GUI 2, 3 and 4; HUD off shows the same moment as boss bars and chat lines. Both are shot side by side, as with the
  tutorial mock.
- **Scripted checks:**
  - each element's text on the HUD equals the boss bar or chat line text for the same state;
  - bridge clients still get bars and chat;
  - Instinct off hides the cards;
  - HUD off brings the bars and chat back.

## Decisions for the owner

1. **Instinct:** HUD cards **instead of** the chat lines (proposed), or both?
2. **Clicks:** while the chat screen is open (proposed), plus the optional unbound LM key: yes or no?
3. **Momentum on screen:** it is new (today it is only in the menu). Show it as proposed, or leave it menu-only?
4. **Anything else on the HUD?** Spar session state (timer, opponent) and war bout clocks are candidates. They are
   left out unless you ask, so the scope stays exactly the five elements above.
