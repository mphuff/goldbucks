package com.goldbucks.economy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** The town's shared state: today's orders, the running event, and the Mayor. Saved with the economy. */
public final class Town {
	// ---- orders ----

	public static final class Order {
		public int id;
		/** The job this order is tagged with, or null for anyone. */
		public Job job;
		/** Item id, or null for a passenger. */
		public String item;
		public int count;
		public int pay;
		public boolean passenger;
		/** For passengers: where they want to go, as "x y z" plus a label after a bar, set by the game. */
		public String destination;
		/** Tourists buy two things where they get out. */
		public boolean tourist;
		public UUID filledBy;

		public boolean open() {
			return filledBy == null;
		}

		public String describe() {
			if (passenger) {
				return (tourist ? "Tourist" : "Passenger") + " ride" + (destination == null ? "" : " to " + destinationLabel());
			}

			return count + " " + itemName(item);
		}

		public String destinationLabel() {
			if (destination == null) {
				return "";
			}

			int bar = destination.indexOf('|');
			return bar < 0 ? destination : destination.substring(bar + 1);
		}
	}

	/** "minecraft:baked_potato" -> "baked potato". */
	public static String itemName(String itemId) {
		if (itemId == null) {
			return "";
		}

		int colon = itemId.indexOf(':');
		return (colon < 0 ? itemId : itemId.substring(colon + 1)).replace('_', ' ');
	}

	final List<Order> orders = new ArrayList<>();
	int nextOrderId = 1;
	/** Town Feast: how many feast orders each player filled today. */
	final Map<UUID, Integer> feastFills = new LinkedHashMap<>();

	public List<Order> orders() {
		return Collections.unmodifiableList(orders);
	}

	public Order order(int id) {
		for (Order order : orders) {
			if (order.id == id) {
				return order;
			}
		}

		return null;
	}

	/**
	 * Posts the day's orders: one per job held (more with perks and events), plus the general ones.
	 * {@code jobSlots} lists a job once for each order it should get.
	 */
	void postOrders(List<Job> jobSlots, TownEvent event, TownData data, Settings settings, Random random) {
		orders.clear();
		feastFills.clear();

		if (event == TownEvent.HOLIDAY) {
			return;
		}

		for (Job job : jobSlots) {
			TownData.OrderTemplate template = pick(data, job, event, random, false);

			if (template != null) {
				add(template, data, settings, random, false);
			}
		}

		int general = settings.generalOrders + TownEvent.extraGeneralOrders(event);

		for (int i = 0; i < general; i++) {
			TownData.OrderTemplate template = pick(data, null, event, random, false);

			if (template != null) {
				add(template, data, settings, random, false);
			}
		}

		if (event == null) {
			return;
		}

		// Event orders: every template written for this event, plus event-specific extras.
		for (TownData.OrderTemplate template : data.orders) {
			if (event.id().equals(template.event)) {
				add(template, data, settings, random, false);
			}
		}

		switch (event) {
			case TOWN_FEAST -> {
				for (int i = 0; i < 3; i++) {
					TownData.OrderTemplate template = pick(data, Job.COOK, null, random, false);

					if (template != null) {
						add(template, data, settings, random, false);
					}
				}
			}
			case TOURIST_DAY -> {
				TownData.OrderTemplate ride = pick(data, Job.TAXI_DRIVER, null, random, true);

				for (int i = 0; ride != null && i < 6; i++) {
					add(ride, data, settings, random, true);
				}
			}
			case RAINY_DAY_RIDES -> {
				TownData.OrderTemplate ride = pick(data, Job.TAXI_DRIVER, null, random, true);

				for (int i = 0; ride != null && i < 2; i++) {
					add(ride, data, settings, random, false);
				}
			}
			default -> {
			}
		}
	}

	private static TownData.OrderTemplate pick(TownData data, Job job, TownEvent event, Random random, boolean passengerOnly) {
		List<TownData.OrderTemplate> choices = new ArrayList<>();

		for (TownData.OrderTemplate template : data.orders) {
			boolean jobMatches = job == null ? template.job == null : job == template.jobOrNull();
			boolean everyDay = template.event == null;

			if (jobMatches && everyDay && (!passengerOnly || template.passenger())) {
				choices.add(template);
			}
		}

		return choices.isEmpty() ? null : choices.get(random.nextInt(choices.size()));
	}

