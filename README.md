# EVE Farm

EVE Farm is a Windows desktop app that keeps all your EVE Online characters in one place: assets and net worth over time, wallet journal and transactions, market orders, contracts, industry jobs, loyalty points and LP store value, NPC kills from your game logs, officer spawns and drops, and agents.

## Download

1. Download `EVEFarm-<version>-win64.zip` from the [latest release](https://github.com/alexandruconea/evefarm/releases/latest).
2. Unzip it anywhere, for example `C:\Games\EVEFarm`, and run `EVEFarm.exe`. Java is included, so there is nothing else to install.
3. Open **File → Characters... → Add Character...** and log in with your EVE account in the browser window that opens.

## Updates

EVE Farm checks for a new version when it starts and once a day. **Help → Check for Updates...** checks right away. An update is only downloaded from this repository's releases, and only installed when its signature matches the release key built into the app. Your data is backed up before the new version is installed.

## Your data

Everything stays on your computer, in `%USERPROFILE%\.evefarm`:

- `evefarm.db` holds your characters, history and settings;
- `backups` holds automatic daily backups (the last 7 days and one per month for a year);
- `logs` holds the log files.

**Options → Settings...** can copy every automatic backup to a second folder, such as a cloud-synced one. **Options → Backup Data...** and **Restore Data...** create or restore a backup by hand.

EVE Farm logs in through the official EVE SSO (OAuth 2.0 with PKCE), so it never sees your password. Login tokens are encrypted with Windows DPAPI and can only be read by your Windows account.

## Building from source

You need JDK 25.

- `mvnw.cmd verify` builds the app and runs the tests.
- `packaging\build-exe.cmd` creates `EVEFarm.exe` and the release zip in `packaging\dist`.

## Examples:
<img width="1491" height="986" alt="Values" src="https://github.com/user-attachments/assets/c77adace-76bc-46c6-96aa-fb21ad5d79fb" />
<img width="1491" height="986" alt="LP Store" src="https://github.com/user-attachments/assets/a7412728-e5b3-42ec-b572-ee0cb52dfdb2" />
<img width="1488" height="981" alt="NPC Hunt" src="https://github.com/user-attachments/assets/2bdd4d0f-3410-4b67-b39a-7e0005c89267" />

## License

EVE Farm's own code is released under the [MIT License](LICENSE). The license doesn't cover EVE Online's names, images or game data, which belong to their owners (see Trademarks below).

## Trademarks

© 2014 CCP hf. All rights reserved. "EVE", "EVE Online", "CCP", and all related logos and images are trademarks or registered trademarks of CCP hf.

EVE Farm is an independent, fan-made application that uses the official EVE Online APIs under the EVE Developer License Agreement. It is not made, endorsed or supported by Fenris Creations (formerly CCP Games), is not affiliated with it in any way, and Fenris Creations is not responsible for its content or functioning.
