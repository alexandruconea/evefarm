EVE Farm for Linux
==================

A desktop tracker for EVE Online: net worth over time, wallet, LP value,
market orders, industry jobs, contracts, and NPC kill history - all pulled
from your own EVE character(s) via EVE's official SSO login.

Getting started
---------------
1. Extract the archive wherever you like - no installation needed:

     tar -xzf EVEFarm-<version>-linux-x64.tar.gz

2. Run bin/EVEFarm inside the EVEFarm folder. To start it from your
   applications menu instead, run ./add-to-menu.sh in the EVEFarm folder
   once.
3. Add a character from the app. It opens EVE's own login page in your
   browser; you sign in there, and only EVE's official login page ever
   sees your password. This app never sees or stores it.

Your EVE logins
---------------
EVE Farm keeps the key that protects your EVE logins in your desktop
keyring (GNOME Keyring, KWallet or another Secret Service keyring), the
same place your browser keeps its passwords. If adding a character fails
with a keyring message, install libsecret and a keyring, for example on
Ubuntu, Debian or Mint:

  sudo apt install libsecret-1-0 gnome-keyring

Optional extras
---------------
- Voice alerts in the Abyss tab: speech-dispatcher (spd-say) or espeak-ng
- Desktop notifications: notify-send (package libnotify-bin on Ubuntu)

Your data
---------
Everything you track (wallet snapshots, settings, cached prices, etc.) is
stored locally on your own machine, in ~/.evefarm/ (readable only by your
user).

EVE running through Steam (Proton), Lutris, Bottles, Heroic or Wine is found
automatically.
If your Gamelogs folder isn't found, set it in Options > Settings.

Requirements
------------
A 64-bit Linux desktop. A Java runtime is bundled inside this folder, so
you don't need to install Java separately.
