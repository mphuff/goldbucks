package com.goldbucks.economy;

import static com.goldbucks.economy.Fixtures.ALEX;
import static com.goldbucks.economy.Fixtures.STEVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JobsTest {
	@Test
	void payMatchesTheDesign() {
		assertEquals(10, Job.VEHICLE_DEALER.dailyPay());
		assertEquals(8, Job.STORE_OWNER.dailyPay());
		assertEquals(7, Job.COOK.dailyPay());
		assertEquals(6, Job.SALESPERSON.dailyPay());
		assertEquals(5, Job.TAXI_DRIVER.dailyPay());
		assertEquals(4, Job.LIBRARIAN.dailyPay());
	}

	@Test
	void onlyStoreOwnerCookBuilderAndFarmerCanBeShared() {
		for (Job job : Job.values()) {
			assertEquals(job != Job.STORE_OWNER && job != Job.COOK && job != Job.BUILDER && job != Job.FARMER, job.exclusive(), job.name());
		}
	}

	@Test
	void newPlayerNeedsAJobUntilTheyPick() {
		Economy economy = Fixtures.threePlayers();
		assertTrue(economy.needsJob(ALEX));

		assertEquals(Result.OK, economy.chooseJob(ALEX, Job.LIBRARIAN));

		assertFalse(economy.needsJob(ALEX));
		assertEquals(Job.LIBRARIAN, economy.businessesOf(ALEX).get(0).job());
	}

	@Test
	void exclusiveJobCanOnlyBeHeldOnce() {
		Economy economy = Fixtures.threePlayers();
		assertEquals(Result.OK, economy.chooseJob(ALEX, Job.VEHICLE_DEALER));

		assertFalse(economy.jobAvailable(Job.VEHICLE_DEALER));
		assertEquals(Result.JOB_TAKEN, economy.chooseJob(STEVE, Job.VEHICLE_DEALER));
		assertTrue(economy.needsJob(STEVE));
	}

	@Test
	void sharedJobsCanBeHeldByMany() {
		Economy economy = Fixtures.threePlayers();
		assertEquals(Result.OK, economy.chooseJob(ALEX, Job.COOK));
		assertEquals(Result.OK, economy.chooseJob(STEVE, Job.COOK));
	}

	@Test
	void cannotPickASecondJob() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.COOK);
		assertEquals(Result.ALREADY_HAS_JOB, economy.chooseJob(ALEX, Job.LIBRARIAN));
	}

	@Test
	void firedPlayerLosesJobAndItBecomesFree() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.TAXI_DRIVER);

		assertEquals(Result.OK, economy.fire(ALEX));

		assertTrue(economy.needsJob(ALEX));
		assertTrue(economy.jobAvailable(Job.TAXI_DRIVER));
		assertEquals(Result.OK, economy.chooseJob(ALEX, Job.STORE_OWNER));
	}

	@Test
	void whichSalesCountAsTheDailyTask() {
		assertTrue(Job.STORE_OWNER.saleCountsAsTask(ItemKind.OTHER));
		assertTrue(Job.SALESPERSON.saleCountsAsTask(ItemKind.OTHER));
		assertTrue(Job.COOK.saleCountsAsTask(ItemKind.FOOD));
		assertFalse(Job.COOK.saleCountsAsTask(ItemKind.OTHER));
		assertTrue(Job.VEHICLE_DEALER.saleCountsAsTask(ItemKind.VEHICLE));
		assertFalse(Job.VEHICLE_DEALER.saleCountsAsTask(ItemKind.FOOD));
		assertTrue(Job.LIBRARIAN.saleCountsAsTask(ItemKind.BOOK));
		assertFalse(Job.TAXI_DRIVER.saleCountsAsTask(ItemKind.OTHER));
	}
}
