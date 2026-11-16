package com.goldbucks.economy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Turns the economy into JSON and back. Version 2 was saved by 0.2.0; anything it did not have
 * starts empty, so old worlds load with everyone's money, jobs, plots and houses intact.
 */
final class EconomySave {
	private static final Gson GSON = new GsonBuilder().create();
	static final int VERSION = 3;

	private EconomySave() {
	}

	private static final class SavedAccount {
		String id;
		String name;
		int balance;
		int savings;
		List<String> owned;
		int propertyValue;
		String nameColour;
		Map<String, Integer> daysWorked;
		int streak;
		Integer lastWorkedDay;
		List<String> achievements;
		Map<String, Integer> stats;
		List<String> homeAllowed;
		Map<String, String> pets;
		int awaySales;
		int awayMoney;
		boolean awayEmptyShop;
		int ordersToday;
		int advertsToday;
		int salesToday;
		int builderBlocksToday;
		int interestDayDeposits;
		String heraldAd;
	}

	private static final class SavedBusiness {
		int id;
		String job;
		String owner;
		boolean hasPlot;
		int plotX;
		int plotZ;
		int plotSize;
		int lifetimeSales;
		boolean taskDoneToday;
		String agreedBuyer;
		List<Long> shopSigns;
		List<String> allowed;
		boolean everOpened;
	}

	private static final class SavedHouse {
		String owner;
		int index;
		int x;
		int z;
	}

	private static final class SavedOrder {
		int id;
		String job;
		String item;
		int count;
		int pay;
		boolean passenger;
		String destination;
		boolean tourist;
		String filledBy;
	}

	private static final class SavedTown {
		List<SavedOrder> orders;
		int nextOrderId = 1;
		String event;
		int eventEndsDay = -1;
		Map<String, String> eventData;
		String newShopOwner;
		String grandOpeningOwner;
		String mayor;
		int nextElectionDay = 0;
		Integer lastStartedDay;
		int mayorTaxRate = -1;
		boolean holidayUsed;
		int holidayDay = -1;
		String townName;
		String mayorAnnouncement;
		List<String> marketChoices;
		String marketPick;
		Map<String, String> candidates;
		Map<String, String> votes;
		int squareX;
		int squareY;
		int squareZ;
		boolean squareBuilt;
		String clerkId;
	}

	private static final class Saved {
		int version = VERSION;
		long clockTicks;
		int day;
		int nextBusinessId = 1;
		List<SavedAccount> accounts = new ArrayList<>();
		List<SavedBusiness> businesses = new ArrayList<>();
		List<SavedHouse> houses = new ArrayList<>();
		SavedTown town;
	}

	static String write(Economy economy) {
		Saved saved = new Saved();
		saved.clockTicks = economy.clockTicks();
		saved.day = economy.day();
		saved.nextBusinessId = economy.nextBusinessId();

		for (Account account : economy.accountMap().values()) {
			SavedAccount s = new SavedAccount();
			s.id = account.id().toString();
			s.name = account.name();
			s.balance = account.balance();
			s.savings = account.savings();
			s.owned = new ArrayList<>(account.owned);
			s.propertyValue = account.propertyValue;
			s.nameColour = account.nameColour;
			s.daysWorked = new LinkedHashMap<>();
			account.daysWorked.forEach((job, days) -> s.daysWorked.put(job.name(), days));
			s.streak = account.streak;
			s.lastWorkedDay = account.lastWorkedDay;
			s.achievements = new ArrayList<>(account.achievements);
			s.stats = new LinkedHashMap<>(account.stats);
			s.homeAllowed = account.homeAllowed.stream().map(UUID::toString).toList();
			s.pets = new LinkedHashMap<>();
			account.pets.forEach((kind, entity) -> s.pets.put(kind, entity.toString()));
			s.awaySales = account.awaySales;
			s.awayMoney = account.awayMoney;
			s.awayEmptyShop = account.awayEmptyShop;
			s.ordersToday = account.ordersToday;
			s.advertsToday = account.advertsToday;
			s.salesToday = account.salesToday;
			s.builderBlocksToday = account.builderBlocksToday;
			s.interestDayDeposits = account.interestDayDeposits;
			s.heraldAd = account.heraldAd;
			saved.accounts.add(s);
		}

		for (Business business : economy.businessMap().values()) {
			SavedBusiness s = new SavedBusiness();
			s.id = business.id();
			s.job = business.job().name();
			s.owner = business.owner().toString();
			s.hasPlot = business.plot() != null;

			if (business.plot() != null) {
				s.plotX = business.plot().minX();
				s.plotZ = business.plot().minZ();
				s.plotSize = business.plot().size();
			}

			s.lifetimeSales = business.lifetimeSales();
			s.taskDoneToday = business.taskDoneToday();
			UUID agreed = economy.agreedSaleMap().get(business.id());
			s.agreedBuyer = agreed == null ? null : agreed.toString();
			s.shopSigns = new ArrayList<>(business.shopSigns);
			s.allowed = business.allowed.stream().map(UUID::toString).toList();
			s.everOpened = business.everOpened;
			saved.businesses.add(s);
		}

		for (House house : economy.houseMap().values()) {
			SavedHouse s = new SavedHouse();
			s.owner = house.owner().toString();
			s.index = house.index();
			s.x = house.lot().minX();
			s.z = house.lot().minZ();
			saved.houses.add(s);
		}

		saved.town = writeTown(economy.town);
		return GSON.toJson(saved);
	}

