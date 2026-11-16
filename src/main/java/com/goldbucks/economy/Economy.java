package com.goldbucks.economy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The whole money game: every player's record and every rule that changes it.
 * This class has no Minecraft dependencies so the rules can be tested on their own.
 */
public final class Economy {
	private final Map<UUID, Account> accounts = new LinkedHashMap<>();
	private final Map<Integer, Business> businesses = new LinkedHashMap<>();
	private final Map<UUID, House> houses = new LinkedHashMap<>();
	/** Business id -> the buyer the owner has agreed to sell to. */
	private final Map<Integer, UUID> agreedSales = new LinkedHashMap<>();
	private final Map<Integer, Bill> bills = new LinkedHashMap<>();
	final Town town = new Town();
	private int nextBusinessId = 1;
	/** The save format this economy was read from; 0 for a new one. */
	private int loadedVersion;
	private int nextBillId = 1;
	private long clockTicks;
	private int day;

	// ---- accounts ----

	/** Returns the player's record, creating it with starting money on their first visit. */
	public Account join(UUID id, String name) {
		Account account = accounts.get(id);

		if (account == null) {
			account = new Account(id, name, Rules.startingMoney());
			accounts.put(id, account);
		} else {
			account.rename(name);
		}

		return account;
	}

	public boolean hasAccount(UUID id) {
		return accounts.containsKey(id);
	}

	public Account account(UUID id) {
		return accounts.get(id);
	}

	public Collection<Account> accounts() {
		return Collections.unmodifiableCollection(accounts.values());
	}

	public Town town() {
		return town;
	}

	/** What the side bar shows: cash, savings, property and the base price of each business. */
	public int netWorth(UUID player) {
		Account account = accounts.get(player);
		return account == null ? 0 : account.ownWorth() + businessesOf(player).size() * Rules.businessBasePrice();
	}

	/** Adds to a player's count of something, for achievements. */
	public void count(UUID player, String stat, int amount) {
		Account account = accounts.get(player);

		if (account != null) {
			account.addStat(stat, amount);
		}
	}

	/** Records an achievement. Returns true the first time, when the reward is paid. */
	public boolean award(UUID player, String achievement, int reward) {
		Account account = accounts.get(player);

		if (account == null || !account.achievements.add(achievement)) {
			return false;
		}

		account.add(reward);
		return true;
	}

	// ---- jobs and businesses ----

	public Collection<Business> businesses() {
		return Collections.unmodifiableCollection(businesses.values());
	}

	public Business business(int id) {
		return businesses.get(id);
	}

	public List<Business> businessesOf(UUID owner) {
		List<Business> owned = new ArrayList<>();

		for (Business business : businesses.values()) {
			if (business.owner().equals(owner)) {
				owned.add(business);
			}
		}

		return owned;
	}

	public boolean holdsJob(UUID player, Job job) {
		for (Business business : businessesOf(player)) {
			if (business.job() == job) {
				return true;
			}
		}

		return false;
	}

	/** The first owner of a business with this job, or null. */
	public UUID holderOf(Job job) {
		for (Business business : businesses.values()) {
			if (business.job() == job) {
				return business.owner();
			}
		}

		return null;
	}

	/** A player with no business must pick a job. */
	public boolean needsJob(UUID player) {
		return accounts.containsKey(player) && businessesOf(player).isEmpty();
	}

	public boolean jobAvailable(Job job) {
		if (!Settings.current().jobEnabled(job)) {
			return false;
		}

		if (!job.exclusive()) {
			return true;
		}

		for (Business business : businesses.values()) {
			if (business.job() == job) {
				return false;
			}
		}

		return true;
	}

	/** Picking a job starts a business. Only a player without one may pick. */
	public Result chooseJob(UUID player, Job job) {
		if (!accounts.containsKey(player)) {
			return Result.NO_ACCOUNT;
		}

		if (!businessesOf(player).isEmpty()) {
			return Result.ALREADY_HAS_JOB;
		}

		if (!Settings.current().jobEnabled(job)) {
			return Result.JOB_CLOSED;
		}

		if (!jobAvailable(job)) {
			return Result.JOB_TAKEN;
		}

		Business business = new Business(nextBusinessId++, job, player);
		businesses.put(business.id(), business);
		return Result.OK;
	}

	/** Host power: the player loses every business and must pick a new job. */
	public Result fire(UUID player) {
		if (!accounts.containsKey(player)) {
			return Result.NO_ACCOUNT;
		}

		for (Business business : businessesOf(player)) {
			businesses.remove(business.id());
			agreedSales.remove(business.id());
		}

		return Result.OK;
	}

	/** Marks today's task done for every business of this job the player owns. */
	public boolean completeTask(UUID player, Job job) {
		boolean any = false;

		for (Business business : businessesOf(player)) {
			if (business.job() == job) {
				business.setTaskDoneToday(true);
				any = true;
			}
		}

		return any;
	}

	/** Whether the player still has today's task to do for this job. */
	public boolean taskPending(UUID player, Job job) {
		for (Business business : businessesOf(player)) {
			if (business.job() == job && !business.taskDoneToday()) {
				return true;
			}
		}

		return false;
	}

	// ---- land ----

