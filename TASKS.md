# Task list

Built from `DESIGN.md`. A task is ticked only when its test passes.

Test kinds:
- **unit**: plain rule tests in `src/test`, run by `./gradlew test`.
- **game**: tests on a real headless Minecraft server in `src/gametest`, run by `./gradlew build`.
- **client**: tests on a headless game client, run by `scripts/client-test.sh`.

## Milestone 1: foundation

- [x] 1.1 Rule tests run in the build. Test: unit `RulesTest`.
- [x] 1.2 Player record with $20 starting money. Test: unit `EconomyTest.newPlayerStartsWithTwenty`.
- [x] 1.3 Records save and reload with the world. Test: unit `EconomyTest.survivesSaveAndLoad`.
- [x] 1.4 In-game tests run in the build. Test: game `FoundationGameTest.modLoads`.
- [x] 1.5 A joining player gets a record. Test: game `FoundationGameTest.joiningPlayerGetsAccount`.
- [x] 1.6 Side bar shows online players and net worth with the Goldbuck picture. Test: client `SidebarClientTest`.

## Milestone 2: world rules

- [x] 2.1 No flying. Test: game `WorldRulesGameTest.playersCannotFly`.
- [x] 2.2 No monsters. Test: game `WorldRulesGameTest.monstersDoNotSpawn`.
- [x] 2.3 Players cannot die; hunger drains but does no damage. Test: game `WorldRulesGameTest.playersCannotDie`.
- [x] 2.4 No all-items menu. Test: game `WorldRulesGameTest.playersAreNotCreative`.
- [x] 2.5 Cheat commands blocked for everyone but the host. Test: game `WorldRulesGameTest.cheatCommandsBlocked`.

## Milestone 3: jobs

- [x] 3.1 Job list, pay and exclusivity rules. Test: unit `JobsTest`.
- [x] 3.2 Job menu on first login. Test: client `JobMenuClientTest`; game `JobsGameTest.choosingAJob`.
- [x] 3.3 Payday clock independent of time of day. Test: unit `PaydayTest.clockIgnoresSkippedNights`.
- [x] 3.4 Pay only for online players who did a task. Test: unit `PaydayTest.payRequiresTaskAndOnline`.
- [x] 3.5 Daily tasks detected for each job. Test: game `JobsGameTest.tasks*`.

## Milestone 4: plots and houses

- [x] 4.1 Plot claims: 16×16, no overlap. Test: unit `PlotsTest`.
- [x] 4.2 Only the owner can build on a plot. Test: game `PlotsGameTest.strangersCannotBuild`.
- [x] 4.3 Starting house built in the neighborhood, ten blocks apart. Test: game `HousesGameTest.houseIsBuilt`; client `JobMenuClientTest` (house at ground level).

## Milestone 5: shops, credit and debt

- [x] 5.1 Purchase rules: price cap $40, credit to -$100, money to the owner. Test: unit `ShopRulesTest`.
- [x] 5.2 Shop sign on a chest: the owner stocks the chest and writes the price. Test: game `ShopGameTest.ownerStocksShop`.
- [x] 5.3 Buying from a shop. Test: game `ShopGameTest.buyerGetsItemOwnerGetsMoney`.
- [x] 5.4 Debt label above the name. Test: game `ShopGameTest.debtorIsLabelled`.
- [x] 5.5 Debt repaid from salary. Test: unit `PaydayTest.salaryRepaysDebt`.

## Milestone 6: taxes and expiry

- [x] 6.1 Tax of 10% above $50 every 7 days, removed from the game. Test: unit `TaxTest`.
- [x] 6.2 Bought items expire after a random 5–7 days; blocks and gathered items do not. Test: unit `ExpiryTest`; game `ShopGameTest.boughtItemsExpireButBlocksAndFoodDoNot`.
- [x] 6.3 Bought food can be eaten once. Test: game `ShopGameTest.boughtFoodIsSingleUse`.

## Milestone 7: business sales and host commands

- [x] 7.1 Business price is $50 plus lifetime sales. Test: unit `BusinessSaleTest.price`.
- [x] 7.2 Sale needs the owner's agreement; plot, shop and job transfer; seller picks a new job. Test: unit `BusinessSaleTest.transfer`; game `BusinessGameTest`.
- [x] 7.3 Host commands: fire, set balance, forgive debt. Test: game `HostCommandsGameTest`.

## Version 0.2.0: fixes from the first playtest

