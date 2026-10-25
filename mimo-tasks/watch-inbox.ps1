# Mimo task inbox watcher: polls GitHub for new task files and runs each one with Claude Code.
# Start it from the repo root and leave the window open:
#   powershell -ExecutionPolicy Bypass -File .\mimo-tasks\watch-inbox.ps1
# Stop with Ctrl+C. Needs: Claude Code (`claude`, signed in), Git for Windows, and Java 17 for builds.

$ErrorActionPreference = 'Continue'
$repoDir  = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$inboxDir = Join-Path $repoDir 'mimo-tasks\inbox'
$logDir   = Join-Path $repoDir 'mimo-tasks\.logs'
$seenFile = Join-Path $repoDir 'mimo-tasks\.seen.txt'
$pollSeconds = 300

Set-Location $repoDir
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

if (-not (Get-Command claude -ErrorAction SilentlyContinue)) {
    Write-Host "Claude Code is not installed or not on PATH. Install it with:" -ForegroundColor Red
    Write-Host "  irm https://claude.ai/install.ps1 | iex" -ForegroundColor Red
    Write-Host "then run 'claude' once to sign in, and start this watcher again." -ForegroundColor Red
    exit 1
}

# Tools Claude Code may use without asking. Anything else is denied, so a task cannot run
# arbitrary programs: file edits, git, the repo's own build/audit scripts, and Java/Python.
$allowedTools = @(
    'Read', 'Edit', 'Write', 'Glob', 'Grep',
    'Bash(git *)',
    'Bash(bash tools/*)', 'Bash(bash scripts/*)',
    'Bash(python3 *)', 'Bash(python *)', 'Bash(py *)',
    'Bash(java *)', 'Bash(javac *)', 'Bash(javap *)', 'Bash(jar *)', 'Bash(unzip *)',
    'Bash(ls *)', 'Bash(cat *)', 'Bash(grep *)', 'Bash(find *)', 'Bash(sha256sum *)', 'Bash(mkdir *)', 'Bash(mv *)'
)

$seen = @{}
if (Test-Path $seenFile) { Get-Content $seenFile | ForEach-Object { if ($_) { $seen[$_] = $true } } }

Write-Host "Watching mimo-tasks/inbox/ every $($pollSeconds / 60) minutes with Claude Code. Ctrl+C to stop." -ForegroundColor Green

while ($true) {
    try {
        git pull --quiet --rebase --autostash 2>$null

        Get-ChildItem (Join-Path $inboxDir '*.md') -ErrorAction SilentlyContinue |
            Sort-Object Name |
            ForEach-Object {
                $name = $_.Name
                if ($seen.ContainsKey($name)) { return }

                Write-Host "`n[$(Get-Date -Format 'HH:mm')] New task: $name" -ForegroundColor Yellow
                $log = Join-Path $logDir ($name -replace '\.md$', '.log')
                $prompt = "Read and carry out the task in mimo-tasks/inbox/$name. Follow the repo's rules in docs/ " +
                          "(never deploy to or touch the live server, never modify the KaiPath jar). When finished, " +
                          "move the task file to mimo-tasks/done/, write the report the task asks for, commit, and push."

                claude -p $prompt --permission-mode acceptEdits --allowedTools @allowedTools 2>&1 |
                    Tee-Object -FilePath $log
                Write-Host "Exit code $LASTEXITCODE. Log: $log" -ForegroundColor Cyan

                $seen[$name] = $true
                Add-Content -Path $seenFile -Value $name
            }
    } catch {
        Write-Host "Poll error: $_" -ForegroundColor Red
    }
    Start-Sleep -Seconds $pollSeconds
}