	/** Who owns the land under this block, or null if it is free. */
	public UUID landOwnerAt(int x, int z) {
		for (House house : houses.values()) {
			if (houseCovers(house, x, z)) {
				return house.owner();
			}
		}

		for (Business business : businesses.values()) {
			if (business.plot() != null && business.plot().contains(x, z)) {
				return business.owner();
			}
		}

		return null;
	}

	private boolean houseCovers(House house, int x, int z) {
		for (Plot area : house.areas(ownedBy(house.owner()))) {
			if (area.contains(x, z)) {
				return true;
			}
		}

		return false;
	}

	private Set<String> ownedBy(UUID player) {
		Account account = accounts.get(player);
		return account == null ? Set.of() : account.owned;
	}

	/** Whether this player may build at this block: it is free, theirs, or the owner allowed them. */
	public boolean mayBuild(UUID player, int x, int z) {
		UUID owner = landOwnerAt(x, z);

		if (owner == null || owner.equals(player)) {
			return true;
		}

		return isAllowedAt(player, x, z);
	}

	/** Whether the owner of the land here has given this player permission to build. */
	public boolean isAllowedAt(UUID player, int x, int z) {
		for (House house : houses.values()) {
			if (houseCovers(house, x, z)) {
				Account owner = accounts.get(house.owner());
				return owner != null && owner.homeAllowed.contains(player);
			}
		}

		Business business = businessAt(x, z);
		return business != null && business.allowed.contains(player);
	}

	public Result allowOnPlots(UUID owner, UUID builder, boolean allow) {
		if (!accounts.containsKey(builder) || owner.equals(builder)) {
			return Result.NO_ACCOUNT;
		}

		List<Business> owned = businessesOf(owner);

		if (owned.isEmpty()) {
			return Result.NO_BUSINESS;
		}

		for (Business business : owned) {
			if (allow) {
				business.allowed.add(builder);
			} else {
				business.allowed.remove(builder);
			}
		}

		return Result.OK;
	}

	public Result allowAtHome(UUID owner, UUID builder, boolean allow) {
		Account account = accounts.get(owner);

		if (account == null || !accounts.containsKey(builder) || owner.equals(builder)) {
			return Result.NO_ACCOUNT;
		}

		if (allow) {
			account.homeAllowed.add(builder);
		} else {
			account.homeAllowed.remove(builder);
		}

		return Result.OK;
	}

	/** The business whose plot covers this block, or null. */
	public Business businessAt(int x, int z) {
		for (Business business : businesses.values()) {
			if (business.plot() != null && business.plot().contains(x, z)) {
				return business;
			}
		}

		return null;
	}

	/** Whose land a wanted plot would run into, or null if the spot is free. */
	public UUID landInTheWay(Plot wanted) {
		return landInTheWay(wanted, null, null);
	}

	/** As above, ignoring one business plot and one house (for growing them). */
	private UUID landInTheWay(Plot wanted, Business ignoreBusiness, UUID ignoreHouse) {
		for (Business business : businesses.values()) {
			if (business != ignoreBusiness && business.plot() != null && business.plot().overlaps(wanted)) {
				return business.owner();
			}
		}

		for (House house : houses.values()) {
			if (house.owner().equals(ignoreHouse)) {
				continue;
			}

			for (Plot area : house.areas(ownedBy(house.owner()))) {
				if (area.overlaps(wanted)) {
					return house.owner();
				}
			}
		}

		return null;
	}

	/** Claims the standard plot centred on a block for one of the player's businesses. */
	public Result claimPlot(UUID player, int businessId, int x, int z) {
		Business business = businesses.get(businessId);

		if (business == null) {
			return Result.NO_BUSINESS;
		}

		if (!business.owner().equals(player)) {
			return Result.NOT_OWNER;
		}

		if (business.plot() != null) {
			return Result.ALREADY_CLAIMED;
		}

		Plot wanted = Plot.around(x, z);

		if (landInTheWay(wanted) != null) {
			return Result.OVERLAPS;
		}

		business.setPlot(wanted);
		return Result.OK;
	}

	public House house(UUID owner) {
		return houses.get(owner);
	}

	public Collection<House> houses() {
		return Collections.unmodifiableCollection(houses.values());
	}

	/**
	 * Gives the player the next house lot in the neighborhood row, which starts at the given block.
	 * Lots are HOUSE_SIZE wide with HOUSE_GAP blocks between them. Returns the existing house if they have one.
	 */
	public House assignHouse(UUID owner, int rowStartX, int rowStartZ) {
		return assignHouse(owner, rowStartX, rowStartZ, lot -> true);
	}

	/** How many lots along the row are tried before giving up on finding good land. */
	public static final int HOUSE_LOTS_TO_TRY = 40;

