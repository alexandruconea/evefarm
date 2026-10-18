# EVE Farm Guide

EVE Farm is an app for Windows and Linux that keeps all your EVE Online characters in one place. It reads your data through EVE's official API, saves it on your computer and keeps it for as long as you keep the app, even after EVE stops showing it.

This guide walks through every part of the app. Menu and button names are written exactly as they appear in the app, for example **File > Characters...**.

## Contents

1. [Getting started](#getting-started)
2. [Characters](#characters)
3. [Updating your data](#updating-your-data)
4. [The tabs](#the-tabs)
   - [Values](#values)
   - [Tracker](#tracker)
   - [Assets](#assets)
   - [Journal](#journal)
   - [Market Orders](#market-orders)
   - [Transactions](#transactions)
   - [Contracts](#contracts)
   - [Industry Jobs](#industry-jobs)
   - [LP Store](#lp-store)
   - [LP Purchases](#lp-purchases)
   - [NPC Kills](#npc-kills)
   - [Abyss](#abyss)
   - [Mining](#mining)
   - [Agents](#agents)
   - [Standings](#standings)
   - [Skills](#skills)
   - [Industry Calculator](#industry-calculator)
5. [Working with tables](#working-with-tables)
6. [Settings](#settings)
7. [Copy EVE Settings](#copy-eve-settings)
8. [Backups and restoring](#backups-and-restoring)
9. [App updates](#app-updates)
10. [Tray icon and notifications](#tray-icon-and-notifications)
11. [Your data and privacy](#your-data-and-privacy)
12. [Trademarks](#trademarks)

## Getting started

1. Download EVE Farm from the [latest release](https://github.com/alexandruconea/evefarm/releases/latest):
   - **Windows:** `EVEFarm-<version>-win64.zip`. Unzip it anywhere, for example `C:\Games\EVEFarm`, and run `EVEFarm.exe`.
   - **Linux:** `EVEFarm-<version>-linux-x64.tar.gz`. Extract it anywhere and run `EVEFarm/bin/EVEFarm`; `EVEFarm/add-to-menu.sh` adds it to your applications menu. Your EVE logins are protected with a key kept in your desktop keyring, so EVE Farm needs `libsecret` and a keyring such as GNOME Keyring or KWallet (on Ubuntu, Debian or Mint: `sudo apt install libsecret-1-0 gnome-keyring`).

   Java is included, so there is nothing else to install.
2. The first time, the **Characters** window opens by itself. Click **Add Character...** and log in with your EVE account in the browser.
3. Open **Update > Update...** and click **Update** to load everything for the first time.

Only one copy of EVE Farm runs at a time. Starting it again brings the open window to the front.

## Characters

Open **File > Characters...** to manage your characters.

- **Add Character...** opens EVE's login page in your browser. Log in, pick the character and approve the permissions. EVE Farm never sees your password. You can add as many characters as you like, from any number of accounts. **Cancel Login** stops a login you don't want to finish.
- **Set as Main** marks your main character. It is listed first and is selected first in the Values and LP Store tabs, and it has a star next to its name.
- **Remove** removes a character. Its login and its current assets, orders, contracts and jobs are removed. Its history (journal, transactions, Tracker, NPC kills and officers) is kept.
- **Copy EVE Settings...** copies window positions and other in-game settings between characters. See [Copy EVE Settings](#copy-eve-settings).

If a character changes owner (it was sold or transferred to another account), EVE Farm refuses to log it in again under the new owner. Remove it and add it again to keep tracking it. Its history so far is kept.

If EVE no longer accepts a character's login, for example after you revoked EVE Farm's access to the account, its updates fail with a message that asks you to add it again. Click **Add Character...** and log in with that character. Its history is kept.

When a new version of EVE Farm needs a new permission from EVE, the release notes say so, and characters added before that version must be added again.

## Updating your data

The round button at the right end of the tabs updates everything that can be updated, for all your characters. Hover it to see what it will update and how long the rest has to wait. It is gray when everything was updated recently, and it spins while an update runs.

To choose what to update, open **Update > Update...**:

- Tick what you want to update and click **Update**. The window closes and the update runs in the background while the round button spins. Click **Now** next to a single line to update only that line.
- **All** ticks or clears every line.
- **All Characters** updates every character. **First Character** updates only the first one.

| Line | What it fetches | Can run again after |
| --- | --- | --- |
| Market Prices | Item prices from your price provider | 1 hour |
| Assets | Every item your characters own, where it is and what it's worth | 1 hour |
| Market Orders | Your open orders, plus the orders that closed since the last update | 20 minutes |
| Journal | Your wallet journal | 1 hour |
| Transactions | Your market buys and sells | 1 hour |
| Contracts | Your contracts | 1 hour |
| Industry Jobs | Your industry jobs | 1 hour |
| Tracker Snapshot | Wallet, net worth and skill points, saved as one point in the Tracker chart | 1 hour |
| Loyalty Points | Your LP with every corporation | 1 hour |
| NPC Kills | Reads your Gamelogs for new NPC kills | 1 hour |
| Mining Ledger | What your characters mined | 10 minutes |
| Standings | Your standing with factions, NPC corporations and agents | 1 hour |
| Skills | Your skills, attributes and implants, for the skill planner | 5 minutes |

While a line is waiting, its **Now** button shows the time left. The waits follow EVE's own cache times, so updating sooner wouldn't bring new data.

Some things happen by themselves:

- when EVE Farm starts, it updates the assets and skills and takes a Tracker snapshot of every character;
- the LP of every character is read once an hour, as soon as EVE renews it, for **LP per Day** in the LP Store tab;
- market prices refresh once a day;
- the Gamelogs are scanned for new kills every 10 minutes;
- logins are kept alive in the background, so you don't have to log in again.

During EVE's daily downtime the servers don't answer. The update says so; try again after the downtime.

## The tabs

You can hide tabs you don't use and change their order in **Options > Show Tabs...** (tick a tab to show it, **Move Up** and **Move Down** to reorder). To hide a tab quickly, right-click it and choose **Close Tab**; it comes back when you tick it again in **Show Tabs...**.

To keep the screens tidy, the explanations sit behind the mouse: hover a tab, a field, a button or a line of the Industry Result to read what it does.

### Values

A quick look at what your characters are worth. The left card is the **Grand Total** of all characters; the right card is the character chosen in **Character:**.

Each card shows:

- **Total**: everything below added up;
- **Wallet Balance**: ISK in the wallet;
- **Assets**: the value of everything the character owns;
- **Sell Orders**: the value of the items on your open sell orders;
- **Escrows (To Cover)**: the ISK still needed to fill your buy orders, on top of the escrow already paid;
- **Best Asset**, **Best Ship** and **Best Module**: the most valuable item of each kind, with its value.

### Tracker

A chart of your net worth over time. Each point is a snapshot, taken when EVE Farm starts and when you run **Tracker Snapshot** in the Update window.

- **Quick Date** picks a range: 1 Day, 1 Week, 2 Weeks, 1 Month, 3 Months, 6 Months, 1 Year or 2 Years. **From** and the date next to it set any range you like.
- **All Profiles** shows all characters together. Clear it and pick characters in the **Characters** list to see only some of them.
- **Series** lists the lines in the chart. Click a series to hide it, click again to show it:
  - Total, Wallet Balance, Assets, Implants, Sell Orders;
  - Escrows (ISK held for your buy orders) and Escrows To Cover (ISK still needed for them);
  - Manufacturing (the value of what your running manufacturing and reaction jobs will produce, counting every unit a run makes once the Industry tab has downloaded EVE's blueprint data);
  - Contracts (the price and reward of the contracts you issued that are still open, not those issued for your corporation or offered to you by others) and Contract Collateral (the collateral of your open courier contracts and of the courier contracts you are carrying);
  - Skill Points and LP Value.
- Hover the chart to see the values at a date. Scroll to zoom, drag to move, and double-click to show everything again.
- **Skill Point Filters...** chooses, per character, whether its skill points count in the total. The value counted is what you would get by extracting the skill points above 5,000,000: the number of 500,000 SP extractions times the price of a Large Skill Injector minus the price of a Skill Extractor. **Extra SP kept** raises that floor if you never want to extract below a certain amount.
- **LP Value** is the LP you hold with your favorite corporation in the LP Store tab, times your **Set ISK/LP Target...**, the same as **Estimated Value** in the LP Store. It goes up when you earn LP and down when you spend it, not with the market. A snapshot keeps the target it was taken with.
- **Manage Snapshots...** lists every snapshot. Select some and click **Delete Selected** to remove wrong points. You can also right-click a point in the chart and choose **Delete Snapshot**.

### Assets

Every item your characters own: location, quantity, unit price, total value, volume, ISK per m3, group, category, and the ship a module is fitted to.

- **Show:** chooses what you see. **Current assets** is the list from the last update. EVE Farm also saves your whole asset list as it was at the end of each month, so you can pick a past month to see what you owned then.
- The total value of what's shown is at the bottom.
- Blueprint copies have **(Blueprint Copy)** after their name and count as 0 ISK, as copies can't be sold on the market.

### Journal

Your wallet journal: date, type, amount, balance, description and the two parties of every entry. EVE only shows the last 30 days; EVE Farm keeps every entry it has seen.

### Market Orders

Your buy and sell orders with price, quantity, location, issue and expiry date, status and range.

- **Show closed orders** adds the orders that were filled, cancelled or expired. EVE only shows those for 90 days; EVE Farm keeps them.
- **Outbid** says whether someone else has a better price than you at the same station: a cheaper sell order or a higher buy order. Orders of your own characters don't count. EVE Farm checks every 15 minutes while it runs, so the column fills in about a minute after it starts. **Competitor Price** (hidden at first) shows that best price.
- **Broker's Fee** and **Broker's Fee %** show what you paid to place the order: the broker's fee in your wallet journal from the second the order was issued. They stay empty when the journal has no fee from that second, for example for orders placed before EVE Farm saved your journal.
- Hidden columns (right-click a column header and choose **Choose Columns...** to show them): **Market Sell Min**, **Market Buy Max**, **Market Price**, **Market Margin %** and **Market Profit +**, which compare your price with the market.

### Transactions

Your market purchases and sales: date, item, group, quantity, price, total, buy or sell, client and location. EVE Farm keeps every transaction it has seen.

### Contracts

Your contracts: type, status, title, issuer, assignee, acceptor, price, reward, collateral, volume, start and end location and dates. Finished contracts are kept.

Click the **Info** icon at the start of a row to see what's in the contract. The window lists the items included and, for item exchanges, the items asked for in return, each with its price and an **Estimated value** of the whole contract. Contracts that expired long ago may no longer show their items in EVE.

### Industry Jobs

Your industry jobs: activity (manufacturing, research, copying, invention, reactions), blueprint, product, runs, facility, cost, start and end date and status. A job that has finished shows as **Ready** until you deliver it. Finished jobs are kept.

### LP Store

What your loyalty points are worth in each corporation's LP store, and how much LP you earn each day. It has two tabs: **Offers** and **LP per Day**.

In **Offers**:

1. Pick a **Character:** and a **Corporation:**. **Your LP** at the bottom shows the character's LP with that corporation.
2. Click **Show Offers** to load and price the store.

For each offer you see the item, quantity, LP cost, ISK cost, the other items it requires and what they cost, and the market prices. When the offer is a blueprint, EVE Farm prices the item it builds and adds the build materials and their cost.

- **ISK/LP (Sell)** and **ISK/LP (Buy)**: the ISK you make per LP, selling at the lowest sell price or to the highest buy order, after the ISK cost and the other costs.
- **Profit (Sell)** and **Profit (Buy)**: the ISK you make on the whole offer.
- **Net ISK/LP** and **Net Profit**: the same after the fees your main character pays when selling in Jita 4-4. Selling with a sell order pays the sales tax and the broker fee; selling to buy orders pays only the sales tax. The sales tax is 7.5% less 11% for each level of Accounting. The broker fee is 3% less 0.3% for each level of Broker Relations, less 0.03% for each point of standing with the Caldari State and 0.02% for each point with the Caldari Navy, and at least 100 ISK. The line under the table shows the character and the fees used. Skills EVE Farm has not read yet count as level IV.
- **Sold per Day**: how many units people buy from sell orders per day in The Forge, the region of Jita, over the last 30 days. EVE's market history doesn't say which side each sale was on, so EVE Farm estimates it from each day's average, lowest and highest price; sales into buy orders are left out. It is refreshed once a day. For a blueprint, it is the item the blueprint builds. Check it before you buy an offer, so you don't end up with items nobody buys.
- **Days to Sell**: the units of the offer divided by **Sold per Day**, that is how many days of sales the whole offer equals. **No sales** means nobody bought from sell orders in the last 30 days. You share the market with other sellers, so expect it to take longer.
- **For Sale** (hidden at first): how many units are for sale in Jita 4-4 right now, with Fuzzwork prices.
- **Set ISK/LP Target...** sets the ISK per LP you aim for. ISK/LP cells at or above it turn green, those below it turn red. **Estimated Value** at the bottom is your LP times that target, and the Tracker's **LP Value** uses it too.
- **Favorite** remembers the corporation. It opens first next time, and the Tracker uses it for **LP Value**.
- Right-click an **Other Requirements** or **Build Materials** cell and choose **Copy** to copy the items of the selected offers as a list you can paste into EVE's multibuy window.
- **LP History...** shows every change in your LP that EVE Farm has seen, per character and corporation.
- **Record Purchase...** saves what you bought from the selected offer, so the **LP Purchases** tab can tell you when selling it pays.

**LP per Day** is a calendar of the last year, one square per day, like the contributions chart on GitHub. The darker the square, the more LP your characters earned that day.

- Pick a **Character:** and a **Corporation:** to see only theirs.
- Hover a day to see its LP, where it came from and what it is worth at your ISK/LP target. Click it to list it under the calendar.
- The line under the calendar adds up the last 7 and 30 days and shows your best day.
- EVE only shows how much LP you have, not when you earned it, and it renews that number once an hour. EVE Farm reads it as soon as EVE has new data, counts what went up since the read before, and puts it at the time EVE's data is from. Reading more often wouldn't bring anything new, so **Update** doesn't read it again before EVE renews it either. The line under the summary says how fresh your LP is and when the next read is; hover **Your LP** in **Offers** to see the same.
- LP you earned while EVE Farm was closed shows on the day it reads it next, with a mark in the corner of the square, and the hover tip says since when. LP you spend between two reads isn't seen.
- Squares with only an outline are days before EVE Farm started saving your LP.

### LP Purchases

What you bought in LP stores and what it is worth now, so you know when you can sell without losing ISK. Buy the same item at different ISK/LP over time and each purchase is kept apart.

To record a purchase:

- In the **LP Store** tab, select the offer and click **Record Purchase...**. Set **Times bought**, check **Bought on** and click **Save**. The LP and ISK come from the offer. **Other cost per offer** is what the required items and build materials cost now; change it if you paid a different amount.
- Or click **Review Payments...** here. It lists the payments to LP stores in your wallet journal that are not recorded yet, with the offers of that store whose ISK cost fits the amount paid. The offer that best fits what you listed, sold and have of each item in the 30 days after the payment comes first, and **Find** narrows the list by name. Pick the offer, click **Record...** and save. **Ignore** hides a payment for good. Offers with no ISK cost leave no payment in the journal, so record those from the LP Store tab. When payments are waiting, the button shows how many.
- A purchase you recorded from the LP Store tab is matched to its payment by itself: same character, same store and the same ISK, paid within a week of the **Bought on** date. It isn't listed again, and its **Bought on** becomes the exact time of the payment.
- **Target ISK/LP** starts as the **ISK/LP (Sell)** the LP Store showed when you recorded the purchase. When the offer showed no ISK/LP above 0, it starts as your **Set ISK/LP Target...** value instead. Set it to what you want to make, or to 0 for no target.

The top table has one row per item. The bottom table shows each purchase of the item you select, or every purchase when none is selected.

- **Left**: the units you still have: what you bought, less what you sold and used.
- **Avg Cost**: what one unit cost you in ISK: the ISK paid and the other items. The LP is not counted.
- **Break-even**: the lowest sell price that gets that ISK back after the sales tax and broker fee. Below it you lose ISK.
- **Target Price**: the sell price at which the purchase makes its **Target ISK/LP**, counted the way the LP Store tab counts ISK/LP.
- **Sell Now**: the lowest sell price right now. It is green at or above the Target Price, yellow between the Break-even and the Target Price, and red below the Break-even.
- **If Sold Now**: what you would make on what is left, selling at Sell Now after the tax and fee, against what it cost you in ISK. **Realized** is the same for what you already sold.
- **Sold per Day** and **Days to Sell** work as in the LP Store tab, for the units you have left.
- **ISK/LP Now** (bottom table): the ISK/LP the purchase makes at today's lowest sell price.
- The bottom table also has **Corporation**, **Offers**, **Used**, **LP Spent**, **ISK Spent** and **Other Cost**, hidden at first. Right-click a header and choose **Choose Columns...** to show them.

Your market sales come from **Update**: each sale of the item by any of your characters counts against the oldest purchase made before it. Sales of the same item that did not come from an LP store count too. Used some units yourself? Select the purchase, click **Edit...** and set **Units used**. **Show sold** also lists what you have sold or used up. The sales tax and the broker fee are those of the character who made the purchase, worked out as for the **Net** columns of the LP Store tab: from its Accounting and Broker Relations skills and its standings with the Caldari State and the Caldari Navy (skills EVE Farm has not read yet count as level IV).

### NPC Kills

Everything EVE Farm learns from your Gamelogs: the combat logs EVE writes on your computer. It finds them by itself; if not, set the folder in **Options > Settings...**. New kills show up within 10 minutes.

The tab has three parts.

**All Kills** counts your kills by date, faction, ship type and system.

- Filter by date (**Quick Date** or **From**), characters (**All Profiles** or the **Characters** list), **Faction**, **Ship Types** and **System**.
- **Export PDF...** saves a kills report with the totals, active days, and the kills per ship type, system and date.

**Officers** lists every officer NPC your characters met in asteroid belts.

- Each row shows when and where it was seen, which characters fought it, whether it was killed, the escort kills and the officer bounty.
- **Journal** shows whether the bounty was confirmed by your wallet journal. Run **Update > Journal** within 30 days of the kill, or EVE's journal will no longer show it.
- Select an officer to see its whole spawn, the belt it was in, and your notes.
- **Drops** records what it dropped. Click **Add...**, search for the item, set the quantity and check the price; EVE Farm suggests the market price, and you can type the price you actually sold it for.
- **Scan Gamelogs** checks the logs right away instead of waiting. The first scan also downloads EVE's NPC list, which needs the internet.

**Spawns** groups the NPCs you fought into spawns: when and where, how long the fight took, how many NPCs were killed, the bounty and which characters fought them. Select a spawn to see each NPC with the damage dealt and taken. **Save as .txt...** saves the spawns shown in the list to a text file, each with its NPCs; use the filter to choose which spawns go in.

Every 1,000 NPCs your characters kill in asteroid belts, EVE Farm shows a notification.

### Abyss

Tracks your Abyssal Deadspace runs with a timer that starts and stops by itself, counts the loot and works out your profit per hour.

**Tracking runs**

1. Pick the **Character:** that runs the filament.
2. Set the **Filament:** (tier and weather) and the **Fleet:** (1 Cruiser, 2 Destroyers or 3 Frigates). These are remembered.
3. Click **Start Tracking**, then take the filament in EVE.

EVE Farm checks where the character is every 10 seconds. When it enters the Abyss the timer starts; when it leaves, the run is saved with its time and ship. If the character comes back in a capsule, the run is saved as lost. The fleet is set from the ship you fly when EVE Farm recognizes its class. Click **Stop Tracking** when you are done.

**Counting the loot**

EVE Farm counts loot from what you copy in EVE. In EVE, open your cargo hold, press Ctrl+A, then Ctrl+C.

- Before the run, copy your cargo. EVE Farm takes it as the **cargo before** the next run. **Paste Cargo Before** uses what is on the clipboard if you copied it earlier.
- After the run, copy your cargo again. EVE Farm takes it as the **cargo after**, and the difference is the loot. It is priced and saved with the run.
- **Open the run window after each run** opens the run so you can check the loot.
- In the run window, right-click an item that isn't loot (your own ammo, for example) and choose **Always Ignore**. **Ignored Items...** lists those items, and **Stop Ignoring** brings one back.

While tracking, EVE Farm reads the clipboard every second, but only a list of EVE items is used. Anything else is ignored and never saved.

**Runs and statistics**

- The runs table shows each run: start, time, tier, weather, fleet, ship, result, loot, filament cost, profit, ISK per hour and notes.
- **Add Run...** adds a run by hand. **Edit...** changes one, for example to paste the cargo, fix the time or add notes. **Delete** removes it.
- The filament cost counts one filament per ship in the fleet, at the market price.
- **Statistics** sums up your runs: runs, ships lost, loot, filaments, profit, average profit, average time and ISK per hour. **Group by:** groups them by Tier, Filament or Fleet.

**Voice**

It reads your characters' Gamelogs and speaks with Windows' own voice. On Linux it uses speech-dispatcher (`spd-say`) or espeak-ng; if neither is installed, the Abyss tab says so. It only plays a sound; it never sends anything or acts in the game.

- **Say who gets aggro** says a character's name as soon as NPCs start shooting at it.
- **Volume** sets how loud it is.

### Mining

Everything your characters mined, kept for as long as you keep the app (EVE only shows 30 days). Run **Update > Mining Ledger** to fill it.

- Each row shows the ore's icon, the date, character, system, ore, kind, quantity, volume, unit value and value.
- **Show:** picks the period: Today, Last 7 days, Last 30 days, This month or All time.
- **Value:** chooses how the ore is valued: at the ore price, at the compressed ore price, or as the refined minerals. For refined minerals, set your **Refining yield (%)**.
- **Statistics** shows the totals for what's in the table: volume, value, days mined and ISK per day. **Group by:** splits them by day, character, ore, kind or system, with each group's share of the total.

A character added before EVE Farm 1.0.14 must be added again (**File > Characters... > Add Character...**) so EVE Farm may read its mining ledger.

### Agents

A searchable list of all of EVE's NPC agents: name, corporation, faction, division, level, type, whether it is a locator agent, and its station, system, constellation, region and security status.

- Click **Refresh Agent Data...** the first time to download the list. It changes rarely.
- System, constellation and region names have a zKillboard icon. Click it to see the recent kills there, so you can judge how dangerous the area is.

### Standings

Your characters' standings with EVE's factions, NPC corporations and agents. Run **Update > Standings** to fill it.

The tab has two sides. Drag the bar between them to make one wider; EVE Farm remembers where you leave it.

- **Factions and corporations** (left): each faction, best standing first, with its corporations just below it, indented. Corporations whose faction you have no standing with come after, then those whose faction isn't known.
- **Agents** (right): each agent with its standing, corporation, level, division and system.
- Click a faction on the left to see only its agents on the right, or a corporation to see only that corporation's agents, for the same character. **Show All Agents**, Esc, or a click below the rows shows every agent again.
- Positive standings are blue, negative ones red.
- Each side has its own filters and columns. Click a column header to sort another way; the grouping on the left comes back when the tab is opened again.

The corporation, faction, level, division and system of an agent, and the faction of a corporation, come from the agent list. Click **Refresh Agent Data...** in the Agents tab once to fill them; without it, clicking a faction finds none of its agents.

EVE's API gives the standing without the bonus from the Connections, Diplomacy and Criminal Connections skills, so it can differ a little from the value the game shows with your skills applied.

A character added before EVE Farm 1.0.16 must be added again (**File > Characters... > Add Character...**) so EVE Farm may read its standings.

### Skills

A skill planner: make training plans for each character and see how long they take.

Run **Update > Skills** first. The first time, it also downloads the list of EVE's skills, which takes a few seconds.

**At the top** you see the character's attributes, total and unallocated skill points, and when the next attribute remap is available. The attributes are the ones EVE reports, with implants and any active cerebral accelerator.

**Cerebral accelerators.** EVE Farm works out from the attributes how much an active accelerator adds, recognizes which accelerator gives that bonus, and how long it lasts with your Biology skill. EVE doesn't say when it was injected, so EVE Farm counts from the first time it sees it; skills are read every time EVE Farm starts, so start it soon after you inject one. The end date is shown next to the skill points, marked "about" while it is an estimate. Click **Accelerator...** to type the time left that EVE shows when you hover the booster icon under Active Boosters (for example `505:31:31`, hours:minutes:seconds), a time like `6d 4h`, or the end date. Training times use the accelerator until it ends and the normal speed after that, splitting a level that is in training when it runs out.

**Plans** (left): **New...**, **Rename...**, **Copy...** and **Delete**. A character can have as many plans as you like.

**The plan** (right) lists each skill level in training order: training time, finish date, how much is already done, skill points, attributes, group and notes. **Prerequisite** marks levels added because a planned skill needs them. Below the list are the total training time, the finish date, the skill books you still have to buy with their price, and how many skill injectors would finish the plan right away, with their price.

**Skill injectors.** EVE Farm counts the cheapest mix of Large and Small Skill Injectors that gives the skill points the plan still needs, after your unallocated skill points. It follows EVE's rule that an injector gives fewer skill points the more the character has: a Large Skill Injector gives 500,000 SP under 5 million, 400,000 up to 50 million, 300,000 up to 80 million and 150,000 above that, and a Small one a fifth of that. The count goes up a threshold as soon as the injected points cross it. Prices come from your price provider.

- **Add Skills...** opens the skill browser: skills by group, with a search box, each with its description, rank, attributes and what it requires (trained, planned or missing). Click **Plan to I** ... **Plan to V**; the levels and prerequisites it needs are added for you.
- **Remove** takes the selected levels off. Levels that need them, and prerequisites nothing else needs, go too; EVE Farm asks first.
- **Move Up** and **Move Down** change the order. A skill can't go above something it needs.
- **Notes...** adds a note to a level.
- **Import...** adds skills from text: a skill list, one per line (for example `Caldari Cruiser 4` or `Caldari Cruiser IV`), or a ship fit copied from EVE. For a fit, EVE Farm adds every skill the ship, modules, charges and drones need.
- **From Skill Queue** adds the skills in the character's training queue in EVE, in the same order.
- **Copy as Text** copies the plan as a skill list, one level per line, to paste anywhere.

Training times use the character's attributes as of the last update and assume an Omega clone. Levels you have trained leave the plan by themselves after the next update.

A character added before EVE Farm 1.0.16 must be added again (**File > Characters... > Add Character...**) so EVE Farm may read its skill queue.

### Industry Calculator

Works out what it costs to build an item and what you make selling it. The first time you open the tab, EVE Farm downloads EVE's blueprint and solar system data (about 27 MB, refreshed once a month).

1. Click **Choose...** and search for what you want to build, for example `Jaguar`.
2. Pick the **Character:** whose skills count, the number of **Runs:**, and the blueprint's **ME:** and **TE:**.
3. Type the **System:** you build in. As you type, EVE Farm suggests matching systems with their security and region; pick one with the arrow keys and Enter, or click it. EVE Farm then shows the system's security and its manufacturing cost index, refreshed every hour.
4. Choose where you build: an NPC station, or a Raitaru, Azbel or Sotiyo with no rig, a T1 rig or a T2 rig for material (ME) and time (TE), and the structure's **Facility tax %**. The rig bonus grows in low-sec and null-sec, as in the game.
5. Set the **Broker fee %** you pay: in an NPC station it is 3% less 0.3% for each level of Broker Relations, and less again with good standings; in a player structure it is the owner's fee plus 0.5%. Set it to 0 if you sell to buy orders. The **Sales tax** comes from the character's Accounting skill: 7.5% less 11% for each level.

The **Materials** tab lists what the job uses, with the price you pay for each and whether you build it or buy it. The **Result** at the top right shows what the product sells for, the sales tax and broker fee, the materials, the job fee, the total cost, the profit, **To spend** (the ISK you still pay out: what is left to buy, the job fees and the invention), the build time and the ISK per hour. Hover a line for more, such as the price per unit or what the surplus is worth.

**Building components.** The **Components:** setting decides what you make yourself:

- **Buy all**: every component is bought;
- **Build when cheaper** (the default): EVE Farm works out what each component would cost you to make, all the way down: Tech II components from their blueprints, advanced materials from reactions, fuel blocks from their blueprints. Whatever costs less to make than to buy is built; the rest is bought;
- **Build all from reactions**: the whole chain is built, from the reactions to the finished item, even where buying would cost less. Only the raw materials are bought, along with what only the reactions use, such as moon materials and fuel blocks.

The **Build or buy** tab lists every item you could make with its market price, build cost and job time; **More profitable** says whether building or buying it costs you less, and **Saving** how much you save that way; tick or clear **Build** to decide yourself. Set the ME and TE of your component blueprints above the table (reactions have none). Reactions run in a refinery and only in low-sec, null-sec or a wormhole, so in high-sec their products are bought. The **Shopping list** tab shows everything left to buy, and its **Copy for Multibuy** button copies it to paste into Multibuy in EVE.

**Using what you already have.** The **Stock:** setting leaves out what your characters already own, from their assets as of the last update:

- **Don't use** (the default): everything is bought or built;
- **In the build system**: only items in the system typed in **System:** count;
- **Anywhere**: items anywhere count.

The items of all your characters count, in hangars, containers and cargo holds; fitted modules and assembled items don't. What you have is taken first at every step of the chain: the **Shopping list** shows **Needed**, **In Stock** and **To Buy**, the **Build or buy** and **Chain** tabs show what comes from stock, and an item you have enough of needs no job at all, so a surplus you kept from an earlier build is used up first. **To spend** in the Result drops by what you have; it can be higher than the total cost when you keep a surplus, as you pay for whole runs now and use the rest later. The total cost and the profit stay the same, as the items you have are worth their market price too. Run **Update > Update...** for **Assets** after buying or moving items, so EVE Farm sees them.

**The production chain.** The **Chain** tab shows the order to build in, from the raw materials to the finished item. Step 1 is buying the Shopping list. Each next step lists its jobs, for a ship for example the simple reactions, the complex reactions, the components and last the ship itself, with the runs, what the job makes, what the chain needs, the job time and what it uses. The jobs of one step run side by side on the character's manufacturing and reaction lines, and the step takes as long as its slowest job. What the whole chain needs of one item is made in one job, so a fuel block used by five reactions is built once. To see the whole chain from the reactions, choose **Build all from reactions**. The time of all the steps before the final build is shown as **Component time** and counts in the ISK per hour.

**Surplus.** A job makes whole runs, so it often makes more than you need: a reaction makes 100 or more units per run, and a chain may need only a few of them. The **Surplus** column shows what is left over, and the **Surplus** setting above the Build or buy table decides how it counts:

- **Keep it for later builds** (the default): the surplus costs nothing, as it goes into your next builds;
- **Sell it**: you sell the surplus at the market price less the sales tax and broker fee, and only the difference between what it cost you to make and what you get for it counts;
- **Count it as waste**: you pay for everything the jobs make.

The Result shows what the surplus cost you to make and what it adds to the cost.

The numbers follow EVE's rules:

- each material is the base quantity times the runs, less the blueprint's ME and the structure's bonus, never less than one per run;
- the build time is cut by the blueprint's TE, the structure, Industry (4% a level), Advanced Industry (3% a level), every skill the blueprint needs that speeds up building, such as Advanced Small Ship Construction or Mechanical Engineering (1% a level; Mutagenic Stabilization 2% a level), and a Zainou 'Beancounter' Industry implant (1%, 2% or 4%);
- the job fee is the estimated item value (from EVE's adjusted prices) times the system's cost index, less the structure's bonus, plus the facility tax and the 4% SCC surcharge;
- invention and copying jobs pay the same fee on 2% of the item value;
- a system's security counts as in the game: anything above 0.0 is at least 0.1, low-sec;
- the broker fee is at least 100 ISK;
- prices come from your price provider.

**Tech II items.** With **Include invention** ticked, the cost of inventing the blueprint copies is added: datacores, the decryptor, and the copying and invention job fees, spread over the runs of a successful copy. The **Invention** tab compares building without a decryptor and with each decryptor: the chance of success with your skills (base chance × (1 + science skill levels / 30 + encryption skill level / 40) × the decryptor's bonus), the runs, ME and TE of the invented copy, the invention cost per run, the cost and profit per unit, and the ISK per hour. Each invented copy has only a few runs, so every copy is a job of its own: the materials are rounded job by job, and the jobs run side by side on the character's manufacturing lines (1 + Mass Production + Advanced Mass Production). The build time and the ISK per hour use that time, and the Result shows how many jobs run at once. The most profitable choice is selected; click another row to see its materials and numbers. While it is ticked, **ME:** and **TE:** show the invented copy's values for the chosen row and can't be changed. Clear **Include invention** if you already have the copy, and set its ME and TE yourself. The box is greyed out for blueprints that can't be invented.

If the character's skills aren't saved yet, they count as level IV; run **Update > Skills** for the real ones. If the character lacks a skill needed to build or invent the item, a note under the settings says so.

## Working with tables

Most tabs show a table, and all of them work the same way.

**Filters**

- Click **Add** to add a filter line. Each line has a tick box to turn it on or off, **And**/**Or** to combine it with the line above, the column to look in (**All** looks in every column), the condition (Contains, Equals, Not Equals, Starts With, Ends With, Greater Than, Less Than) and the value.
- **Clear** removes all filters. **Showing X of Y** says how many rows match.
- **Save...** saves the filters under a name; **Load...** brings them back. Saved filters belong to their tab.

**Columns**

- Hover a column header to see what the column shows and how it is worked out. **Choose Columns...** shows the same when you hover a column in its list.
- Click a column header to sort by it; click again to reverse. Numbers and amounts sort as numbers.
- Drag a header to move the column. The order is remembered.
- Right-click a header and choose **Choose Columns...** to show or hide columns. Some useful columns are hidden at first, such as the IDs.

**Copying**

- Select rows, right-click a cell and choose **Copy** followed by the column name. The values of that column for the selected rows are copied, one per line.

## Settings

Open **Options > Settings...**.

- **Theme:** Windows (Light on Linux), Dark or EVE. Restart EVE Farm to see the new theme.
- **Price provider:** where prices come from:
  - **CCP (ESI, global average)**: EVE's own average price across all markets;
  - **Fuzzwork (Jita 4-4 sell price)**: prices from the Jita 4-4 market;
  - **Janice (Jita 4-4, requires API key)**: Janice's Jita prices. Janice has no public API, so you need your own key. **How do I get one?** explains how to ask for it on their Discord. The key is stored encrypted.
- **Default price:** which price is used to value items: Sell Maximum, Sell Average, Sell Median, Sell Percentile, Sell Minimum, Midpoint, Buy Maximum, Buy Average, Buy Median, Buy Percentile or Buy Minimum. It only applies to Fuzzwork and Janice; CCP has a single average price.
- **Gamelogs folder:** where EVE writes its Gamelogs, used by NPC Kills and the Abyss voice. Usually found by itself, also for EVE run through Steam (Proton), Lutris or Wine on Linux; **Browse...** sets it by hand.
- **Extra backup copy folder:** a second folder for the automatic backups, such as a cloud-synced one.
- **Updates:** **Check for new versions automatically**.
- **Notifications:** which desktop notifications you get. See [Tray icon and notifications](#tray-icon-and-notifications).

After changing the price provider or the default price, click **Now** next to **Market Prices** in **Update > Update...** to load the new prices.

## Copy EVE Settings

Copies EVE's in-game settings (window positions, overview, chat channels and so on) from one character to another, so a new character looks like your main straight away. Open it from **File > Characters... > Copy EVE Settings...**. Close EVE before you copy.

1. Check the **EVE settings folder:**. It is the folder ending in `_tranquility` inside `%LOCALAPPDATA%\CCP\EVE` on Windows, or inside `AppData/Local/CCP/EVE` in EVE's Proton or Wine folder on Linux. **Browse...** picks another one and **Rescan** reads it again.
2. Pick the **Source character:** to copy from and the **Target character:** to copy to. If you use several launcher profiles, pick the source and target launcher profile too.
3. Keyboard shortcuts are saved per account, not per character. Tick **Also copy keyboard shortcuts and other account-wide settings** to copy them too. EVE Farm picks the account files of the chosen characters for you.
4. Click **Copy Settings** and confirm.

Before replacing anything, EVE Farm makes a backup of the target files and checks it. If the copy fails, the files are left unchanged or put back from the backup. The backups stay in a `.evefarm-backups` folder inside EVE's settings folder.

## Backups and restoring

- EVE Farm backs up your data automatically once a day. It keeps the last 7 days and one backup per month for a year, in `%USERPROFILE%\.evefarm\backups` (`~/.evefarm/backups` on Linux).
- **Options > Settings... > Extra backup copy folder** copies every automatic backup to a second folder too.
- **Options > Backup Data...** saves a backup wherever you like.
- **Options > Restore Data...** restores a backup. EVE Farm checks the file first, then puts it in place the next time it starts. Your current data is saved beside it before it's replaced, so you can go back.
- Before installing an app update, EVE Farm also backs up your data. It keeps the backups of the last 3 updates.

A backup restored on another computer, user account or system keeps all the history, but the characters must be added again, because their logins can only be read where they were saved.

## App updates

- EVE Farm checks for a new version when it starts and once a day. When there is one, an **Update available** button appears at the top right.
- **Help > Check for Updates...** checks right away.
- The update window shows the release notes. **Update Now** downloads and installs the update and restarts EVE Farm. **Later** asks again next time. **Skip This Version** stops asking until a newer version comes out.
- An update is only downloaded from EVE Farm's GitHub releases and only installed when its signature matches the release key built into the app. Your data is backed up first.
- To stop the automatic check, clear **Check for new versions automatically** in **Options > Settings...**.

**Help > About EVE Farm** shows the version you have.

## Tray icon and notifications

- Minimizing EVE Farm hides it in the tray, next to the clock. Click the tray icon, or right-click it and choose **Open**, to bring it back. **Exit** closes the app. On a Linux desktop without a tray, such as GNOME without extensions, EVE Farm minimizes like any other window.
- EVE Farm shows desktop notifications (on Linux through `notify-send`) for Abyss runs (entering the Abyss, a run saved, the loot counted, a ship lost), for every 1,000 NPCs killed in belts, and for a new version while the window is hidden.
- Every 15 minutes while it runs, EVE Farm also checks your characters and tells you about:
  - **industry jobs** that are ready to deliver;
  - **market orders** that someone else has outbid at the same station: a cheaper sell order or a higher buy order. Orders of your own characters don't count;
  - **skill queues** that end within 24 hours or are empty;
  - **your contracts** that someone accepted in the last day, or that expire within 24 hours. Contracts for your corporation don't count;
  - **LP Store purchases** whose lowest sell price climbs back to their **Target Price** after being below it. See [LP Purchases](#lp-purchases).

  Each event is shown once. An order that is outbid again after leading is shown again. The market order check runs even when its notification is off, as it keeps the **Outbid** column of Market Orders up to date. When several things happen at once, they come in one notification per kind. Turn each kind on or off under **Notifications** in **Options > Settings...**. Skill queue notifications need the character added with EVE Farm 1.0.16 or later.

## Your data and privacy

Everything stays on your computer, in `%USERPROFILE%\.evefarm` on Windows and `~/.evefarm` on Linux:

- `evefarm.db` holds your characters, history and settings;
- `backups` holds the automatic backups;
- `logs` holds the log files.

EVE Farm logs in through EVE's official login (EVE SSO), so it never sees your password. Logins and the Janice key are encrypted: on Windows with Windows' own protection, so only your Windows account can read them; on Linux with a key kept in your desktop keyring.

EVE Farm has no telemetry and sends nothing to its developer. It only connects to:

- EVE's official login and API, for the characters you add;
- Fuzzwork, for market prices and EVE's static data (items, NPCs, agents);
- Janice, only if you choose it as your price provider;
- EVE's image server, for item icons;
- GitHub, to check for and download updates;
- zKillboard, only when you click one of its links.

A character's location and ship are only read while you track Abyss runs for it. EVE Farm saves the runs it detects, not where the character has been.

**Uninstalling:** close EVE Farm and delete the folder you unzipped it into. To remove your data too, delete `%USERPROFILE%\.evefarm` (`~/.evefarm` on Linux); copy any backups you want to keep first. On Linux, also delete **EVE Farm login key** from your keyring. EVE Farm installs nothing else and doesn't change system settings.

## Trademarks

© 2014 CCP hf. All rights reserved. "EVE", "EVE Online", "CCP", and all related logos and images are trademarks or registered trademarks of CCP hf.

EVE Farm is an independent, fan-made application that uses the official EVE Online APIs under the EVE Developer License Agreement. It is not made, endorsed or supported by Fenris Creations (formerly CCP Games), is not affiliated with it in any way, and Fenris Creations is not responsible for its content or functioning.
