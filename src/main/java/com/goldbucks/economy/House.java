package com.goldbucks.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A player's starting house in the neighborhood. */
public record House(UUID owner, int index, Plot lot) {
	/** The house and the row of blocks along its front (south) wall, where the doorstep and name sign are. */
	private Plot core() {
		return new Plot(lot.minX(), lot.minZ(), lot.size(), lot.size() + 1);
	}

	/** The land around the house that an upgrade adds, or null if it adds none. */
	public static Plot upgradeArea(Plot lot, StoreKind upgrade) {
		int size = lot.size();
		return switch (upgrade) {
			case PORCH -> new Plot(lot.minX(), lot.minZ() + size + 1, size, 3);
			case GARDEN -> new Plot(lot.minX(), lot.minZ() - 7, size, 7);
			case STABLE -> new Plot(lot.minX() - 5, lot.minZ(), 5, size);
			default -> null;
		};
	}

	/** Every piece of land this house covers, given the upgrades its owner has bought. */
	public List<Plot> areas(Set<String> owned) {
		List<Plot> areas = new ArrayList<>();
		areas.add(core());

		for (StoreKind upgrade : new StoreKind[] {StoreKind.PORCH, StoreKind.GARDEN, StoreKind.STABLE}) {
			if (owned.contains(upgrade.id())) {
				areas.add(upgradeArea(lot, upgrade));
			}
		}

		return areas;
	}

	public boolean covers(int x, int z) {
		return core().contains(x, z);
	}

	public boolean overlaps(Plot other) {
		return core().overlaps(other);
	}
}