	/**
	 * Gives the player the first free lot in the row whose land is good (for example, not water)
	 * and that nobody else's land is in the way of. If no lot along the row is good, the first free one is used.
	 */
	public House assignHouse(UUID owner, int rowStartX, int rowStartZ, java.util.function.Predicate<Plot> goodLand) {
		House existing = houses.get(owner);

		if (existing != null) {
			return existing;
		}

		Set<Integer> taken = new java.util.HashSet<>();

		for (House house : houses.values()) {
			taken.add(house.index());
		}

		int chosen = -1;
		int firstFree = -1;

		for (int index = 0; index < HOUSE_LOTS_TO_TRY + taken.size() && chosen < 0; index++) {
			if (taken.contains(index)) {
				continue;
			}

			Plot lot = lotAt(rowStartX, rowStartZ, index);
			boolean free = landInTheWay(new Plot(lot.minX(), lot.minZ(), lot.size(), lot.size() + 1)) == null;

			if (free && firstFree < 0) {
				firstFree = index;
			}

			if (free && goodLand.test(lot)) {
				chosen = index;
			}
		}

		int index = chosen >= 0 ? chosen : firstFree;

		// Nothing free along the stretch tried: go past the end of the row.
		while (index < 0 || taken.contains(index)) {
			index = index < 0 ? HOUSE_LOTS_TO_TRY + taken.size() : index + 1;
		}
		House house = new House(owner, index, lotAt(rowStartX, rowStartZ, index));
		houses.put(owner, house);
		return house;
	}

	private static Plot lotAt(int rowStartX, int rowStartZ, int index) {
		return new Plot(rowStartX + index * (Rules.HOUSE_SIZE + Rules.HOUSE_GAP), rowStartZ, Rules.HOUSE_SIZE);
	}

	/** Whose land is in the way of a house upgrade, or null if it can be built. */
	public UUID upgradeBlockedBy(UUID owner, StoreKind upgrade) {
		House house = houses.get(owner);
		Plot area = house == null ? null : House.upgradeArea(house.lot(), upgrade);
		return area == null ? null : landInTheWay(area, null, owner);
	}

	// ---- shopping ----

	/** Checks a price an owner wants to set, against the usual cap. */
	public static Result checkPrice(int price) {
		return checkPrice(price, Rules.priceCap());
	}

	public static Result checkPrice(int price, int cap) {
		if (price < 1) {
			return Result.BAD_PRICE;
		}

		return price > cap ? Result.PRICE_TOO_HIGH : Result.OK;
	}

	/** The highest price this business may charge for this kind of item, counting Master perks. */
	public int priceCapFor(Business business, ItemKind kind, boolean enchantedBook) {
		Account owner = accounts.get(business.owner());
		boolean master = owner != null && owner.level(business.job()) == Level.MASTER;

		if (master && business.job() == Job.VEHICLE_DEALER && kind == ItemKind.VEHICLE) {
			return Settings.current().masterPriceCap;
		}

		if (master && business.job() == Job.LIBRARIAN && enchantedBook) {
			return Settings.current().masterPriceCap;
		}

		return Rules.priceCap();
	}

	/** Whether this purchase would be allowed, without making it. */
	public Result canPurchase(UUID buyer, int businessId, int price) {
		return canPurchase(buyer, businessId, price, Rules.priceCap());
	}

	public Result canPurchase(UUID buyer, int businessId, int price, int cap) {
		Account buyerAccount = accounts.get(buyer);
		Business business = businesses.get(businessId);

		if (buyerAccount == null) {
			return Result.NO_ACCOUNT;
		}

		if (business == null) {
			return Result.NO_BUSINESS;
		}

		if (business.owner().equals(buyer)) {
			return Result.OWN_SHOP;
		}

		Result priceCheck = checkPrice(price, cap);

		if (!priceCheck.ok()) {
			return priceCheck;
		}

		return buyerAccount.canSpend(price) ? Result.OK : Result.OVER_CREDIT_LIMIT;
	}

	public Result purchase(UUID buyer, int businessId, int price, ItemKind kind) {
		return purchase(buyer, businessId, price, kind, Rules.priceCap());
	}

	/** Moves the money for a sale and counts it towards the business's history and daily task. */
	public Result purchase(UUID buyer, int businessId, int price, ItemKind kind, int cap) {
		Result check = canPurchase(buyer, businessId, price, cap);

		if (!check.ok()) {
			return check;
		}

		Business business = businesses.get(businessId);
		Account owner = accounts.get(business.owner());
		accounts.get(buyer).add(-price);
		owner.add(price);
		recordSale(business, price, kind);

		// Salesperson Expert: the town adds $1 for a sale to a player not yet sold to today.
		if (business.job() == Job.SALESPERSON && owner.level(Job.SALESPERSON).atLeast(Level.EXPERT)
				&& !owner.soldToToday.contains(buyer)) {
			owner.add(1);
		}

		owner.soldToToday.add(buyer);
		return Result.OK;
	}

	/** A townsfolk customer buys one item. The town pays, so the money is new. */
	public void townsfolkPurchase(int businessId, int price, ItemKind kind) {
		Business business = businesses.get(businessId);

		if (business == null) {
			return;
		}

		Account owner = accounts.get(business.owner());
		owner.add(price);
		recordSale(business, price, kind);
		owner.addStat("townsfolk_sales", 1);

		if (business.job() == Job.SALESPERSON && town.event == TownEvent.BIG_SALE_DAY) {
			owner.add(1);
		}

		if (business.job() == Job.SALESPERSON && owner.level(Job.SALESPERSON) == Level.MASTER) {
			owner.add(Math.max(1, price / 10));
		}
	}

	private void recordSale(Business business, int price, ItemKind kind) {
		Account owner = accounts.get(business.owner());
		business.addSales(price);
		owner.salesToday++;
		owner.addStat("sales", 1);

		if (business.job().saleCountsAsTask(kind)) {
			business.setTaskDoneToday(true);
		}
	}

