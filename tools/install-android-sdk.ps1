# installs the pinned android sdk pieces, no admin needed, move the numbers forward on purpose (see android/README.md)
# licenses are not accepted here, accept them once by hand (the android studio first run does it)
# uses the android cli from the command line tools with --no-metrics, so nothing is reported to google
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue' # the progress bar makes powershell downloads very slow

$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$toolsZip = 'https://dl.google.com/android/repository/commandlinetools-win-16111833_latest.zip'
$toolsSha256 = 'e5885e2e59038c0778a85fc19f01de7ce7c567c105553f7617b936b1e0e87b2b'
$packages = @('platform-tools', 'platforms/android-37.0@2.0.0', 'build-tools/36.0.0@36.0.0')

$android = Join-Path $sdk 'cmdline-tools\latest\bin\android.exe'
if (-not (Test-Path $android)) {
    $tmp = Join-Path ([IO.Path]::GetTempPath()) ('cmdline-tools-' + [guid]::NewGuid())
    New-Item -ItemType Directory -Path $tmp | Out-Null
    $zip = Join-Path $tmp 'tools.zip'
    Invoke-WebRequest -Uri $toolsZip -OutFile $zip -UseBasicParsing
    $actual = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLower()
    if ($actual -ne $toolsSha256) { throw "checksum mismatch, got $actual" }
    Expand-Archive $zip -DestinationPath $tmp
    New-Item -ItemType Directory -Force (Join-Path $sdk 'cmdline-tools') | Out-Null
    Move-Item (Join-Path $tmp 'cmdline-tools') (Join-Path $sdk 'cmdline-tools\latest')
    Remove-Item $tmp -Recurse -Force
}

& $android --no-metrics --sdk=$sdk sdk install @packages
if ($LASTEXITCODE -ne 0) { throw "sdk install failed with exit code $LASTEXITCODE" }
& $android --no-metrics --sdk=$sdk sdk list