	private void add(TownData.OrderTemplate template, TownData data, Settings settings, Random random, boolean tourist) {
		Order order = new Order();
		order.id = nextOrderId++;
		order.job = template.jobOrNull();
		order.passenger = template.passenger();
		order.tourist = tourist;

		if (order.passenger) {
			order.count = 1;
			order.pay = Math.max(1, template.pay);
		} else {
			order.item = template.item;
			order.count = template.min + random.nextInt(template.max - template.min + 1);
			order.pay = template.pay > 0 ? template.pay
					: orderPay(data.valueOf(template.item), order.count, settings);
		}

		orders.add(order);
	}

	/** What an item order pays: its value times the pay percent, kept between the least and most. */
	public static int orderPay(double valueEach, int count, Settings settings) {
		int pay = (int) Math.round(valueEach * count * settings.orderPayPercent / 100.0);
		return Math.max(settings.orderMinPay, Math.min(settings.orderMaxPay, pay));
	}

	/** Some events make some orders pay double. */
	public static int payFor(Order order, TownEvent event) {
		if (event == null) {
			return order.pay;
		}

		boolean doubled = switch (event) {
			case HARVEST_FESTIVAL -> order.job == Job.FARMER;
			case GOLD_RUSH -> order.item != null && order.item.contains("gold");
			case RAINY_DAY_RIDES -> order.passenger;
			default -> false;
		};

		return doubled ? order.pay * 2 : order.pay;
	}

	// ---- events ----

	TownEvent event;
	/** The last day the current event runs. */
	int eventEndsDay = -1;
	/** Anything an event needs to remember, such as where the treasure is. */
	final Map<String, String> eventData = new LinkedHashMap<>();
	/** A player who opened their first shop today; tomorrow is their Grand Opening. */
	UUID newShopOwner;
	UUID grandOpeningOwner;
	/** Events taken part in today, so each counts once per player. */
	final Map<UUID, Boolean> joinedEvent = new LinkedHashMap<>();

	public TownEvent event() {
		return event;
	}

	public int eventEndsDay() {
		return eventEndsDay;
	}

	public Map<String, String> eventData() {
		return eventData;
	}

	public UUID grandOpeningOwner() {
		return grandOpeningOwner;
	}

	/** What the world is like at dawn, which some events need. */
	public record Conditions(boolean raining, boolean thunderedYesterday, boolean fullMoon) {
		public static final Conditions CLEAR = new Conditions(false, false, false);

		boolean allow(TownEvent.Condition condition, boolean newShop) {
			return switch (condition) {
				case NONE -> true;
				case FULL_MOON -> fullMoon;
				case RAIN -> raining;
				case AFTER_THUNDER -> thunderedYesterday;
				case NEW_SHOP -> newShop;
			};
		}
	}

	/** Every event the game could pick by itself today. */
	public static List<TownEvent> eligible(Conditions conditions, Settings settings) {
		List<TownEvent> list = new ArrayList<>();

		for (TownEvent candidate : TownEvent.values()) {
			if (candidate.randomAllowed() && !settings.disabledEvents.contains(candidate.id())
					&& conditions.allow(candidate.condition(), false)) {
				list.add(candidate);
			}
		}

		return list;
	}

	/** Picks the event for a new day, or keeps a running one. Returns the event if one started today. */
	TownEvent chooseEvent(int day, Conditions conditions, Settings settings, Random random) {
		joinedEvent.clear();

		if (event != null && day <= eventEndsDay) {
			return null;
		}

		event = null;
		eventData.clear();
		grandOpeningOwner = null;

		if (!settings.eventsEnabled) {
			newShopOwner = null;
			return null;
		}

		TownEvent chosen = null;

		if (day >= nextElectionDay) {
			chosen = TownEvent.ELECTION_DAY;
		} else if (holidayDay == day) {
			chosen = TownEvent.HOLIDAY;
		} else if (day % settings.marketDayEvery == 0) {
			chosen = marketPick != null ? marketPick : TownEvent.MARKET_DAY;
			marketPick = null;
			marketChoices.clear();
		} else if (newShopOwner != null) {
			chosen = TownEvent.GRAND_OPENING;
			grandOpeningOwner = newShopOwner;
		} else if (random.nextInt(100) < settings.randomEventPercent) {
			List<TownEvent> list = eligible(conditions, settings);
			list.remove(TownEvent.MARKET_DAY);

			if (!list.isEmpty()) {
				chosen = list.get(random.nextInt(list.size()));
			}
		}

		newShopOwner = null;

		if (chosen != null) {
			start(chosen, day);
		}

		// The Mayor picks the next Market Day from three choices, offered the day before.
		if ((day + 1) % settings.marketDayEvery == 0 && marketChoices.isEmpty()) {
			List<TownEvent> list = eligible(Conditions.CLEAR, settings);
			Collections.shuffle(list, random);
			list.remove(TownEvent.MARKET_DAY);
			marketChoices.add(TownEvent.MARKET_DAY);
			marketChoices.addAll(list.subList(0, Math.min(2, list.size())));
		}

		return chosen;
	}

