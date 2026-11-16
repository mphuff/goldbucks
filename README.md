# Goldbucks

A jobs-and-money mod for a family LAN world on Minecraft Java 26.3 (Fabric).
The rules are in [DESIGN.md](DESIGN.md), the planned town update is in [DESIGN-TOWN.md](DESIGN-TOWN.md), and the build checklist is in [TASKS.md](TASKS.md).

## Install (each Mac)

1. Install Fabric Loader for Minecraft 26.3 with the installer from fabricmc.net.
2. Download Fabric API for 26.3 and put the `.jar` in your Minecraft `mods` folder.
3. Download `goldbucks.jar` from https://mphuff.github.io/goldbucks/ (or this repo's Releases page) and put it in the same `mods` folder. Remove any older Goldbucks jar first.
4. Start Minecraft with the Fabric profile. Every Mac must use the same mod version.

On a Mac the mods folder is `~/Library/Application Support/minecraft/mods`.

## Start a world

Goldbucks only runs in worlds where it is switched on. All your other worlds stay normal Minecraft.

1. One player creates a new world. On the Create World screen, open **More > Game Rules**, type `gold` in the search box, and switch **Goldbucks** to **ON**.
2. That player opens the world to LAN, and is the host.
3. Everyone else joins from the multiplayer screen.

In a Goldbucks world the mod puts everyone in survival mode, removes monsters, and stops players being hurt.

**An existing world:** the host types `/goldbucks on` to make it a Goldbucks world, or `/goldbucks off` to stop. Turning it off keeps everyone's money, jobs and houses for if it is turned on again; the buildings stay in the world. `/goldbucks status` says which it is.


## How to play

- **First login:** you get a house in the neighborhood with your name on a sign, $20, a guide book, and a menu to pick your job. Your house is your spawn point; `/home` takes you back to it.
- **Side bar:** shows everyone online with their **Cash** (what you can spend now) and **Worth** (cash plus savings, things bought at the Town Store, and $50 for each business).
- **Help:** `/goldbucks` lists every command. `/shop` explains how to sell. `/balance` shows your money and net worth.
- **Your plot:** stand where you want your business and type `/plot claim`. You get 16×16 blocks that only you can build on. `/plot allow <player>` lets a friend help; `/home allow <player>` does the same for your house.
- **Payday:** every Minecraft day (20 minutes of play) you are paid if you did your job's task that day. `/job` shows your task, your level and whether today's is done.
- **Open a shop:** on your plot, place a chest, put the items for sale in it, and put a sign on top of the chest or on its side. Write `[Shop]` on the first line and a price such as `$5` on the second. Nothing may cost more than $40. When you finish the sign, the game tells you whether the shop is open, and what townsfolk think the item is worth.
- **Buy:** right-click someone's shop sign to buy one item.
- **Credit:** you can spend down to -$100. Debt shows on your name tag and comes out of your pay.
- **Tax:** every 7 days you pay 10% of the cash you have above $50 (the Mayor can change the rate). Money in the bank is not taxed.
- **Expiry:** tools and other items bought from a shop vanish 5 to 7 days later. Blocks and food do not.
- **Sell your business:** `/business sell <player>`. The buyer clicks the offer in chat. The price is $50 plus everything the business has ever sold.

### The town

- **The town square** is next to spawn. Right-click the **lectern** for the Town Board: orders that pay, every day, whether or not anyone else is online. Filling an order tagged with your job counts as your daily task. `/board` shows the orders anywhere.
- **Townsfolk** (villagers) visit shops and buy one thing if it is in stock and fairly priced. Price too high and they walk away, and you are told.
- **The Town Store:** right-click the **Town Clerk** or type `/store`. House upgrades, a bigger plot, pets, a name colour, a shop banner, fireworks, adverts and ads in the news. Things you keep count toward your net worth.
- **Taxi passengers** wait at the **Taxi Stand**. Ride up in a boat, right-click one to let them in, and take them where their name tag says.
- **The bank:** savings are safe from tax and earn interest once there is a Banker. Use the Banker's `[Bank]` sign, or the Town Clerk when there is no Banker. `/bank` tells you where to go.
- **The Herald:** the morning news at dawn. `/news` reads it again; a copy sits in the lectern.
- **Levels:** work your job on 5, 15 and 30 days to become Skilled, Expert and Master. Each level pays more and unlocks a perk.
- **Achievements:** a Goldbucks tab in the advancements screen (press L). Each one pays a small reward.
- **The Mayor:** on Election Day, run with `/mayor run <promise>` and vote with `/vote` (not for yourself). The Mayor earns $5 a day and can set the tax rate (`/mayor tax`), name the town, write a line of news, pick the Market Day theme and declare one holiday per term. `/mayor` lists the powers.
- **Events:** Market Day every 7 days, and sometimes another event at dawn: Fishing Season, Tourist Day, Treasure Hunt, Lost Pet and more. A bar at the top of the screen shows what is on.
- **Your own settings:** `/goldbucks settings` switches the sale sound, the sale message and the side bar for your screen only.

