package com.goldbucks.economy;

import static com.goldbucks.economy.Fixtures.EVERYONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ExpiryTest {
	@Test
	void expiryIsFiveToSevenDaysAfterPurchase() {
		Economy economy = Fixtures.threePlayers();
		economy.endDay(EVERYONE);
		economy.endDay(EVERYONE);
		Random random = new Random(42);
		Set<Integer> seen = new HashSet<>();

		for (int i = 0; i < 500; i++) {
			seen.add(economy.rollExpiryDay(random) - economy.day());
		}

		assertEquals(Set.of(5, 6, 7), seen);
	}

	@Test
	void itemExpiresOnItsDayAndNotBefore() {
		Economy economy = Fixtures.threePlayers();
		int expires = 5;

		for (int day = 1; day <= 4; day++) {
			economy.endDay(EVERYONE);
			assertFalse(economy.isExpired(expires), "day " + day);
		}

		economy.endDay(EVERYONE);
		assertTrue(economy.isExpired(expires));
	}
}
