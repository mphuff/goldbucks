package com.goldbucks.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** How a townsfolk customer decides what to buy. */
public final class Townsfolk {
	private Townsfolk() {
	}

	/** One shop sign as a customer sees it. */
	public record Offer(int index, String itemId, int price, boolean inStock, double value, double payMultiplier) {
		/** The most a customer will pay for this item. */
		public double mostTheyWillPay(Settings settings) {
			return value * settings.fairPricePercent / 100.0 * payMultiplier;
		}

		public boolean fair(Settings settings) {
			return value > 0 && price <= mostTheyWillPay(settings);
		}

		/** Priced at or below the item's value. */
		public boolean bargain() {
			return value > 0 && price <= value * payMultiplier;
		}
	}

	/** What a customer did: bought from an offer, or why not. */
	public record Choice(Offer bought, Offer tooPricey, Offer empty) {
		public boolean sale() {
			return bought != null;
		}
	}

	/**
	 * Picks the shop a customer buys from: the cheapest (for its value) of the in-stock, fairly priced offers.
	 * When nothing qualifies, says which shop was too pricey or empty, so the owner can be told.
	 * {@code bargainsOnly} is for Big Sale Day.
	 */
	public static Choice choose(List<Offer> offers, Settings settings, boolean bargainsOnly) {
		List<Offer> good = new ArrayList<>();
		Offer tooPricey = null;
		Offer empty = null;

		for (Offer offer : offers) {
			if (offer.value() <= 0) {
				continue;
			}

			if (!offer.inStock()) {
				empty = empty == null ? offer : empty;
			} else if (!offer.fair(settings) || (bargainsOnly && !offer.bargain())) {
				tooPricey = tooPricey == null ? offer : tooPricey;
			} else {
				good.add(offer);
			}
		}

		if (good.isEmpty()) {
			return new Choice(null, tooPricey, empty);
		}

		good.sort(Comparator.comparingDouble(offer -> offer.price() / offer.value()));
		return new Choice(good.get(0), null, null);
	}
}
