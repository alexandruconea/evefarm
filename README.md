# EVE Farm

EVE Farm is a Windows desktop app that keeps all your EVE Online characters in one place: assets and net worth over time, wallet journal and transactions, market orders, contracts, industry jobs, loyalty points and LP store value, NPC kills from your game logs, officer spawns and drops, agents, and Abyssal Deadspace runs with a timer that starts and stops by itself.

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

EVE only shows the last 30 to 90 days of most history, so EVE Farm saves it as it updates and keeps it for as long as you keep the app: wallet journal and transactions, closed market orders (**Show closed orders** in the Market Orders tab), finished contracts with their items, finished industry jobs, every change in your loyalty points (**LP History...** in the LP Store tab), and your whole asset list as it was at the end of each month (**Show** at the bottom of the Assets tab).

**Options → Settings...** can copy every automatic backup to a second folder, such as a cloud-synced one. **Options → Backup Data...** and **Restore Data...** create or restore a backup by hand.

EVE Farm logs in through the official EVE SSO (OAuth 2.0 with PKCE), so it never sees your password. Login tokens are encrypted with Windows DPAPI and can only be read by your Windows account.

## Privacy

EVE Farm has no telemetry and sends nothing to its developer. It only connects to:

- EVE's official login and API (`login.eveonline.com`, `esi.evetech.net`), for the characters you add;
- Fuzzwork (`www.fuzzwork.co.uk`, `market.fuzzwork.co.uk`), for market prices and EVE's static data;
- Janice (`janice.e-351.com`), only if you choose it as your price provider;
- `images.evetech.net`, for item icons;
- GitHub (`api.github.com`, `github.com`), to check for and download updates. You can turn the automatic check off in **Options → Settings...**.

A character's location and ship are only read while you track Abyss runs for it in the **Abyss** tab, every 10 seconds. EVE Farm saves the runs it detects (start, length and ship), not where the character has been. While tracking, it also checks the clipboard every second, so that a cargo list you copy in EVE counts as the cargo before or after a run. Anything that isn't a list of EVE items is ignored and never saved.

The aggro voice in the **Abyss** tab reads your characters' Gamelogs on this computer and speaks with Windows' own voice. It only plays a sound; it never sends anything or acts in the game for you.

## Uninstalling

1. Close EVE Farm.
2. Delete the folder you unzipped it into. EVE Farm doesn't install anything else and doesn't change Windows settings.
3. To remove your data too, delete `%USERPROFILE%\.evefarm`. This also deletes the automatic backups, so copy any you want to keep first.
4. If you used **Copy EVE Settings...**, the EVE files it replaced are kept in a `.evefarm-backups` folder inside EVE's settings folder (`%LOCALAPPDATA%\CCP\EVE\..._tranquility`). Delete it when you no longer need them.

## Building from source

You need JDK 25.

- `mvnw.cmd verify` builds the app and runs the tests.
- `packaging\build-exe.cmd` creates `EVEFarm.exe` and the release zip in `packaging\dist`.

## Examples:
<img width="1491" height="986" alt="Values" src="https://github.com/user-attachments/assets/c77adace-76bc-46c6-96aa-fb21ad5d79fb" />
<img width="1491" height="986" alt="LP Store" src="https://github.com/user-attachments/assets/a7412728-e5b3-42ec-b572-ee0cb52dfdb2" />
<img width="1488" height="981" alt="NPC Hunt" src="https://github.com/user-attachments/assets/2bdd4d0f-3410-4b67-b39a-7e0005c89267" />
<img width="1780" height="884" alt="image" src="https://github.com/user-attachments/assets/3b492a16-e08c-44ac-a07c-7388e85da428" />


## Code signing policy

Free code signing provided by [SignPath.io](https://about.signpath.io), certificate by [SignPath Foundation](https://signpath.org).

- Committers and reviewers: [Alex Conea](https://github.com/alexandruconea)
- Approvers: [Alex Conea](https://github.com/alexandruconea)

Only `EVEFarm.exe` is signed, and only when it is built from this repository by the [release workflow](.github/workflows/release.yml) for a version tag. Every signing request is approved by hand. The privacy policy is in [Privacy](#privacy).

## License

EVE Farm's own code is released under the [MIT License](LICENSE). The license doesn't cover EVE Online's names, images or game data, which belong to their owners (see Trademarks below).

## Trademarks

© 2014 CCP hf. All rights reserved. "EVE", "EVE Online", "CCP", and all related logos and images are trademarks or registered trademarks of CCP hf.

EVE Farm is an independent, fan-made application that uses the official EVE Online APIs under the EVE Developer License Agreement. It is not made, endorsed or supported by Fenris Creations (formerly CCP Games), is not affiliated with it in any way, and Fenris Creations is not responsible for its content or functioning.
