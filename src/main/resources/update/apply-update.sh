#!/bin/sh
process_id=$1
install_dir=${2%/}
staged_dir=${3%/}
log_file=$4
launch=${5:-launch}
wait_seconds=${6:-120}

previous_dir="$install_dir.old"
failure_file="$(dirname "$log_file")/update-failed.txt"
trap '' HUP
cd "$(dirname "$0")" || cd /

log() {
    printf '%s %s\n' "$(date '+%Y-%m-%dT%H:%M:%S')" "$1" >> "$log_file"
}

fail() {
    log "update failed: $1"
    printf '%s\n' "$1" > "$failure_file" || log "could not record the failure for EVE Farm"
    if [ ! -e "$install_dir" ] && [ -d "$previous_dir" ]; then
        if mv "$previous_dir" "$install_dir"; then
            log "restored the previous version"
        fi
    fi
}

move_back_own_files() {
    for own in "$previous_dir"/* "$previous_dir"/.[!.]* "$previous_dir"/..?*; do
        if [ ! -e "$own" ] && [ ! -L "$own" ]; then
            continue
        fi
        name=$(basename "$own")
        if [ -e "$install_dir/$name" ] || [ -L "$install_dir/$name" ]; then
            continue
        fi
        mv "$own" "$install_dir/$name" || log "couldn't move $name back into the app folder"
    done
}

apply_update() {
    log "waiting for EVE Farm (pid $process_id) to exit"
    waited=0
    while kill -0 "$process_id" 2>/dev/null; do
        if [ "$waited" -ge "$wait_seconds" ]; then
            fail "EVE Farm did not exit"
            return 1
        fi
        sleep 1
        waited=$((waited + 1))
    done
    if [ ! -d "$staged_dir" ]; then
        fail "the new version wasn't found in $staged_dir"
        return 1
    fi
    if [ -e "$previous_dir" ] && ! rm -rf "$previous_dir"; then
        fail "the previous backup copy $previous_dir couldn't be removed"
        return 1
    fi
    if ! mv "$install_dir" "$previous_dir"; then
        fail "the current version couldn't be moved aside"
        return 1
    fi
    if ! mv "$staged_dir" "$install_dir"; then
        mv "$previous_dir" "$install_dir"
        fail "the new version couldn't be put in place"
        return 1
    fi
    move_back_own_files
    log "installed the new version in $install_dir"
    rm -f "$failure_file"
}

apply_update

if [ "$launch" = launch ] && [ -x "$install_dir/bin/EVEFarm" ]; then
    cd "$HOME" 2>/dev/null || cd /
    nohup "$install_dir/bin/EVEFarm" >/dev/null 2>&1 &
fi
