# Builds a signed apk and publishes it as a GitHub release.
#   .\tools\release.ps1 0.1.0
# The first run creates the signing key at %USERPROFILE%\.planner\release.jks. Back up that file and its
# password: without them, updates can no longer be installed over the app. Neither ever goes in the repo.
param([Parameter(Mandatory)][string]$Version)
$ErrorActionPreference = 'Stop'

$root = Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$keystore = "$env:USERPROFILE\.planner\release.jks"

function Read-Secret($prompt) {
    $secure = Read-Host $prompt -AsSecureString
    [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}

$password = Read-Secret 'signing key password'
if ($password.Length -lt 6) { throw 'the password needs at least 6 characters' }
$env:PLANNER_KEYSTORE_PASSWORD = $password

if (-not (Test-Path $keystore)) {
    if ((Read-Secret 'once more, this creates the key') -ne $password) { throw 'the passwords differ' }
    New-Item -ItemType Directory -Force (Split-Path $keystore) | Out-Null
    & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -keystore $keystore -alias planner -keyalg RSA -keysize 4096 -validity 36500 `
        -dname 'CN=fourthbreakfast' -storepass:env PLANNER_KEYSTORE_PASSWORD -keypass:env PLANNER_KEYSTORE_PASSWORD
    Write-Host "created $keystore, back it up with the password"
}
$env:PLANNER_KEYSTORE = $keystore

Push-Location "$root\android"
try { .\gradlew.bat :core:test testDebugUnitTest assembleRelease --no-configuration-cache --console=plain; if ($LASTEXITCODE) { throw 'the build failed' } }
finally { Pop-Location }

$apk = "$root\android\app\build\outputs\apk\release\planner-$Version.apk"
Move-Item -Force "$root\android\app\build\outputs\apk\release\app-release.apk" $apk

# the release notes are this version's part of CHANGELOG.md; without one, github writes its own
$notes = @('--generate-notes')
$changelog = Get-Content "$root\CHANGELOG.md" -Raw -ErrorAction SilentlyContinue
if ($changelog -match "(?ms)^## $([regex]::Escape($Version))[^\r\n]*\r?\n(.*?)(?=^## |\z)") {
    $notesFile = Join-Path $env:TEMP "planner-$Version-notes.md"
    [System.IO.File]::WriteAllText($notesFile, $Matches[1].Trim(), (New-Object System.Text.UTF8Encoding $false))
    $notes = @('--notes-file', (wsl -d NixOS -- wslpath -a ($notesFile -replace '\\', '/')))
} else {
    Write-Host "no section for $Version in CHANGELOG.md, using github's own notes"
}

# gh is signed in on nixos
$linuxApk = (wsl -d NixOS -- wslpath -a ($apk -replace '\\', '/'))
wsl -d NixOS -- gh release create "v$Version" $linuxApk --repo fourthbreakfast/planner --title "planner $Version" @notes
if ($LASTEXITCODE) { throw 'publishing failed' }
Write-Host "published v$Version"
