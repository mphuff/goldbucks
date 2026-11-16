# The town update: design (version 0.3.0)

This adds to [DESIGN.md](DESIGN.md), which stays the rulebook for everything already built.
Where this file changes an existing rule, it says so.

The goal: give the kids something worth saving for, customers who are not their siblings,
a sense of getting better at their job, and a reason to log in tomorrow.

## Keep it feeling like Minecraft

Every feature below follows these rules. When in doubt, the rule wins over the feature.

1. **Survival stays the core.** Money buys convenience, land, pets and style. It never replaces gathering, mining, farming or crafting.
2. **The town never undercuts the players.** The Town Store does not sell anything a player can craft, cook, farm or mine. Food, tools, boats, books and blocks come from players.
3. **Use what Minecraft already has.** Townsfolk are villagers. Achievements are advancements, with the normal toast popup and advancements screen. The town board is a lectern and signs. Events show on a boss bar. Sounds and particles are vanilla ones. No custom mobs or models.
4. **Everything is made of blocks.** House upgrades and the town square are ordinary blocks that look like something a player could have built.
5. **Short, plain messages.** The youngest player is 10. Chat lines stay under about 12 words where possible, and the details go in a book or screen.

---

## 1. The town square, town board and townsfolk

### The town square

- On first start, the mod builds a small town square at world spawn, next to the neighborhood. It is a 15×15 stone plaza with:
  - the **Town Board**: a lectern under a row of signs, with a bell;
  - the **Town Store** stall, run by a villager called the **Town Clerk**;
  - a **Taxi Stand** sign, where townsfolk passengers wait;
  - a **Mayor's podium** (used once there is a Mayor, see section 4);
  - a fountain and lanterns, for looks.
- Only the host can build or break blocks on the square.
- Worlds started on 0.2.0 or earlier get the square on first load of 0.3.0, placed on the nearest clear flat ground to spawn.

### Town Board orders

The town posts jobs every morning. These are paid whether or not any sibling is online.

- At dawn on the mod's day clock, the board posts new orders. Each order has:
  - what is wanted (vanilla items and a count, for example "6 bread"), or a ride (Taxi orders);
  - the pay;
  - a job tag (for example "Cook order"), or none.
- **How many:** one order for each job currently held by a player, plus two orders with no job tag. With three players that is about five orders a day.
- **Pay:** the items' value from the item value list (see "Item values" below) × 1.3, rounded, with a minimum of $3 and a maximum of $20.
- **Filling an order:** right-click the lectern to open the board screen. It lists the orders. If you have the items, the **Deliver** button takes them and pays you at once. The first player to deliver gets the order.
- **Orders count as job tasks.** Filling an order tagged with your job counts as that day's task. This is the key fix: every job can now earn its pay without another player's help.
- Orders not filled by the next dawn disappear.
- No player may fill more than three orders a day, so one player cannot take them all.
- Items delivered to the town leave the game.

**Taxi orders.** A townsfolk passenger waits at the Taxi Stand with a destination: a player's shop or house. Ride up in a boat (or on a camel), right-click the passenger to let them in, and carry them there, at least 30 blocks. They get out and you are paid. A tourist (Tourist Day) also buys two things at the shop they are taken to.

**Builder orders** ask for building blocks (for example "32 stone bricks"), or for a town build during an event (see section 5).

### Townsfolk customers

Villagers from the town walk to player shops and buy things.

- Each day, each player who owns at least one open shop gets **2 townsfolk visits**. Events and adverts add more (see sections 2 and 5).
- A townsfolk customer is a villager named "Townsfolk" plus a first name ("Townsfolk Ada"). They cannot be hurt, pushed into lava, traded with, or led away.
- When someone is within 48 blocks of the shop, the customer appears a few blocks away and walks up to the sign. Otherwise the visit happens without a visible villager: the sale still goes through and the owner still hears about it.
- At the shop, the customer looks at each sign in turn and buys **one item** from the first shop that passes both checks:
  - **In stock.** The chest has the item.
  - **Fair price.** The price is no more than 1.5× the item's value on the item value list. Items not on the list are never bought by townsfolk.