	private static SavedTown writeTown(Town town) {
		SavedTown s = new SavedTown();
		s.orders = new ArrayList<>();

		for (Town.Order order : town.orders) {
			SavedOrder o = new SavedOrder();
			o.id = order.id;
			o.job = order.job == null ? null : order.job.name();
			o.item = order.item;
			o.count = order.count;
			o.pay = order.pay;
			o.passenger = order.passenger;
			o.destination = order.destination;
			o.tourist = order.tourist;
			o.filledBy = string(order.filledBy);
			s.orders.add(o);
		}

		s.nextOrderId = town.nextOrderId;
		s.event = town.event == null ? null : town.event.id();
		s.eventEndsDay = town.eventEndsDay;
		s.eventData = new LinkedHashMap<>(town.eventData);
		s.newShopOwner = string(town.newShopOwner);
		s.grandOpeningOwner = string(town.grandOpeningOwner);
		s.mayor = string(town.mayor);
		s.nextElectionDay = town.nextElectionDay;
		s.lastStartedDay = town.lastStartedDay;
		s.squareX = town.squareX;
		s.squareY = town.squareY;
		s.squareZ = town.squareZ;
		s.squareBuilt = town.squareBuilt;
		s.clerkId = town.clerkId;
		s.mayorTaxRate = town.mayorTaxRate;
		s.holidayUsed = town.holidayUsed;
		s.holidayDay = town.holidayDay;
		s.townName = town.townName;
		s.mayorAnnouncement = town.mayorAnnouncement;
		s.marketChoices = town.marketChoices.stream().map(TownEvent::id).toList();
		s.marketPick = town.marketPick == null ? null : town.marketPick.id();
		s.candidates = new LinkedHashMap<>();
		town.candidates.forEach((who, promise) -> s.candidates.put(who.toString(), promise));
		s.votes = new LinkedHashMap<>();
		town.votes.forEach((voter, choice) -> s.votes.put(voter.toString(), choice.toString()));
		return s;
	}

	private static String string(UUID id) {
		return id == null ? null : id.toString();
	}

	private static UUID uuid(String text) {
		return text == null ? null : UUID.fromString(text);
	}

