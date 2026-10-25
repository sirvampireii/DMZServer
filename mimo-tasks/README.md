# Mimo task inbox

Build tasks are dropped here as standalone markdown files. A watcher on the owner's PC pulls the
repo every 5 minutes and runs each new file with Claude Code, which edits the local repo, builds,
commits, and pushes. Results land in `done/`.

## Start the watcher (Windows PowerShell, from the repo root)

```powershell
powershell -ExecutionPolicy Bypass -File .\mimo-tasks\watch-inbox.ps1
```

Leave the window open. Requirements: Claude Code installed and signed in (`irm https://claude.ai/install.ps1 | iex`,
then run `claude` once), Git for Windows, and Java 17 for `build.sh`.

## Rules for tasks

- One task per file, readable without any chat context.
- Name files `YYYY-MM-DD-short-topic.md`; they run in name order.
- Tasks never deploy to or touch the live server, and never modify the KaiPath jar.
- Each task says what to report; the report goes in `done/<task-name>.result.md`.
- Anyone with push access can add a task, and it runs on the owner's PC with the owner's git login.

Local only (gitignored): `.seen.txt` (tasks already run) and `.logs/` (Claude Code output per task).