	/** Remembers a working shop sign. Returns true if it is the business's first shop, which starts a Grand Opening tomorrow. */
	public boolean registerShop(int businessId, long signPos) {
		Business business = businesses.get(businessId);

		if (business == null) {
			return false;
		}

		business.shopSigns.add(signPos);

		if (!business.everOpened) {
			business.everOpened = true;
			town.newShopOwner = business.owner();
			return true;
		}

		return false;
	}

	public void forgetShop(int businessId, long signPos) {
		Business business = businesses.get(businessId);

		if (business != null) {
			business.shopSigns.remove(signPos);
		}
	}

	/** How many townsfolk visits this player's shops get today. */
	public int townsfolkVisits(UUID owner, boolean anyShopPricedFairly) {
		Settings settings = Settings.current();
		Account account = accounts.get(owner);

		if (account == null || !settings.townsfolkEnabled) {
			return 0;
		}

		int visits = settings.townsfolkVisitsPerDay;

		if (holdsJob(owner, Job.STORE_OWNER)) {
			Level level = account.level(Job.STORE_OWNER);
			visits += level == Level.MASTER ? 2 : level.atLeast(Level.SKILLED) ? 1 : 0;
		}

		if (anyShopPricedFairly) {
			visits += 1;
		}

		visits += account.advertsToday * 2;
		visits *= TownEvent.visitMultiplier(town.event);

		if (town.event == TownEvent.GRAND_OPENING && owner.equals(town.grandOpeningOwner)) {
			visits += 4;
		}

		return visits;
	}

	/** Notes a sale made while the owner was offline, for their login summary. */
	public void recordAwaySale(UUID owner, int price) {
		Account account = accounts.get(owner);

		if (account != null) {
			account.awaySales++;
			account.awayMoney += price;
		}
	}

	public void recordAwayEmptyShop(UUID owner) {
		Account account = accounts.get(owner);

		if (account != null) {
			account.awayEmptyShop = true;
		}
	}

	public void clearAway(UUID owner) {
		Account account = accounts.get(owner);

		if (account != null) {
			account.awaySales = 0;
			account.awayMoney = 0;
			account.awayEmptyShop = false;
		}
	}

	// ---- the Town Store ----

	/** Whether this player could buy this store item right now, without buying it. */
	public Result canBuy(UUID player, String storeId) {
		Account account = accounts.get(player);
		TownData.StoreItem item = TownData.current().storeItem(storeId);

		if (account == null) {
			return Result.NO_ACCOUNT;
		}

		if (item == null || !item.enabled || item.kind() == null) {
			return Result.NO_SUCH_ITEM;
		}

		StoreKind kind = item.kind();

		if (kind.permanent() && account.owns(kind.id())) {
			return Result.ALREADY_OWNED;
		}

		if (item.requires != null && !account.owns(item.requires.toLowerCase(java.util.Locale.ROOT))) {
			return Result.NEEDS_OTHER_ITEM;
		}

		if (kind == StoreKind.BIGGER_PLOT || kind == StoreKind.BIGGEST_PLOT) {
			Business business = plottedBusiness(player);

			if (business == null) {
				return Result.NO_BUSINESS;
			}

			if (business.plot().size() >= item.size) {
				return Result.ALREADY_OWNED;
			}

			if (landInTheWay(Plot.around(business.plot().centerX(), business.plot().centerZ(), item.size), business, null) != null) {
				return Result.OVERLAPS;
			}
		}

		if (kind.category() == StoreKind.Category.HOME) {
			if (houses.get(player) == null) {
				return Result.NO_SUCH_ITEM;
			}

			if (upgradeBlockedBy(player, kind) != null) {
				return Result.OVERLAPS;
			}
		}

		if ((kind == StoreKind.ADVERT || kind == StoreKind.SHOP_BANNER) && businessesOf(player).isEmpty()) {
			return Result.NO_BUSINESS;
		}

		return account.canSpend(item.price) ? Result.OK : Result.OVER_CREDIT_LIMIT;
	}

	/** The player's first business that has a plot, or null. */
	public Business plottedBusiness(UUID player) {
		for (Business business : businessesOf(player)) {
			if (business.plot() != null) {
				return business;
			}
		}

		return null;
	}

	/** Buys a store item: takes the money and records it. The game then builds or hands over the thing. */
	public Result buy(UUID player, String storeId) {
		Result check = canBuy(player, storeId);

		if (!check.ok()) {
			return check;
		}

		Account account = accounts.get(player);
		TownData.StoreItem item = TownData.current().storeItem(storeId);
		StoreKind kind = item.kind();
		account.add(-item.price);

		if (kind.permanent()) {
			account.owned.add(kind.id());
			account.propertyValue += item.price;
		}

		switch (kind) {
			case BIGGER_PLOT, BIGGEST_PLOT -> {
				Business business = plottedBusiness(player);
				business.setPlot(Plot.around(business.plot().centerX(), business.plot().centerZ(), item.size));
			}
			case ADVERT -> account.advertsToday++;
			default -> {
			}
		}

		account.addStat("store_buys", 1);
		return Result.OK;
	}

