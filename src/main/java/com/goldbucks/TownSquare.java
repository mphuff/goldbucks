package com.goldbucks;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.levelgen.Heightmap;

import com.goldbucks.economy.Economy;
import com.goldbucks.economy.Town;

/**
 * The town square near spawn: a stone plaza with the Town Board (a lectern), the Town Clerk who runs
 * the store, the Taxi Stand, the Mayor's podium, a fountain and lanterns. Only the host can change it.
 */
public final class TownSquare {
	/** Half the width of the square: it runs from centre - 7 to centre + 7. */
	public static final int HALF = 7;
	public static final String CLERK_TAG = "goldbucks_clerk";
	private static final int QUIET = 2;
	/** Where the square's centre goes, measured from the world spawn. */
	private static int offsetX = -14;
	private static int offsetZ = 12;

	private TownSquare() {
	}

	/** Moves the square. The automated tests use this to keep it clear of the test area. */
	public static void setOffset(int dx, int dz) {
		offsetX = dx;
		offsetZ = dz;
	}

	public static BlockPos center(Town town) {
		return new BlockPos(town.squareX, town.squareY, town.squareZ);
	}

	public static boolean contains(Town town, BlockPos pos) {
		return town.squareBuilt && Math.abs(pos.getX() - town.squareX) <= HALF && Math.abs(pos.getZ() - town.squareZ) <= HALF
				&& pos.getY() >= town.squareY - 2 && pos.getY() <= town.squareY + 12;
	}

	public static BlockPos board(Town town) {
		return center(town).offset(0, 1, -5);
	}

	public static BlockPos clerkSpot(Town town) {
		return center(town).offset(5, 1, 0);
	}

	public static BlockPos taxiStand(Town town) {
		return center(town).offset(-5, 1, 5);
	}

	public static BlockPos podiumSign(Town town) {
		return center(town).offset(-5, 2, -5);
	}

	/** The fences and lanterns at the corners, which storms can knock over. */
	public static List<BlockPos> lanternPosts(Town town) {
		BlockPos c = center(town);
		return List.of(c.offset(-6, 1, -6), c.offset(6, 1, -6), c.offset(-6, 1, 6), c.offset(6, 1, 6),
				c.offset(-6, 2, -6), c.offset(6, 2, -6), c.offset(-6, 2, 6), c.offset(6, 2, 6));
	}

	/** The corner of the area to fill during Construction Boom (5 by 5, one block above the floor). */
	public static BlockPos constructionCorner(Town town) {
		return center(town).offset(1, 1, 1);
	}

	/** Builds the square if this world does not have one yet. Older worlds get theirs on first load. */
	public static void ensureBuilt(MinecraftServer server) {
		Economy economy = EconomyService.economy(server);
		Town town = economy.town();
		ServerLevel level = server.overworld();

		if (!town.squareBuilt) {
			BlockPos spawn = level.getRespawnData().pos();
			int[] spot = drySpot(level, spawn.getX() + offsetX, spawn.getZ() + offsetZ);
			int x = spot[0];
			int z = spot[1];
			level.getChunk(x >> 4, z >> 4);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
			town.squareX = x;
			town.squareY = y;
			town.squareZ = z;
			town.squareBuilt = true;
			build(level, town);
			EconomyService.changed(server);
		}

		ensureClerk(server);
	}

	/**
	 * The nearest dry spot for the square, looking outwards from the planned centre in steps of 16 blocks
	 * (away from the neighborhood, which is to the east). Falls back to the planned centre.
	 */
	static int[] drySpot(ServerLevel level, int x, int z) {
		int[][] steps = {{0, 0}, {-16, 0}, {0, 16}, {0, -16}, {-16, 16}, {-16, -16}, {-32, 0}, {0, 32}, {0, -32},
				{-32, 16}, {-32, -16}, {-32, 32}, {-32, -32}, {-48, 0}, {-48, 16}, {-48, -16}, {-48, 32}, {-48, -32}};

		for (int[] step : steps) {
			int cx = x + step[0];
			int cz = z + step[1];

			if (LandService.dryLand(level, cx - HALF, cz - HALF, HALF * 2 + 1, HALF * 2 + 1)) {
				return new int[] {cx, cz};
			}
		}

		return new int[] {x, z};
	}

