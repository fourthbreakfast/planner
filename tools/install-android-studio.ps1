# installs the pinned android studio, move the version forward on purpose (see android/README.md)
$version = '2026.2.2.8'
winget install --id Google.AndroidStudio --exact --version $version --accept-package-agreements --accept-source-agreements
