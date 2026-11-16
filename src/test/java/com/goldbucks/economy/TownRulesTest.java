package com.goldbucks.economy;

import static com.goldbucks.economy.Fixtures.ALEX;
import static com.goldbucks.economy.Fixtures.EVERYONE;
import static com.goldbucks.economy.Fixtures.KAI;
import static com.goldbucks.economy.Fixtures.STEVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The town update's rules: store, net worth, orders, levels, the bank, elections, events and townsfolk. */
class TownRulesTest {
	@BeforeEach
	void freshWorld() {
		Fixtures.freshWorld();
	}

	@AfterEach
	void defaults() {
		Settings.reset();
	}

	// ---- the store and net worth ----

	@Test
	void storeTakesMoneyAndPropertyCountsTowardNetWorth() {
		Economy economy = Fixtures.threePlayers();
		economy.assignHouse(ALEX, 0, 0);
		economy.setBalance(ALEX, 100);

		assertEquals(Result.OK, economy.buy(ALEX, "porch"));
		assertEquals(60, economy.account(ALEX).balance());
		assertEquals(100, economy.netWorth(ALEX), "the porch is still worth what was paid");
		assertTrue(economy.account(ALEX).owns("porch"));
		assertEquals(Result.ALREADY_OWNED, economy.buy(ALEX, "porch"));
	}

