<#
  Project wrapper (2026-11-20). The shared tools (Px, Trace, Mesh, ModelPreview, Pose, GeoPreview, PxPass, ItemKit and
  the rest) live in the mod-toolkit folder next to this project (see tools\toolkit.json and the toolkit's README.txt).
  This compiles them together with this project's own Java in this folder and runs one class - same usage as before:
    .\run.ps1 <MainClass> [args...]        e.g.  .\run.ps1 ShinobiItems      .\run.ps1 GeoPreview <geo> <png> <out.png>
  No param() block on purpose: Java options such as -out / -debug must reach Java untouched.
#>
$proj = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)          # tools\fx -> tools -> the project
$cfgFile = Join-Path $proj 'tools\toolkit.json'
if (-not (Test-Path -LiteralPath $cfgFile)) { Write-Output "missing $cfgFile"; exit 1 }
$s = [System.IO.File]::ReadAllText($cfgFile)
if ($s.Length -gt 0 -and $s[0] -eq [char]0xFEFF) { $s = $s.Substring(1) }
$tk = [System.IO.Path]::GetFullPath((Join-Path $proj ($s | ConvertFrom-Json).toolkit))
if (-not (Test-Path -LiteralPath (Join-Path $tk 'fx\run.ps1'))) { Write-Output "mod-toolkit not found at $tk (tools\toolkit.json 'toolkit')"; exit 1 }
$old = $env:MODTOOLKIT_PROJECT
$env:MODTOOLKIT_PROJECT = $proj
try { & (Join-Path $tk 'fx\run.ps1') @args } finally { $env:MODTOOLKIT_PROJECT = $old }
exit $LASTEXITCODE
