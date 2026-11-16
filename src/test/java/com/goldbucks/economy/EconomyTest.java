package com.goldbucks.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class EconomyTest {
	private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Test
	void newPlayerStartsWithTwenty() {
		Economy economy = new Economy();
		Account account = economy.join(ALEX, "Alex");

		assertEquals(20, account.balance());
		assertEquals(20, economy.netWorth(ALEX));
		assertFalse(account.inDebt());
	}

	@Test
	void rejoiningDoesNotResetMoney() {
		Economy economy = new Economy();
		Account first = economy.join(ALEX, "Alex");
		first.setBalance(7);

		Account again = economy.join(ALEX, "AlexRenamed");

		assertSame(first, again);
		assertEquals(7, again.balance());
		assertEquals("AlexRenamed", again.name());
	}

	@Test
	void survivesSaveAndLoad() {
		Economy economy = new Economy();
		economy.join(ALEX, "Alex").setBalance(-35);
		economy.join(STEVE, "Steve");

		Economy loaded = Economy.fromJson(economy.toJson());

		assertEquals(2, loaded.accounts().size());
		assertEquals(-35, loaded.account(ALEX).balance());
		assertEquals(35, loaded.account(ALEX).debt());
		assertTrue(loaded.account(ALEX).inDebt());
		assertEquals("Steve", loaded.account(STEVE).name());
		assertEquals(20, loaded.account(STEVE).balance());
	}

	@Test
	void emptySaveLoadsAsEmptyEconomy() {
		assertEquals(0, Economy.fromJson("").accounts().size());
		assertEquals(0, Economy.fromJson(null).accounts().size());
	}
}
