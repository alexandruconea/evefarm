#!/bin/sh
set -eu

app_dir=$(cd "$(dirname "$0")" && pwd)
case "$app_dir" in
    *'"'* | *'$'* | *'`'* | *'\'*)
        echo "Move the EVEFarm folder to a path without quotes, \$, \` or \\ and run this again." >&2
        exit 1
        ;;
esac
entries="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
mkdir -p "$entries"
cat > "$entries/evefarm.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=EVE Farm
Comment=EVE Online multi-character asset and net-worth tracker
Exec="$app_dir/bin/EVEFarm"
Icon=$app_dir/lib/EVEFarm.png
Terminal=false
Categories=Game;Utility;
StartupWMClass=com-evefarm-Main
EOF
echo "EVE Farm is now in your applications menu."
