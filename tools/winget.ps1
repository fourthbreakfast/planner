# Writes the winget manifest for one release and, when asked, offers it to microsoft/winget-pkgs as a pull request.
#   .\tools\winget.ps1 0.9.0            writes tools\winget\0.9.0 from the release on github
#   .\tools\winget.ps1 0.9.0 -Submit    also opens the pull request, from a fork of winget-pkgs under your account
# The installer's address and checksum come from the published release, so run it after release.ps1.
param([Parameter(Mandatory)][string]$Version, [switch]$Submit)
$ErrorActionPreference = 'Stop'

$root = Split-Path $PSScriptRoot -Parent
$id = 'fourthbreakfast.planner'
$url = "https://github.com/fourthbreakfast/planner/releases/download/v$Version/planner-$Version.msi"
$folder = Join-Path $PSScriptRoot "winget\$Version"
New-Item -ItemType Directory -Force $folder | Out-Null

# the checksum of the file people will download, not of a local build
$download = Join-Path $env:TEMP "planner-$Version.msi"
Invoke-WebRequest $url -OutFile $download -UseBasicParsing
$sha = (Get-FileHash $download -Algorithm SHA256).Hash

$schema = '1.9.0'
$files = @{
    "$id.yaml" = @"
# yaml-language-server: `$schema=https://aka.ms/winget-manifest.version.$schema.schema.json
PackageIdentifier: $id
PackageVersion: $Version
DefaultLocale: en-US
ManifestType: version
ManifestVersion: $schema
"@
    "$id.installer.yaml" = @"
# yaml-language-server: `$schema=https://aka.ms/winget-manifest.installer.$schema.schema.json
PackageIdentifier: $id
PackageVersion: $Version
InstallerType: msi
Scope: user
UpgradeBehavior: install
Installers:
- Architecture: x64
  InstallerUrl: $url
  InstallerSha256: $sha
ManifestType: installer
ManifestVersion: $schema
"@
    "$id.locale.en-US.yaml" = @"
# yaml-language-server: `$schema=https://aka.ms/winget-manifest.defaultLocale.$schema.schema.json
PackageIdentifier: $id
PackageVersion: $Version
PackageLocale: en-US
Publisher: fourthbreakfast
PublisherUrl: https://github.com/fourthbreakfast
PackageName: planner
PackageUrl: https://github.com/fourthbreakfast/planner
License: AGPL-3.0
LicenseUrl: https://github.com/fourthbreakfast/planner/blob/main/LICENSE
ShortDescription: tasks, an agenda and notes, synced between your own devices without an account
Tags:
- tasks
- todo
- calendar
- notes
- syncthing
ReleaseNotesUrl: https://github.com/fourthbreakfast/planner/releases/tag/v$Version
ManifestType: defaultLocale
ManifestVersion: $schema
"@
}
foreach ($name in $files.Keys) {
    [System.IO.File]::WriteAllText((Join-Path $folder $name), $files[$name], (New-Object System.Text.UTF8Encoding $false))
}
Write-Host "wrote $folder"
if (-not $Submit) { return }

# gh is signed in on nixos. the files go onto a branch of your fork of winget-pkgs, then a pull request is opened
function gh { wsl -d NixOS -- gh @args; if ($LASTEXITCODE) { throw "gh $($args[0]) failed" } }
$me = (gh api user --jq .login)
wsl -d NixOS -- gh repo fork microsoft/winget-pkgs --clone=false 2>$null
gh api -X POST "repos/$me/winget-pkgs/merge-upstream" -f branch=master | Out-Null
$base = (gh api "repos/$me/winget-pkgs/git/ref/heads/master" --jq .object.sha)
$branch = "planner-$Version"
gh api -X POST "repos/$me/winget-pkgs/git/refs" -f ref="refs/heads/$branch" -f sha=$base | Out-Null
foreach ($name in $files.Keys) {
    $content = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($files[$name]))
    gh api -X PUT "repos/$me/winget-pkgs/contents/manifests/f/fourthbreakfast/planner/$Version/$name" `
        -f message="planner $Version" -f content=$content -f branch=$branch | Out-Null
}
gh pr create --repo microsoft/winget-pkgs --head "${me}:$branch" --title "New version: $id version $Version" `
    --body "planner $Version, from https://github.com/fourthbreakfast/planner/releases/tag/v$Version"
Write-Host "offered planner $Version to winget"