	/** Changes a player's name colour. The first pick comes with the Name colour item; later changes cost $10. */
	public Result setNameColour(UUID player, String colour) {
		Account account = accounts.get(player);

		if (account == null) {
			return Result.NO_ACCOUNT;
		}

		if (!account.owns(StoreKind.NAME_COLOUR.id())) {
			return Result.NEEDS_OTHER_ITEM;
		}

		if (account.nameColour != null) {
			if (!account.canSpend(10)) {
				return Result.OVER_CREDIT_LIMIT;
			}

			account.add(-10);
		}

		account.nameColour = colour;
		return Result.OK;
	}

	public void setHeraldAd(UUID player, String text) {
		Account account = accounts.get(player);

		if (account != null) {
			account.heraldAd = text;
		}
	}

	public void setPet(UUID player, String kind, UUID entity) {
		Account account = accounts.get(player);

		if (account != null) {
			account.pets.put(kind, entity);
		}
	}

	// ---- town orders ----

	/** The jobs that get an order today: one per job held, plus one more for each perk that adds one. */
	public List<Job> orderSlots() {
		List<Job> slots = new ArrayList<>();
		Set<Job> held = EnumSet.noneOf(Job.class);

		for (Business business : businesses.values()) {
			held.add(business.job());
		}

		for (Job job : held) {
			slots.add(job);
			Account account = accounts.get(holderOf(job));
			Level level = account == null ? Level.APPRENTICE : account.level(job);
			Level extraAt = job == Job.COOK ? Level.EXPERT : Level.SKILLED;
			boolean perk = job != Job.STORE_OWNER && job != Job.SALESPERSON && job != Job.BANKER;

			if (perk && level.atLeast(extraAt)) {
				slots.add(job);
			}
		}

		return slots;
	}

	/** What happened when a player filled an order. */
	public record Filled(Result result, int pay, boolean taskDone) {
	}

	/** Checks an order can be filled by this player, without filling it. */
	public Result canFill(UUID player, int orderId) {
		Account account = accounts.get(player);
		Town.Order order = town.order(orderId);

		if (account == null) {
			return Result.NO_ACCOUNT;
		}

		if (order == null) {
			return Result.NO_SUCH_ORDER;
		}

		if (!order.open()) {
			return Result.ORDER_TAKEN;
		}

		return account.ordersToday >= Settings.current().ordersPerPlayerPerDay ? Result.ORDER_LIMIT : Result.OK;
	}

	/** Pays for a filled order. The game has already taken the items (or delivered the passenger). */
	public Filled fill(UUID player, int orderId) {
		Result check = canFill(player, orderId);

		if (!check.ok()) {
			return new Filled(check, 0, false);
		}

		Account account = accounts.get(player);
		Town.Order order = town.order(orderId);
		order.filledBy = player;
		int pay = Town.payFor(order, town.event);

		if (order.passenger && holdsJob(player, Job.TAXI_DRIVER)) {
			Level level = account.level(Job.TAXI_DRIVER);

			if (level.atLeast(Level.EXPERT)) {
				pay += pay / 2;
			}

			if (level == Level.MASTER && town.event == TownEvent.RAINY_DAY_RIDES) {
				pay *= 2;
			}
		}

		account.add(pay);
		account.ordersToday++;
		account.addStat(order.passenger ? "passengers" : "orders", 1);

		if (town.event == TownEvent.TOWN_FEAST && order.job == Job.COOK) {
			town.feastFills.merge(player, 1, Integer::sum);
		}

		boolean taskDone = false;

		if (order.passenger && holdsJob(player, Job.TAXI_DRIVER)) {
			taskDone = completeTask(player, Job.TAXI_DRIVER);
		} else if (order.job != null && holdsJob(player, order.job)) {
			taskDone = completeTask(player, order.job);
		}

		return new Filled(Result.OK, pay, taskDone);
	}

	// ---- the bank ----

	/** Puts money into savings. {@code bankerBusiness} is the bank used, or -1 for the Town Clerk. */
	public Result deposit(UUID player, int amount, int bankerBusiness) {
		Account account = accounts.get(player);

		if (account == null) {
			return Result.NO_ACCOUNT;
		}

		if (account.inDebt()) {
			return Result.IN_DEBT;
		}

		if (amount < 1 || amount > account.balance()) {
			return Result.BAD_AMOUNT;
		}

		account.add(-amount);
		account.setSavings(account.savings() + amount);

		if (town.event == TownEvent.INTEREST_DAY) {
			account.interestDayDeposits += amount;
		}

		payBanker(player, bankerBusiness);
		return Result.OK;
	}

	public Result withdraw(UUID player, int amount, int bankerBusiness) {
		Account account = accounts.get(player);

		if (account == null) {
			return Result.NO_ACCOUNT;
		}

		if (amount < 1) {
			return Result.BAD_AMOUNT;
		}

		if (amount > account.savings()) {
			return Result.NOT_ENOUGH_SAVINGS;
		}

		account.setSavings(account.savings() - amount);
		account.add(amount);
		account.interestDayDeposits = Math.min(account.interestDayDeposits, account.savings());
		payBanker(player, bankerBusiness);
		return Result.OK;
	}

	private void payBanker(UUID customer, int bankerBusiness) {
		Business bank = businesses.get(bankerBusiness);

		if (bank == null || bank.job() != Job.BANKER || bank.owner().equals(customer)) {
			return;
		}

		Account banker = accounts.get(bank.owner());
		int fee = Settings.current().bankerFee;

		if (banker.level(Job.BANKER).atLeast(Level.SKILLED)) {
			fee += 1;
		}

		banker.add(fee);
		bank.setTaskDoneToday(true);
	}

