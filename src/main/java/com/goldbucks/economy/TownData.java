package com.goldbucks.economy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

/**
 * The town's content, read from data files that a data pack can replace:
 * what items are worth, what the store sells, what orders the board can post, and event names.
 */
public final class TownData {
	private static final Gson GSON = new Gson();
	private static volatile TownData current = new TownData();

	/** Item id (like minecraft:bread) -> value of one item in Goldbucks. */
	public Map<String, Double> itemValues = new LinkedHashMap<>();
	public List<StoreItem> store = new ArrayList<>();
	public List<OrderTemplate> orders = new ArrayList<>();
	/** Event id -> its name and description. */
	public Map<String, EventText> events = new LinkedHashMap<>();

	public static final class StoreItem {
		public String id;
		public String name;
		public int price;
		public String description;
		/** The store id that must be owned first, or null. */
		public String requires;
		/** For plot growth: the new plot size. */
		public int size;
		public boolean enabled = true;

		public StoreKind kind() {
			return StoreKind.parse(id);
		}
	}

	public static final class OrderTemplate {
		/** The job this order is tagged with, or null for anyone. */
		public String job;
		/** The item wanted, or null for a passenger. */
		public String item;
		public int min = 1;
		public int max = 1;
		/** "item" or "passenger". */
		public String type = "item";
		/** Fixed pay; 0 means work it out from the items' value. */
		public int pay;
		/** Only posted while this event runs, or null for any day. */
		public String event;

		public Job jobOrNull() {
			return Job.parse(job);
		}

		public boolean passenger() {
			return "passenger".equals(type);
		}
	}

	public static final class EventText {
		public String name;
		public String description;
	}

	public static TownData current() {
		return current;
	}

	public static void activate(TownData data) {
		current = data;
	}

	// ---- reading ----

	public static Map<String, Double> parseItemValues(String json) {
		return GSON.fromJson(json, new TypeToken<LinkedHashMap<String, Double>>() { }.getType());
	}

	public static List<StoreItem> parseStore(String json) {
		return GSON.fromJson(json, new TypeToken<ArrayList<StoreItem>>() { }.getType());
	}

	public static List<OrderTemplate> parseOrders(String json) {
		return GSON.fromJson(json, new TypeToken<ArrayList<OrderTemplate>>() { }.getType());
	}

	public static Map<String, EventText> parseEvents(String json) {
		return GSON.fromJson(json, new TypeToken<LinkedHashMap<String, EventText>>() { }.getType());
	}

	/** Builds town data from the four files' text. Throws JsonParseException if any file is not valid JSON. */
	public static TownData parse(String itemValues, String store, String orders, String events) {
		TownData data = new TownData();
		requireShape("item_values.json", itemValues, true);
		requireShape("store.json", store, false);
		requireShape("orders.json", orders, false);
		requireShape("events.json", events, true);

		try {
			data.itemValues = orEmpty(parseItemValues(itemValues));
			data.store = orEmptyList(parseStore(store));
			data.orders = orEmptyList(parseOrders(orders));
			data.events = orEmpty(parseEvents(events));
		} catch (IllegalStateException | NumberFormatException wrongShape) {
			throw new JsonParseException(wrongShape.getMessage(), wrongShape);
		}

		return data;
	}

	private static void requireShape(String file, String json, boolean object) {
		com.google.gson.JsonElement tree = com.google.gson.JsonParser.parseString(json);

		if (object ? !tree.isJsonObject() : !tree.isJsonArray()) {
			throw new JsonParseException(file + " must be a JSON " + (object ? "object { }" : "list [ ]") + ".");
		}
	}

	private static <K, V> Map<K, V> orEmpty(Map<K, V> map) {
		return map == null ? new LinkedHashMap<>() : map;
	}

	private static <T> List<T> orEmptyList(List<T> list) {
		return list == null ? new ArrayList<>() : list;
	}

	// ---- looking things up ----

	/** The value of one of this item, or 0 if it has none (townsfolk never buy it). */
	public double valueOf(String itemId) {
		Double value = itemValues.get(itemId);
		return value == null ? 0 : value;
	}

	public StoreItem storeItem(String id) {
		for (StoreItem item : store) {
			if (item.id != null && item.id.equalsIgnoreCase(id)) {
				return item;
			}
		}

		return null;
	}

	public StoreItem storeItem(StoreKind kind) {
		return storeItem(kind.id());
	}

	public String eventName(TownEvent event) {
		EventText text = events.get(event.id());
		return text == null || text.name == null || text.name.isBlank() ? event.defaultName() : text.name;
	}

