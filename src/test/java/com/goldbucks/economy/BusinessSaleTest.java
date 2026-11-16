package com.goldbucks.economy;

import static com.goldbucks.economy.Fixtures.ALEX;
import static com.goldbucks.economy.Fixtures.KAI;
import static com.goldbucks.economy.Fixtures.STEVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BusinessSaleTest {
	@Test
	void price() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.STORE_OWNER);
		int shop = Fixtures.businessId(economy, ALEX);
		assertEquals(50, economy.business(shop).price());

		economy.purchase(STEVE, shop, 30, ItemKind.OTHER);
		economy.purchase(KAI, shop, 7, ItemKind.OTHER);

		assertEquals(87, economy.business(shop).price());
	}

	@Test
	void ownerMustAgree() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.LIBRARIAN);
		int shop = Fixtures.businessId(economy, ALEX);
		economy.setBalance(STEVE, 500);

		assertEquals(Result.NOT_FOR_SALE, economy.buyBusiness(STEVE, shop));
		assertEquals(Result.NOT_OWNER, economy.agreeToSell(STEVE, shop, STEVE));

		economy.agreeToSell(ALEX, shop, KAI);
		assertEquals(Result.NOT_FOR_SALE, economy.buyBusiness(STEVE, shop));
		assertEquals(ALEX, economy.business(shop).owner());
	}

	@Test
	void transfer() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.LIBRARIAN);
		economy.chooseJob(STEVE, Job.COOK);
		int library = Fixtures.businessId(economy, ALEX);
		economy.claimPlot(ALEX, library, 100, 100);
		economy.setBalance(STEVE, 200);

		assertEquals(Result.OK, economy.agreeToSell(ALEX, library, STEVE));
		assertEquals(Result.OK, economy.buyBusiness(STEVE, library));

		// Money: the game's price of $50 moves from buyer to seller.
		assertEquals(150, economy.account(STEVE).balance());
		assertEquals(70, economy.account(ALEX).balance());
		// Plot and job go with the business; the buyer keeps their old one too.
		assertEquals(STEVE, economy.landOwnerAt(100, 100));
		assertEquals(2, economy.businessesOf(STEVE).size());
		// The seller must pick a new job, and the sold job is not free.
		assertTrue(economy.needsJob(ALEX));
		assertFalse(economy.jobAvailable(Job.LIBRARIAN));
		assertEquals(Result.JOB_TAKEN, economy.chooseJob(ALEX, Job.LIBRARIAN));
		assertEquals(Result.OK, economy.chooseJob(ALEX, Job.STORE_OWNER));
	}

	@Test
	void buyerCanUseCreditButOnlyToTheLimit() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.LIBRARIAN);
		int library = Fixtures.businessId(economy, ALEX);
		economy.agreeToSell(ALEX, library, STEVE);

		// Steve has $20; price $50 leaves him at -$30, within the limit.
		assertEquals(Result.OK, economy.buyBusiness(STEVE, library));
		assertEquals(-30, economy.account(STEVE).balance());

		// A business priced past the limit cannot be bought.
		economy.chooseJob(ALEX, Job.TAXI_DRIVER);
		int taxi = economy.businessesOf(ALEX).get(0).id();
		economy.agreeToSell(ALEX, taxi, STEVE);
		economy.setBalance(STEVE, -60);
		assertEquals(Result.OVER_CREDIT_LIMIT, economy.buyBusiness(STEVE, taxi));
		assertEquals(ALEX, economy.business(taxi).owner());
	}

	@Test
	void ownerOfTwoBusinessesIsPaidForEachTaskDone() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.LIBRARIAN);
		economy.chooseJob(STEVE, Job.TAXI_DRIVER);
		economy.setBalance(STEVE, 100);
		int library = Fixtures.businessId(economy, ALEX);
		economy.agreeToSell(ALEX, library, STEVE);
		economy.buyBusiness(STEVE, library);

		economy.completeTask(STEVE, Job.LIBRARIAN);
		economy.completeTask(STEVE, Job.TAXI_DRIVER);
		economy.endDay(Fixtures.EVERYONE);

		// $100 - $50 purchase + $4 librarian + $5 taxi.
		assertEquals(59, economy.account(STEVE).balance());
	}

	@Test
	void hostPowers() {
		Economy economy = Fixtures.threePlayers();
		assertEquals(Result.OK, economy.setBalance(ALEX, -80));
		assertEquals(Result.OK, economy.forgiveDebt(ALEX));
		assertEquals(0, economy.account(ALEX).balance());

		economy.setBalance(STEVE, 33);
		economy.forgiveDebt(STEVE);
		assertEquals(33, economy.account(STEVE).balance());
	}
}