	/** Interest one player would earn now. No Banker, no interest. */
	public int interestFor(UUID player) {
		Account account = accounts.get(player);
		UUID bankerId = holderOf(Job.BANKER);

		if (account == null || bankerId == null) {
			return 0;
		}

		Settings settings = Settings.current();
		Level bankerLevel = accounts.get(bankerId).level(Job.BANKER);
		int rate = settings.interestPercent + (bankerLevel.atLeast(Level.EXPERT) ? 1 : 0);
		int cap = settings.interestCapPerWeek + (bankerLevel == Level.MASTER ? 15 : 0);
		int earning = account.savings() + account.interestDayDeposits;
		return Math.min(cap, earning * rate / 100);
	}

	// ---- the Mayor ----

	public Result runForMayor(UUID player, String promise) {
		if (!accounts.containsKey(player)) {
			return Result.NO_ACCOUNT;
		}

		if (!town.electionOpen()) {
			return Result.NO_ELECTION;
		}

		town.candidates.put(player, promise == null ? "" : promise);
		return Result.OK;
	}

	public Result vote(UUID voter, UUID candidate) {
		if (!accounts.containsKey(voter)) {
			return Result.NO_ACCOUNT;
		}

		if (!town.electionOpen()) {
			return Result.NO_ELECTION;
		}

		if (!town.candidates.containsKey(candidate)) {
			return Result.NOT_A_CANDIDATE;
		}

		if (voter.equals(candidate)) {
			return Result.SELF_VOTE;
		}

		if (town.votes.containsKey(voter)) {
			return Result.ALREADY_VOTED;
		}

		town.votes.put(voter, candidate);
		return Result.OK;
	}

	private Result checkMayor(UUID player) {
		return player.equals(town.mayor) ? Result.OK : Result.NOT_MAYOR;
	}

	public Result setMayorTax(UUID player, int percent) {
		Result check = checkMayor(player);

		if (!check.ok()) {
			return check;
		}

		Settings settings = Settings.current();

		if (percent < settings.mayorTaxMin || percent > settings.mayorTaxMax) {
			return Result.OUT_OF_RANGE;
		}

		town.mayorTaxRate = percent;
		return Result.OK;
	}

	public Result pickMarketDay(UUID player, TownEvent choice) {
		Result check = checkMayor(player);

		if (!check.ok()) {
			return check;
		}

		if (!town.marketChoices.contains(choice)) {
			return Result.OUT_OF_RANGE;
		}

		town.marketPick = choice;
		return Result.OK;
	}

	public Result declareHoliday(UUID player) {
		Result check = checkMayor(player);

		if (!check.ok()) {
			return check;
		}

		if (town.holidayUsed) {
			return Result.ALREADY_USED;
		}

		town.holidayUsed = true;
		town.holidayDay = day + 1;
		return Result.OK;
	}

	public Result nameTown(UUID player, String name) {
		Result check = checkMayor(player);

		if (check.ok()) {
			town.townName = name;
		}

		return check;
	}

	public Result announce(UUID player, String message) {
		Result check = checkMayor(player);

		if (check.ok()) {
			town.mayorAnnouncement = message;
		}

		return check;
	}

	/** Host power: remove the Mayor and hold an election tomorrow. */
	public void impeach() {
		town.mayor = null;
		town.mayorTaxRate = -1;
		town.nextElectionDay = day + 1;
	}

	/** The tax rate in force: the Mayor's if they set one (kept inside the Mayor's limits), otherwise the settings. */
	public int taxRate() {
		Settings settings = Settings.current();

		if (town.mayor != null && town.mayorTaxRate >= 0) {
			return Math.max(settings.mayorTaxMin, Math.min(settings.mayorTaxMax, town.mayorTaxRate));
		}

		return settings.taxPercent;
	}

	// ---- events ----

	/** Host power: start an event now, replacing any other. */
	public void startEvent(TownEvent event) {
		town.start(event, day);
	}

	public void stopEvent() {
		town.stopEvent();
	}

	/** Records that a player took part in today's event. Returns true the first time today. */
	public boolean joinEvent(UUID player) {
		if (town.event == null || town.joinedEvent.containsKey(player)) {
			return false;
		}

		town.joinedEvent.put(player, true);
		count(player, "events", 1);
		return true;
	}

	/** Pays a reward from the town to a player (event prizes, host bonuses). */
	public Result reward(UUID player, int amount) {
		Account account = accounts.get(player);

		if (account == null) {
			return Result.NO_ACCOUNT;
		}

		account.add(amount);
		return Result.OK;
	}

	// ---- builders' bills ----

	public record Bill(int id, UUID from, UUID to, int amount, String what) {
	}

	public Bill sendBill(UUID from, UUID to, int amount, String what) {
		if (!accounts.containsKey(from) || !accounts.containsKey(to) || from.equals(to) || amount < 1 || amount > 100_000) {
			return null;
		}

		Bill bill = new Bill(nextBillId++, from, to, amount, what);
		bills.put(bill.id(), bill);
		return bill;
	}

	public Bill bill(int id) {
		return bills.get(id);
	}