	public String eventDescription(TownEvent event) {
		EventText text = events.get(event.id());
		return text == null || text.description == null || text.description.isBlank() ? event.defaultDescription() : text.description;
	}

	// ---- checking ----

	/**
	 * Every problem with this data, given the settings it will be used with.
	 * {@code itemExists} says whether an item id is a real item in the game.
	 */
	public List<String> validate(Settings settings, Predicate<String> itemExists) {
		List<String> problems = new ArrayList<>();

		for (Map.Entry<String, Double> entry : itemValues.entrySet()) {
			if (!itemExists.test(entry.getKey())) {
				problems.add("item_values: " + entry.getKey() + " is not an item.");
			}

			if (entry.getValue() == null || !(entry.getValue() > 0) || entry.getValue() > 100_000) {
				problems.add("item_values: " + entry.getKey() + " must be worth more than 0.");
			}
		}

		Set<String> ids = new HashSet<>();

		for (StoreItem item : store) {
			if (item.id == null || item.kind() == null) {
				problems.add("store: " + item.id + " is not something the store knows how to sell.");
				continue;
			}

			if (!ids.add(item.id.toLowerCase())) {
				problems.add("store: " + item.id + " is listed twice.");
			}

			if (item.name == null || item.name.isBlank()) {
				problems.add("store: " + item.id + " needs a name.");
			}

			if (item.price < 1 || item.price > 1_000_000) {
				problems.add("store: " + item.id + " must cost at least $1.");
			}
		}

		for (StoreItem item : store) {
			if (item.kind() == null || item.requires == null) {
				continue;
			}

			StoreItem needed = storeItem(item.requires);

			if (needed == null) {
				problems.add("store: " + item.id + " requires " + item.requires + ", which the store does not sell.");
			} else if (!needed.enabled && item.enabled) {
				problems.add("store: " + item.id + " requires " + item.requires + ", which is switched off, so it could never be bought.");
			} else if (needed.kind() != null && !needed.kind().permanent()) {
				problems.add("store: " + item.id + " requires " + item.requires + ", which is used up, so it cannot be owned.");
			}

			if (requiresLoop(item)) {
				problems.add("store: " + item.id + " requires itself, directly or through other items.");
			}
		}

		StoreItem bigger = storeItem(StoreKind.BIGGER_PLOT);
		StoreItem biggest = storeItem(StoreKind.BIGGEST_PLOT);

		if (bigger != null && bigger.size <= settings.plotSize) {
			problems.add("store: bigger_plot size (" + bigger.size + ") must be larger than plotSize (" + settings.plotSize + ").");
		}

		if (bigger != null && biggest != null && biggest.size <= bigger.size) {
			problems.add("store: biggest_plot size (" + biggest.size + ") must be larger than bigger_plot (" + bigger.size + ").");
		}

		for (StoreItem plot : new StoreItem[] {bigger, biggest}) {
			if (plot != null && plot.size > 64) {
				problems.add("store: " + plot.id + " size must be 64 or less.");
			}
		}

		for (OrderTemplate order : orders) {
			String label = "orders: " + (order.item == null ? order.type : order.item);

			if (order.job != null && order.jobOrNull() == null) {
				problems.add(label + " names a job that does not exist: " + order.job + ".");
			}

			if (order.event != null && TownEvent.parse(order.event) == null) {
				problems.add(label + " names an event that does not exist: " + order.event + ".");
			}

			if (order.passenger()) {
				if (order.pay < 1) {
					problems.add(label + " needs a pay of at least $1.");
				}

				continue;
			}

			if (!"item".equals(order.type)) {
				problems.add(label + " has an unknown type: " + order.type + ". Use item or passenger.");
				continue;
			}

			if (order.item == null || !itemExists.test(order.item)) {
				problems.add(label + " is not an item.");
			} else if (order.pay < 1 && valueOf(order.item) <= 0) {
				problems.add(label + " has no fixed pay and no value in item_values, so it would pay nothing.");
			}

			if (order.min < 1 || order.max < order.min || order.max > 64 * 27) {
				problems.add(label + " needs 1 <= min <= max.");
			}
		}

		for (String id : events.keySet()) {
			if (TownEvent.parse(id) == null) {
				problems.add("events: " + id + " is not an event.");
			}
		}

		return problems;
	}

	private boolean requiresLoop(StoreItem start) {
		Set<String> seen = new HashSet<>();
		StoreItem item = start;

		while (item != null && item.requires != null) {
			if (!seen.add(item.id.toLowerCase())) {
				return true;
			}

			item = storeItem(item.requires);

			if (item == start) {
				return true;
			}
		}

		return false;
	}
}
