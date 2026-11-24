<#
  run.ps1 - builds and checks the LM GuiKit corpus.

    .\run.ps1                 pages -> specs, GuiKit on today's real layout, GuiKit on the corpus, mockups\ refreshed
    .\run.ps1 -NoMockups      the same, but leaves mockups\ alone

  1. gen\build_corpus.py writes tools\specs\*.json (the corpus), tools\real\guikit-real.json (today's layout) and
     findings\layout-findings.md.
  2. GuiKit checks today's real layout; its report goes into the findings (second generator pass).
  3. GuiKit checks the corpus. Exit code = GuiKit's on the corpus: 0 fine, 1 a screen fails, 2 not done.
  4. The kept mockups (1920x1080, GUI 2, jpg) and an index go to mockups\.
  Needs: mod-toolkit next to DMZServer (..\..\..\mod-toolkit) with the guikit-src\ files in its fx\, Python 3 with
  Pillow, Minecraft 1.20.1 installed (CurseForge: %USERPROFILE%\curseforge\minecraft\Install).
#>
param([switch]$NoMockups)
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$guikit = Join-Path $here '..\..\..\mod-toolkit\scripts\guikit.ps1'
if (-not (Test-Path -LiteralPath $guikit)) { throw "no mod-toolkit at $guikit" }
$env:PYTHONUTF8 = '1'
$preview = Join-Path $here 'build\preview\guikit'
New-Item -ItemType Directory -Force (Join-Path $here 'build') | Out-Null

& python (Join-Path $here 'gen\build_corpus.py')
if ($LASTEXITCODE -ne 0) { throw 'build_corpus.py failed' }

# today's real layout (its own pass: GuiKit always writes to build\preview\guikit)
& $guikit -Project $here -Spec (Join-Path $here 'tools\real\guikit-real.json') | Select-Object -First 1
Copy-Item -LiteralPath (Join-Path $preview 'report.txt') -Destination (Join-Path $here 'build\real-report.txt') -Force
& python (Join-Path $here 'gen\build_corpus.py') | Out-Null

# the corpus
Remove-Item -LiteralPath $preview -Recurse -Force -ErrorAction SilentlyContinue
& $guikit -Project $here
$code = $LASTEXITCODE
if (-not $NoMockups) {
    $mock = Join-Path $here 'mockups'
    New-Item -ItemType Directory -Force $mock | Out-Null
    Get-ChildItem -LiteralPath $mock -File | Remove-Item -Force
    Get-ChildItem -LiteralPath $preview -File | Where-Object { $_.Extension -in '.jpg', '.html', '.txt' } | Copy-Item -Destination $mock
}
exit $code