	public Result payBill(UUID payer, int billId) {
		Bill bill = bills.get(billId);

		if (bill == null || !bill.to().equals(payer)) {
			return Result.NO_SUCH_ORDER;
		}

		Account account = accounts.get(payer);

		if (!account.canSpend(bill.amount())) {
			return Result.OVER_CREDIT_LIMIT;
		}

		account.add(-bill.amount());
		Account builder = accounts.get(bill.from());
		builder.add(bill.amount());

		// Expert Builders get 10% more from the town on every bill paid.
		if (holdsJob(bill.from(), Job.BUILDER) && builder.level(Job.BUILDER).atLeast(Level.EXPERT)) {
			builder.add(Math.max(1, bill.amount() / 10));
		}

		bills.remove(billId);
		return Result.OK;
	}

	/** Counts a block a Builder placed on someone else's land. Returns true when that finished the task. */
	public boolean builderPlaced(UUID builder) {
		Account account = accounts.get(builder);

		if (account == null || !holdsJob(builder, Job.BUILDER)) {
			return false;
		}

		account.builderBlocksToday++;
		return account.builderBlocksToday >= Rules.BUILDER_BLOCKS && taskPending(builder, Job.BUILDER)
				&& completeTask(builder, Job.BUILDER);
	}

	// ---- time: payday and taxes ----

	public int day() {
		return day;
	}

	public long clockTicks() {
		return clockTicks;
	}

	/** A level a player just reached in a job. */
	public record LevelUp(UUID player, Job job, Level level) {
	}

	/** What happened when a day ended. */
	public record DayReport(int day, Map<UUID, Integer> pay, Map<UUID, Integer> tax, Map<UUID, List<String>> breakdown,
			List<LevelUp> levelUps, Map<UUID, Integer> interest, Town.ElectionResult election, UUID feastWinner) {
	}

	/**
	 * Advances the mod's own clock by one tick. It only counts ticks the server actually ran,
	 * so sleeping or changing the time of day cannot bring payday forward.
	 * Returns a report when a day just ended and payday ran, otherwise null.
	 */
	public DayReport tick(Set<UUID> online) {
		clockTicks++;

		if (clockTicks % Rules.ticksPerDay() != 0) {
			return null;
		}

		return endDay(online);
	}

	/** Pays salaries, collects tax on tax days, and closes the day. Call {@link #startDay} after it. */
	public DayReport endDay(Set<UUID> online) {
		return endDay(online, new Random());
	}

	public DayReport endDay(Set<UUID> online, Random random) {
		Settings settings = Settings.current();
		Map<UUID, Integer> pay = new LinkedHashMap<>();
		Map<UUID, List<String>> breakdown = new LinkedHashMap<>();
		List<LevelUp> levelUps = new ArrayList<>();
		Map<UUID, Set<Job>> workedJobs = new LinkedHashMap<>();

		for (Business business : businesses.values()) {
			UUID owner = business.owner();

			if (business.taskDoneToday() && online.contains(owner)) {
				Account account = accounts.get(owner);
				Job job = business.job();
				int base = job.dailyPay();
				int bonus = account.level(job).bonus();
				account.add(base + bonus);
				pay.merge(owner, base + bonus, Integer::sum);
				breakdown.computeIfAbsent(owner, k -> new ArrayList<>()).add(job.title() + " pay " + money(base)
						+ (bonus > 0 ? ", " + account.level(job).title() + " bonus " + money(bonus) : ""));
				workedJobs.computeIfAbsent(owner, k -> EnumSet.noneOf(Job.class)).add(job);
			}

			business.setTaskDoneToday(false);
		}

		for (Map.Entry<UUID, Set<Job>> entry : workedJobs.entrySet()) {
			Account account = accounts.get(entry.getKey());

			for (Job job : entry.getValue()) {
				Level before = account.level(job);
				account.daysWorked.merge(job, 1, Integer::sum);
				Level after = account.level(job);

				if (after != before) {
					levelUps.add(new LevelUp(account.id(), job, after));
				}
			}

			account.streak = account.lastWorkedDay == day - 1 ? account.streak + 1 : 1;
			account.lastWorkedDay = day;
		}

		if (town.mayor != null && online.contains(town.mayor) && accounts.containsKey(town.mayor)) {
			accounts.get(town.mayor).add(settings.mayorPay);
			pay.merge(town.mayor, settings.mayorPay, Integer::sum);
			breakdown.computeIfAbsent(town.mayor, k -> new ArrayList<>()).add("Mayor pay " + money(settings.mayorPay));
		}

		UUID feastWinner = null;

		if (town.event == TownEvent.TOWN_FEAST && !town.feastFills.isEmpty()) {
			feastWinner = Collections.max(town.feastFills.entrySet(), Map.Entry.comparingByValue()).getKey();
			accounts.get(feastWinner).add(10);
		}

		Town.ElectionResult election = town.event == TownEvent.ELECTION_DAY ? town.closeElection(day + 1, settings, random) : null;

		day++;

		Map<UUID, Integer> interest = new LinkedHashMap<>();

		if (day % 7 == 0) {
			for (Account account : accounts.values()) {
				int earned = interestFor(account.id());

				if (earned > 0) {
					account.setSavings(account.savings() + earned);
					interest.put(account.id(), earned);
				}

				account.interestDayDeposits = 0;
			}
		}

		Map<UUID, Integer> tax = day % Rules.taxPeriodDays() == 0 ? taxEveryone() : Map.of();

		for (Account account : accounts.values()) {
			account.newDay();
		}

		return new DayReport(day, pay, tax, breakdown, levelUps, interest, election, feastWinner);
	}

