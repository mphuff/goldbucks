package com.goldbucks.economy;

import static com.goldbucks.economy.Fixtures.ALEX;
import static com.goldbucks.economy.Fixtures.EVERYONE;
import static com.goldbucks.economy.Fixtures.STEVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShopRulesTest {
	private static Economy withAlexStore() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.STORE_OWNER);
		return economy;
	}

	@Test
	void moneyGoesFromBuyerToOwner() {
		Economy economy = withAlexStore();
		int shop = Fixtures.businessId(economy, ALEX);

		assertEquals(Result.OK, economy.purchase(STEVE, shop, 12, ItemKind.OTHER));

		assertEquals(8, economy.account(STEVE).balance());
		assertEquals(32, economy.account(ALEX).balance());
		assertEquals(12, economy.business(shop).lifetimeSales());
	}

	@Test
	void nothingMayCostMoreThanForty() {
		Economy economy = withAlexStore();
		int shop = Fixtures.businessId(economy, ALEX);

		assertEquals(Result.OK, Economy.checkPrice(40));
		assertEquals(Result.PRICE_TOO_HIGH, Economy.checkPrice(41));
		assertEquals(Result.BAD_PRICE, Economy.checkPrice(0));
		assertEquals(Result.PRICE_TOO_HIGH, economy.purchase(STEVE, shop, 41, ItemKind.OTHER));
		assertEquals(20, economy.account(STEVE).balance());
	}

	@Test
	void creditGoesDownToMinusOneHundredAndNoFurther() {
		Economy economy = withAlexStore();
		int shop = Fixtures.businessId(economy, ALEX);

		// $20 -> -$100 is $120 of spending: three $40 purchases.
		assertEquals(Result.OK, economy.purchase(STEVE, shop, 40, ItemKind.OTHER));
		assertEquals(Result.OK, economy.purchase(STEVE, shop, 40, ItemKind.OTHER));
		assertEquals(Result.OK, economy.purchase(STEVE, shop, 40, ItemKind.OTHER));
		assertEquals(-100, economy.account(STEVE).balance());
		assertEquals(100, economy.account(STEVE).debt());
		assertTrue(economy.account(STEVE).inDebt());

		assertEquals(Result.OVER_CREDIT_LIMIT, economy.purchase(STEVE, shop, 1, ItemKind.OTHER));
		assertEquals(-100, economy.account(STEVE).balance());
		assertEquals(140, economy.account(ALEX).balance());
	}

	@Test
	void cannotBuyFromYourOwnShop() {
		Economy economy = withAlexStore();
		assertEquals(Result.OWN_SHOP, economy.purchase(ALEX, Fixtures.businessId(economy, ALEX), 5, ItemKind.OTHER));
	}

	@Test
	void aSaleCountsAsTheStoreOwnersTask() {
		Economy economy = withAlexStore();
		economy.purchase(STEVE, Fixtures.businessId(economy, ALEX), 5, ItemKind.OTHER);

		economy.endDay(EVERYONE);

		// $20 start + $5 sale + $8 pay.
		assertEquals(33, economy.account(ALEX).balance());
	}

	@Test
	void aCookOnlyGetsCreditForSellingFood() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.COOK);
		int shop = Fixtures.businessId(economy, ALEX);

		economy.purchase(STEVE, shop, 1, ItemKind.OTHER);
		economy.endDay(EVERYONE);
		assertEquals(21, economy.account(ALEX).balance());

		economy.purchase(STEVE, shop, 1, ItemKind.FOOD);
		economy.endDay(EVERYONE);
		assertEquals(29, economy.account(ALEX).balance());
	}
}
