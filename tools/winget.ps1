# Generate reviewable WinGet manifests from the published setup executable, never from a local rebuild.
# .\tools\winget.ps1 -Version 1.2.1
# Validate with: winget validate --manifest tools\winget\1.2.1
# Submit the generated directory to microsoft/winget-pkgs after validation.
param(
    [Parameter(Mandatory)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$Version,
    [string]$OutputDirectory = (Join-Path $PSScriptRoot "winget\$Version")
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$id = 'fourthbreakfast.planner'
$schema = '1.28.0'
$url = "https://github.com/fourthbreakfast/planner/releases/download/v$Version/planner-$Version-setup.exe"
$download = Join-Path ([IO.Path]::GetTempPath()) ("planner-winget-$([guid]::NewGuid()).exe")
try {
    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $download
    $sha = (Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash
} finally {
    if (Test-Path -LiteralPath $download) { Remove-Item -LiteralPath $download }
}
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
InstallerType: inno
Scope: user
UpgradeBehavior: install
InstallModes:
- interactive
- silent
- silentWithProgress
ProductCode: '{7d1a5b0e-3c6f-4e0b-9a52-0f1c8e2d4b61}_is1'
AppsAndFeaturesEntries:
- DisplayName: planner
  Publisher: fourthbreakfast
  ProductCode: '{7d1a5b0e-3c6f-4e0b-9a52-0f1c8e2d4b61}_is1'
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
ShortDescription: Tasks, agenda, habits and notes, together on your own devices.
Tags:
- tasks
- todo
- calendar
- notes
- habits
- syncthing
ReleaseNotesUrl: https://github.com/fourthbreakfast/planner/releases/tag/v$Version
ManifestType: defaultLocale
ManifestVersion: $schema
"@
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
foreach ($name in $files.Keys) {
    [IO.File]::WriteAllText((Join-Path $OutputDirectory $name), $files[$name] + "`n", (New-Object Text.UTF8Encoding $false))
}
Write-Host "WinGet manifests written to $OutputDirectory"