- [x] 8.1 Issue 1, how to sell is not clear: `/shop` steps, steps shown after a plot claim, feedback when a shop sign is written, guide book for new players. Test: game `ShopGameTest.signTellsOwnerWhatIsWrong`, `ShopGameTest.signOnTheSideOfTheChestWorks`, `HousesGameTest.houseIsBuilt` (guide book).
- [x] 8.2 Issue 2, look-up commands for everyone: unknown commands are no longer called host-only; new `/balance`, `/shop`, `/home`, `/guide`, `/goldbucks`. Test: game `WorldRulesGameTest.lookUpCommandsWorkForEveryone`.
- [x] 8.3 Issue 3, whose house is whose: name sign by the door, spawn point at the house, `/home`, signs added to houses from 0.1.0. Test: game `HousesGameTest.houseIsBuilt`; client `JobMenuClientTest` screenshot.
- [x] 8.4 Issue 4, plot refusal names whose land is in the way. Test: unit `PlotsTest.refusalCanNameWhoseLandIsInTheWay`.

## Version 0.3.0: the town update

Built from [DESIGN-TOWN.md](DESIGN-TOWN.md), all in one version.

### Settings and data files

- [x] 9.1 World settings file with defaults; every game number read from it. Test: unit `SettingsTest`, `RulesTest.numbersFollowTheSettings`; game `SettingsGameTest.settingsFileIsWrittenAndHostCanChangeIt`.
- [x] 9.2 Bad values and bad combinations refused; broken files fall back to defaults and the host is told. Test: unit `SettingsTest.*Rejected`, `SettingsTest.aBrokenFileFallsBackToDefaultsAndSaysWhy`; game `SettingsGameTest.aBrokenSettingsFileFallsBackToDefaults`.
- [x] 9.3 Data files (item values, store, orders, events) load from data packs and are checked, including against the real item list. Test: unit `TownDataTest`; game `SettingsGameTest.dataFilesNameRealItems`.
- [x] 9.4 Host settings screen, `/host set`, `/host get`, `/host problems`; only the host can use them. Test: client `TownClientTest`; game `SettingsGameTest.onlyTheHostCanChangeSettings`.
- [x] 9.5 Each player's own settings (sale sound, sale message, side bar) with `/goldbucks settings`. Test: client `TownClientTest`.
- [x] 9.6 Worlds saved by 0.2.0 load with everything intact. Test: unit `TownRulesTest.aWorldSavedBy020LoadsWithEverythingIntact`.

### The square, the board and townsfolk

- [x] 10.1 Town square with board, clerk, taxi stand, podium, fountain and lanterns; host-only building. Test: game `TownGameTest.townSquareIsBuiltAndOnlyTheHostCanChangeIt`.
- [x] 10.2 Orders posted each day per job held, perks and events, pay limits, three a day each. Test: unit `TownRulesTest.orders*`, `eventsChangeOrders`.
- [x] 10.3 Delivering an order takes the items, pays, and counts as the job task. Test: unit `TownRulesTest.fillingAnOrderPaysAndCountsAsTheJobTask`; game `TownGameTest.boardOrdersTakeItemsAndPay`.
- [x] 10.4 Townsfolk buy only in-stock, fairly priced items, cheapest first; walking customers. Test: unit `TownRulesTest.townsfolk*`; game `TownGameTest.townsfolkBuyOnlyAtFairPrices`, `walkingCustomersArriveAndBuy`.
- [x] 10.5 Taxi passengers and tourists. Test: game `TownGameTest.passengersPayTheDriver`.

### The store and net worth

- [x] 11.1 Store rules: prices, buy once, requirements, credit, switched-off items. Test: unit `TownRulesTest.store*`, `switchedOffStoreItemsCannotBeBought`.
- [x] 11.2 Every house upgrade is built, never over a player's own build, and its land is protected. Test: game `TownGameTest.storeBuildsEveryHouseUpgrade`, `upgradesNeverBuildOverWhatAPlayerMade`; unit `TownRulesTest.houseUpgradesProtectTheirLand`.
- [x] 11.3 Plot growth. Test: unit `TownRulesTest.plotsGrowAroundTheirCentreUnlessLandIsInTheWay`.
- [x] 11.4 Pets, name colour, banner, fireworks, adverts and Herald ads. Test: game `TownGameTest.petsAreTameAndCannotBeHurt`, `nameColourShowsInTheTeamColour`, `heraldIsComposedAndKeptInTheLectern`.
- [x] 11.5 Net worth counts savings, property and businesses. Test: unit `TownRulesTest.netWorth*`.
- [x] 11.6 Store screen. Test: game `TownGameTest.storeMenuListsItemsWithReasons`; client `TownClientTest`.

### Feedback

