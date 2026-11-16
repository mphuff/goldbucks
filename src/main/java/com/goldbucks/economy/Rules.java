package com.goldbucks.economy;

/**
 * The game's numbers. Most come from the world settings (see {@link Settings}); the few that
 * fix the shape of buildings are constants. This class has no Minecraft dependencies.
 */
public final class Rules {
	public static final int HOUSE_SIZE = 7;
	public static final int HOUSE_GAP = 10;
	/** Blocks a Builder must place on someone else's land in a day. */
	public static final int BUILDER_BLOCKS = 32;
	/** How far a taxi passenger must be carried. */
	public static final int TAXI_RIDE_BLOCKS = 30;

	private Rules() {
	}

	private static Settings s() {
		return Settings.current();
	}

	public static int startingMoney() {
		return s().startingMoney;
	}

	public static int creditLimit() {
		return s().creditLimit;
	}

	public static int priceCap() {
		return s().priceCap;
	}

	/** The tax rate in force: the Mayor's rate if one was set, otherwise the settings. */
	public static int taxPercent() {
		return s().taxPercent;
	}

	public static int taxFreeAmount() {
		return s().taxFreeAmount;
	}

	public static int taxPeriodDays() {
		return s().taxPeriodDays;
	}

	public static int ticksPerDay() {
		return s().dayLengthTicks;
	}

	public static int expiryMinDays() {
		return s().expiryMinDays;
	}

	public static int expiryMaxDays() {
		return s().expiryMaxDays;
	}

	public static int businessBasePrice() {
		return s().businessBasePrice;
	}

	public static int plotSize() {
		return s().plotSize;
	}
}
