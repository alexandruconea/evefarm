#!/bin/sh
set -eu

if [ $# -ne 1 ]; then
    echo "usage: packaging/linux/check-package.sh <EVEFarm-version-linux-x64.tar.gz>" >&2
    exit 2
fi
archive=$(realpath "$1")
cd "$(dirname "$0")/../.."

work=$(mktemp -d)
trap 'pkill -f "$work/app/EVEFarm/bin/EVEFarm" 2>/dev/null || true; rm -rf "$work"' EXIT
mkdir -p "$work/app" "$work/home"
tar -xzf "$archive" -C "$work/app"

for path in bin/EVEFarm lib/runtime/lib/jspawnhelper add-to-menu.sh; do
    if [ ! -x "$work/app/EVEFarm/$path" ]; then
        echo "$path is missing or not executable" >&2
        exit 1
    fi
done
for path in lib/app/evefarm.jar lib/EVEFarm.png README.txt; do
    if [ ! -f "$work/app/EVEFarm/$path" ]; then
        echo "$path is missing" >&2
        exit 1
    fi
done

HOME="$work/home" XDG_DATA_HOME="$work/home/.local/share" "$work/app/EVEFarm/add-to-menu.sh"
if ! grep -qx "Exec=\"$work/app/EVEFarm/bin/EVEFarm\"" "$work/home/.local/share/applications/evefarm.desktop"; then
    echo "add-to-menu.sh wrote a wrong menu entry" >&2
    exit 1
fi

EVEFARM_LINUX_ARCHIVE="$archive" EVEFARM_LINUX_APP_DIR="$work/app/EVEFarm" \
    ./mvnw -B -q surefire:test -Dtest=RealLinuxArchiveTest

EVEFARM_KEYRING_TEST=1 dbus-run-session -- sh -c \
    'printf ci-keyring | gnome-keyring-daemon --unlock >/dev/null; ./mvnw -B -q surefire:test -Dtest=LinuxKeyringTest'

HOME="$work/home" xvfb-run -a dbus-run-session -- sh -c \
    'printf ci-keyring | gnome-keyring-daemon --unlock >/dev/null; exec "$0"' \
    "$work/app/EVEFarm/bin/EVEFarm" > "$work/app-output.txt" 2>&1 &
sleep 45

logs=$(find "$work/home/.evefarm/logs" -name 'evefarm.log*' 2>/dev/null || true)
if [ -n "$logs" ]; then
    cat $logs
fi
if ! pgrep -f "$work/app/EVEFarm/bin/EVEFarm" > /dev/null; then
    echo "EVE Farm stopped by itself:" >&2
    cat "$work/app-output.txt" >&2
    exit 1
fi
pkill -f "$work/app/EVEFarm/bin/EVEFarm" || true
sleep 5

if [ ! -f "$work/home/.evefarm/evefarm.db" ]; then
    echo "EVE Farm didn't create its database" >&2
    exit 1
fi
if [ "$(stat -c %a "$work/home/.evefarm")" != 700 ]; then
    echo "the data folder can be read by other users" >&2
    exit 1
fi
if [ -n "$logs" ] && grep -h -e "^SEVERE:" -e "libsecret couldn't be loaded" $logs; then
    echo "EVE Farm logged errors while starting" >&2
    exit 1
fi
echo "The packaged app starts, creates its private data folder and loads libsecret"
