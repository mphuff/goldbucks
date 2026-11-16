package com.goldbucks.economy;

/** What the code does with each store item. The data file sets names, prices and requirements. */
public enum StoreKind {
	PORCH(Category.HOME, true),
	GARDEN(Category.HOME, true),
	BASEMENT(Category.HOME, true),
	SECOND_FLOOR(Category.HOME, true),
	STABLE(Category.HOME, true),
	POOL(Category.HOME, true),
	TOWER(Category.HOME, true),
	BIGGER_PLOT(Category.LAND, true),
	BIGGEST_PLOT(Category.LAND, true),
	CAT(Category.PET, true),
	WOLF(Category.PET, true),
	PARROT(Category.PET, true),
	AXOLOTL(Category.PET, true),
	NAME_COLOUR(Category.STYLE, true),
	SHOP_BANNER(Category.STYLE, true),
	FIREWORKS(Category.STYLE, false),
	ADVERT(Category.BUSINESS, false),
	HERALD_AD(Category.BUSINESS, false);

	public enum Category {
		HOME("Home upgrades"), LAND("Land"), PET("Pets"), STYLE("Style"), BUSINESS("Business");

		private final String title;

		Category(String title) {
			this.title = title;
		}

		public String title() {
			return title;
		}
	}

	private final Category category;
	private final boolean permanent;

	StoreKind(Category category, boolean permanent) {
		this.category = category;
		this.permanent = permanent;
	}

	public Category category() {
		return category;
	}

	/** Permanent items are bought once and count toward net worth; the rest are used up. */
	public boolean permanent() {
		return permanent;
	}

	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	public static StoreKind parse(String id) {
		if (id == null) {
			return null;
		}

		for (StoreKind kind : values()) {
			if (kind.id().equalsIgnoreCase(id.strip())) {
				return kind;
			}
		}

		return null;
	}
}
