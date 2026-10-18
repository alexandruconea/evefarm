#!/bin/sh
set -eu

if [ $# -ne 1 ]; then
    echo "usage: packaging/linux/package.sh <version>" >&2
    exit 2
fi
version=$1
cd "$(dirname "$0")/../.."

if [ ! -f target/evefarm.jar ]; then
    echo "target/evefarm.jar is missing - build it first with ./mvnw -B verify" >&2
    exit 1
fi

rm -rf packaging/linux/stage dist/EVEFarm
mkdir -p packaging/linux/stage dist
cp target/evefarm.jar packaging/linux/stage/
jpackage --type app-image --input packaging/linux/stage --dest dist --name EVEFarm \
    --main-jar evefarm.jar --main-class com.evefarm.Main --icon packaging/linux/evefarm.png \
    --java-options --enable-native-access=ALL-UNNAMED \
    --app-version "$version" --vendor "Alex Conea" \
    --description "EVE Online multi-character asset and net-worth tracker"
rm -rf packaging/linux/stage
cp packaging/linux/README.txt dist/EVEFarm/README.txt
cp packaging/linux/add-to-menu.sh dist/EVEFarm/add-to-menu.sh
chmod 755 dist/EVEFarm/add-to-menu.sh

archive="dist/EVEFarm-$version-linux-x64.tar.gz"
rm -f "$archive"
tar --format=gnu --sort=name --owner=0 --group=0 --numeric-owner --hard-dereference \
    -C dist -czf "$archive" EVEFarm
echo "Packaged $archive"
