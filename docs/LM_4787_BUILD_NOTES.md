# LM 4.7.87 build notes: spars and wars on the HUD instead of chat

Branch `lm-4787-hud-spar-war`, stacked on 4.7.86. The task asked for 4.7.86, but that version number was already used by
the height-budget build, and version numbers are never reused.

**Presentation only.** No spar, war, wager, settle, sign-up length, TP or currency change.

**Who still gets chat.** A player with the Legacy Mechanics client and the HUD switch on gets each line once, on the
HUD. HUD-off and bridge (KaiPath) players get the same chat line as before, word for word.

## What moved to the HUD

| Was chat | On the HUD now | Tap opens |
|---|---|---|
| Spar session start card | the fight pill shows at once, plus a TP toast "Session started with X" and the tip | Sparring |
| Spar end summary, with TP earned | one TP toast with the same lines | — |
| Daily streak ("Daily training secured. Streak N days") | a toast on the spar chip | — |
| Spar Board post to its target, and accept to the poster | a War toast | Spar Board / Sparring |
| Wager winnings paid at login | the Coins toast (same amount and format) | — |
| Dojo login nudge | a War toast | Dojo Home |
| War requests to the master and elites | a War pill, held 15 s | Dojo > Wars |
| War accepted / the 60-second sign-up | a War pill with the countdown and signed-up counts; it refreshes each second and hides when sign-up closes | Dojo > Wars |
| War announcements (sign-up closed, rules, next bout, Fight!, bout results, winner, called off) | War toasts | Dojo > Wars |
| Mentor and apprentice invites | a War pill | Bond invites (Accept and Decline live there) |
| Bond replies (accepted, denied, cancelled) | War toasts | Training bonds |

Live bouts stay on the fight pill, as before. Perfect Training, the recover countdown and combo-broken were already
chip-only for HUD players.

## How

- **`LmHud.WAR`** is a new element, an event element that always sends.
- **The client draws War in the top row**, beside the fight pill. It waits for room, like the elite and boost chips,
  so on a small window (GUI 4) the core pills keep their place: Underdog, fight, death penalty and TP notice.
- **`LmHud.toastOrChat`** gives a HUD player the toast and everyone else the chat line. `LmHud.chatLines` makes the
  HUD lines from the chat line, with the same words and colors, minus the `[Tag]` and the card chrome.
- **Login notices** (winnings, nudge) wait up to 5 s for the client's hello, so an LM client gets the toast, not chat.
  Bridge clients get chat at once.
- **Mentor invites use the War element** as a general dojo toast. The bonds live under Dojo, Instinct is rival-only,
  Coins is currency and TP is TP.

## Proof

- **`build.sh`:** every audit passes, including the six § lm 4.7.87 checks.
- **Release check, run 1** (`DMZRig/results/release-4.7.87-run1`): 332 PASS, 1 FAIL.
  - The new no-doubling matrix passed **20 of 20**. It covers spar start, end and streak; board post; wager winnings
    and the dojo nudge; war declare, accept and sign-up, and result; and mentor invite. Each was checked for a HUD
    player, a HUD-off player and a bridge player: once on the HUD, once in chat, never both.
  - The one failure: at GUI 4 with every element up, the War pill pushed the death penalty and TP notice out of the
    top row. Fixed (War waits for room).
- **The scripted re-run** after the fix (`DMZRig/results/release-4.7.87`): **333 PASS, 0 FAIL, 0 failing rows**. Both matrices pass (22/22 elements, 20/20 spar and war lines), and at GUI 4 the HUD draws Underdog, fight, penalty, TP, Momentum, coins and Instinct, with War, elite, boost, spar and Potential waiting for room.

## Still owed (in game)

- Every element at GUI scale 3 and 4, in Full and Lite: the War pill above all, its sign-up countdown, and the toasts.
- The bridge fallback: chat lines unchanged.
- The HUD switch off on the More tab brings the chat back.
