# Goldbucks economy mod: design

A jobs-and-money mod for a small family LAN world (2–3 players, ages 10–14).
There is no way to win; the point is to run a business, earn, spend and compete on net worth.

Goldbucks only runs in worlds with its **Goldbucks** game rule on (Create World > More > Game Rules, or `/goldbucks on` by the host). Every other world is untouched: no rules, houses, files or commands.

The next set of features (the town square, store, townsfolk, levels, new jobs and events) is planned in [DESIGN-TOWN.md](DESIGN-TOWN.md).

## Setup

- Minecraft Java 26.3, Fabric. Every Mac on the LAN has the mod installed.
- New world, opened to LAN by one player (the host), who is also the admin.
- Players gather and craft their own materials. There is no all-items menu.
- There is a hunger bar. There are no monsters, and players cannot die.
- Flying is disabled, so getting around takes effort and transport is worth paying for.
- Cheat commands (time, gamemode, give and similar) are blocked for everyone except the host. Look-up commands (`/balance`, `/job`, `/plot`, `/shop`, `/business`, `/home`, `/goldbucks`) work for everyone.

## Money

- The currency is the Goldbuck. The game tracks each player's balance; there is no physical cash item.
- The Goldbuck picture appears on screen next to balances.
- Everyone starts with $20 and a house.
- A side bar shows the players currently online and each one's net worth.

## Jobs

Chosen from a list on first login. A player keeps the job unless they are fired or sell the business.

| Job | Daily pay | How many players | Daily task (any one earns the day's pay) |
|---|---|---|---|
| Vehicle Dealer | $10 | One | Sell a vehicle item (boat, minecart, rail, saddle) |
| Store Owner | $8 | Several | Make a sale |
| Cook / Restaurant Owner | $7 | Several | Cook food, or sell a meal |
| Salesperson | $6 | One | Make a sale to a player not yet sold to that day |
| Taxi Driver | $5 | One | Carry another player a set distance |
| Librarian | $4 | One | Sell a book, or write and sign one |

## Payday

- Once per Minecraft day, measured on the mod's own clock so sleeping or changing the time cannot trigger it.
- Paid only to players who are online and completed at least one job task that day.

## Plots

- Every job comes with one plot and a store on it.
- All plots are 16×16 blocks and cannot overlap.
- Only the owner can build or break blocks on their plot.

## Starting houses

- On first login the mod builds each player a house in "the neighborhood", a residential area near the world spawn.
- Houses stand in a row, ten blocks apart, and are separate from job plots.
- Every house is the same simple design: a 7×7 wooden house with a door, two windows, a bed, a chest and a torch.
- A sign by the door names the owner. The house is the player's spawn point, and `/home` takes them back to it.
- Only the owner can build or break blocks in their house.

## Shops

- A shop is a sign on the owner's plot, placed on or above a chest. Line 1 reads `[Shop]`, line 2 the price (for example `$5`).
- The chest holds the stock. Clicking the sign buys one item from it.
- No item may cost more than $40.
- The buyer receives the item; the money goes to the shop owner.
- Shops only sell. Players cannot sell items to a shop.
- When an owner writes a shop sign, the game says whether the shop is open or what is missing. `/shop` and the guide book explain the steps.

## Credit and debt

- A purchase goes through even if the buyer cannot afford it, down to a balance of -$100.
- A player in debt has a label above their name showing the amount.
- Debt is repaid automatically out of salary.

## Taxes

- Every 7 Minecraft days, each player pays 10% of their balance above $50.
- Tax money leaves the game.

## Item expiry

- Applies only to items bought from a shop. Things a player gathers or crafts never expire.
- Bought food can be eaten once.
- Other bought items expire a random 5–7 Minecraft days after purchase.
- Building blocks never expire.

## Buying a business

- The owner must agree to the sale.
- The game sets the price: $50 plus everything the business has ever sold. There is no cap.
- The plot, shop and job all transfer to the buyer, who also keeps any business they already had.
- The seller picks a new job.

## Host powers

- Fire a player, who then picks a new job.
- Set balances and forgive debt.

## Points to confirm at review

These follow from the decisions above but were never stated outright.

1. Pay amounts and daily tasks in the jobs table were proposed and not objected to.
2. Tax is read as 10% of the balance above $50, not 10% of the whole balance.
3. Net worth on the side bar means the balance, shown negative when in debt. It does not include the value of businesses owned.
4. A player who owns several businesses is paid for each one whose task they completed that day.
5. Since players cannot die, an empty hunger bar stops sprinting but does no damage.
6. A player who sells or is fired can only pick a job that is free. Store Owner and Cook are always available.

## Build order

Each step is playable on its own.

1. Player record, starting money and the side bar.
2. World rules: no flying, no monsters, no death, hunger, blocked commands.
3. Job screen on first login, payday clock, daily tasks.
4. Plots and plot protection.
5. Shops, price cap, credit and debt.
6. Taxes and item expiry.
7. Buying a business and host commands.