	/** Starts a new day: picks the day's event and posts the town orders. Returns the event that started, or null. */
	public TownEvent startDay(Town.Conditions conditions, Random random) {
		if (town.lastStartedDay >= day) {
			return null;
		}

		town.lastStartedDay = day;
		TownEvent started = town.chooseEvent(day, conditions, Settings.current(), random);
		town.postOrders(orderSlots(), town.event, TownData.current(), Settings.current(), random);
		return started;
	}

	/** Whether today's event and orders have been set up yet. */
	public boolean dayStarted() {
		return town.lastStartedDay >= day;
	}

	/** Posts a fresh set of orders without changing the event (used when the host starts an event). */
	public void repostOrders(Random random) {
		town.postOrders(orderSlots(), town.event, TownData.current(), Settings.current(), random);
	}

	private static String money(int amount) {
		return amount < 0 ? "-$" + (-amount) : "$" + amount;
	}

	/** What a balance owes in tax at the settings' rate. */
	public static int taxOn(int balance) {
		return taxOn(balance, Rules.taxPercent());
	}

	public static int taxOn(int balance, int percent) {
		return balance > Rules.taxFreeAmount() ? (balance - Rules.taxFreeAmount()) * percent / 100 : 0;
	}

	private Map<UUID, Integer> taxEveryone() {
		Map<UUID, Integer> taken = new LinkedHashMap<>();
		int rate = taxRate();

		for (Account account : accounts.values()) {
			int tax = taxOn(account.balance(), rate);

			if (tax > 0) {
				account.add(-tax);
				taken.put(account.id(), tax);
			}
		}

		return taken;
	}

	/** Takes tax from everyone. The money leaves the game. Returns the total taken. */
	public int collectTaxes() {
		return taxEveryone().values().stream().mapToInt(Integer::intValue).sum();
	}

	/** Days left until the next tax day. */
	public int daysUntilTax() {
		return Rules.taxPeriodDays() - day % Rules.taxPeriodDays();
	}

	// ---- item expiry ----

	/** The day a shop-bought item bought today stops working. */
	public int rollExpiryDay(Random random) {
		return day + Rules.expiryMinDays() + random.nextInt(Rules.expiryMaxDays() - Rules.expiryMinDays() + 1);
	}

	public boolean isExpired(int expiryDay) {
		return day >= expiryDay;
	}

	// ---- buying a business ----

	/** The owner agrees to sell this business to this buyer at the game's price. */
	public Result agreeToSell(UUID owner, int businessId, UUID buyer) {
		Business business = businesses.get(businessId);

		if (business == null) {
			return Result.NO_BUSINESS;
		}

		if (!business.owner().equals(owner)) {
			return Result.NOT_OWNER;
		}

		if (!accounts.containsKey(buyer) || buyer.equals(owner)) {
			return Result.NO_ACCOUNT;
		}

		agreedSales.put(businessId, buyer);
		return Result.OK;
	}

	public UUID agreedBuyer(int businessId) {
		return agreedSales.get(businessId);
	}

	/** The buyer pays the game's price and takes over the plot, shop and job. */
	public Result buyBusiness(UUID buyer, int businessId) {
		Business business = businesses.get(businessId);

		if (business == null) {
			return Result.NO_BUSINESS;
		}

		if (!buyer.equals(agreedSales.get(businessId))) {
			return Result.NOT_FOR_SALE;
		}

		Account buyerAccount = accounts.get(buyer);
		int price = business.price();

		if (!buyerAccount.canSpend(price)) {
			return Result.OVER_CREDIT_LIMIT;
		}

		buyerAccount.add(-price);
		accounts.get(business.owner()).add(price);
		business.setOwner(buyer);
		business.setTaskDoneToday(false);
		agreedSales.remove(businessId);
		return Result.OK;
	}

	// ---- host powers ----

	public Result setBalance(UUID player, int balance) {
		Account account = accounts.get(player);

		if (account == null) {
			return Result.NO_ACCOUNT;
		}

		account.setBalance(balance);
		return Result.OK;
	}

	public Result forgiveDebt(UUID player) {
		Account account = accounts.get(player);

		if (account == null) {
			return Result.NO_ACCOUNT;
		}

		if (account.inDebt()) {
			account.setBalance(0);
		}

		return Result.OK;
	}

	// ---- saving ----

	public String toJson() {
		return EconomySave.write(this);
	}

	public static Economy fromJson(String json) {
		return EconomySave.read(json);
	}

	// Package access for the save code.
	Map<UUID, Account> accountMap() {
		return accounts;
	}

	Map<Integer, Business> businessMap() {
		return businesses;
	}

	Map<UUID, House> houseMap() {
		return houses;
	}

	Map<Integer, UUID> agreedSaleMap() {
		return agreedSales;
	}

	int nextBusinessId() {
		return nextBusinessId;
	}

	void setNextBusinessId(int id) {
		nextBusinessId = id;
	}

	public int loadedVersion() {
		return loadedVersion;
	}

	void setLoadedVersion(int version) {
		loadedVersion = version;
	}

	void setClock(long ticks, int day) {
		this.clockTicks = ticks;
		this.day = day;
	}
}
