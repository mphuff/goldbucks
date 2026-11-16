package com.goldbucks.economy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

final class Fixtures {
	static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000001");
	static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000002");
	static final UUID KAI = UUID.fromString("00000000-0000-0000-0000-000000000003");
	static final Set<UUID> EVERYONE = Set.of(ALEX, STEVE, KAI);

	private Fixtures() {
	}

	/** Three players, each with $20 and no job. */
	static Economy threePlayers() {
		Economy economy = new Economy();
		economy.join(ALEX, "Alex");
		economy.join(STEVE, "Steve");
		economy.join(KAI, "Kai");
		return economy;
	}

	static int businessId(Economy economy, UUID owner) {
		return economy.businessesOf(owner).get(0).id();
	}

	static String resource(String name) {
		try (InputStream in = Fixtures.class.getResourceAsStream("/data/goldbucks/town/" + name)) {
			if (in == null) {
				throw new IllegalStateException("Missing data file " + name);
			}

			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The town data shipped in the mod. */
	static TownData shippedData() {
		return TownData.parse(resource("item_values.json"), resource("store.json"), resource("orders.json"), resource("events.json"));
	}

	/** Default settings and the shipped data, as a fresh world would have. */
	static void freshWorld() {
		Settings.reset();
		TownData.activate(shippedData());
	}

	/** Every player works for this many days in their jobs. */
	static void workDays(Economy economy, int days) {
		for (int i = 0; i < days; i++) {
			for (Business business : economy.businesses()) {
				economy.completeTask(business.owner(), business.job());
			}

			economy.endDay(EVERYONE, new java.util.Random(i));
		}
	}
}
