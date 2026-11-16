package com.goldbucks.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The world settings, and the checks that stop a bad value or a bad combination of values from being used. */
class SettingsTest {
	@AfterEach
	void defaults() {
		Settings.reset();
	}

	private static List<String> problemsAfter(Consumer<Settings> change) {
		Settings settings = new Settings();
		change.accept(settings);
		return settings.validate();
	}

	private static void assertRejected(String why, Consumer<Settings> change) {
		assertFalse(problemsAfter(change).isEmpty(), why);
	}

	@Test
	void defaultsAreValid() {
		assertEquals(List.of(), new Settings().validate());
	}

	@Test
	void defaultsMatchTheDesign() {
		Settings s = new Settings();
		assertEquals(20, s.startingMoney);
		assertEquals(10, s.payFor(Job.VEHICLE_DEALER));
		assertEquals(7, s.payFor(Job.BUILDER));
		assertEquals(6, s.payFor(Job.FARMER));
		assertEquals(8, s.payFor(Job.BANKER));
		assertEquals(5, s.skilledDays);
		assertEquals(15, s.expertDays);
		assertEquals(30, s.masterDays);
		assertEquals(150, s.fairPricePercent);
		assertEquals(33, s.randomEventPercent);
	}

	@Test
	void singleValuesOutOfRangeAreRejected() {
		assertRejected("negative credit limit", s -> s.creditLimit = -1);
		assertRejected("zero price cap", s -> s.priceCap = 0);
		assertRejected("tax over 100%", s -> s.taxPercent = 101);
		assertRejected("no tax days", s -> s.taxPeriodDays = 0);
		assertRejected("a day shorter than a minute", s -> s.dayLengthTicks = 100);
		assertRejected("a tiny plot", s -> s.plotSize = 2);
		assertRejected("negative interest", s -> s.interestPercent = -5);
		assertRejected("townsfolk paying less than value", s -> s.fairPricePercent = 50);
		assertRejected("no orders a player may fill", s -> s.ordersPerPlayerPerDay = 0);
		assertRejected("Market Day every day", s -> s.marketDayEvery = 1);
		assertRejected("event chance over 100%", s -> s.randomEventPercent = 150);
		assertRejected("negative pay", s -> s.jobPay.put("COOK", -3));
	}

	@Test
	void combinationsThatBreakTheGameAreRejected() {
		assertRejected("starting deeper in debt than the credit limit allows", s -> {
			s.creditLimit = 50;
			s.startingMoney = -60;
		});
		assertRejected("expiry window upside down", s -> {
			s.expiryMinDays = 8;
			s.expiryMaxDays = 6;
		});
		assertRejected("levels out of order", s -> s.expertDays = 4);
		assertRejected("Master no harder than Expert", s -> s.masterDays = s.expertDays);
		assertRejected("level bonus going down", s -> s.masterBonus = 0);
		assertRejected("Master price cap below the normal cap", s -> s.priceCap = 80);
		assertRejected("Mayor tax limits upside down", s -> {
			s.mayorTaxMin = 20;
			s.mayorTaxMax = 10;
		});
		assertRejected("order pay limits upside down", s -> {
			s.orderMinPay = 30;
			s.orderMaxPay = 20;
		});
	}

	@Test
	void someJobThatPlayersCanShareMustStayOn() {
		assertRejected("every shareable job off, so a new player might have nothing to pick", s -> {
			s.jobEnabled.put("STORE_OWNER", false);
			s.jobEnabled.put("COOK", false);
			s.jobEnabled.put("BUILDER", false);
			s.jobEnabled.put("FARMER", false);
		});

		assertEquals(List.of(), problemsAfter(s -> {
			s.jobEnabled.put("STORE_OWNER", false);
			s.jobEnabled.put("COOK", false);
			s.jobEnabled.put("BUILDER", false);
		}), "one shareable job left on is enough");
	}

	@Test
	void namesMustBeReal() {
		assertRejected("pay for a job that does not exist", s -> s.jobPay.put("ASTRONAUT", 5));
		assertRejected("switching off an event that does not exist", s -> s.disabledEvents.add("dragon_attack"));
		assertEquals(List.of(), problemsAfter(s -> s.disabledEvents.add("treasure_hunt")));
	}

	@Test
	void invalidSettingsCannotBeActivated() {
		Settings bad = new Settings();
		bad.priceCap = -1;
		assertThrows(IllegalArgumentException.class, () -> Settings.activate(bad));
		assertEquals(40, Settings.current().priceCap, "the old settings stay in force");
	}