	void start(TownEvent chosen, int day) {
		event = chosen;
		eventEndsDay = day + chosen.days() - 1;
		eventData.clear();
	}

	void stopEvent() {
		event = null;
		eventEndsDay = -1;
		eventData.clear();
		grandOpeningOwner = null;
	}

	// ---- the town square ----

	/** Centre of the town square at floor height. Set once it is built. */
	public int squareX;
	public int squareY;
	public int squareZ;
	public boolean squareBuilt;
	/** The Town Clerk villager's id. */
	public String clerkId;

	// ---- the Mayor and elections ----

	UUID mayor;
	int nextElectionDay = 0;
	/** The last day startDay ran for, so a day is never started twice. */
	int lastStartedDay = -1;
	/** The Mayor's tax rate, or -1 to use the settings. */
	int mayorTaxRate = -1;
	boolean holidayUsed;
	int holidayDay = -1;
	String townName = "Goldbucks Town";
	String mayorAnnouncement;
	final List<TownEvent> marketChoices = new ArrayList<>();
	TownEvent marketPick;
	final Map<UUID, String> candidates = new LinkedHashMap<>();
	final Map<UUID, UUID> votes = new LinkedHashMap<>();

	public UUID mayor() {
		return mayor;
	}

	public int nextElectionDay() {
		return nextElectionDay;
	}

	public int mayorTaxRate() {
		return mayorTaxRate;
	}

	public boolean holidayUsed() {
		return holidayUsed;
	}

	public String townName() {
		return townName;
	}

	public String mayorAnnouncement() {
		return mayorAnnouncement;
	}

	public List<TownEvent> marketChoices() {
		return Collections.unmodifiableList(marketChoices);
	}

	public TownEvent marketPick() {
		return marketPick;
	}

	public Map<UUID, String> candidates() {
		return Collections.unmodifiableMap(candidates);
	}

	public Map<UUID, UUID> votes() {
		return Collections.unmodifiableMap(votes);
	}

	public boolean electionOpen() {
		return event == TownEvent.ELECTION_DAY;
	}

	/** The result of an election: the winner (null if nobody ran), and whether a coin flip decided it. */
	public record ElectionResult(UUID winner, boolean coinFlip, Map<UUID, Integer> tally) {
	}

	ElectionResult closeElection(int day, Settings settings, Random random) {
		Map<UUID, Integer> tally = new LinkedHashMap<>();

		for (UUID candidate : candidates.keySet()) {
			tally.put(candidate, 0);
		}

		for (UUID choice : votes.values()) {
			tally.computeIfPresent(choice, (who, count) -> count + 1);
		}

		int best = -1;
		List<UUID> leaders = new ArrayList<>();

		for (Map.Entry<UUID, Integer> entry : tally.entrySet()) {
			if (entry.getValue() > best) {
				best = entry.getValue();
				leaders.clear();
			}

			if (entry.getValue() == best) {
				leaders.add(entry.getKey());
			}
		}

		UUID winner = leaders.isEmpty() ? null : leaders.get(random.nextInt(leaders.size()));
		boolean coinFlip = leaders.size() > 1;

		if (winner != null) {
			mayor = winner;
			mayorTaxRate = -1;
			holidayUsed = false;
			holidayDay = -1;
			mayorAnnouncement = null;
		}

		candidates.clear();
		votes.clear();
		nextElectionDay = day + settings.mayorTermDays;
		return new ElectionResult(winner, coinFlip, tally);
	}
}
