package com.goldbucks.economy;

/** A rectangle of land, given by its lowest corner and its width (x) and depth (z). */
public record Plot(int minX, int minZ, int size, int depth) {
	/** A square plot. */
	public Plot(int minX, int minZ, int size) {
		this(minX, minZ, size, size);
	}

	public boolean contains(int x, int z) {
		return x >= minX && x < minX + size && z >= minZ && z < minZ + depth;
	}

	public boolean overlaps(Plot other) {
		return minX < other.minX + other.size && other.minX < minX + size
				&& minZ < other.minZ + other.depth && other.minZ < minZ + depth;
	}

	public int centerX() {
		return minX + size / 2;
	}

	public int centerZ() {
		return minZ + depth / 2;
	}

	/** The plot of the standard size centred on a block. */
	public static Plot around(int x, int z) {
		return around(x, z, Rules.plotSize());
	}

	public static Plot around(int x, int z, int size) {
		return new Plot(x - size / 2, z - size / 2, size);
	}
}