- [x] 12.1 Sale ping above the hotbar with a sound, wherever the owner is. Test: client `TownClientTest` (settings); game `TownGameTest.townsfolkBuyOnlyAtFairPrices`.
- [x] 12.2 Payday title, sparkles and breakdown; level-up title. Test: client `TownClientTest` screenshot `morning-herald`.
- [x] 12.3 Morning Herald, `/news`, book in the lectern. Test: game `TownGameTest.heraldIsComposedAndKeptInTheLectern`.
- [x] 12.4 Login summary of sales while away. Test: game `TownGameTest.loginSummaryShowsSalesWhileAway`.

### Levels and achievements

- [x] 13.1 Days worked, levels, pay bonus, streaks; levels stay with the job. Test: unit `TownRulesTest.levels*`, `streaksCountDaysInARow`.
- [x] 13.2 Level on the name tag and tab list; Cook perks. Test: game `ProgressGameTest.levelsShowOnTheNameTagAndCooksGetPerks`.
- [x] 13.3 Perks for every job. Test: unit `TownRulesTest.perksAddOrdersVisitsAndRaiseCaps`, `bankRules`, `interestIsCapped`.
- [x] 13.4 25 achievements as vanilla advancements with rewards. Test: game `ProgressGameTest.achievementsAreAdvancementsWithRewards`.

### New jobs and the Mayor

- [x] 14.1 Builder, Farmer and Banker; build permission and bills. Test: unit `JobsTest`, `TownRulesTest.ownersCanLetOthersBuild`, `buildersCountBlocksOnOthersLand`, `billsArePaidOnlyByTheirReceiver`.
- [x] 14.2 The bank: savings, interest, no tax on savings, Town Clerk with no Banker. Test: unit `TownRulesTest.savings*`, `bankRules`, `interestDayDepositsCountTwice`; game `TownGameTest.theBankerRunsTheBank`.
- [x] 14.3 Elections: running, no self-votes, coin flips. Test: unit `TownRulesTest.elections*`, `aTieIsACoinFlip`; game `ProgressGameTest.electionsMakeAMayor`.
- [x] 14.4 Mayor powers and limits, `/host impeach`. Test: unit `TownRulesTest.mayorPowersHaveLimits`; game `ProgressGameTest.electionsMakeAMayor`.

### Events

- [x] 15.1 Event engine: Market Day, random chance, conditions, multi-day events, switching off. Test: unit `TownRulesTest.marketDay*`, `conditions*`, `multiDayEventsKeepGoing`, `eventsCanBeSwitchedOff`, `aNewShopGetsAGrandOpeningTheNextDay`.
- [x] 15.2 Boss bar; every event starts and stops cleanly. Test: game `ProgressGameTest.everyEventCanStartAndStopCleanly`.
- [x] 15.3 Treasure Hunt, Storm Repairs, Construction Boom, Lost Pet, Holiday, Town Feast. Test: game `ProgressGameTest.*`; unit `TownRulesTest.theFeastWinnerGetsTen`.
- [x] 15.4 Host event, bonus and contest commands. Test: game `ProgressGameTest.lostPetAndHolidayAndHostEventCommands`.

## Version 0.3.1: only in chosen worlds

- [x] 16.1 Goldbucks game rule, off by default, shown on the Create World screen; mod does nothing in other worlds. Test: game `ActivationGameTest.goldbucksCanBeSwitchedOffAndOnPerWorld`; client `PlainWorldClientTest`.
- [x] 16.2 `/goldbucks on`, `off`, `status`, host only; switching on sets up everyone online. Test: game `ActivationGameTest.*`; client `PlainWorldClientTest`.
- [x] 16.3 Worlds from 0.1 and 0.2 stay on; worlds first opened with 0.3.0 start off and the host is reminded. Test: unit `TownRulesTest.aWorldSavedBy020LoadsWithEverythingIntact`.
- [x] 16.4 Job menu fits nine jobs with its title. Test: client `PlainWorldClientTest` screenshot `switched-on`.

## Version 0.3.2: fixes from the 0.3.1 playtest

- [x] 17.1 Issue 5, side bar and store disagree: the side bar shows each player's cash next to their net worth, and the store shows both. Test: game `FoundationGameTest.joiningPlayerGetsAccount`; client `SidebarClientTest`, `TownClientTest` screenshots.

## Version 0.3.3: fixes from the 0.3.2 playtest

