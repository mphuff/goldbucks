package com.goldbucks.economy;

import java.util.Locale;

/** The pre-baked town events. Names and descriptions can be changed in the events data file. */
public enum TownEvent {
	MARKET_DAY("Market Day", "Townsfolk come shopping in crowds, and there are extra orders.", 1, Condition.NONE, true),
	FISHING_SEASON("Fishing Season", "Townsfolk want boats and fishing rods, and pay double for them.", 1, Condition.NONE, true),
	RAILWAY_WEEK("Railway Week", "Lay a rail line from the town square to the neighborhood. $40 when it connects.", 3, Condition.NONE, true),
	HARVEST_FESTIVAL("Harvest Festival", "Farm orders pay double, and the town wants pumpkin pie.", 1, Condition.NONE, true),
	TOWN_FEAST("Town Feast", "The town wants a feast: big food orders. $10 for whoever fills the most.", 1, Condition.FULL_MOON, true),
	TOURIST_DAY("Tourist Day", "Tourists wait at the Taxi Stand. Each one taken to a shop buys two things there.", 1, Condition.NONE, true),
	RAINY_DAY_RIDES("Rainy Day Rides", "Nobody wants to walk in the rain. Passenger orders pay double.", 1, Condition.RAIN, true),
	BOOK_FAIR("Book Fair", "Townsfolk buy written books, and there are book orders. The host picks the best story.", 1, Condition.NONE, true),
	TREASURE_HUNT("Treasure Hunt", "A treasure chest is buried near town. Follow the clues in the news!", 1, Condition.NONE, true),
	GRAND_OPENING("Grand Opening", "A new shop opened! It gets extra townsfolk visits today.", 1, Condition.NEW_SHOP, false),
	BIG_SALE_DAY("Big Sale Day", "Double townsfolk visits to shops priced at or below value.", 1, Condition.NONE, true),
	CONSTRUCTION_BOOM("Construction Boom", "Fill the marked area at the town square with blocks. $30 to share.", 1, Condition.NONE, true),
	STORM_REPAIRS("Storm Repairs", "The storm knocked down fences and lanterns at the square. $3 for each one put back.", 1, Condition.AFTER_THUNDER, true),
	GOLD_RUSH("Gold Rush", "Gold orders pay double.", 1, Condition.NONE, true),
	INTEREST_DAY("Interest Day", "Money saved at the bank today earns double interest.", 1, Condition.NONE, true),
	ELECTION_DAY("Election Day", "Run for Mayor with /mayor run, and vote with /vote. Voting closes at the end of the day.", 1, Condition.NONE, false),
	HOLIDAY("Holiday", "The Mayor declared a holiday! $5 for everyone, and fireworks at the square.", 1, Condition.NONE, false),
	NIGHT_MARKET("Night Market", "Full moon! Shops with a lantern or torch by the sign get double visits.", 1, Condition.FULL_MOON, true),
	LOST_PET("Lost Pet", "Whiskers the cat is lost! Bring her back to the town square.", 1, Condition.NONE, true),
	SHOP_INSPECTOR("Shop Inspector", "An inspector checks every shop. Full stock and fair prices earn $10.", 1, Condition.NONE, true),
	CONTEST("Contest", "The host has started a contest!", 1, Condition.NONE, false);

	public enum Condition {
		NONE, FULL_MOON, RAIN, AFTER_THUNDER, NEW_SHOP
	}

	private final String defaultName;
	private final String defaultDescription;
	private final int days;
	private final Condition condition;
	private final boolean randomAllowed;

	TownEvent(String name, String description, int days, Condition condition, boolean randomAllowed) {
		this.defaultName = name;
		this.defaultDescription = description;
		this.days = days;
		this.condition = condition;
		this.randomAllowed = randomAllowed;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String defaultName() {
		return defaultName;
	}

	public String defaultDescription() {
		return defaultDescription;
	}

	public int days() {
		return days;
	}

	public Condition condition() {
		return condition;
	}

	/** Whether the game may pick this event by itself. */
	public boolean randomAllowed() {
		return randomAllowed;
	}

	public static TownEvent parse(String id) {
		if (id == null) {
			return null;
		}

		for (TownEvent event : values()) {
			if (event.id().equalsIgnoreCase(id.strip())) {
				return event;
			}
		}

		return null;
	}

	/** How many times the usual townsfolk visits a shop owner gets today. */
	public static int visitMultiplier(TownEvent event) {
		if (event == null) {
			return 1;
		}

		return switch (event) {
			case MARKET_DAY -> 3;
			case BIG_SALE_DAY, NIGHT_MARKET -> 2;
			default -> 1;
		};
	}

	/** Extra orders with no job tag that the event adds to the board. */
	public static int extraGeneralOrders(TownEvent event) {
		return event == MARKET_DAY ? 2 : 0;
	}
}
