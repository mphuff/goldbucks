package com.goldbucks.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RulesTest {
	@BeforeEach
	void defaults() {
		Settings.reset();
	}

	@Test
	void numbersMatchTheDesign() {
		assertEquals(20, Rules.startingMoney());
		assertEquals(100, Rules.creditLimit());
		assertEquals(40, Rules.priceCap());
		assertEquals(10, Rules.taxPercent());
		assertEquals(50, Rules.taxFreeAmount());
		assertEquals(7, Rules.taxPeriodDays());
		assertEquals(5, Rules.expiryMinDays());
		assertEquals(7, Rules.expiryMaxDays());
		assertEquals(50, Rules.businessBasePrice());
		assertEquals(16, Rules.plotSize());
		assertEquals(10, Rules.HOUSE_GAP);
		assertEquals(24000, Rules.ticksPerDay());
	}

	@Test
	void numbersFollowTheSettings() {
		Settings changed = new Settings();
		changed.startingMoney = 35;
		changed.priceCap = 25;
		Settings.activate(changed);

		assertEquals(35, Rules.startingMoney());
		assertEquals(25, Rules.priceCap());
		assertEquals(35, new Economy().join(Fixtures.ALEX, "Alex").balance());
		assertEquals("Nothing may cost more than $25.", Result.PRICE_TOO_HIGH.message());
	}
}