- Cheaper shops are visited first. An owner with a shop priced at or below the item's value gets an extra visit that day.
- Shop-bought items cannot be delivered to town orders, so buying and handing things on does not pay.
- **What the owner sees and hears:** green villager sparkles and a "yes" sound for a sale. For a shop priced too high, the villager shakes its head, shows the angry cloud, and the owner gets "Townsfolk Ada thought your bread was too pricey." For an empty chest, "Townsfolk Ada wanted bread, but your shop is empty."
- A townsfolk purchase counts as a sale for job tasks, just like a player purchase. The item leaves the game.
- At most three visible townsfolk walk around at once, so the world does not fill with villagers.

This teaches pricing and stock-keeping without a lecture: price too high and they walk away, run out of stock and you miss sales.

### Item values

- A list of vanilla items with a value in Goldbucks, for example: bread $2, cooked beef $3, cake $8, oak boat $6, minecart $10, saddle $15, book $4, written book $8, wheat $1, 16 stone bricks $3.
- It sets the fair price for townsfolk and the pay for town orders.
- It ships with 83 common items and is a data file (see section 7), so the host can change it.

---

## 2. The Town Store

Something big to save for.

### How it works

- Right-click the Town Clerk, or type `/store`, to open the store screen. It looks like the vanilla villager trading screen: a list on the left and details on the right, with a **Buy** button.
- Store purchases use your balance, and can use credit down to -$100 like shop purchases. The $40 price cap applies only to player shops, not to the store.
- Money spent at the store leaves the game.
- Every item shows its price, what it does, and whether you already have it.

### What it sells

**Home upgrades.** The mod builds them onto your house, block by block over a few seconds with a sound, so you can watch. Each one can be bought once.

| Upgrade | Price | What it adds |
|---|---|---|
| Porch | $40 | A covered porch with a bench by the front door |
| Garden | $60 | A fenced flower and vegetable patch behind the house |
| Basement | $120 | A room under the house, with stairs down |
| Second floor | $200 | A full upstairs room with windows and stairs |
| Stable | $150 | A stable next to the house, with a hay bale and water trough |
| Pool | $200 | A small pool in the garden (needs the Garden) |
| Lookout tower | $350 | A tall corner tower with a ladder and a view (needs the Second floor) |

- Each upgrade also grows the protected area around the house to cover it.
- If something the player built is in the way, the store says what is blocking it and does not take the money.

**Land.**

| Item | Price | What it does |
|---|---|---|
| Bigger plot | $150 | Your business plot grows from 16×16 to 24×24 |
| Biggest plot | $300 | Your plot grows from 24×24 to 32×32 (needs the Bigger plot) |

A plot can only grow into empty land. The store says whose land is in the way, as plot claims do now.

**Pets.** A tame vanilla animal that follows you, with your name on its collar or tag.

| Pet | Price |
|---|---|
| Cat | $40 |
| Wolf | $50 |
| Parrot | $60 |
| Axolotl in a bucket | $40 |

Pets cannot be hurt, and are counted as owned once bought, even if they wander off. `/pet call` brings them back to you.

**Style.**

| Item | Price | What it does |
|---|---|---|
| Name colour | $30 | Pick a colour for your name in chat, the tab list and the side bar. Can be changed again for $10. |
| Shop banner | $25 | A white banner to design on the loom, a sign for "OPEN", and a glow ink sac to make it glow |
| Fireworks show | $15 | Fireworks over your house or shop at dusk. Used up when it goes off. |

**Business.**

| Item | Price | What it does |
|---|---|---|
| Advert | $10 | 2 extra townsfolk visits to your shops today. Used up at the end of the day. |
| Herald ad | $8 | One line about your shop in tomorrow's morning news (see section 6) |

### Net worth now counts what you own

This **changes DESIGN.md "Points to confirm" item 3**, which said net worth was the balance only.

- **Net worth = balance + bank savings + the price paid for every home upgrade, land, pet and style item still owned + $50 for each business owned.**
- Things that get used up (adverts, fireworks, Herald ads) do not count.
- `/balance` shows the breakdown: cash, savings, property, businesses.
- The side bar still shows one number per player.

