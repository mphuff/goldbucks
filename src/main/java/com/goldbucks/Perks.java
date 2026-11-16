package com.goldbucks;

import com.goldbucks.economy.Job;
import com.goldbucks.economy.Level;

/** What each job's levels unlock, in words for the player. The rules themselves live where they apply. */
public final class Perks {
	private Perks() {
	}

	public static String describe(Job job, Level level) {
		int index = switch (level) {
			case SKILLED -> 0;
			case EXPERT -> 1;
			case MASTER -> 2;
			default -> -1;
		};

		if (index < 0) {
			return "";
		}

		String[] perks = switch (job) {
			case VEHICLE_DEALER -> new String[] {"An extra vehicle order on the board each day.",
					"Townsfolk pay up to twice as much for your vehicles.", "You may price vehicles up to $60."};
			case STORE_OWNER -> new String[] {"One more townsfolk visit each day.",
					"Write x2, x3 or x4 on line 3 of a shop sign to sell a bundle.", "Two more townsfolk visits each day."};
			case COOK -> new String[] {"Food you cook fills one more hunger point.", "An extra Cook order each day.",
					"Food you cook also gives 10 seconds of Speed."};
			case SALESPERSON -> new String[] {"Townsfolk you sell to buy a second item if you have it.",
					"The town adds $1 for each sale to a player you have not sold to today.", "The town adds 10% to every townsfolk sale."};
			case TAXI_DRIVER -> new String[] {"An extra passenger each day.", "Passenger fares pay 50% more.",
					"Passenger fares pay double when it rains (on Rainy Day Rides)."};
			case LIBRARIAN -> new String[] {"An extra book order each day.", "Townsfolk pay up to twice as much for written books.",
					"You may price enchanted books up to $60."};
			case BUILDER -> new String[] {"An extra Builder order each day.", "The town adds 10% to every bill paid to you.",
					"Town builds during events pay double."};
			case FARMER -> new String[] {"An extra Farm order each day.", "Crops on your plot grow faster.",
					"Animals on your plot are ready to breed again sooner."};
			case BANKER -> new String[] {"The town pays you $1 more per deposit or withdrawal.", "Savings earn 1% more interest.",
					"Each player can earn $15 more interest a week."};
		};

		return perks[index];
	}
}