	@Test
	void netWorthAddsSavingsPropertyAndBusinesses() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.COOK);
		economy.setBalance(ALEX, 70);
		economy.deposit(ALEX, 30, -1);
		assertEquals(40, economy.account(ALEX).balance());
		assertEquals(30, economy.account(ALEX).savings());
		assertEquals(40 + 30 + 50, economy.netWorth(ALEX));
	}

	@Test
	void usedUpItemsDoNotCountTowardNetWorth() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.STORE_OWNER);

		assertEquals(Result.OK, economy.buy(ALEX, "advert"));
		assertEquals(10, economy.account(ALEX).balance());
		assertEquals(1, economy.account(ALEX).advertsToday());
		assertEquals(10 + 50, economy.netWorth(ALEX));
		assertEquals(Result.OK, economy.buy(ALEX, "advert"), "adverts can be bought again");
	}

	@Test
	void storeRequirementsAndCredit() {
		Economy economy = Fixtures.threePlayers();
		economy.assignHouse(ALEX, 0, 0);

		assertEquals(Result.NEEDS_OTHER_ITEM, economy.buy(ALEX, "pool"), "the pool needs the garden");
		assertEquals(Result.NEEDS_OTHER_ITEM, economy.buy(ALEX, "tower"), "the tower needs the second floor");
		assertEquals(Result.OK, economy.buy(ALEX, "garden"), "credit can pay for the garden");
		assertEquals(-40, economy.account(ALEX).balance());
		assertEquals(Result.OVER_CREDIT_LIMIT, economy.buy(ALEX, "pool"), "but not past the credit limit");
		assertEquals(Result.NO_SUCH_ITEM, economy.buy(ALEX, "jetpack"));
		assertEquals(Result.NO_BUSINESS, economy.buy(STEVE, "advert"), "adverts need a business");
	}

	@Test
	void switchedOffStoreItemsCannotBeBought() {
		TownData data = Fixtures.shippedData();
		data.storeItem("cat").enabled = false;
		TownData.activate(data);

		assertEquals(Result.NO_SUCH_ITEM, Fixtures.threePlayers().buy(ALEX, "cat"));
	}

	@Test
	void plotsGrowAroundTheirCentreUnlessLandIsInTheWay() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.COOK);
		economy.chooseJob(STEVE, Job.STORE_OWNER);
		economy.claimPlot(ALEX, Fixtures.businessId(economy, ALEX), 0, 0);
		economy.setBalance(ALEX, 1000);

		assertEquals(Result.NEEDS_OTHER_ITEM, economy.buy(ALEX, "biggest_plot"));
		assertEquals(Result.OK, economy.buy(ALEX, "bigger_plot"));
		assertEquals(new Plot(-12, -12, 24), economy.plottedBusiness(ALEX).plot());

		// Steve claims land just past Alex's new edge; the biggest plot would reach it.
		economy.claimPlot(STEVE, Fixtures.businessId(economy, STEVE), 22, 0);
		assertEquals(Result.OVERLAPS, economy.buy(ALEX, "biggest_plot"));
		assertEquals(1000 - 150, economy.account(ALEX).balance(), "a refused purchase costs nothing");
	}

	@Test
	void houseUpgradesProtectTheirLand() {
		Economy economy = Fixtures.threePlayers();
		House house = economy.assignHouse(ALEX, 0, 0);
		economy.setBalance(ALEX, 500);
		int stableX = house.lot().minX() - 3;
		int z = house.lot().minZ() + 2;

		assertNull(economy.landOwnerAt(stableX, z), "free before the stable");
		assertEquals(Result.OK, economy.buy(ALEX, "stable"));
		assertEquals(ALEX, economy.landOwnerAt(stableX, z), "the stable's land is Alex's");
		assertFalse(economy.mayBuild(STEVE, stableX, z));
	}

	@Test
	void anUpgradeIsRefusedWhenItWouldRunIntoSomeoneElsesLand() {
		Economy economy = Fixtures.threePlayers();
		economy.assignHouse(ALEX, 0, 0);
		economy.chooseJob(STEVE, Job.COOK);
		// Steve's plot sits right behind Alex's house, where the garden would go.
		economy.claimPlot(STEVE, Fixtures.businessId(economy, STEVE), 3, -10);
		economy.setBalance(ALEX, 500);

		assertEquals(STEVE, economy.upgradeBlockedBy(ALEX, StoreKind.GARDEN));
		assertEquals(Result.OVERLAPS, economy.buy(ALEX, "garden"));
		assertEquals(500, economy.account(ALEX).balance());
	}

	@Test
	void nameColourChangesCostTenAfterTheFirst() {
		Economy economy = Fixtures.threePlayers();
		economy.setBalance(ALEX, 100);

		assertEquals(Result.NEEDS_OTHER_ITEM, economy.setNameColour(ALEX, "gold"));
		economy.buy(ALEX, "name_colour");
		assertEquals(Result.OK, economy.setNameColour(ALEX, "gold"));
		assertEquals(70, economy.account(ALEX).balance(), "the first colour is included");
		assertEquals(Result.OK, economy.setNameColour(ALEX, "aqua"));
		assertEquals(60, economy.account(ALEX).balance());
		assertEquals("aqua", economy.account(ALEX).nameColour());
	}

	// ---- where houses go ----

	@Test
	void housesSkipLotsThatAreNotGoodLand() {
		Economy economy = Fixtures.threePlayers();
		int step = Rules.HOUSE_SIZE + Rules.HOUSE_GAP;
		// Lots 0 and 1 are water; lot 2 is the first dry one.
		House alex = economy.assignHouse(ALEX, 0, 0, lot -> lot.minX() >= 2 * step);
		assertEquals(2, alex.index());
		assertEquals(new Plot(2 * step, 0, Rules.HOUSE_SIZE), alex.lot());

		// The next player gets the next dry lot, never one already taken.
		House steve = economy.assignHouse(STEVE, 0, 0, lot -> lot.minX() >= 2 * step);
		assertEquals(3, steve.index());

		// If nothing along the row is good land, the first free lot is used anyway (the builders fill it in).
		House kai = economy.assignHouse(KAI, 0, 0, lot -> false);
		assertEquals(0, kai.index());
	}

	@Test
	void housesSkipLotsWhereSomeoneElsesLandIsInTheWay() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(STEVE, Job.COOK);
		economy.claimPlot(STEVE, Fixtures.businessId(economy, STEVE), 3, 3);
		House alex = economy.assignHouse(ALEX, 0, 0, lot -> true);
		assertTrue(alex.index() > 0, "the first lot is under Steve's plot, so it is skipped");
		assertFalse(alex.lot().overlaps(economy.businessesOf(STEVE).get(0).plot()), "the house is clear of Steve's plot");
	}

	// ---- permission to build ----

	@Test
	void ownersCanLetOthersBuild() {
		Economy economy = Fixtures.threePlayers();
		House house = economy.assignHouse(ALEX, 0, 0);
		economy.chooseJob(ALEX, Job.COOK);
		economy.claimPlot(ALEX, Fixtures.businessId(economy, ALEX), 100, 100);
		int hx = house.lot().minX() + 1;
		int hz = house.lot().minZ() + 1;

		assertFalse(economy.mayBuild(STEVE, 100, 100));
		assertEquals(Result.OK, economy.allowOnPlots(ALEX, STEVE, true));
		assertTrue(economy.mayBuild(STEVE, 100, 100));
		assertFalse(economy.mayBuild(STEVE, hx, hz), "the plot permission does not cover the house");
		assertEquals(Result.OK, economy.allowAtHome(ALEX, STEVE, true));
		assertTrue(economy.mayBuild(STEVE, hx, hz));
		assertFalse(economy.mayBuild(KAI, 100, 100));
		economy.allowOnPlots(ALEX, STEVE, false);
		assertFalse(economy.mayBuild(STEVE, 100, 100));
		assertEquals(Result.NO_ACCOUNT, economy.allowOnPlots(ALEX, ALEX, true), "no point allowing yourself");
	}

	@Test
	void buildersCountBlocksOnOthersLand() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.BUILDER);

		for (int i = 1; i < Rules.BUILDER_BLOCKS; i++) {
			assertFalse(economy.builderPlaced(ALEX));
		}

		assertTrue(economy.builderPlaced(ALEX), "the 32nd block finishes the task");
		assertFalse(economy.builderPlaced(STEVE), "only builders count");
	}

	@Test
	void billsArePaidOnlyByTheirReceiver() {
		Economy economy = Fixtures.threePlayers();
		Economy.Bill bill = economy.sendBill(ALEX, STEVE, 15, "a shop roof");

		assertNotNull(bill);
		assertEquals(Result.NO_SUCH_ORDER, economy.payBill(KAI, bill.id()));
		assertEquals(Result.OK, economy.payBill(STEVE, bill.id()));
		assertEquals(35, economy.account(ALEX).balance());
		assertEquals(5, economy.account(STEVE).balance());
		assertEquals(Result.NO_SUCH_ORDER, economy.payBill(STEVE, bill.id()), "a bill is paid once");
		assertNull(economy.sendBill(ALEX, ALEX, 5, "myself"));
		assertNull(economy.sendBill(ALEX, STEVE, 0, "nothing"));
	}

	// ---- orders ----

	@Test
	void ordersArePostedForEveryJobHeldPlusGeneralOnes() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.COOK);
		economy.chooseJob(STEVE, Job.TAXI_DRIVER);
		Settings settings = new Settings();
		settings.eventsEnabled = false;
		Settings.activate(settings);

		economy.startDay(Town.Conditions.CLEAR, new Random(1));
		List<Town.Order> orders = economy.town().orders();

		assertEquals(2 + 2, orders.size());
		assertTrue(orders.stream().anyMatch(o -> o.job == Job.COOK && !o.passenger));
		assertTrue(orders.stream().anyMatch(o -> o.job == Job.TAXI_DRIVER && o.passenger));
		assertEquals(2, orders.stream().filter(o -> o.job == null).count());

		for (Town.Order order : orders) {
			assertTrue(order.pay >= 3 && order.pay <= 20, "pay within the limits: " + order.pay);
		}
	}

	@Test
	void orderPayFollowsValueWithinLimits() {
		Settings settings = new Settings();
		assertEquals(3, Town.orderPay(0.1, 5, settings), "never below the least");
		assertEquals(20, Town.orderPay(15, 4, settings), "never above the most");
		assertEquals(10, Town.orderPay(2, 4, settings), "2 x 4 x 130% = 10.4");
	}

	@Test
	void fillingAnOrderPaysAndCountsAsTheJobTask() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.FARMER);
		economy.startDay(Town.Conditions.CLEAR, new Random(2));
		Town.Order farm = economy.town().orders().stream().filter(o -> o.job == Job.FARMER).findFirst().orElseThrow();
		int before = economy.account(ALEX).balance();

		Economy.Filled filled = economy.fill(ALEX, farm.id);

		assertEquals(Result.OK, filled.result());
		assertTrue(filled.taskDone(), "a Farm order is the Farmer's task");
		assertEquals(before + filled.pay(), economy.account(ALEX).balance());
		assertEquals(Result.ORDER_TAKEN, economy.fill(STEVE, farm.id).result());
	}

	@Test
	void aPlayerCanFillOnlySoManyOrdersADay() {
		Economy economy = Fixtures.threePlayers();
		Settings settings = new Settings();
		settings.generalOrders = 6;
		settings.ordersPerPlayerPerDay = 2;
		settings.eventsEnabled = false;
		Settings.activate(settings);
		economy.startDay(Town.Conditions.CLEAR, new Random(3));
		List<Town.Order> orders = economy.town().orders();

		assertEquals(Result.OK, economy.fill(ALEX, orders.get(0).id).result());
		assertEquals(Result.OK, economy.fill(ALEX, orders.get(1).id).result());
		assertEquals(Result.ORDER_LIMIT, economy.fill(ALEX, orders.get(2).id).result());
		assertEquals(Result.OK, economy.fill(STEVE, orders.get(2).id).result());
		assertEquals(Result.NO_SUCH_ORDER, economy.fill(STEVE, 9999).result());
	}

	@Test
	void eventsChangeOrders() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.FARMER);
		economy.startEvent(TownEvent.HARVEST_FESTIVAL);
		economy.repostOrders(new Random(4));
		Town.Order farm = economy.town().orders().stream().filter(o -> o.job == Job.FARMER).findFirst().orElseThrow();
		assertEquals(farm.pay * 2, economy.fill(ALEX, farm.id).pay(), "Harvest Festival doubles farm orders");
		assertTrue(economy.town().orders().stream().anyMatch(o -> "minecraft:pumpkin_pie".equals(o.item)), "pie orders");

		economy.startEvent(TownEvent.HOLIDAY);
		economy.repostOrders(new Random(4));
		assertTrue(economy.town().orders().isEmpty(), "no orders on a holiday");

		economy.startEvent(TownEvent.TOURIST_DAY);
		economy.repostOrders(new Random(4));
		assertEquals(6, economy.town().orders().stream().filter(o -> o.tourist).count(), "six tourists");
	}

	// ---- levels ----

	@Test
	void levelsComeWithDaysWorkedAndRaisePay() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.COOK);

		Fixtures.workDays(economy, 4);
		assertEquals(Level.APPRENTICE, economy.account(ALEX).level(Job.COOK));

		economy.completeTask(ALEX, Job.COOK);
		Economy.DayReport fifth = economy.endDay(EVERYONE, new Random());
		assertEquals(Level.SKILLED, economy.account(ALEX).level(Job.COOK));
		assertEquals(List.of(new Economy.LevelUp(ALEX, Job.COOK, Level.SKILLED)), fifth.levelUps());
		assertEquals(7, fifth.pay().get(ALEX), "the level counts from the next day");

		economy.completeTask(ALEX, Job.COOK);
		assertEquals(7 + 1, economy.endDay(EVERYONE, new Random()).pay().get(ALEX), "Skilled pays $1 more");

		Fixtures.workDays(economy, 30);
		assertEquals(Level.MASTER, economy.account(ALEX).level(Job.COOK));
		economy.completeTask(ALEX, Job.COOK);
		assertEquals(7 + 5, economy.endDay(EVERYONE, new Random()).pay().get(ALEX));
	}

	@Test
	void daysOfflineOrWithoutTheTaskDoNotCount() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.COOK);
		economy.endDay(EVERYONE, new Random());
		economy.completeTask(ALEX, Job.COOK);
		economy.endDay(Set.of(STEVE), new Random());
		assertEquals(0, economy.account(ALEX).daysWorked(Job.COOK));
	}

	@Test
	void levelsStayWithTheJobAfterSellingTheBusiness() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.COOK);
		Fixtures.workDays(economy, 6);
		economy.fire(ALEX);
		economy.chooseJob(ALEX, Job.STORE_OWNER);
		assertEquals(Level.APPRENTICE, economy.account(ALEX).level(Job.STORE_OWNER));
		assertEquals(Level.SKILLED, economy.account(ALEX).level(Job.COOK), "still Skilled at cooking");
	}

	@Test
	void perksAddOrdersVisitsAndRaiseCaps() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.VEHICLE_DEALER);
		economy.chooseJob(STEVE, Job.STORE_OWNER);
		assertEquals(2, economy.townsfolkVisits(STEVE, false));

		Fixtures.workDays(economy, 5);
		assertEquals(2, economy.orderSlots().stream().filter(j -> j == Job.VEHICLE_DEALER).count(), "Skilled dealer: an extra order");
		assertEquals(3, economy.townsfolkVisits(STEVE, false), "Skilled store owner: an extra visit");
		assertEquals(4, economy.townsfolkVisits(STEVE, true), "a fairly priced shop gets one more");

		Business dealer = economy.businessesOf(ALEX).get(0);
		assertEquals(40, economy.priceCapFor(dealer, ItemKind.VEHICLE, false));
		Fixtures.workDays(economy, 25);
		assertEquals(60, economy.priceCapFor(dealer, ItemKind.VEHICLE, false), "Master dealer: vehicles up to $60");
		assertEquals(40, economy.priceCapFor(dealer, ItemKind.FOOD, false), "but only vehicles");
		assertEquals(4, economy.townsfolkVisits(STEVE, false), "Master store owner: two extra visits");
	}

	@Test
	void streaksCountDaysInARow() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.COOK);
		Fixtures.workDays(economy, 3);
		assertEquals(3, economy.account(ALEX).streak());
		economy.endDay(EVERYONE, new Random());
		Fixtures.workDays(economy, 1);
		assertEquals(1, economy.account(ALEX).streak(), "a missed day starts again");
	}

	// ---- the bank ----

	@Test
	void savingsAreSafeFromTaxAndEarnInterestWhenThereIsABanker() {
		Economy economy = Fixtures.threePlayers();
		economy.setBalance(ALEX, 250);
		economy.deposit(ALEX, 200, -1);

		for (int i = 0; i < 7; i++) {
			economy.endDay(EVERYONE, new Random());
		}

		assertEquals(250, economy.account(ALEX).balance() + economy.account(ALEX).savings(),
				"no tax on savings (cash $50 is tax-free), and no interest without a Banker");

		economy.chooseJob(KAI, Job.BANKER);

		for (int i = 0; i < 7; i++) {
			economy.endDay(EVERYONE, new Random());
		}

		assertEquals(210, economy.account(ALEX).savings(), "5% interest");
	}

	@Test
	void interestIsCapped() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(KAI, Job.BANKER);
		economy.setBalance(ALEX, 2000);
		economy.deposit(ALEX, 2000, -1);
		assertEquals(25, economy.interestFor(ALEX));
	}

	@Test
	void bankRules() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(KAI, Job.BANKER);
		int bank = Fixtures.businessId(economy, KAI);

		assertEquals(Result.BAD_AMOUNT, economy.deposit(ALEX, 0, bank));
		assertEquals(Result.BAD_AMOUNT, economy.deposit(ALEX, 21, bank), "cannot save more than you have");
		assertEquals(Result.OK, economy.deposit(ALEX, 10, bank));
		assertEquals(21, economy.account(KAI).balance(), "the town pays the banker $1");
		assertTrue(economy.businessesOf(KAI).get(0).taskDoneToday(), "a customer is the banker's task");
		assertEquals(Result.NOT_ENOUGH_SAVINGS, economy.withdraw(ALEX, 11, bank));
		assertEquals(Result.OK, economy.withdraw(ALEX, 10, bank));
		assertEquals(20, economy.account(ALEX).balance(), "using the bank costs the customer nothing");

		economy.setBalance(STEVE, -5);
		assertEquals(Result.IN_DEBT, economy.deposit(STEVE, 1, bank));
	}

	@Test
	void interestDayDepositsCountTwice() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(KAI, Job.BANKER);
		economy.setBalance(ALEX, 100);
		economy.startEvent(TownEvent.INTEREST_DAY);
		economy.deposit(ALEX, 100, -1);
		assertEquals(10, economy.interestFor(ALEX));
	}

	// ---- the Mayor ----

	@Test
	void electionsNeedCandidatesVotesAndNoSelfVotes() {
		Economy economy = Fixtures.threePlayers();
		assertEquals(Result.NO_ELECTION, economy.runForMayor(ALEX, "Free cake"));

		economy.startEvent(TownEvent.ELECTION_DAY);
		assertEquals(Result.OK, economy.runForMayor(ALEX, "Free cake"));
		assertEquals(Result.OK, economy.runForMayor(STEVE, "More boats"));
		assertEquals(Result.SELF_VOTE, economy.vote(ALEX, ALEX));
		assertEquals(Result.NOT_A_CANDIDATE, economy.vote(ALEX, KAI));
		assertEquals(Result.OK, economy.vote(ALEX, STEVE));
		assertEquals(Result.ALREADY_VOTED, economy.vote(ALEX, STEVE));
		assertEquals(Result.OK, economy.vote(KAI, STEVE));

		Economy.DayReport report = economy.endDay(EVERYONE, new Random());

		assertEquals(STEVE, report.election().winner());
		assertFalse(report.election().coinFlip());
		assertEquals(STEVE, economy.town().mayor());
		assertEquals(economy.day() + 14 - 1, economy.town().nextElectionDay() - 1);
	}

	@Test
	void aTieIsACoinFlip() {
		Economy economy = Fixtures.threePlayers();
		economy.startEvent(TownEvent.ELECTION_DAY);
		economy.runForMayor(ALEX, "");
		economy.runForMayor(STEVE, "");
		economy.vote(ALEX, STEVE);
		economy.vote(STEVE, ALEX);

		Economy.DayReport report = economy.endDay(EVERYONE, new Random(5));
		assertTrue(report.election().coinFlip());
		assertTrue(Set.of(ALEX, STEVE).contains(economy.town().mayor()));
	}

	@Test
	void mayorPowersHaveLimits() {
		Economy economy = Fixtures.threePlayers();
		economy.startEvent(TownEvent.ELECTION_DAY);
		economy.runForMayor(ALEX, "");
		economy.vote(STEVE, ALEX);
		economy.endDay(EVERYONE, new Random());

		assertEquals(Result.NOT_MAYOR, economy.setMayorTax(STEVE, 8));
		assertEquals(Result.OUT_OF_RANGE, economy.setMayorTax(ALEX, 20));
		assertEquals(Result.OK, economy.setMayorTax(ALEX, 5));
		assertEquals(5, economy.taxRate());
		assertEquals(Result.OK, economy.declareHoliday(ALEX));
		assertEquals(Result.ALREADY_USED, economy.declareHoliday(ALEX), "one holiday per term");
		assertEquals(Result.OK, economy.nameTown(ALEX, "Huffville"));
		assertEquals("Huffville", economy.town().townName());

		// The Mayor is paid even without a job task.
		Economy.DayReport report = economy.endDay(EVERYONE, new Random());
		assertEquals(5, report.pay().get(ALEX));
		economy.startDay(Town.Conditions.CLEAR, new Random());
		assertEquals(TownEvent.HOLIDAY, economy.town().event(), "the holiday is the next day");

		// If the host narrows the Mayor's range, the rate in force stays inside it.
		Settings narrower = new Settings();
		narrower.mayorTaxMin = 8;
		Settings.activate(narrower);
		assertEquals(8, economy.taxRate());

		economy.impeach();
		assertNull(economy.town().mayor());
		assertEquals(10, economy.taxRate(), "back to the settings' rate");
	}

	// ---- events ----

	@Test
	void marketDayComesEverySevenDaysAndElectionsWhenDue() {
		Economy economy = Fixtures.threePlayers();
		economy.startDay(Town.Conditions.CLEAR, new Random());
		assertEquals(TownEvent.ELECTION_DAY, economy.town().event(), "a new world elects a Mayor first");
		economy.endDay(EVERYONE, new Random());

		Settings quiet = new Settings();
		quiet.randomEventPercent = 0;
		Settings.activate(quiet);

		while (economy.day() < 7) {
			economy.startDay(Town.Conditions.CLEAR, new Random());

			if (economy.day() != 7) {
				assertNull(economy.town().event(), "no random events when the chance is 0 (day " + economy.day() + ")");
			}

			if (economy.day() == 6) {
				assertEquals(3, economy.town().marketChoices().size(), "three Market Day choices the day before");
			}

			economy.endDay(EVERYONE, new Random());
		}

		economy.startDay(Town.Conditions.CLEAR, new Random());
		assertEquals(TownEvent.MARKET_DAY, economy.town().event());
	}

	@Test
	void conditionsLimitWhichEventsCanStart() {
		Settings settings = new Settings();
		List<TownEvent> clear = Town.eligible(Town.Conditions.CLEAR, settings);
		assertFalse(clear.contains(TownEvent.RAINY_DAY_RIDES));
		assertFalse(clear.contains(TownEvent.NIGHT_MARKET));
		assertFalse(clear.contains(TownEvent.STORM_REPAIRS));
		assertFalse(clear.contains(TownEvent.ELECTION_DAY), "elections are never random");
		assertFalse(clear.contains(TownEvent.HOLIDAY), "only the Mayor declares holidays");
		assertFalse(clear.contains(TownEvent.CONTEST), "only the host starts contests");
		assertTrue(Town.eligible(new Town.Conditions(true, false, false), settings).contains(TownEvent.RAINY_DAY_RIDES));
		assertTrue(Town.eligible(new Town.Conditions(false, false, true), settings).contains(TownEvent.NIGHT_MARKET));

		settings.disabledEvents.add("treasure_hunt");
		assertFalse(Town.eligible(Town.Conditions.CLEAR, settings).contains(TownEvent.TREASURE_HUNT));
	}

	@Test
	void aNewShopGetsAGrandOpeningTheNextDay() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.STORE_OWNER);
		economy.town().nextElectionDay = 100;
		int id = Fixtures.businessId(economy, ALEX);

		assertTrue(economy.registerShop(id, 42L));
		assertFalse(economy.registerShop(id, 43L), "only the first shop");
		economy.endDay(EVERYONE, new Random());
		economy.startDay(Town.Conditions.CLEAR, new Random());

		assertEquals(TownEvent.GRAND_OPENING, economy.town().event());
		assertEquals(2 + 4, economy.townsfolkVisits(ALEX, false));
	}

	@Test
	void multiDayEventsKeepGoing() {
		Economy economy = Fixtures.threePlayers();
		economy.town().nextElectionDay = 100;
		economy.startEvent(TownEvent.RAILWAY_WEEK);
		economy.endDay(EVERYONE, new Random());
		economy.startDay(Town.Conditions.CLEAR, new Random());
		assertEquals(TownEvent.RAILWAY_WEEK, economy.town().event());
		economy.endDay(EVERYONE, new Random());
		economy.startDay(Town.Conditions.CLEAR, new Random());
		assertEquals(TownEvent.RAILWAY_WEEK, economy.town().event());
		economy.endDay(EVERYONE, new Random());
		Settings quiet = new Settings();
		quiet.randomEventPercent = 0;
		Settings.activate(quiet);
		economy.startDay(Town.Conditions.CLEAR, new Random());
		assertNull(economy.town().event(), "it ends after three days");
	}

	@Test
	void eventsCanBeSwitchedOff() {
		Economy economy = Fixtures.threePlayers();
		Settings off = new Settings();
		off.eventsEnabled = false;
		Settings.activate(off);

		for (int i = 0; i < 20; i++) {
			assertNull(economy.startDay(Town.Conditions.CLEAR, new Random(i)));
			economy.endDay(EVERYONE, new Random());
		}
	}

	@Test
	void eventsMultiplyTownsfolkVisits() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.COOK);
		economy.startEvent(TownEvent.MARKET_DAY);
		assertEquals(6, economy.townsfolkVisits(ALEX, false));
		economy.buy(ALEX, "advert");
		assertEquals((2 + 2) * 3, economy.townsfolkVisits(ALEX, false));
	}

	@Test
	void theFeastWinnerGetsTen() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.COOK);
		economy.startEvent(TownEvent.TOWN_FEAST);
		economy.repostOrders(new Random(7));
		Town.Order cookOrder = economy.town().orders().stream().filter(o -> o.job == Job.COOK).findFirst().orElseThrow();
		int pay = economy.fill(STEVE, cookOrder.id).pay();

		Economy.DayReport report = economy.endDay(EVERYONE, new Random());
		assertEquals(STEVE, report.feastWinner());
		assertEquals(20 + pay + 10, economy.account(STEVE).balance());
	}

	@Test
	void takingPartCountsOncePerDay() {
		Economy economy = Fixtures.threePlayers();
		assertFalse(economy.joinEvent(ALEX), "no event, nothing to join");
		economy.startEvent(TownEvent.LOST_PET);
		assertTrue(economy.joinEvent(ALEX));
		assertFalse(economy.joinEvent(ALEX));
		assertEquals(1, economy.account(ALEX).stat("events"));
	}

	// ---- townsfolk ----

	@Test
	void townsfolkBuyTheBestValueFairlyPricedOffer() {
		Settings settings = new Settings();
		List<Townsfolk.Offer> offers = List.of(
				new Townsfolk.Offer(0, "minecraft:bread", 4, true, 2, 1),
				new Townsfolk.Offer(1, "minecraft:cake", 9, true, 8, 1),
				new Townsfolk.Offer(2, "minecraft:dirt", 1, true, 0, 1));

		Townsfolk.Choice choice = Townsfolk.choose(offers, settings, false);
		assertEquals(1, choice.bought().index(), "bread at $4 is too pricey (most $3); cake at $9 of $12 is fine");

		Townsfolk.Choice none = Townsfolk.choose(List.of(offers.get(0), new Townsfolk.Offer(3, "minecraft:cookie", 1, false, 1, 1)), settings, false);
		assertFalse(none.sale());
		assertEquals(0, none.tooPricey().index());
		assertEquals(3, none.empty().index());

		Townsfolk.Choice bargains = Townsfolk.choose(List.of(offers.get(1)), settings, true);
		assertFalse(bargains.sale(), "on Big Sale Day only shops at or below value sell");

		Townsfolk.Choice perk = Townsfolk.choose(List.of(new Townsfolk.Offer(0, "minecraft:oak_boat", 15, true, 6, 2)), settings, false);
		assertTrue(perk.sale(), "a perk that doubles what they pay lets a boat sell for $15");
	}

	@Test
	void townsfolkSalesPayTheOwnerAndCountAsTheTask() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.COOK);
		int id = Fixtures.businessId(economy, ALEX);

		economy.townsfolkPurchase(id, 3, ItemKind.FOOD);

		assertEquals(23, economy.account(ALEX).balance());
		assertTrue(economy.businessesOf(ALEX).get(0).taskDoneToday());
		assertEquals(3, economy.business(id).lifetimeSales());
		assertEquals(1, economy.account(ALEX).stat("townsfolk_sales"));
	}

	// ---- saving ----

	@Test
	void everythingNewSurvivesSaveAndLoad() {
		Economy economy = Fixtures.threePlayers();
		economy.chooseJob(ALEX, Job.COOK);
		economy.chooseJob(KAI, Job.BANKER);
		economy.assignHouse(ALEX, 0, 0);
		economy.setBalance(ALEX, 300);
		economy.buy(ALEX, "porch");
		economy.buy(ALEX, "name_colour");
		economy.setNameColour(ALEX, "gold");
		economy.deposit(ALEX, 50, -1);
		economy.allowAtHome(ALEX, STEVE, true);
		economy.registerShop(Fixtures.businessId(economy, ALEX), 1234L);
		Fixtures.workDays(economy, 6);
		economy.award(ALEX, "first_sale", 5);
		economy.startEvent(TownEvent.ELECTION_DAY);
		economy.runForMayor(STEVE, "Boats for all");
		economy.vote(ALEX, STEVE);
		economy.repostOrders(new Random(9));

		Economy loaded = Economy.fromJson(economy.toJson());
		Account alex = loaded.account(ALEX);

		assertEquals(economy.account(ALEX).balance(), alex.balance());
		assertEquals(economy.account(ALEX).savings(), alex.savings());
		assertTrue(alex.owns("porch"));
		assertEquals("gold", alex.nameColour());
		assertEquals(Level.SKILLED, alex.level(Job.COOK));
		assertTrue(alex.achievements().contains("first_sale"));
		assertTrue(alex.homeAllowed().contains(STEVE));
		assertEquals(economy.netWorth(ALEX), loaded.netWorth(ALEX));
		assertEquals(Set.of(1234L), loaded.businessesOf(ALEX).get(0).shopSigns());
		assertEquals(TownEvent.ELECTION_DAY, loaded.town().event());
		assertEquals("Boats for all", loaded.town().candidates().get(STEVE));
		assertEquals(STEVE, loaded.town().votes().get(ALEX));
		assertEquals(economy.town().orders().size(), loaded.town().orders().size());
		assertEquals(economy.town().orders().get(0).item, loaded.town().orders().get(0).item);
	}

	@Test
	void aWorldSavedBy020LoadsWithEverythingIntact() {
		String old = """
				{"version":2,"clockTicks":96000,"day":4,"nextBusinessId":3,
				 "accounts":[{"id":"00000000-0000-0000-0000-000000000001","name":"Alex","balance":-35},
				             {"id":"00000000-0000-0000-0000-000000000002","name":"Steve","balance":61}],
				 "businesses":[{"id":1,"job":"COOK","owner":"00000000-0000-0000-0000-000000000001","hasPlot":true,"plotX":-8,"plotZ":-8,
				                "lifetimeSales":12,"taskDoneToday":true}],
				 "houses":[{"owner":"00000000-0000-0000-0000-000000000001","index":0,"x":8,"z":8}]}
				""";

		Economy economy = Economy.fromJson(old);

		assertEquals(4, economy.day());
		assertEquals(-35, economy.account(ALEX).balance());
		assertEquals(61, economy.account(STEVE).balance());
		assertEquals(new Plot(-8, -8, 16), economy.businessesOf(ALEX).get(0).plot());
		assertEquals(12, economy.businessesOf(ALEX).get(0).lifetimeSales());
		assertEquals(new Plot(8, 8, 7), economy.house(ALEX).lot());
		assertEquals(0, economy.account(ALEX).savings());
		assertEquals(5, economy.town().nextElectionDay(), "the first election is the next day");
		assertFalse(economy.registerShop(1, 5L), "old shops do not get a Grand Opening");
		assertEquals(2, economy.loadedVersion(), "an old world is recognised, so Goldbucks stays on in it");
		assertEquals(EconomySave.VERSION, Economy.fromJson(economy.toJson()).loadedVersion(), "and saves in the new format");
	}
}