	static void build(ServerLevel level, Town town) {
		BlockPos c = center(town);

		for (int dx = -HALF - 1; dx <= HALF + 1; dx++) {
			for (int dz = -HALF - 1; dz <= HALF + 1; dz++) {
				level.getChunk((c.getX() + dx) >> 4, (c.getZ() + dz) >> 4);
			}
		}

		BlockState floor = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState path = Blocks.POLISHED_ANDESITE.defaultBlockState();

		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				boolean cross = dx == 0 || dz == 0;
				level.setBlock(c.offset(dx, 0, dz), cross ? path : floor, QUIET);

				// Fill any dip or water below the floor, so the square does not float.
				for (int dy = -1; dy >= -40 && level.getBlockState(c.offset(dx, dy, dz)).canBeReplaced(); dy--) {
					level.setBlock(c.offset(dx, dy, dz), Blocks.DIRT.defaultBlockState(), QUIET);
				}

				for (int dy = 1; dy <= 8; dy++) {
					level.setBlock(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), QUIET);
				}
			}
		}

		// Fountain in the middle.
		level.setBlock(c, Blocks.WATER.defaultBlockState(), 3);

		for (Direction side : Direction.Plane.HORIZONTAL) {
			level.setBlock(c.relative(side).above(), Blocks.STONE_BRICK_SLAB.defaultBlockState(), QUIET);
		}

		// The Town Board: a lectern in front of a little wall of signs, with the town bell beside it.
		BlockPos board = board(town);
		level.setBlock(board, Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), 3);
		level.setBlock(board.east(), Blocks.BELL.defaultBlockState(), 3);

		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = 0; dy <= 1; dy++) {
				level.setBlock(board.offset(dx, dy, -1), Blocks.SPRUCE_PLANKS.defaultBlockState(), QUIET);
			}
		}

		wallSign(level, board.offset(0, 1, 0), "TOWN BOARD", "Right-click the", "lectern for", "today's orders");

		// The Town Store stall.
		BlockPos clerk = clerkSpot(town);
		level.setBlock(clerk.west(), Blocks.BARREL.defaultBlockState(), 3);
		level.setBlock(clerk.east(), Blocks.SPRUCE_PLANKS.defaultBlockState(), QUIET);
		level.setBlock(clerk.east().above(), Blocks.SPRUCE_PLANKS.defaultBlockState(), QUIET);
		wallSignFacing(level, clerk.above(), Direction.WEST, "TOWN STORE", "Right-click", "the Town Clerk", "or type /store");

		// The Taxi Stand.
		BlockPos taxi = taxiStand(town);
		level.setBlock(taxi, Blocks.OAK_FENCE.defaultBlockState(), 3);
		standingSign(level, taxi.above(), "TAXI STAND", "Passengers", "wait here", "");

		// The Mayor's podium.
		BlockPos podium = podiumSign(town).below();
		level.setBlock(podium, Blocks.SPRUCE_PLANKS.defaultBlockState(), QUIET);
		standingSign(level, podium.above(), "MAYOR", "(none yet)", "", "");

		buildLanterns(level, town);
	}

	/** Puts the corner lantern posts back. */
	static void buildLanterns(ServerLevel level, Town town) {
		List<BlockPos> posts = lanternPosts(town);

		for (int i = 0; i < 4; i++) {
			level.setBlock(posts.get(i), Blocks.OAK_FENCE.defaultBlockState(), 3);
			level.setBlock(posts.get(i + 4), Blocks.LANTERN.defaultBlockState(), 3);
		}
	}

	static void wallSign(ServerLevel level, BlockPos pos, String... lines) {
		wallSignFacing(level, pos, Direction.SOUTH, lines);
	}

	static void wallSignFacing(ServerLevel level, BlockPos pos, Direction facing, String... lines) {
		level.setBlock(pos, Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing), 3);
		writeSign(level, pos, lines);
	}

	static void standingSign(ServerLevel level, BlockPos pos, String... lines) {
		level.setBlock(pos, Blocks.SPRUCE_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 0), 3);
		writeSign(level, pos, lines);
	}

	public static void writeSign(ServerLevel level, BlockPos pos, String... lines) {
		if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
			java.util.List<Component> text = new java.util.ArrayList<>();

			for (int i = 0; i < 4; i++) {
				text.add(Component.literal(i < lines.length ? lines[i] : ""));
			}

			SignText signText = new SignText(text, text, DyeColor.BLACK, true);
			sign.setText(signText, SignTextSlot.FRONT);
			sign.setText(signText, SignTextSlot.BACK);
			sign.setWaxed(true);
			sign.setChanged();
			level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
		}
	}

	/** Makes sure the Town Clerk is standing at the stall. */
	public static void ensureClerk(MinecraftServer server) {
		Town town = EconomyService.economy(server).town();
		ServerLevel level = server.overworld();
		BlockPos spot = clerkSpot(town);

		if (town.clerkId != null) {
			// An existing clerk can only be seen (and so checked) while the square's entities are loaded.
			if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(spot))) {
				return;
			}

			Entity existing = level.getEntity(UUID.fromString(town.clerkId));

			if (existing != null && existing.isAlive()) {
				return;
			}
		}

		level.getChunk(spot.getX() >> 4, spot.getZ() >> 4);
		Villager clerk = EntityTypes.VILLAGER.create(level, EntitySpawnReason.MOB_SUMMONED);

		if (clerk == null) {
			return;
		}

		clerk.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 90f, 0f);
		clerk.setYHeadRot(90f);
		clerk.setCustomName(Component.literal("Town Clerk"));
		clerk.setCustomNameVisible(true);
		Townspeople.makeHarmless(clerk);
		clerk.setNoAi(true);
		clerk.addTag(CLERK_TAG);
		// Remember the new clerk first, so the clean-up of stray clerks leaves this one alone.
		town.clerkId = clerk.getUUID().toString();
		level.addFreshEntity(clerk);
		EconomyService.changed(server);
	}

	/** Updates the Mayor's sign on the podium. */
	public static void updatePodium(MinecraftServer server) {
		Economy economy = EconomyService.economy(server);
		Town town = economy.town();

		if (!town.squareBuilt) {
			return;
		}

		String mayor = town.mayor() == null ? "(none yet)" : economy.account(town.mayor()).name();
		String line3 = "";
		String line4 = "";

		if (town.electionOpen()) {
			line3 = "Election today!";
			line4 = "/vote";
		} else if (town.mayorTaxRate() >= 0) {
			line3 = "Tax: " + economy.taxRate() + "%";
		}

		writeSign(server.overworld(), podiumSign(town), "MAYOR", mayor, line3, line4);
	}
}
