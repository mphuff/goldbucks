package com.goldbucks.economy;

import static com.goldbucks.economy.Fixtures.ALEX;
import static com.goldbucks.economy.Fixtures.EVERYONE;
import static com.goldbucks.economy.Fixtures.KAI;
import static com.goldbucks.economy.Fixtures.STEVE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TaxTest {
	@Test
	void tenPercentOfEverythingAboveFifty() {
		assertEquals(0, Economy.taxOn(-30));
		assertEquals(0, Economy.taxOn(50));
		assertEquals(1, Economy.taxOn(60));
		assertEquals(5, Economy.taxOn(100));
		assertEquals(95, Economy.taxOn(1000));
		assertEquals(0, Economy.taxOn(59));
	}

	@Test
	void taxIsTakenEverySevenDaysAndVanishes() {
		Economy economy = Fixtures.threePlayers();
		economy.setBalance(ALEX, 250);
		economy.setBalance(STEVE, 40);
		economy.setBalance(KAI, -20);

		for (int day = 1; day <= 6; day++) {
			economy.endDay(EVERYONE);
		}

		assertEquals(250, economy.account(ALEX).balance());

		economy.endDay(EVERYONE);

		assertEquals(230, economy.account(ALEX).balance());
		assertEquals(40, economy.account(STEVE).balance());
		assertEquals(-20, economy.account(KAI).balance());

		int total = economy.accounts().stream().mapToInt(Account::balance).sum();
		assertEquals(250, total);
	}

	@Test
	void taxComesRoundAgainOnDayFourteen() {
		Economy economy = Fixtures.threePlayers();
		economy.setBalance(ALEX, 150);

		for (int day = 1; day <= 14; day++) {
			economy.endDay(EVERYONE);
		}

		// Day 7: 150 -> 140. Day 14: 140 -> 131.
		assertEquals(131, economy.account(ALEX).balance());
	}
}
