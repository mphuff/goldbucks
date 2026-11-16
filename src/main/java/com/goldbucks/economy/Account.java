package com.goldbucks.economy;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One player's record. Balance is in whole Goldbucks and is negative when in debt. */
public final class Account {
	private final UUID id;
	private String name;
	private int balance;
	private int savings;
	/** Store items owned for good (house upgrades, land, pets, style), by store id. */
	final Set<String> owned = new LinkedHashSet<>();
	/** What the owned items cost, which counts toward net worth. */
	int propertyValue;
	String nameColour;
	final Map<Job, Integer> daysWorked = new EnumMap<>(Job.class);
	int streak;
	int lastWorkedDay = -1;
	final Set<String> achievements = new LinkedHashSet<>();
	/** Running counts used by achievements: sales, townsfolk sales, orders, passengers, events and so on. */
	final Map<String, Integer> stats = new LinkedHashMap<>();
	/** Players allowed to build in this player's house area. */
	final Set<UUID> homeAllowed = new LinkedHashSet<>();
	/** Pet kind -> the pet animal's id in the world. */
	final Map<String, UUID> pets = new LinkedHashMap<>();
	/** Things that happened while the player was offline, for the login summary. */
	int awaySales;
	int awayMoney;
	boolean awayEmptyShop;
	// Today's counters, cleared each day.
	int ordersToday;
	int advertsToday;
	int salesToday;
	int builderBlocksToday;
	final Set<UUID> soldToToday = new LinkedHashSet<>();
	/** Savings added on Interest Day count twice at the next interest payment. */
	int interestDayDeposits;
	/** A Herald ad bought today, printed tomorrow. */
	String heraldAd;

	Account(UUID id, String name, int balance) {
		this.id = id;
		this.name = name;
		this.balance = balance;
	}

	public UUID id() {
		return id;
	}

	public String name() {
		return name;
	}

	void rename(String name) {
		this.name = name;
	}

	public int balance() {
		return balance;
	}

	void setBalance(int balance) {
		this.balance = balance;
	}

	public boolean inDebt() {
		return balance < 0;
	}

	public int debt() {
		return Math.max(0, -balance);
	}

	void add(int amount) {
		balance += amount;
	}

	/** Whether the player could pay this much without passing the credit limit. */
	public boolean canSpend(int amount) {
		return balance - amount >= -Rules.creditLimit();
	}

	public int savings() {
		return savings;
	}

	void setSavings(int savings) {
		this.savings = savings;
	}

	public Set<String> owned() {
		return java.util.Collections.unmodifiableSet(owned);
	}

	public boolean owns(String storeId) {
		return owned.contains(storeId);
	}

	public int propertyValue() {
		return propertyValue;
	}

	public String nameColour() {
		return nameColour;
	}

	public int daysWorked(Job job) {
		return daysWorked.getOrDefault(job, 0);
	}

	public Level level(Job job) {
		return Level.forDays(daysWorked(job));
	}

	public int streak() {
		return streak;
	}

	public Set<String> achievements() {
		return java.util.Collections.unmodifiableSet(achievements);
	}

	public int stat(String key) {
		return stats.getOrDefault(key, 0);
	}

	void addStat(String key, int amount) {
		stats.merge(key, amount, Integer::sum);
	}

	public Set<UUID> homeAllowed() {
		return java.util.Collections.unmodifiableSet(homeAllowed);
	}

	public Map<String, UUID> pets() {
		return java.util.Collections.unmodifiableMap(pets);
	}

	public int awaySales() {
		return awaySales;
	}

	public int awayMoney() {
		return awayMoney;
	}

	public boolean awayEmptyShop() {
		return awayEmptyShop;
	}

	public int ordersToday() {
		return ordersToday;
	}

	public int advertsToday() {
		return advertsToday;
	}

	public int salesToday() {
		return salesToday;
	}

	public int builderBlocksToday() {
		return builderBlocksToday;
	}

	public String heraldAd() {
		return heraldAd;
	}

	void newDay() {
		ordersToday = 0;
		advertsToday = 0;
		salesToday = 0;
		builderBlocksToday = 0;
		soldToToday.clear();
	}

	/** Everything the side bar adds up: cash, savings and property. Businesses are added by the economy. */
	int ownWorth() {
		return balance + savings + propertyValue;
	}
}
