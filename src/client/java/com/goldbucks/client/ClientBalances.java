package com.goldbucks.client;

import java.util.List;

import com.goldbucks.net.BalancesPayload;

/** The latest side bar contents received from the server. */
public final class ClientBalances {
	private static volatile List<BalancesPayload.Entry> entries = List.of();

	private ClientBalances() {
	}

	public static List<BalancesPayload.Entry> entries() {
		return entries;
	}

	public static void set(List<BalancesPayload.Entry> newEntries) {
		entries = List.copyOf(newEntries);
	}

	public static void clear() {
		entries = List.of();
	}
}
