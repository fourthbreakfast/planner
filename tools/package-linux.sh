#!/usr/bin/env bash
# Build the Linux x86-64 release packages on Linux. The .deb uses the desktop's software installer for updates.
set -euo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
if [[ $(uname -s) != Linux || $(uname -m) != x86_64 ]]; then
  echo 'Linux release packaging requires an x86-64 Linux host.' >&2
  exit 1
fi
version=$(sed -n 's/^plannerVersion=//p' "$root/android/gradle.properties" | tr -d '\r')
[[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo 'Invalid planner version.' >&2; exit 1; }
cd "$root/android"
bash ./gradlew :desktop:packageDeb :desktop:packageLinuxTar --no-configuration-cache --console=plain
shopt -s nullglob
deb_dir=desktop/build/compose/binaries/main/deb
deb="$deb_dir/fourthbreakfast-planner_${version}_amd64.deb"
if [[ ! -f $deb ]]; then
  echo "Missing generated Debian package: $deb" >&2
  available=("$deb_dir/"*.deb)
  if (( ${#available[@]} )); then
    printf 'Available .deb: %s\n' "${available[@]}" >&2
  else
    echo "No .deb files found in $deb_dir." >&2
  fi
  exit 1
fi
for field in Package Version Architecture; do
  case $field in
    Package) expected=fourthbreakfast-planner ;;
    Version) expected=$version ;;
    Architecture) expected=amd64 ;;
  esac
  actual=$(dpkg-deb --field "$deb" "$field")
  if [[ $actual != "$expected" ]]; then
    echo "Invalid $field in $deb: expected '$expected', got '$actual'." >&2
    exit 1
  fi
done
stage=$(mktemp -d)
trap 'rm -rf -- "$stage"' EXIT
dpkg-deb --raw-extract "$deb" "$stage"
python3 - "$stage" <<'PY'
from pathlib import Path
import shlex
import sys

stage = Path(sys.argv[1])
app = stage / "opt/fourthbreakfast-planner"

def layout_error(message):
    paths = sorted(
        str(path.relative_to(stage))
        for path in stage.rglob("*")
        if (path.is_file() or path.is_symlink()) and (
            path.suffix in (".desktop", ".png", ".svg", ".ico") or
            (path.parent.name == "bin" and "runtime" not in path.relative_to(stage).parts)
        )
    )
    details = "\n".join(f"  {path}" for path in paths[:20]) or "  (none)"
    raise SystemExit(f"{message}\nExpected app root: {app.relative_to(stage)}\nRelevant packaged paths:\n{details}")

launcher = app / "bin/planner"
engine = app / "lib/app/resources/syncthing"
if not engine.is_file():
    layout_error("Missing bundled lib/app/resources/syncthing engine.")
# jpackage can copy resources before createDistributable applies its permissions.
engine.chmod(0o755)
if not launcher.is_file() or not launcher.stat().st_mode & 0o111:
    layout_error("The package must contain an executable private bin/planner launcher.")
shared_launcher = stage / "usr/bin/planner"
if shared_launcher.exists() or shared_launcher.is_symlink():
    raise SystemExit("The package must not replace GNOME Planner's shared launcher.")
expected_desktop = app / "lib/fourthbreakfast-planner-planner.desktop"
if not expected_desktop.is_file() or not (app / "lib/planner.png").is_file():
    layout_error("Missing private lib/fourthbreakfast-planner-planner.desktop or lib/planner.png.")
control = stage / "DEBIAN/control"
lines = control.read_text().splitlines()
for i, line in enumerate(lines):
    if line.startswith("Depends:"):
        end = i + 1
        value = line.removeprefix("Depends:").strip()
        while end < len(lines) and lines[end].startswith((" ", "\t")):
            value += " " + lines[end].strip()
            end += 1
        dependencies = [item.strip() for item in value.split(",") if item.strip()]
        for package in ("libnotify-bin", "xdg-utils"):
            if package not in dependencies:
                dependencies.append(package)
        lines[i:end] = ["Depends: " + ", ".join(dependencies)]
        break
else:
    lines.append("Depends: libnotify-bin, xdg-utils")
control.write_text("\n".join(lines) + "\n")
for desktop in stage.glob("**/*.desktop"):
    # The bundled JRE also has a legal/java.desktop directory, not a launcher.
    if not desktop.is_file():
        continue
    if desktop != expected_desktop:
        raise SystemExit(f"Unexpected desktop entry: {desktop.relative_to(stage)}")
    entries = [line for line in desktop.read_text().splitlines() if not line.startswith("StartupWMClass=")]
    launch = next((line.removeprefix("Exec=") for line in entries if line.startswith("Exec=")), "")
    if shlex.split(launch) != ["/opt/fourthbreakfast-planner/bin/planner"] or "Icon=/opt/fourthbreakfast-planner/lib/planner.png" not in entries:
        icons = [line for line in entries if line.startswith("Icon=")]
        layout_error(f"The desktop entry must use this package's private launcher and icon; got Exec={launch!r}, {icons!r}.")
    start = entries.index("[Desktop Entry]")
    end = next((i for i in range(start + 1, len(entries)) if entries[i].startswith("[")), len(entries))
    entries.insert(end, "StartupWMClass=fourthbreakfast-planner")
    desktop.write_text("\n".join(entries) + "\n")
PY
mkdir -p desktop/build/linux
dpkg-deb --root-owner-group --build "$stage" "desktop/build/linux/planner-$version-linux-x64.deb"
echo "Built desktop/build/linux/planner-$version-linux-x64.deb and desktop/build/portable/planner-$version-linux-x64.tar.gz"
