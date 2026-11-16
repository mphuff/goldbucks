package com.goldbucks.economy;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** A job together with its plot and sales history. It can change owner. */
public final class Business {
	private final int id;
	private final Job job;
	private UUID owner;
	private Plot plot;
	private int lifetimeSales;
	private boolean taskDoneToday;
	/** Shop signs on the plot, as packed block positions, so townsfolk know where to go. */
	final Set<Long> shopSigns = new LinkedHashSet<>();
	/** Players the owner lets build on the plot. */
	final Set<UUID> allowed = new LinkedHashSet<>();
	/** Has any shop of this business ever opened; the first one starts a Grand Opening. */
	boolean everOpened;

	Business(int id, Job job, UUID owner) {
		this.id = id;
		this.job = job;
		this.owner = owner;
	}

	public int id() {
		return id;
	}

	public Job job() {
		return job;
	}

	public UUID owner() {
		return owner;
	}

	void setOwner(UUID owner) {
		this.owner = owner;
		allowed.clear();
	}

	/** Null until the owner claims a spot. */
	public Plot plot() {
		return plot;
	}

	void setPlot(Plot plot) {
		this.plot = plot;
	}

	public int lifetimeSales() {
		return lifetimeSales;
	}

	void addSales(int amount) {
		lifetimeSales += amount;
	}

	void setLifetimeSales(int amount) {
		lifetimeSales = amount;
	}

	public boolean taskDoneToday() {
		return taskDoneToday;
	}

	void setTaskDoneToday(boolean done) {
		taskDoneToday = done;
	}

	public Set<Long> shopSigns() {
		return Collections.unmodifiableSet(shopSigns);
	}

	public Set<UUID> allowed() {
		return Collections.unmodifiableSet(allowed);
	}

	/** What the game charges to buy this business. */
	public int price() {
		return Rules.businessBasePrice() + lifetimeSales;
	}
}
