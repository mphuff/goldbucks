package com.goldbucks.economy;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Every number and switch the host can change, saved in the world folder.
 * A settings object is only ever made active after {@link #validate()} finds no problems,
 * so the rest of the mod can trust every value.
 */
public final class Settings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static volatile Settings current = new Settings();

	// ---- money ----
	@Info(tab = "Money", text = "Money each new player starts with")
	public int startingMoney = 20;
	@Info(tab = "Money", text = "How far into debt a player may go")
	public int creditLimit = 100;
	@Info(tab = "Money", text = "Highest price a player shop may charge")
	public int priceCap = 40;
	@Info(tab = "Money", text = "Weekly tax, as a percent of money over the tax-free amount")
	public int taxPercent = 10;
	@Info(tab = "Money", text = "Money a player can keep before tax applies")
	public int taxFreeAmount = 50;
	@Info(tab = "Money", text = "Days between tax days")
	public int taxPeriodDays = 7;
	@Info(tab = "Money", text = "Fewest days before a shop-bought tool expires")
	public int expiryMinDays = 5;
	@Info(tab = "Money", text = "Most days before a shop-bought tool expires")
	public int expiryMaxDays = 7;
	@Info(tab = "Money", text = "Base price of a business, before its lifetime sales")
	public int businessBasePrice = 50;
	@Info(tab = "Money", text = "Bank interest every 7 days, in percent")
	public int interestPercent = 5;
	@Info(tab = "Money", text = "Most interest one player can earn in a week")
	public int interestCapPerWeek = 25;
	@Info(tab = "Money", text = "What the town pays the Banker per deposit or withdrawal")
	public int bankerFee = 1;

	// ---- jobs ----
	@Info(tab = "Jobs", text = "Length of a game day in ticks (24000 = 20 minutes)")
	public int dayLengthTicks = 24000;
	@Info(tab = "Jobs", text = "Size of a business plot, in blocks per side")
	public int plotSize = 16;
	@Info(tab = "Jobs", text = "Pay per day for each job")
	public Map<String, Integer> jobPay = defaultJobPay();
	@Info(tab = "Jobs", text = "Jobs that can be picked")
	public Map<String, Boolean> jobEnabled = defaultJobEnabled();
	@Info(tab = "Jobs", text = "Days worked to reach Skilled")
	public int skilledDays = 5;
	@Info(tab = "Jobs", text = "Days worked to reach Expert")
	public int expertDays = 15;
	@Info(tab = "Jobs", text = "Days worked to reach Master")
	public int masterDays = 30;
	@Info(tab = "Jobs", text = "Extra pay per day at Skilled")
	public int skilledBonus = 1;
	@Info(tab = "Jobs", text = "Extra pay per day at Expert")
	public int expertBonus = 3;
	@Info(tab = "Jobs", text = "Extra pay per day at Master")
	public int masterBonus = 5;
	@Info(tab = "Jobs", text = "Price cap for Master perks (vehicles, enchanted books)")
	public int masterPriceCap = 60;
	@Info(tab = "Jobs", text = "Extra pay per day for the Mayor")
	public int mayorPay = 5;
	@Info(tab = "Jobs", text = "Days between Mayor elections")
	public int mayorTermDays = 14;
	@Info(tab = "Jobs", text = "Lowest tax rate the Mayor may set")
	public int mayorTaxMin = 5;
	@Info(tab = "Jobs", text = "Highest tax rate the Mayor may set")
	public int mayorTaxMax = 15;

	// ---- town ----
	@Info(tab = "Town", text = "Town orders with no job tag, posted each day")
	public int generalOrders = 2;
	@Info(tab = "Town", text = "Order pay, as a percent of the items' value")
	public int orderPayPercent = 130;
	@Info(tab = "Town", text = "Least an order pays")
	public int orderMinPay = 3;
	@Info(tab = "Town", text = "Most an order pays")
	public int orderMaxPay = 20;
	@Info(tab = "Town", text = "Most orders one player may fill in a day")
	public int ordersPerPlayerPerDay = 3;
	@Info(tab = "Town", text = "Townsfolk come to shop")
	public boolean townsfolkEnabled = true;
	@Info(tab = "Town", text = "Townsfolk visits per shop owner per day")
	public int townsfolkVisitsPerDay = 2;
	@Info(tab = "Town", text = "Most townsfolk will pay, as a percent of an item's value")
	public int fairPricePercent = 150;
	@Info(tab = "Town", text = "Most townsfolk walking around at once")
	public int maxVisibleTownsfolk = 3;
	@Info(tab = "Town", text = "Town reward for most achievements")
	public int achievementReward = 5;
	@Info(tab = "Town", text = "Town reward for the biggest achievements")
	public int bigAchievementReward = 20;

	// ---- events ----
	@Info(tab = "Events", text = "Events happen")
	public boolean eventsEnabled = true;
	@Info(tab = "Events", text = "Days between Market Days")
	public int marketDayEvery = 7;
	@Info(tab = "Events", text = "Chance of a random event on other days, in percent")
	public int randomEventPercent = 33;
	@Info(tab = "Events", text = "Events that never start at random")
	public List<String> disabledEvents = new ArrayList<>();

	// ---- world rules ----
	@Info(tab = "World rules", text = "No monsters")
	public boolean noMonsters = true;
	@Info(tab = "World rules", text = "Players cannot be hurt or die")
	public boolean noDeath = true;
	@Info(tab = "World rules", text = "Everyone but the host plays in survival (no flying)")
	public boolean survivalOnly = true;
	@Info(tab = "World rules", text = "Cheat commands are host-only")
	public boolean cheatsHostOnly = true;

	/** Describes a setting for the settings screen. */
	@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
	@java.lang.annotation.Target(java.lang.annotation.ElementType.FIELD)
	public @interface Info {
		String tab();

		String text();
	}

	private static Map<String, Integer> defaultJobPay() {
		Map<String, Integer> pay = new LinkedHashMap<>();

		for (Job job : Job.values()) {
			pay.put(job.name(), job.defaultPay());
		}

		return pay;
	}

	private static Map<String, Boolean> defaultJobEnabled() {
		Map<String, Boolean> enabled = new LinkedHashMap<>();

		for (Job job : Job.values()) {
			enabled.put(job.name(), true);
		}

		return enabled;
	}

	// ---- the active settings ----

	public static Settings current() {
		return current;
	}

	/** Makes these settings the active ones. Refuses settings with problems. */
	public static void activate(Settings settings) {
		List<String> problems = settings.validate();

		if (!problems.isEmpty()) {
			throw new IllegalArgumentException("Invalid settings: " + problems);
		}

		current = settings;
	}

	/** Back to the built-in defaults. */
	public static void reset() {
		current = new Settings();
	}

	public int payFor(Job job) {
		Integer pay = jobPay.get(job.name());
		return pay == null ? job.defaultPay() : pay;
	}

	public boolean jobEnabled(Job job) {
		Boolean enabled = jobEnabled.get(job.name());
		return enabled == null || enabled;
	}

	// ---- checking ----

	/**
	 * Every problem with these settings, in plain words. Empty means they are safe to use.
	 * This checks single values and combinations of values that would break the game.
	 */
	public List<String> validate() {
		List<String> problems = new ArrayList<>();

		range(problems, "creditLimit", creditLimit, 0, 100_000);
		range(problems, "startingMoney", startingMoney, -creditLimit, 100_000);
		range(problems, "priceCap", priceCap, 1, 100_000);
		range(problems, "taxPercent", taxPercent, 0, 100);
		range(problems, "taxFreeAmount", taxFreeAmount, 0, 1_000_000);
		range(problems, "taxPeriodDays", taxPeriodDays, 1, 365);
		range(problems, "expiryMinDays", expiryMinDays, 1, 365);
		range(problems, "expiryMaxDays", expiryMaxDays, 1, 365);

		if (expiryMaxDays < expiryMinDays) {
			problems.add("expiryMaxDays (" + expiryMaxDays + ") must not be less than expiryMinDays (" + expiryMinDays + ").");
		}

		range(problems, "businessBasePrice", businessBasePrice, 0, 1_000_000);
		range(problems, "interestPercent", interestPercent, 0, 100);
		range(problems, "interestCapPerWeek", interestCapPerWeek, 0, 1_000_000);
		range(problems, "bankerFee", bankerFee, 0, 1000);

		range(problems, "dayLengthTicks", dayLengthTicks, 1200, 240_000);
		range(problems, "plotSize", plotSize, 4, 64);

		if (jobPay == null || jobEnabled == null) {
			problems.add("jobPay and jobEnabled must list the jobs.");
		} else {
			for (String name : jobPay.keySet()) {
				if (Job.parse(name) == null) {
					problems.add("jobPay names a job that does not exist: " + name + ".");
				}
			}

			for (String name : jobEnabled.keySet()) {
				if (Job.parse(name) == null) {
					problems.add("jobEnabled names a job that does not exist: " + name + ".");
				}
			}

			for (Map.Entry<String, Integer> entry : jobPay.entrySet()) {
				if (entry.getValue() == null || entry.getValue() < 0 || entry.getValue() > 10_000) {
					problems.add("Pay for " + entry.getKey() + " must be between 0 and 10000.");
				}
			}

			boolean openJob = false;

			for (Job job : Job.values()) {
				if (!job.exclusive() && jobEnabled(job)) {
					openJob = true;
				}
			}

			if (!openJob) {
				problems.add("At least one job that many players can share (Store Owner, Cook, Builder or Farmer) must be on, "
						+ "or new players could be left with no job to pick.");
			}
		}

		range(problems, "skilledDays", skilledDays, 1, 10_000);

		if (!(skilledDays < expertDays && expertDays < masterDays)) {
			problems.add("Level days must go up: skilledDays < expertDays < masterDays.");
		}

		range(problems, "skilledBonus", skilledBonus, 0, 10_000);

		if (!(skilledBonus <= expertBonus && expertBonus <= masterBonus)) {
			problems.add("Level bonuses must not go down: skilledBonus <= expertBonus <= masterBonus.");
		}

		if (masterPriceCap < priceCap) {
			problems.add("masterPriceCap (" + masterPriceCap + ") must be at least priceCap (" + priceCap + "), or the Master perk would lower prices.");
		}

		range(problems, "mayorPay", mayorPay, 0, 10_000);
		range(problems, "mayorTermDays", mayorTermDays, 1, 365);
		range(problems, "mayorTaxMin", mayorTaxMin, 0, 100);
		range(problems, "mayorTaxMax", mayorTaxMax, 0, 100);

		if (mayorTaxMax < mayorTaxMin) {
			problems.add("mayorTaxMax must not be less than mayorTaxMin.");
		}

		range(problems, "generalOrders", generalOrders, 0, 20);
		range(problems, "orderPayPercent", orderPayPercent, 1, 1000);
		range(problems, "orderMinPay", orderMinPay, 1, 10_000);
		range(problems, "orderMaxPay", orderMaxPay, 1, 10_000);

		if (orderMaxPay < orderMinPay) {
			problems.add("orderMaxPay (" + orderMaxPay + ") must not be less than orderMinPay (" + orderMinPay + ").");
		}

		range(problems, "ordersPerPlayerPerDay", ordersPerPlayerPerDay, 1, 100);
		range(problems, "townsfolkVisitsPerDay", townsfolkVisitsPerDay, 0, 50);
		range(problems, "fairPricePercent", fairPricePercent, 100, 1000);
		range(problems, "maxVisibleTownsfolk", maxVisibleTownsfolk, 0, 20);
		range(problems, "achievementReward", achievementReward, 0, 10_000);
		range(problems, "bigAchievementReward", bigAchievementReward, 0, 10_000);

		range(problems, "marketDayEvery", marketDayEvery, 2, 365);
		range(problems, "randomEventPercent", randomEventPercent, 0, 100);

		if (disabledEvents == null) {
			problems.add("disabledEvents must be a list (it can be empty).");
		} else {
			for (String id : disabledEvents) {
				if (TownEvent.parse(id) == null) {
					problems.add("disabledEvents names an event that does not exist: " + id + ".");
				}
			}
		}

		return problems;
	}

	private static void range(List<String> problems, String name, int value, int min, int max) {
		if (value < min || value > max) {
			problems.add(name + " is " + value + " but must be between " + min + " and " + max + ".");
		}
	}

	// ---- saving and loading ----

	public String toJson() {
		return GSON.toJson(this);
	}

	public Settings copy() {
		return fromJsonUnchecked(toJson());
	}

	/** Reads settings, filling anything missing with the default. Does not validate. */
	public static Settings fromJsonUnchecked(String json) {
		Settings settings = GSON.fromJson(json, Settings.class);

		if (settings == null) {
			return new Settings();
		}

		// Jobs added in newer versions get their defaults.
		Settings defaults = new Settings();

		if (settings.jobPay == null) {
			settings.jobPay = defaults.jobPay;
		}

		if (settings.jobEnabled == null) {
			settings.jobEnabled = defaults.jobEnabled;
		}

		for (Job job : Job.values()) {
			settings.jobPay.putIfAbsent(job.name(), job.defaultPay());
			settings.jobEnabled.putIfAbsent(job.name(), true);
		}

		if (settings.disabledEvents == null) {
			settings.disabledEvents = new ArrayList<>();
		}

		return settings;
	}

	/** What reading a settings file produced: the settings to use, and any problems found. */
	public record Loaded(Settings settings, List<String> problems) {
		public boolean ok() {
			return problems.isEmpty();
		}
	}

	/**
	 * Reads a settings file. If it is broken or has problems, the defaults are used instead
	 * and the problems are reported, so a bad edit can never break the world.
	 */
	public static Loaded load(String json) {
		if (json == null || json.isBlank()) {
			return new Loaded(new Settings(), List.of());
		}

		Settings settings;

		try {
			JsonElement tree = JsonParser.parseString(json);

			if (!tree.isJsonObject()) {
				return new Loaded(new Settings(), List.of("The settings file is not a JSON object."));
			}

			List<String> unknown = unknownKeys(tree.getAsJsonObject());

			if (!unknown.isEmpty()) {
				return new Loaded(new Settings(), List.of("Unknown settings: " + String.join(", ", unknown) + "."));
			}

			settings = fromJsonUnchecked(json);
		} catch (JsonSyntaxException | IllegalStateException | NumberFormatException broken) {
			return new Loaded(new Settings(), List.of("The settings file could not be read: " + broken.getMessage()));
		}

		List<String> problems = settings.validate();
		return problems.isEmpty() ? new Loaded(settings, List.of()) : new Loaded(new Settings(), problems);
	}

	private static List<String> unknownKeys(JsonObject object) {
		List<String> unknown = new ArrayList<>();

		for (String key : object.keySet()) {
			if (field(key) == null) {
				unknown.add(key);
			}
		}

		return unknown;
	}

	// ---- one setting at a time, for the settings screen and /host set ----

	/** The names of the settings that hold a single number or switch, in screen order. */
	public static List<String> simpleNames() {
		List<String> names = new ArrayList<>();

		for (Field field : Settings.class.getDeclaredFields()) {
			if (!Modifier.isStatic(field.getModifiers()) && (field.getType() == int.class || field.getType() == boolean.class)) {
				names.add(field.getName());
			}
		}

		return names;
	}

	public static Info info(String name) {
		Field field = field(name);
		return field == null ? null : field.getAnnotation(Info.class);
	}

	private static Field field(String name) {
		try {
			Field field = Settings.class.getDeclaredField(name);
			return Modifier.isStatic(field.getModifiers()) ? null : field;
		} catch (NoSuchFieldException missing) {
			return null;
		}
	}

	/** The value of one setting as text, or null if there is no such setting. */
	public String get(String name) {
		if (name.startsWith("pay.")) {
			Job job = Job.parse(name.substring(4));
			return job == null ? null : String.valueOf(payFor(job));
		}

		if (name.startsWith("job.")) {
			Job job = Job.parse(name.substring(4));
			return job == null ? null : String.valueOf(jobEnabled(job));
		}

		if (name.equals("disabledEvents")) {
			return String.join(",", disabledEvents);
		}

		Field field = field(name);

		try {
			return field == null || !(field.getType() == int.class || field.getType() == boolean.class)
					? null : String.valueOf(field.get(this));
		} catch (IllegalAccessException impossible) {
			return null;
		}
	}

	/**
	 * A copy of these settings with one value changed. Returns problems instead when the value
	 * cannot be read or the change would leave the settings invalid.
	 */
	public Changed with(String name, String value) {
		Settings copy = copy();
		String problem = copy.set(name, value.strip());

		if (problem != null) {
			return new Changed(null, List.of(problem));
		}

		List<String> problems = copy.validate();
		return problems.isEmpty() ? new Changed(copy, List.of()) : new Changed(null, problems);
	}

	/** Several changes at once, checked together, so a pair of values that only work together can be saved. */
	public Changed with(Map<String, String> changes) {
		Settings copy = copy();

		for (Map.Entry<String, String> change : changes.entrySet()) {
			String problem = copy.set(change.getKey(), change.getValue().strip());

			if (problem != null) {
				return new Changed(null, List.of(problem));
			}
		}

		List<String> problems = copy.validate();
		return problems.isEmpty() ? new Changed(copy, List.of()) : new Changed(null, problems);
	}

	public record Changed(Settings settings, List<String> problems) {
		public boolean ok() {
			return settings != null;
		}
	}

	private String set(String name, String value) {
		if (name.startsWith("pay.")) {
			Job job = Job.parse(name.substring(4));

			if (job == null) {
				return "There is no job called " + name.substring(4) + ".";
			}

			Integer number = parseInt(value);

			if (number == null) {
				return "Pay for " + job.title() + " must be a whole number.";
			}

			jobPay.put(job.name(), number);
			return null;
		}

		if (name.startsWith("job.")) {
			Job job = Job.parse(name.substring(4));
			Boolean on = parseBoolean(value);

			if (job == null) {
				return "There is no job called " + name.substring(4) + ".";
			}

			if (on == null) {
				return "Use true or false for " + name + ".";
			}

			jobEnabled.put(job.name(), on);
			return null;
		}

		if (name.equals("disabledEvents")) {
			disabledEvents = new ArrayList<>();

			for (String part : value.split(",")) {
				if (!part.isBlank()) {
					disabledEvents.add(part.strip().toLowerCase(Locale.ROOT));
				}
			}

			return null;
		}

		Field field = field(name);

		if (field == null || !(field.getType() == int.class || field.getType() == boolean.class)) {
			return "There is no setting called " + name + ".";
		}

		try {
			if (field.getType() == int.class) {
				Integer number = parseInt(value);

				if (number == null) {
					return name + " must be a whole number.";
				}

				field.setInt(this, number);
			} else {
				Boolean on = parseBoolean(value);

				if (on == null) {
					return "Use true or false for " + name + ".";
				}

				field.setBoolean(this, on);
			}
		} catch (IllegalAccessException impossible) {
			return "That setting cannot be changed.";
		}

		return null;
	}

	private static Integer parseInt(String text) {
		try {
			return Integer.parseInt(text.replace("$", "").strip());
		} catch (NumberFormatException notANumber) {
			return null;
		}
	}

	private static Boolean parseBoolean(String text) {
		return switch (text.toLowerCase(Locale.ROOT)) {
			case "true", "on", "yes" -> true;
			case "false", "off", "no" -> false;
			default -> null;
		};
	}
}