	@Test
	void savedSettingsLoadBack() {
		Settings settings = new Settings();
		settings.priceCap = 30;
		settings.jobPay.put("COOK", 9);
		settings.disabledEvents.add("lost_pet");

		Settings.Loaded loaded = Settings.load(settings.toJson());

		assertTrue(loaded.ok());
		assertEquals(30, loaded.settings().priceCap);
		assertEquals(9, loaded.settings().payFor(Job.COOK));
		assertEquals(List.of("lost_pet"), loaded.settings().disabledEvents);
	}

	@Test
	void aMissingOrEmptyFileMeansDefaults() {
		assertTrue(Settings.load(null).ok());
		assertTrue(Settings.load("").ok());
		assertEquals(20, Settings.load("{}").settings().startingMoney);
	}

	@Test
	void anOldFileGetsNewJobsWithTheirDefaults() {
		Settings.Loaded loaded = Settings.load("{\"jobPay\": {\"COOK\": 9}, \"jobEnabled\": {\"COOK\": true}}");

		assertTrue(loaded.ok(), loaded.problems().toString());
		assertEquals(9, loaded.settings().payFor(Job.COOK));
		assertEquals(8, loaded.settings().payFor(Job.BANKER));
		assertTrue(loaded.settings().jobEnabled(Job.FARMER));
	}

	@Test
	void aBrokenFileFallsBackToDefaultsAndSaysWhy() {
		Settings.Loaded broken = Settings.load("{ this is not json");
		assertFalse(broken.ok());
		assertEquals(40, broken.settings().priceCap);

		Settings.Loaded wrongType = Settings.load("{\"priceCap\": \"lots\"}");
		assertFalse(wrongType.ok());

		Settings.Loaded notAnObject = Settings.load("[1, 2]");
		assertFalse(notAnObject.ok());

		Settings.Loaded misspelt = Settings.load("{\"pricecap\": 30}");
		assertFalse(misspelt.ok(), "a misspelt setting is reported, not silently ignored");
		assertTrue(misspelt.problems().get(0).contains("pricecap"));

		Settings.Loaded badValue = Settings.load("{\"taxPercent\": 400}");
		assertFalse(badValue.ok());
		assertEquals(10, badValue.settings().taxPercent, "the defaults are used instead");
		assertTrue(badValue.problems().get(0).contains("taxPercent"));
	}

	@Test
	void changingOneSettingIsChecked() {
		Settings settings = new Settings();

		Settings.Changed good = settings.with("priceCap", "35");
		assertTrue(good.ok());
		assertEquals(35, good.settings().priceCap);
		assertEquals(40, settings.priceCap, "the original is not touched");

		assertFalse(settings.with("priceCap", "ten").ok(), "not a number");
		assertFalse(settings.with("priceCap", "0").ok(), "out of range");
		assertFalse(settings.with("noSuchThing", "1").ok(), "no such setting");
		assertFalse(settings.with("noMonsters", "maybe").ok(), "not a switch value");
		assertFalse(settings.with("masterDays", "10").ok(), "would put levels out of order");
		assertTrue(settings.with("noMonsters", "off").ok());
		assertEquals(12, settings.with("pay.cook", "12").settings().payFor(Job.COOK));
		assertFalse(settings.with("pay.astronaut", "12").ok());
		assertFalse(settings.with("job.farmer", "sometimes").ok());
	}

	@Test
	void valuesThatOnlyWorkTogetherCanBeChangedTogether() {
		Settings settings = new Settings();
		// Raising the price cap above 60 alone fails, because the Master cap must be at least as high.
		assertFalse(settings.with("priceCap", "70").ok());

		Map<String, String> both = new LinkedHashMap<>();
		both.put("priceCap", "70");
		both.put("masterPriceCap", "90");
		Settings.Changed changed = settings.with(both);

		assertTrue(changed.ok(), changed.problems().toString());
		assertEquals(70, changed.settings().priceCap);
	}

	@Test
	void everySimpleSettingHasADescriptionAndCanBeRead() {
		Settings settings = new Settings();

		for (String name : Settings.simpleNames()) {
			assertNotNull(Settings.info(name), name + " needs a description for the settings screen");
			assertNotNull(settings.get(name), name);
		}

		assertEquals("10", settings.get("pay.vehicle_dealer"));
		assertEquals("true", settings.get("job.banker"));
	}
}