## Jobs

| Job | Pay per day | Daily task |
|---|---|---|
| Vehicle Dealer | $10 | Sell a boat, minecart, rail, saddle or harness, or fill a vehicle order |
| Store Owner | $8 | Make a sale, or fill a store order |
| Banker | $8 | Someone uses your bank, or fill a Bank order |
| Cook | $7 | Cook food, sell a meal, or fill a Cook order |
| Builder | $7 | Place 32 blocks on someone else's land with their permission, or fill a Builder order |
| Salesperson | $6 | Make a sale, or fill a sales order |
| Farmer | $6 | Sell a crop, egg, milk or wool, or fill a Farm order |
| Taxi Driver | $5 | Give a player or a townsfolk passenger a ride |
| Librarian | $4 | Sell a book, write and sign one, or fill a book order |

Store Owner, Cook, Builder and Farmer can be held by more than one player. Builders send bills with `/bill <player> <amount> <what for>`.

## Host guide

Only the host (the player who opened the world to LAN) can use these.

- `/host settings` opens the settings screen. Every number and switch in the game is there, in tabs. **Save** checks every change together; if anything would break the game, nothing is saved and the screen says why.
- `/host set <setting> <value>` and `/host get <setting>` do the same from chat.
- The settings are saved in the world folder as `goldbucks/settings.json`. If that file is edited by hand and something is wrong with it, the game uses the defaults and tells you when you join. `/host problems` shows the list again.
- `/host event start <event>`, `/host event stop`, `/host event list`: run any event now.
- `/host bonus <player> <amount> <reason>`: reward someone.
- `/host contest <theme>` starts a contest; `/host contest winner <player> <prize>` names the winner.
- `/host impeach` removes the Mayor and calls an election for the next day.
- `/host fire <player>`, `/host setbalance <player> <amount>`, `/host forgive <player>`, `/host payday`: as before.
- Cheat commands such as `/give` and `/time` are host-only (this can be switched off in the World rules tab).

**Changing the town's content:** item values, store items and prices, town orders and event names are data files. To change them, make a data pack in the world's `datapacks` folder containing any of `data/goldbucks/town/item_values.json`, `store.json`, `orders.json` or `events.json` (copy them from the mod jar to start), then type `/reload`. Files with mistakes are refused, and the mod's own data is used until they are fixed.

## If the game crashes

A crash report that says `OutOfMemoryError: Java heap space` means Minecraft ran out of the memory it was given. Goldbucks was checked for this: hundreds of simulated game days and a real-time run with customers and events did not make its memory use grow. Give Minecraft more memory:

1. In the Minecraft launcher, open **Installations**, hover over the Fabric installation, and click **⋯ > Edit > More options**.
2. In **JVM arguments**, change `-Xmx4G` to `-Xmx6G` (on a Mac with 16 GB or more).
3. If it still happens, lower the render distance from 16 to 12 in Options > Video Settings.

Crash reports are in `~/Library/Application Support/minecraft/crash-reports` on a Mac.

## Building and testing

```bash
./gradlew build
```

This compiles the mod, runs the rule tests, and runs the in-game tests on a headless server. The jar lands in `build/libs`.

```bash
scripts/client-test.sh
```

This runs the on-screen tests in a headless game client (Linux only) and saves screenshots to `build/run/clientGameTest/screenshots`.

**Website.** The page is in `site/` and is published to https://mphuff.github.io/goldbucks/ by the build on `main`, filled in from `gradle.properties`, the release's checksum and `CHANGELOG.md` (add an entry for each new version). To retake its screenshots:

```bash
XVFB_SCREEN=1920x1080x24 GOLDBUCKS_SHOTS=$PWD/site/images scripts/client-test.sh && scripts/site-images.py
```

## Licence

Free to download and play; everything else is reserved. See [LICENSE](LICENSE). Not an official Minecraft product.
