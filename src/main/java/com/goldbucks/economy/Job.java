package com.goldbucks.economy;

/** The jobs a player can hold. Pay is per Minecraft day and comes from the world settings. */
public enum Job {
	VEHICLE_DEALER("Vehicle Dealer", 10, true, "Sell a boat, minecart, rail, saddle or harness, or fill a vehicle order"),
	STORE_OWNER("Store Owner", 8, false, "Make a sale, or fill a store order"),
	COOK("Cook", 7, false, "Cook food, sell a meal, or fill a Cook order"),
	SALESPERSON("Salesperson", 6, true, "Make a sale, or fill a sales order"),
	TAXI_DRIVER("Taxi Driver", 5, true, "Give a player or a townsfolk passenger a ride"),
	LIBRARIAN("Librarian", 4, true, "Sell a book, write and sign one, or fill a book order"),
	BUILDER("Builder", 7, false, "Place 32 blocks on someone else's land with their permission, or fill a Builder order"),
	FARMER("Farmer", 6, false, "Sell a crop, egg, milk or wool, or fill a Farm order"),
	BANKER("Banker", 8, true, "Someone uses your bank, or fill a Bank order");

	private final String title;
	private final int defaultPay;
	private final boolean exclusive;
	private final String task;

	Job(String title, int defaultPay, boolean exclusive, String task) {
		this.title = title;
		this.defaultPay = defaultPay;
		this.exclusive = exclusive;
		this.task = task;
	}

	public String title() {
		return title;
	}

	/** Pay per day, as set in the world settings. */
	public int dailyPay() {
		return Settings.current().payFor(this);
	}

	public int defaultPay() {
		return defaultPay;
	}

	/** Only one player may hold an exclusive job. */
	public boolean exclusive() {
		return exclusive;
	}

	public String task() {
		return task;
	}

	/** The job with this name (any case, spaces or underscores), or null. */
	public static Job parse(String name) {
		if (name == null) {
			return null;
		}

		String wanted = name.strip().replace(' ', '_');

		for (Job job : values()) {
			if (job.name().equalsIgnoreCase(wanted)) {
				return job;
			}
		}

		return null;
	}

	/** Whether selling this kind of item counts as the job's daily task. */
	public boolean saleCountsAsTask(ItemKind kind) {
		return switch (this) {
			case STORE_OWNER, SALESPERSON -> true;
			case COOK -> kind == ItemKind.FOOD;
			case VEHICLE_DEALER -> kind == ItemKind.VEHICLE;
			case LIBRARIAN -> kind == ItemKind.BOOK;
			case FARMER -> kind == ItemKind.FARM;
			case TAXI_DRIVER, BUILDER, BANKER -> false;
		};
	}
}