	static Economy read(String json) {
		Economy economy = new Economy();

		if (json == null || json.isBlank()) {
			return economy;
		}

		Saved saved = GSON.fromJson(json, Saved.class);

		if (saved == null) {
			return economy;
		}

		economy.setLoadedVersion(saved.version);
		economy.setClock(saved.clockTicks, saved.day);
		economy.setNextBusinessId(Math.max(1, saved.nextBusinessId));

		if (saved.accounts != null) {
			for (SavedAccount s : saved.accounts) {
				UUID id = UUID.fromString(s.id);
				Account account = new Account(id, s.name, s.balance);
				account.setSavings(s.savings);

				if (s.owned != null) {
					account.owned.addAll(s.owned);
				}

				account.propertyValue = s.propertyValue;
				account.nameColour = s.nameColour;

				if (s.daysWorked != null) {
					s.daysWorked.forEach((job, days) -> {
						Job parsed = Job.parse(job);

						if (parsed != null) {
							account.daysWorked.put(parsed, days);
						}
					});
				}

				account.streak = s.streak;
				account.lastWorkedDay = s.lastWorkedDay == null ? -1 : s.lastWorkedDay;

				if (s.achievements != null) {
					account.achievements.addAll(s.achievements);
				}

				if (s.stats != null) {
					account.stats.putAll(s.stats);
				}

				if (s.homeAllowed != null) {
					s.homeAllowed.forEach(who -> account.homeAllowed.add(UUID.fromString(who)));
				}

				if (s.pets != null) {
					s.pets.forEach((kind, entity) -> account.pets.put(kind, UUID.fromString(entity)));
				}

				account.awaySales = s.awaySales;
				account.awayMoney = s.awayMoney;
				account.awayEmptyShop = s.awayEmptyShop;
				account.ordersToday = s.ordersToday;
				account.advertsToday = s.advertsToday;
				account.salesToday = s.salesToday;
				account.builderBlocksToday = s.builderBlocksToday;
				account.interestDayDeposits = s.interestDayDeposits;
				account.heraldAd = s.heraldAd;
				economy.accountMap().put(id, account);
			}
		}

		if (saved.businesses != null) {
			for (SavedBusiness s : saved.businesses) {
				Job job = Job.parse(s.job);

				if (job == null) {
					continue;
				}

				Business business = new Business(s.id, job, UUID.fromString(s.owner));

				if (s.hasPlot) {
					// 0.2.0 did not save the size; every plot was 16 then.
					business.setPlot(new Plot(s.plotX, s.plotZ, s.plotSize > 0 ? s.plotSize : 16));
				}

				business.setLifetimeSales(s.lifetimeSales);
				business.setTaskDoneToday(s.taskDoneToday);

				if (s.shopSigns != null) {
					business.shopSigns.addAll(s.shopSigns);
				}

				if (s.allowed != null) {
					s.allowed.forEach(who -> business.allowed.add(UUID.fromString(who)));
				}

				// Shops opened before 0.3 count as opened, so old shops do not all get Grand Openings.
				business.everOpened = s.everOpened || saved.version < 3;
				economy.businessMap().put(business.id(), business);
				economy.setNextBusinessId(Math.max(economy.nextBusinessId(), business.id() + 1));

				if (s.agreedBuyer != null) {
					economy.agreedSaleMap().put(business.id(), UUID.fromString(s.agreedBuyer));
				}
			}
		}

		if (saved.houses != null) {
			for (SavedHouse s : saved.houses) {
				UUID owner = UUID.fromString(s.owner);
				economy.houseMap().put(owner, new House(owner, s.index, new Plot(s.x, s.z, Rules.HOUSE_SIZE)));
			}
		}

		if (saved.town != null) {
			readTown(economy.town, saved.town);
		} else {
			// Worlds from 0.2.0 hold their first election on the next day.
			economy.town.nextElectionDay = saved.day + 1;
		}

		return economy;
	}

	private static void readTown(Town town, SavedTown s) {
		if (s.orders != null) {
			for (SavedOrder o : s.orders) {
				Town.Order order = new Town.Order();
				order.id = o.id;
				order.job = Job.parse(o.job);
				order.item = o.item;
				order.count = o.count;
				order.pay = o.pay;
				order.passenger = o.passenger;
				order.destination = o.destination;
				order.tourist = o.tourist;
				order.filledBy = uuid(o.filledBy);
				town.orders.add(order);
			}
		}

		town.nextOrderId = Math.max(1, s.nextOrderId);
		town.event = TownEvent.parse(s.event);
		town.eventEndsDay = s.eventEndsDay;

		if (s.eventData != null) {
			town.eventData.putAll(s.eventData);
		}

		town.newShopOwner = uuid(s.newShopOwner);
		town.grandOpeningOwner = uuid(s.grandOpeningOwner);
		town.mayor = uuid(s.mayor);
		town.nextElectionDay = s.nextElectionDay;
		town.lastStartedDay = s.lastStartedDay == null ? -1 : s.lastStartedDay;
		town.squareX = s.squareX;
		town.squareY = s.squareY;
		town.squareZ = s.squareZ;
		town.squareBuilt = s.squareBuilt;
		town.clerkId = s.clerkId;
		town.mayorTaxRate = s.mayorTaxRate;
		town.holidayUsed = s.holidayUsed;
		town.holidayDay = s.holidayDay;

		if (s.townName != null) {
			town.townName = s.townName;
		}

		town.mayorAnnouncement = s.mayorAnnouncement;

		if (s.marketChoices != null) {
			for (String id : s.marketChoices) {
				TownEvent event = TownEvent.parse(id);

				if (event != null) {
					town.marketChoices.add(event);
				}
			}
		}

		town.marketPick = TownEvent.parse(s.marketPick);

		if (s.candidates != null) {
			s.candidates.forEach((who, promise) -> town.candidates.put(UUID.fromString(who), promise));
		}

		if (s.votes != null) {
			s.votes.forEach((voter, choice) -> town.votes.put(UUID.fromString(voter), UUID.fromString(choice)));
		}
	}
}