- [x] 18.1 Issue 7, house built in water: new houses take the first dry lot along the row, the town square looks for a dry spot, and anything still over water is filled in underneath. Test: unit `TownRulesTest.housesSkipLotsThatAreNotGoodLand`, `housesSkipLotsWhereSomeoneElsesLandIsInTheWay`; game `HousesGameTest.housesAreNeverBuiltInWater`.
- [x] 18.2 Issue 6, not enough wheat: a one-time farming kit for every player, a farming page in the guide book, hints on the board for where things come from, and smaller first crop orders. Test: game `HousesGameTest.newPlayersGetAFarmingKitOnce`; unit `TownDataTest.shippedDataIsValid`.
- [x] 18.3 Issue 8, out-of-memory crash: no memory leak found in the mod (a 300-day soak and a real-time run with customers and events both stayed flat); the event bar now only sends changes; the README explains how to give Minecraft more memory. Test: manual soak (not in the normal suite, as it changes shared settings).

## Website and public release

A one-page download site on GitHub Pages, with the repository made public. Version 0.4.0 is the first Goldbucks release.

### Decisions so far

- **Hosting:** make this repository public. GitHub Pages then hosts the site from this repo for free, and release downloads work for everyone.
- **Releases:** a `main` branch becomes the default; stable releases are published only from `main` when the version changes; the build no longer replaces a "latest" pre-release on every push.
- **Name:** Goldbucks, for the mod, the currency, the code and the repository.
- **Licence:** anyone may download and play for free; copying, changing, redistributing and selling stay reserved. Credit: Micah Huff.
- **Audience:** public, but not marketed. No install help beyond a short FAQ.
- **Privacy (for the future):** screenshots on the site never show the children's usernames.
- **Screenshots:** re-shot by the headless client at higher resolution in a generated world; real ones from play added later.
- **Brand rules:** no Minecraft logo or font, and the "not an official Minecraft product" line stays.
- **Web address:** https://mphuff.github.io/goldbucks/
- **No analytics.** GitHub's download counts are enough.
- **Trust:** each download shows its SHA-256 checksum and build date.
- **FAQ:** no out-of-memory entry.

### Tasks

- [x] 19.1 Choose the new name: **Goldbucks** (mod and currency). Repository renamed to `goldbucks`.
- [x] 19.2 Rename everything to Goldbucks: code package (`com.goldbucks`), mod id (`goldbucks`), command (`/goldbucks`), game rule (`goldbucks:goldbucks`), world files, item data, chat, screens, side bar, guide book, Herald, advancements, README, design docs, build and jar names. New pixel-art coin artwork and icon (`scripts/draw-goldbuck.py`). No upgrade path from the old name (decided: not needed). All tests renamed and passing.
- [x] 19.2a Name audit: `scripts/name-audit.sh` fails the build if an old name appears anywhere; it runs in the GitHub build.
- [x] 19.3 Licence: free to download and play; everything else reserved.
- [x] 19.4 Pre-public check: no secrets, passwords, email addresses or usernames in the files or history. The history, old tags and old releases still contain the old name and artwork; see 19.5.
- [x] 19.5 Create `main` as a fresh start (one commit with today's files, no old history), make it the default branch, and delete the old branches, tags and releases. The build now publishes stable releases only from `main`, each with a fixed-name `goldbucks.jar` and its checksum.
- [x] 19.6 Re-shoot screenshots at 1920×1080 in a normal generated world, with a plain player name: town square on Market Day, Town Store, Town Board, job menu, side bar with cash and worth, house with upgrades, a shop with a customer, payday and the Herald, host settings. Made by `ScreenshotsClientTest` (`GOLDBUCKS_SHOTS=$PWD/site/images XVFB_SCREEN=1920x1080x24 scripts/client-test.sh`, then `scripts/site-images.py`).
- [x] 19.7 Build the page (`site/`): dark mod-site style with a free pixel heading font; hero with download button and Fabric / Minecraft 26.3 / version badges; gallery; features; requirements; host section (game rule, `/host settings`, data packs); FAQ (nothing happens = switch is off; every computer needs the same version; can I change prices); changelog; footer with licence and disclaimer. Works on phones, light and dark.
- [x] 19.8 Build step that publishes the page on each stable release, filling in the version, download link, checksum, date and changelog (`scripts/build-site.py`, the `pages` job in `build.yml`, `CHANGELOG.md`). Every build checks the page still builds.
- [x] 19.9 Micah: make the repository public and turn on Pages (Settings > Pages > Source: GitHub Actions).
- [ ] 19.10 Check the live site at phone and desktop widths, and check the download button returns the real jar while signed out.

## Blocked or needs a decision

- The "Points to confirm" list at the end of `DESIGN-TOWN.md`, after the first playtest of 0.3.0.