Spending at the store no longer makes you drop down the side bar. The money becomes something you own.

---

## 3. Job levels and achievements

### Levels

Each job has four levels. You go up by doing the job's daily task on more days.

| Level | Days worked in this job | Pay bonus per day |
|---|---|---|
| Apprentice | 0 | none |
| Skilled | 5 | +$1 |
| Expert | 15 | +$3 |
| Master | 30 | +$5 |

- "Days worked" counts days on which you did that job's task, whether by a sale, a town order or anything else that counts.
- Levels belong to the job. If you sell a business and later come back to that job, your days worked are still there.
- Your level and job show in the tab list and on your name tag: **"Expert Cook Ella"**.
- On reaching a new level: a title on screen ("You are now an Expert Cook!"), the vanilla level-up sound and a burst of firework particles. Everyone online sees a chat line.
- `/job` shows your level, days worked, and how many days until the next level.

### Perks

Every level from Skilled up also unlocks a perk. Perks make the job feel different, not just pay more.

| Job | Skilled | Expert | Master |
|---|---|---|---|
| Vehicle Dealer | One extra vehicle order on the board each day | Townsfolk pay up to 2× value for vehicles | Price cap for vehicles goes up to $60 |
| Store Owner | +1 townsfolk visit each day | Write x2, x3 or x4 on line 3 of a shop sign to sell a bundle | +2 townsfolk visits each day |
| Cook | Food you cook fills one more hunger point | One extra Cook order each day | Your food also gives 10 seconds of Speed |
| Salesperson | Townsfolk you sell to also buy a second item, if in stock | Sales to a new player that day pay a $1 bonus from the town | The town adds 10% to every townsfolk sale |
| Taxi Driver | One extra passenger order each day | Passenger fares pay 50% more | Passenger fares pay double again on Rainy Day Rides |
| Librarian | One extra book order each day | Townsfolk buy written books at up to 2× value | Can sell enchanted books up to $60 |
| Builder | One extra Builder order each day | The town adds 10% to every bill paid to you | Your shares of town builds during events are doubled |
| Farmer | One extra Farm order each day | Crops on your plot grow faster | Animals on your plot are ready to breed again sooner |
| Banker | +$1 for each deposit or withdrawal at your bank | Savings earn 1% more interest | Each player can earn $15 more interest a week |

The Mayor is a title, not a job, and has no levels (see section 4).

### Achievements

Achievements are vanilla advancements in their own **Goldbucks** tab. They pop up with the normal toast in the top-right corner, are announced in chat, and can be looked at any time on the advancements screen.

