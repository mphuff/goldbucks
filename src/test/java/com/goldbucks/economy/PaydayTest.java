package com.goldbucks.economy;

import static com.goldbucks.economy.Fixtures.ALEX;
import static com.goldbucks.economy.Fixtures.EVERYONE;
import static com.goldbucks.economy.Fixtures.STEVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class PaydayTest {
	@Test
	void clockIgnoresSkippedNights() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.LIBRARIAN);
		economy.completeTask(ALEX, Job.LIBRARIAN);

		// The clock only moves when ticked, one tick at a time; nothing else can advance it.
		for (int i = 0; i < Rules.ticksPerDay() - 1; i++) {
			assertNull(economy.tick(EVERYONE));
		}

		assertEquals(0, economy.day());
		assertEquals(20, economy.account(ALEX).balance());

		Economy.DayReport report = economy.tick(EVERYONE);
		assertNotNull(report);
		assertEquals(4, report.pay().get(ALEX));
		assertEquals(1, economy.day());
		assertEquals(24, economy.account(ALEX).balance());
	}

	@Test
	void payRequiresTaskAndOnline() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.COOK);
		economy.chooseJob(STEVE, Job.COOK);
		economy.completeTask(ALEX, Job.COOK);
		// Steve did no task.

		economy.endDay(EVERYONE);
		assertEquals(27, economy.account(ALEX).balance());
		assertEquals(20, economy.account(STEVE).balance());

		// Next day Alex does the task but is offline at payday.
		economy.completeTask(ALEX, Job.COOK);
		economy.endDay(Set.of(STEVE));
		assertEquals(27, economy.account(ALEX).balance());
	}

	@Test
	void taskMustBeRedoneEveryDay() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.TAXI_DRIVER);
		economy.completeTask(ALEX, Job.TAXI_DRIVER);

		economy.endDay(EVERYONE);
		economy.endDay(EVERYONE);

		assertEquals(25, economy.account(ALEX).balance());
	}

	@Test
	void taskForAJobYouDoNotHoldDoesNothing() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.TAXI_DRIVER);
		assertFalse(economy.completeTask(ALEX, Job.COOK));

		economy.endDay(EVERYONE);
		assertEquals(20, economy.account(ALEX).balance());
	}

	@Test
	void salaryRepaysDebt() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.VEHICLE_DEALER);
		economy.setBalance(ALEX, -15);

		economy.completeTask(ALEX, Job.VEHICLE_DEALER);
		economy.endDay(EVERYONE);
		assertEquals(-5, economy.account(ALEX).balance());
		assertTrue(economy.account(ALEX).inDebt());

		economy.completeTask(ALEX, Job.VEHICLE_DEALER);
		economy.endDay(EVERYONE);
		assertEquals(5, economy.account(ALEX).balance());
		assertFalse(economy.account(ALEX).inDebt());
	}

	@Test
	void clockAndDaySurviveSaveAndLoad() {
		Economy economy = Fixtures.threePlayers();

		for (int i = 0; i < Rules.ticksPerDay() + 5; i++) {
			economy.tick(EVERYONE);
		}

		Economy loaded = Economy.fromJson(economy.toJson());
		assertEquals(1, loaded.day());
		assertEquals(Rules.ticksPerDay() + 5, loaded.clockTicks());
	}
}
