# Legt das GitHub-Repo Flexy06/Widgetwerk an, hinterlegt den Signierschlüssel als Secrets und pusht.
# Danach baut GitHub Actions bei jedem Push auf main eine signierte APK und veröffentlicht sie als Release.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$repo = 'Flexy06/Widgetwerk'

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host 'GitHub CLI not found - installing via winget...' -ForegroundColor Yellow
    winget install --id GitHub.cli -e --accept-source-agreements --accept-package-agreements
    $env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
}
gh auth status 2>$null
if ($LASTEXITCODE -ne 0) { gh auth login --web --git-protocol https }

# Werte aus keystore.properties lesen
$props = @{}
Get-Content keystore.properties | ForEach-Object { if ($_ -match '^([^=#]+)=(.*)$') { $props[$matches[1].Trim()] = $matches[2].Trim() } }

# Lokales Git-Repo
if (-not (Test-Path .git)) { git init -b main | Out-Null }
git add -A
git diff --cached --quiet
if ($LASTEXITCODE -ne 0) {
    $msg = if (git rev-parse --verify -q HEAD) { "Widgetwerk: update" } else { "Widgetwerk: initial widget pack + release workflow" }
    git commit -q -m $msg -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`nClaude-Session: https://claude.ai/code/session_016CFLFQjt9b8Pfr1tjppuH8"
}

# Repo anlegen (privat), falls es noch nicht existiert
try { gh repo view $repo 2>$null | Out-Null } catch { $LASTEXITCODE = 1 }
if ($LASTEXITCODE -ne 0) {
    gh repo create $repo --private --description 'Material 3 Expressive widget pack for Android with real frosted-glass blur' --source . --remote origin
} elseif (-not (git remote | Select-String -Quiet '^origin$')) {
    git remote add origin "https://github.com/$repo.git"
}

# Secrets VOR dem ersten Push setzen, damit schon der erste Build richtig signiert ist
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $PSScriptRoot $props['storeFile'])))
gh secret set KEYSTORE_BASE64 -R $repo --body $b64
gh secret set SIGNING_STORE_PASSWORD -R $repo --body $props['storePassword']
gh secret set SIGNING_KEY_PASSWORD -R $repo --body $props['keyPassword']
gh secret set SIGNING_KEY_ALIAS -R $repo --body $props['keyAlias']

git push -u origin main

Write-Host ''
Write-Host "Done! Build is running: https://github.com/$repo/actions" -ForegroundColor Green
Write-Host "APKs appear under:      https://github.com/$repo/releases" -ForegroundColor Green
Start-Sleep -Seconds 4
gh run watch -R $repo --exit-status
Read-Host 'Press Enter to close'
