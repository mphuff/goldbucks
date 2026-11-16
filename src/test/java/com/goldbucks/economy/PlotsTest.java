package com.goldbucks.economy;

import static com.goldbucks.economy.Fixtures.ALEX;
import static com.goldbucks.economy.Fixtures.KAI;
import static com.goldbucks.economy.Fixtures.STEVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PlotsTest {
	@Test
	void plotIsSixteenBySixteen() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.COOK);
		int id = Fixtures.businessId(economy, ALEX);

		assertEquals(Result.OK, economy.claimPlot(ALEX, id, 100, 200));

		Plot plot = economy.business(id).plot();
		assertEquals(16, plot.size());
		assertEquals(ALEX, economy.landOwnerAt(100, 200));
		assertEquals(ALEX, economy.landOwnerAt(plot.minX(), plot.minZ()));
		assertEquals(ALEX, economy.landOwnerAt(plot.minX() + 15, plot.minZ() + 15));
		assertNull(economy.landOwnerAt(plot.minX() + 16, plot.minZ()));
		assertNull(economy.landOwnerAt(plot.minX() - 1, plot.minZ()));
	}

	@Test
	void plotsCannotOverlap() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.COOK);
		economy.chooseJob(STEVE, Job.COOK);
		economy.claimPlot(ALEX, Fixtures.businessId(economy, ALEX), 0, 0);
		int steves = Fixtures.businessId(economy, STEVE);

		assertEquals(Result.OVERLAPS, economy.claimPlot(STEVE, steves, 15, 15));
		assertEquals(Result.OVERLAPS, economy.claimPlot(STEVE, steves, -15, 0));
		assertEquals(Result.OK, economy.claimPlot(STEVE, steves, 16, 0));
	}

	@Test
	void oneBusinessOnePlotAndOnlyItsOwnerClaims() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.COOK);
		int id = Fixtures.businessId(economy, ALEX);

		assertEquals(Result.NOT_OWNER, economy.claimPlot(STEVE, id, 0, 0));
		assertEquals(Result.OK, economy.claimPlot(ALEX, id, 0, 0));
		assertEquals(Result.ALREADY_CLAIMED, economy.claimPlot(ALEX, id, 500, 500));
	}

	@Test
	void housesAreSevenWideAndTenApart() {
		Economy economy = Fixtures.threePlayers();
		House first = economy.assignHouse(ALEX, 1000, 50);
		House second = economy.assignHouse(STEVE, 1000, 50);
		House third = economy.assignHouse(KAI, 1000, 50);

		assertEquals(7, first.lot().size());
		assertEquals(1000, first.lot().minX());
		assertEquals(10, second.lot().minX() - (first.lot().minX() + first.lot().size()));
		assertEquals(10, third.lot().minX() - (second.lot().minX() + second.lot().size()));
		assertEquals(ALEX, economy.landOwnerAt(1003, 53));
		assertEquals(STEVE, economy.landOwnerAt(second.lot().minX(), 50));
		assertNull(economy.landOwnerAt(1008, 53));
	}

	@Test
	void aPlayerGetsOnlyOneHouse() {
		Economy economy = Fixtures.threePlayers();
		House first = economy.assignHouse(ALEX, 0, 0);
		assertEquals(first, economy.assignHouse(ALEX, 0, 0));
		assertEquals(1, economy.houses().size());
	}

	@Test
	void plotsCannotCoverAHouse() {
		Economy economy = Fixtures.threePlayers();
		economy.assignHouse(STEVE, 0, 0);
		economy.chooseJob(ALEX, Job.COOK);

		assertEquals(Result.OVERLAPS, economy.claimPlot(ALEX, Fixtures.businessId(economy, ALEX), 3, 3));
	}

	@Test
	void landSurvivesSaveAndLoad() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.COOK);
		economy.claimPlot(ALEX, Fixtures.businessId(economy, ALEX), 100, 100);
		economy.assignHouse(STEVE, 0, 0);

		Economy loaded = Economy.fromJson(economy.toJson());

		assertEquals(ALEX, loaded.landOwnerAt(100, 100));
		assertEquals(STEVE, loaded.landOwnerAt(3, 3));
		assertEquals(Job.COOK, loaded.businessAt(100, 100).job());
	}

	@Test
	void refusalCanNameWhoseLandIsInTheWay() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.COOK);
		economy.claimPlot(ALEX, Fixtures.businessId(economy, ALEX), 0, 0);
		economy.assignHouse(STEVE, 200, 200);

		assertEquals(ALEX, economy.landInTheWay(Plot.around(10, 10)));
		assertEquals(STEVE, economy.landInTheWay(Plot.around(203, 195)));
		assertNull(economy.landInTheWay(Plot.around(100, 100)));
	}

	@Test
	void aHouseOwnsItsDoorstep() {
		Economy economy = Fixtures.threePlayers();
		House house = economy.assignHouse(ALEX, 0, 0);

		// The row of blocks along the front (south) wall holds the doorstep and the name sign.
		assertEquals(ALEX, economy.landOwnerAt(3, house.lot().minZ() + 7));
		assertNull(economy.landOwnerAt(3, house.lot().minZ() + 8));
		assertNull(economy.landOwnerAt(3, house.lot().minZ() - 1));
	}
}