| Achievement | How to get it |
|---|---|
| Welcome to Town | Join the world (the tab's root) |
| First Day on the Job | Pick a job |
| Open for Business | Open your first shop |
| Ka-ching! | Make your first sale |
| Townsfolk Favourite | Sell to 10 townsfolk |
| Rush Hour | Make 5 sales in one day |
| Order Up | Fill your first town order |
| Reliable | Fill 25 town orders |
| Payday | Get your first payday |
| Steady Worker | Work 7 days in a row |
| Skilled / Expert / Master | Reach each level in any job |
| Jack of All Trades | Reach Skilled in three different jobs |
| Homeowner | Buy your first home upgrade |
| Dream House | Own every home upgrade |
| Land Baron | Buy the Biggest plot |
| Best Friend | Buy a pet |
| Saver | Have $100 in the bank |
| Debt-Free | Get from debt back to $0 or more |
| $500 Club | Net worth of $500 |
| $1,000 Club | Net worth of $1,000 |
| Tycoon | Own two businesses at once |
| Taxi! | Carry 10 passengers |
| Party Animal | Take part in 5 events |
| Mr or Madam Mayor | Win an election |

Some achievements give a small reward when earned, paid by the town: $5 for most, $20 for the Master, Dream House and $1,000 Club ones.

---

## 4. New jobs

These join the job menu. The table in DESIGN.md gains these rows.

| Job | Daily pay | How many players | Daily task (any one earns the day's pay) |
|---|---|---|---|
| Builder | $7 | Several | Place 32 blocks on another player's plot or house with their permission, or fill a Builder order |
| Farmer | $6 | Several | Sell a crop, egg, milk or wool, or fill a Farm order |
| Banker | $8 | One | Another player deposits or withdraws at your bank, or fill a Bank order |

### Builder

- Players can let someone build on their land: `/plot allow <player>` and `/plot disallow <player>`. The same works for the house with `/home allow`.
- The Builder's main work is building for other players: shops, upgrades the store does not sell, decorations. The price is agreed between players. The Builder can send a bill with `/bill <player> <amount> <what for>`, which the other player clicks to pay, like a business sale.
- Builder orders on the town board ask for building blocks, or for a town build during events (see section 5).

### Farmer

- Sells crops and animal products from their shop: wheat, carrots, potatoes, beetroot, melons, pumpkins, sugar cane, eggs, milk and wool.
- The Cook needs ingredients and townsfolk buy produce, so the Farmer has customers on both sides.
- Item kind "Farm produce" is added for job tasks.

### Banker

- The Banker writes `[Bank]` on a sign on their plot. That sign is the **bank counter**.
- Right-clicking it opens the bank screen: **Deposit** and **Withdraw**, with an amount.
- **Savings:**
  - earn 5% interest every 7 days, paid by the town, up to $25 a week per player;
  - are not taxed (only the balance above $50 is taxed, as now);
  - cannot be spent at shops or the store. Withdraw them first;
  - cannot be added to while you are in debt.
- Every deposit or withdrawal by someone else counts as the Banker's task. The town also pays the Banker $1 for each one, so using the bank costs the customer nothing.
- **If nobody is the Banker,** the Town Clerk offers deposits and withdrawals, but savings earn no interest. That gives everyone a reason to want a Banker.
- Bank orders on the town board ask for gold: for example "Bring 3 gold ingots to the vault."

### Mayor (an elected title)

The Mayor is held **as well as** a job, not instead of one.

- **Elections** are held every 14 days on Election Day (see event 16). A new world holds its first election on day 0; a world from 0.2.0 holds it on its first new day.
- Anyone can run: `/mayor run`. Candidates may write a one-line promise, shown on the podium sign.
- Everyone votes on a ballot screen opened at the Mayor's podium or with `/vote`. **You cannot vote for yourself.** A tie is settled by a coin flip at the podium, with sound and particles. The host votes too if they are playing.
- The Mayor gets **$5 a day** on top of their job's pay, a gold name tag title ("Mayor Ella"), and the podium.
- **Mayor powers, for their 14-day term:**
  - set the weekly tax rate anywhere from 5% to 15% (`/mayor tax <percent>`);
  - pick the next Market Day's theme from three offered (see section 5);
  - declare one **Holiday** per term (event 17);
  - name the town, shown in the morning news;
  - write one line of the morning news each day (`/mayor announce <message>`).
- The host can remove a Mayor with `/host impeach`, which starts a new election.

---

## 5. Events

### How events run

- **Market Day** happens every 7 days on the mod's day clock. The Mayor picks its theme from three offered events, or the game picks at random if there is no Mayor.
- On other days there is a **1 in 3 chance** that a random small event starts at dawn.
- Never more than one event at a time.
- An event is announced in the morning news, with the bell ringing at the square. While it runs, a **boss bar** at the top of the screen shows its name and a countdown.
- Events last one Minecraft day unless the list says otherwise.
- Taking part (doing anything the event rewards) counts toward the Party Animal achievement.

### Host controls

- `/host event start <event>`: start any event now.
- `/host event stop`: end the current event.
- `/host event list`: show all events.
- `/host bonus <player> <amount> <reason>`: pay a reward. It shows in chat with the reason, for example "for the best storefront".
- `/host contest <theme>`: start a building or writing contest (see event 21). `/host contest winner <player>` names the winner and pays the prize.

### The events

| # | Event | Main jobs | What happens | Reward |
|---|---|---|---|---|
| 1 | **Market Day** | Everyone | Townsfolk visits ×3. Two extra orders on the board. | More sales |
| 2 | **Fishing Season** | Vehicle Dealer | Townsfolk want boats; boat orders posted; fishing rods are worth double | Boats sell at up to 2× value |
| 3 | **Railway Week** (3 days) | Vehicle Dealer, Builder | The town asks for a rail line from the square to the neighborhood. Rail and minecart orders posted. | $40 split between the players who placed the rail, once the line connects |
| 4 | **Harvest Festival** | Farmer, Cook | Farm orders pay double. Pumpkin pie orders posted. | Double farm order pay |
| 5 | **Town Feast** | Cook | The town wants a feast: five big food orders. Starts on a full moon. | $10 bonus for whoever fills the most |
| 6 | **Tourist Day** | Taxi Driver, all shops | Six tourists arrive at the Taxi Stand, each wanting to visit a different shop. Each one carried there buys two items. | Fares plus shop sales |
| 7 | **Rainy Day Rides** | Taxi Driver | Starts when it rains. Townsfolk would rather ride than walk. | Passenger orders pay double |
| 8 | **Book Fair** | Librarian | Townsfolk buy written books. Book and quill orders posted. The host reads the books and picks the best story. | $15 for the best story |
| 9 | **Treasure Hunt** | Librarian, everyone | The mod buries a chest with $30 and some diamonds within 200 blocks of the square. Clues appear in the morning news, and the Librarian can copy a clue book to sell. | The treasure |
| 10 | **Grand Opening** | Store Owner, Salesperson | Starts the day after a new shop opens. The new shop gets 4 extra townsfolk visits. | More sales |
| 11 | **Big Sale Day** | Salesperson, Store Owner | Townsfolk visits ×2, but only to shops priced at or below value. | Salesperson earns $1 from the town per townsfolk sale |
| 12 | **Construction Boom** | Builder | The town asks for a build at the square: the 5 by 5 space beside the fountain opens to everyone for the day. Blocks placed in it count. | $30 when the space is filled, split by blocks placed |
| 13 | **Storm Repairs** | Builder | After a thunderstorm, some fences and lanterns on the square fall down. These are only ever square blocks, never player builds. | $3 for each block put back |
| 14 | **Gold Rush** | Banker, everyone | Gold ingot and gold ore orders posted, paying double. | Double gold pay |
| 15 | **Interest Day** | Banker | Deposits made today earn double interest at the next interest payment. | Double interest |
| 16 | **Election Day** | Mayor | Every 14 days. Candidates sign up in the morning; voting opens at noon and closes at dusk. | The new Mayor |
| 17 | **Holiday** | Mayor | Declared by the Mayor. Fireworks over the square at dusk. Everyone online gets $5. No orders that day. | $5 each |
| 18 | **Night Market** | Everyone | Starts on a full moon. Owners with a lantern, torch or other bright light within 2 blocks of a shop sign get double townsfolk visits. | More sales |
| 19 | **Lost Pet** | Taxi Driver, everyone | A townsfolk's cat has wandered off. A clue in the news says roughly where. Bring it back to the square (it follows you). | $15, or $20 if carried back in a boat or minecart |
| 20 | **Shop Inspector** | Store Owner, Cook, Farmer | Two minutes into the day the inspector checks every shop. Owners whose shops all hold at least 5 items at fair prices get a gold star in chat and $10. | $10 and a gold star |
| 21 | **Contest** (host only) | Everyone | The host starts it with a theme (best storefront, best house, tallest tower, best book). | The host's chosen prize |

Events 16, 17 and 21 never start at random. Event 10 starts only after a new shop opens. Events 5, 7, 13 and 18 start only when the weather or moon allows.

---

## 6. Feedback that feels good

### When you make a sale

- Wherever you are in the world, you hear a short "ka-ching" (the vanilla experience orb sound, higher pitch) and see an action bar line above your hotbar: **"Ka-ching! Ada bought bread for $3."**
- Townsfolk sales use the villager "yes" sound instead.
- Several sales close together are grouped: "3 sales, +$9".

### Payday

- Gold sparkles and happy-villager particles burst around each player who is paid.
- A big title on screen: **"Payday! +$12"**, with the vanilla level-up sound.
- A chat line breaks it down: job pay, level bonus, Mayor pay, debt repaid.

### The morning news: the Goldbucks Herald

At dawn on the mod's clock, the bell at the square rings and everyone online sees:

- A title: **"Day 12"**, and the main headline underneath.
- A short Herald in chat, at most five lines:
  - the day's event, if any;
  - yesterday's top seller ("Ella sold 7 things yesterday!");
  - how many orders are on the board;
  - the Mayor's line, if there is one;
  - a Herald ad, if someone bought one.
- `/news` shows today's Herald again. A copy also sits in the lectern at the square as a book.

### When you log in

If your shops sold anything while you were offline, a short summary: "While you were away: 4 sales, +$11. Your bread shop is empty."

(This only reports sales. Payday still needs you to be online, as DESIGN.md says.)

### Client settings

Each player can turn the sale sound and the action bar line on or off, and move the side bar to the left or right of the screen. See section 7.

---

## 7. Settings and data files

So the host can tune the game without a new version of the mod, and so another family could use it.

### Three layers

1. **World settings** (a file in the world folder, `<world>/goldbucks/settings.json`). Created with the defaults the first time the world loads. Holds every number the game uses, plus:
   - pay per job and which jobs are on;
   - store prices and which items are sold;
   - townsfolk visits per day, order counts and pay limits;
   - event frequency, and which events can start at random;
   - the world rules as on/off switches: no monsters, no flying, no death, cheat commands host-only.
2. **Data files** for content: `data/goldbucks/town/item_values.json`, `store.json`, `orders.json` and `events.json` inside the mod, plus the achievements as advancements in `data/goldbucks/advancement/`. The host can replace any of them with a data pack in the world's `datapacks` folder (same paths), and `/reload` applies the change. Job *task detection* stays in code; data files only set numbers and text.
3. **Client settings** (`config/goldbucks-client.json` on each Mac). Sale sound, action bar messages, side bar position. These only affect that player's screen.

### Changing settings in the game

- **Host settings screen:** `/host settings` opens a screen in the vanilla style with tabs: Money, Jobs, Town, Events, World rules. Each setting has a short description. **Save** sends the changes to the game, which checks the player is the host and the values make sense, then applies them at once. Only the host can open it.
- **Client settings screen:** `/goldbucks settings` (the settings are saved on the player's own computer, not the host's). It is not linked from Mod Menu, so no extra mod is needed.
- **Commands** for the same things: `/host set <setting> <value>` and `/host get <setting>`, so settings can also be changed from chat.
- Changing a setting never deletes anything. Turning a job off stops new players picking it; players who have it keep it.

### Safeguards against bad settings

- Every value has a range, and combinations that would break the game are refused: for example level days out of order, a Master price cap below the normal cap, upside-down tax or order limits, starting money deeper in debt than the credit limit, or every shareable job switched off (a new player could be left with no job).
- Data files are checked too: items must exist in this version of Minecraft, store requirements must exist and not loop, plot sizes must grow, and every order must pay something.
- A broken or invalid settings file is never used: the game falls back to the defaults and tells the host what is wrong when they join (`/host problems` shows it again). A broken data pack falls back to the mod's own data the same way.
- Changes from the settings screen or `/host set` are checked all together before any is saved, so two values that only work together can be changed at once, and a refused change leaves everything as it was.

---

## Points to confirm at review

1. Net worth now includes savings, property and $50 per business (changes DESIGN.md point 3).
2. Town orders and townsfolk sales count as job tasks. This means every job can be paid without another player's help.
3. Pay amounts for the new jobs, level bonuses, store prices and event rewards are first guesses, to tune after playtesting. They are all in the world settings.
4. The Town Store, town orders and achievement rewards add money to the game; the store, taxes and expiring items take it out. The first playtest of 0.4.0 should check balances are not running away. A rough target is about $25–35 of income per player per Minecraft day.
5. You cannot vote for yourself in Mayor elections. With two players that means elections are often decided by the coin flip.
6. Townsfolk only buy items on the item value list, so a shop selling something unusual relies on players.
7. Store purchases can use credit. Should they? The alternative is that the store needs cash.

## Build order

Everything in this file shipped together in version 0.3.0. The checklist and the test for each part are in [TASKS.md](TASKS.md).
