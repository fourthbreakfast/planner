# Builds the signed phone apk and Windows setup, and publishes them as one GitHub release.
#   .\tools\release.ps1 0.1.0
# Requires native GitHub CLI (gh), signed in, and Inno Setup 6; no WSL is needed.
# The working tree must be clean and the version tag must already exist on the release repository at this HEAD.
# The original signing key must be at %USERPROFILE%\.planner\release.jks. Keep its backup and password:
# a replacement key cannot update existing installs. Only the public certificate fingerprint goes in the repo.
param([Parameter(Mandatory)][string]$Version)
$ErrorActionPreference = 'Stop'

$root = Split-Path $PSScriptRoot -Parent
# the version comes from android\gradle.properties: the one asked for here has to be the same
$declared = (Select-String -Path "$root\android\gradle.properties" -Pattern '^plannerVersion=(.+)$').Matches[0].Groups[1].Value
if ($declared -ne $Version) { throw "android\gradle.properties says $declared, not $Version" }
$keystore = "$env:USERPROFILE\.planner\release.jks"
if (-not (Test-Path -LiteralPath $keystore -PathType Leaf)) { throw 'the original signing keystore is required; restore it from backup' }
$expectedCertificate = (Get-Content -LiteralPath "$root\android\signing-certificate.sha256" -Raw).Trim().ToLowerInvariant()
if ($expectedCertificate -notmatch '^[a-f0-9]{64}$') { throw 'the public signing certificate pin must be one SHA-256 digest' }

function Read-Secret($prompt) {
    $secure = Read-Host $prompt -AsSecureString
    $handle = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($handle) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($handle) }
}

function Get-ReleaseNotes([string]$Changelog, [string]$ReleaseVersion) {
    if ($Changelog -match "(?ms)^## $([regex]::Escape($ReleaseVersion))(?:[ \t]+[^\r\n]*)?\r?\n(.*?)(?=^## |\z)") {
        return $Matches[1].Trim()
    }
    return $null
}

function Get-RemoteTagCommit([string[]]$Refs, [string]$Tag) {
    $direct = $null
    $peeled = $null
    foreach ($line in $Refs) {
        if ($line -match '^([a-fA-F0-9]{40}|[a-fA-F0-9]{64})\s+(.+)$') {
            if ($Matches[2] -eq "refs/tags/$Tag") { $direct = $Matches[1] }
            if ($Matches[2] -eq "refs/tags/$Tag^{}") { $peeled = $Matches[1] }
        }
    }
    if ($peeled) { return $peeled }
    return $direct
}

function Get-SigningCertificateSha256([string]$KeytoolOutput) {
    # The first certificate under the requested alias is the signer, before any issuer certificates.
    $fingerprint = [regex]::Match($KeytoolOutput, '(?m)^\s*SHA256:\s*((?:[a-fA-F0-9]{2}:){31}[a-fA-F0-9]{2})\s*$')
    if (-not $fingerprint.Success) { throw 'could not read the signing certificate SHA-256 fingerprint' }
    return $fingerprint.Groups[1].Value.Replace(':', '').ToLowerInvariant()
}

$status = git -C $root status --porcelain --ignore-submodules=none
if ($LASTEXITCODE) { throw 'could not inspect the working tree' }
if ($status) { throw 'the working tree must be clean before building a release' }
$head = git -C $root rev-parse HEAD
if ($LASTEXITCODE) { throw 'could not resolve HEAD' }
$repository = 'fourthbreakfast/planner'
$tag = "v$Version"
# Query the same repository that receives the release, even when this checkout's origin is a fork.
$refs = git ls-remote --tags "https://github.com/$repository.git" "refs/tags/$tag" "refs/tags/$tag^{}"
if ($LASTEXITCODE) { throw 'could not inspect the release repository tag' }
$tagCommit = Get-RemoteTagCommit $refs $tag
if (-not $tagCommit) { throw "$tag must already exist on $repository before using this helper" }
if ($tagCommit -ne $head.Trim()) { throw "$tag does not point to this HEAD; refusing to publish artifacts from another commit" }
Get-Command gh -ErrorAction Stop | Out-Null
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"

$previousKeystore = $env:PLANNER_KEYSTORE
$previousPassword = $env:PLANNER_KEYSTORE_PASSWORD
$previousSyncthing = $env:PLANNER_SYNCTHING
$notesFile = $null
try {
    $password = Read-Secret 'signing key password'
    if ($password.Length -lt 6) { throw 'the password needs at least 6 characters' }
    $env:PLANNER_KEYSTORE_PASSWORD = $password
    $certificateInfo = & "$env:JAVA_HOME\bin\keytool.exe" -J-Duser.language=en -J-Duser.country=US -list -v `
        -keystore $keystore -alias planner -storepass:env PLANNER_KEYSTORE_PASSWORD 2>&1
    if ($LASTEXITCODE) { throw 'could not read the original signing key alias' }
    if ((Get-SigningCertificateSha256 ($certificateInfo -join "`n")) -ne $expectedCertificate) {
        throw 'the signing key does not match the certificate used by existing installs'
    }
    $env:PLANNER_KEYSTORE = $keystore

    Push-Location "$root\android"
    try {
        # Run the native sync tests too, rather than silently skipping them without a host binary.
        .\gradlew.bat :desktop:syncthingWindows --no-configuration-cache --console=plain
        if ($LASTEXITCODE) { throw 'building Syncthing failed' }
        $env:PLANNER_SYNCTHING = "$root\android\desktop\build\syncthing\resources\windows\syncthing.exe"
        .\gradlew.bat ktlintCheck :core:test :sync:test :shared:desktopTest :desktop:test testDebugUnitTest lintDebug `
            assembleRelease :desktop:packageSetup --no-configuration-cache --console=plain
        if ($LASTEXITCODE) { throw 'the build failed' }
    } finally { Pop-Location }

    $apk = "$root\android\app\build\outputs\apk\release\planner-$Version.apk"
    Move-Item -Force "$root\android\app\build\outputs\apk\release\app-release.apk" $apk
    $setup = "$root\android\desktop\build\setup\planner-$Version-setup.exe"
    foreach ($artifact in @($apk, $setup)) {
        if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) { throw "missing release artifact: $artifact" }
    }

    # Only this version's changelog section is published, matching the tag workflow.
    $notes = @('--generate-notes')
    $body = Get-ReleaseNotes (Get-Content "$root\CHANGELOG.md" -Raw -ErrorAction SilentlyContinue) $Version
    if ($body) {
        $notesFile = Join-Path $env:TEMP "planner-$Version-$([guid]::NewGuid())-notes.md"
        [System.IO.File]::WriteAllText($notesFile, $body, (New-Object System.Text.UTF8Encoding $false))
        $notes = @('--notes-file', $notesFile)
    } else {
        Write-Host "no section for $Version in CHANGELOG.md, using github's own notes"
    }
    gh release create $tag $apk $setup --repo $repository --verify-tag --title "planner $Version" @notes
    if ($LASTEXITCODE) { throw 'publishing failed' }
    Write-Host "published v$Version"
} finally {
    $env:PLANNER_KEYSTORE = $previousKeystore
    $env:PLANNER_KEYSTORE_PASSWORD = $previousPassword
    $env:PLANNER_SYNCTHING = $previousSyncthing
    $password = $null
    if ($notesFile -and (Test-Path -LiteralPath $notesFile)) { Remove-Item -LiteralPath $notesFile }
}
