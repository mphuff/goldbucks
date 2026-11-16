package com.goldbucks.economy;

/** How good a player is at a job, from the number of days they have worked it. */
public enum Level {
	APPRENTICE("Apprentice"),
	SKILLED("Skilled"),
	EXPERT("Expert"),
	MASTER("Master");

	private final String title;

	Level(String title) {
		this.title = title;
	}

	public String title() {
		return title;
	}

	public static Level forDays(int days) {
		Settings s = Settings.current();

		if (days >= s.masterDays) {
			return MASTER;
		}

		if (days >= s.expertDays) {
			return EXPERT;
		}

		return days >= s.skilledDays ? SKILLED : APPRENTICE;
	}

	/** Extra pay per day at this level. */
	public int bonus() {
		Settings s = Settings.current();
		return switch (this) {
			case APPRENTICE -> 0;
			case SKILLED -> s.skilledBonus;
			case EXPERT -> s.expertBonus;
			case MASTER -> s.masterBonus;
		};
	}

	/** Days worked needed for the next level, or -1 at Master. */
	public int nextAt() {
		Settings s = Settings.current();
		return switch (this) {
			case APPRENTICE -> s.skilledDays;
			case SKILLED -> s.expertDays;
			case EXPERT -> s.masterDays;
			case MASTER -> -1;
		};
	}

	public boolean atLeast(Level other) {
		return ordinal() >= other.ordinal();
	}
}
