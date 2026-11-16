package com.goldbucks.economy;

/** The outcome of something a player tried to do, with a message to show them when it failed. */
public enum Result {
	OK,
	NO_ACCOUNT,
	PRICE_TOO_HIGH,
	BAD_PRICE,
	OVER_CREDIT_LIMIT,
	OWN_SHOP,
	JOB_TAKEN,
	JOB_CLOSED,
	ALREADY_HAS_JOB,
	NO_BUSINESS,
	NOT_OWNER,
	ALREADY_CLAIMED,
	OVERLAPS,
	NOT_FOR_SALE,
	NO_SUCH_ITEM,
	ALREADY_OWNED,
	NEEDS_OTHER_ITEM,
	NO_SUCH_ORDER,
	ORDER_TAKEN,
	ORDER_LIMIT,
	NOT_ENOUGH_SAVINGS,
	IN_DEBT,
	BAD_AMOUNT,
	NOT_A_CANDIDATE,
	NO_ELECTION,
	SELF_VOTE,
	ALREADY_VOTED,
	NOT_MAYOR,
	OUT_OF_RANGE,
	ALREADY_USED;

	public boolean ok() {
		return this == OK;
	}

	public String message() {
		return switch (this) {
			case OK -> "";
			case NO_ACCOUNT -> "That player has no account.";
			case PRICE_TOO_HIGH -> "Nothing may cost more than $" + Rules.priceCap() + ".";
			case BAD_PRICE -> "The price must be at least $1.";
			case OVER_CREDIT_LIMIT -> "That would put you more than $" + Rules.creditLimit() + " in debt.";
			case OWN_SHOP -> "You cannot buy from your own shop.";
			case JOB_TAKEN -> "Someone already has that job.";
			case JOB_CLOSED -> "The host has switched that job off.";
			case ALREADY_HAS_JOB -> "You already have a job.";
			case NO_BUSINESS -> "There is no such business.";
			case NOT_OWNER -> "That is not yours.";
			case ALREADY_CLAIMED -> "This business already has a plot.";
			case OVERLAPS -> "That spot overlaps someone else's land.";
			case NOT_FOR_SALE -> "The owner has not agreed to sell.";
			case NO_SUCH_ITEM -> "The store does not sell that.";
			case ALREADY_OWNED -> "You already have that.";
			case NEEDS_OTHER_ITEM -> "You need to buy something else first.";
			case NO_SUCH_ORDER -> "That order is gone.";
			case ORDER_TAKEN -> "Someone already filled that order.";
			case ORDER_LIMIT -> "You have filled as many orders as you can today.";
			case NOT_ENOUGH_SAVINGS -> "You do not have that much saved.";
			case IN_DEBT -> "You cannot save while you are in debt.";
			case BAD_AMOUNT -> "That amount does not work.";
			case NOT_A_CANDIDATE -> "That player is not running for Mayor.";
			case NO_ELECTION -> "There is no election right now.";
			case SELF_VOTE -> "You cannot vote for yourself.";
			case ALREADY_VOTED -> "You have already voted.";
			case NOT_MAYOR -> "Only the Mayor can do that.";
			case OUT_OF_RANGE -> "That number is outside what is allowed.";
			case ALREADY_USED -> "That has already been used this term.";
		};
	}
}
