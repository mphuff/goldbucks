package com.goldbucks.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Predicate;

import com.google.gson.JsonParseException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The data files: the shipped ones must be valid, and broken ones must be caught. */
class TownDataTest {
	/** In plain unit tests there is no item registry; game tests check real items. */
	private static final Predicate<String> ANY_ITEM = id -> id.startsWith("minecraft:");

	@AfterEach
	void defaults() {
		Settings.reset();
	}

	private static List<String> problems(TownData data) {
		return data.validate(new Settings(), ANY_ITEM);
	}

	@Test
	void shippedDataIsValid() {
		TownData data = Fixtures.shippedData();
		assertEquals(List.of(), problems(data));
	}

	@Test
	void shippedDataCoversEverything() {
		TownData data = Fixtures.shippedData();

		for (StoreKind kind : StoreKind.values()) {
			assertNotNull(data.storeItem(kind), "the store sells " + kind.id());
		}

		for (TownEvent event : TownEvent.values()) {
			assertTrue(data.events.containsKey(event.id()), "events.json describes " + event.id());
		}

		for (Job job : Job.values()) {
			assertTrue(data.orders.stream().anyMatch(o -> o.jobOrNull() == job && o.event == null),
					"there is an everyday order for " + job.title() + ", so every job can earn without other players");
		}

		assertTrue(data.itemValues.size() >= 80, "about 80 item values");
		assertEquals(2.0, data.valueOf("minecraft:bread"));
		assertEquals(0.0, data.valueOf("minecraft:bedrock"), "items not on the list have no value");
	}

	@Test
	void notJsonIsAnError() {
		assertThrows(JsonParseException.class, () -> TownData.parse("{oops", "[]", "[]", "{}"));
		assertThrows(JsonParseException.class, () -> TownData.parse("[]", "[]", "[]", "{}"), "item values must be an object");
	}

	@Test
	void badItemValuesAreCaught() {
		TownData data = TownData.parse("{\"minecraft:bread\": 0, \"notmod:thing\": 2}", "[]", "[]", "{}");
		List<String> problems = problems(data);
		assertTrue(problems.stream().anyMatch(p -> p.contains("bread")), "zero value");
		assertTrue(problems.stream().anyMatch(p -> p.contains("notmod:thing")), "not an item");
	}

	@Test
	void badStoreItemsAreCaught() {
		String store = """
				[
					{"id": "porch", "name": "Porch", "price": 40},
					{"id": "porch", "name": "Porch again", "price": 40},
					{"id": "jetpack", "name": "Jetpack", "price": 40},
					{"id": "garden", "name": "", "price": 0},
					{"id": "pool", "name": "Pool", "price": 10, "requires": "moat"},
					{"id": "tower", "name": "Tower", "price": 10, "requires": "second_floor"},
					{"id": "second_floor", "name": "Upstairs", "price": 10, "requires": "tower"},
					{"id": "cat", "name": "Cat", "price": 10, "requires": "advert"},
					{"id": "advert", "name": "Advert", "price": 10},
					{"id": "wolf", "name": "Wolf", "price": 10, "requires": "parrot"},
					{"id": "parrot", "name": "Parrot", "price": 10, "enabled": false}
				]""";
		List<String> problems = problems(TownData.parse("{}", store, "[]", "{}"));

		assertTrue(problems.stream().anyMatch(p -> p.contains("listed twice")), "duplicate");
		assertTrue(problems.stream().anyMatch(p -> p.contains("jetpack")), "unknown item");
		assertTrue(problems.stream().anyMatch(p -> p.contains("garden needs a name")), "no name");
		assertTrue(problems.stream().anyMatch(p -> p.contains("garden must cost")), "free");
		assertTrue(problems.stream().anyMatch(p -> p.contains("moat")), "requires something not sold");
		assertTrue(problems.stream().anyMatch(p -> p.contains("requires itself")), "a loop of requirements");
		assertTrue(problems.stream().anyMatch(p -> p.contains("used up")), "requires a used-up item");
		assertTrue(problems.stream().anyMatch(p -> p.contains("switched off")), "requires a switched-off item");
	}

	@Test
	void plotSizesMustGrow() {
		String store = """
				[
					{"id": "bigger_plot", "name": "Bigger", "price": 10, "size": 16},
					{"id": "biggest_plot", "name": "Biggest", "price": 20, "size": 12}
				]""";
		List<String> problems = problems(TownData.parse("{}", store, "[]", "{}"));
		assertTrue(problems.stream().anyMatch(p -> p.contains("bigger_plot size")));
		assertTrue(problems.stream().anyMatch(p -> p.contains("biggest_plot size")));

		// The same data is fine once the normal plot size is smaller.
		Settings small = new Settings();
		small.plotSize = 8;
		String ok = store.replace("\"size\": 12", "\"size\": 20");
		assertEquals(List.of(), TownData.parse("{}", ok, "[]", "{}").validate(small, ANY_ITEM));
	}

	@Test
	void badOrdersAreCaught() {
		String orders = """
				[
					{"job": "astronaut", "item": "minecraft:bread", "min": 1, "max": 2},
					{"item": "minecraft:bread", "min": 5, "max": 2},
					{"item": "minecraft:dirt", "min": 1, "max": 2},
					{"item": "notmod:thing", "min": 1, "max": 2, "pay": 4},
					{"item": "minecraft:bread", "min": 1, "max": 2, "event": "alien_visit"},
					{"type": "teleport"},
					{"type": "passenger", "pay": 0}
				]""";
		List<String> problems = problems(TownData.parse("{\"minecraft:bread\": 2}", "[]", orders, "{}"));

		assertTrue(problems.stream().anyMatch(p -> p.contains("astronaut")), "unknown job");
		assertTrue(problems.stream().anyMatch(p -> p.contains("min <= max")), "min above max");
		assertTrue(problems.stream().anyMatch(p -> p.contains("dirt") && p.contains("pay nothing")), "worthless order");
		assertTrue(problems.stream().anyMatch(p -> p.contains("notmod:thing")), "not an item");
		assertTrue(problems.stream().anyMatch(p -> p.contains("alien_visit")), "unknown event");
		assertTrue(problems.stream().anyMatch(p -> p.contains("teleport")), "unknown type");
		assertTrue(problems.stream().anyMatch(p -> p.contains("passenger needs a pay")), "unpaid passenger");
	}

	@Test
	void eventTextsMustNameRealEvents() {
		List<String> problems = problems(TownData.parse("{}", "[]", "[]", "{\"dragon_day\": {\"name\": \"Dragons\"}}"));
		assertFalse(problems.isEmpty());

		TownData renamed = TownData.parse("{}", "[]", "[]", "{\"market_day\": {\"name\": \"Big Market\"}}");
		assertEquals("Big Market", renamed.eventName(TownEvent.MARKET_DAY));
		assertEquals(TownEvent.MARKET_DAY.defaultDescription(), renamed.eventDescription(TownEvent.MARKET_DAY));
		assertEquals("Lost Pet", renamed.eventName(TownEvent.LOST_PET), "unnamed events keep their own name");
	}
}
