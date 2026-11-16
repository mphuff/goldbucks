package com.goldbucks;

import java.util.UUID;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import com.goldbucks.economy.Account;
import com.goldbucks.economy.Business;
import com.goldbucks.economy.Economy;
import com.goldbucks.economy.House;
import com.goldbucks.economy.Plot;
import com.goldbucks.economy.Result;
import com.goldbucks.economy.Rules;

/** Land in the running game: business plots, starting houses, and keeping strangers from building on them. */
public final class LandService {
	/** Where the neighborhood's first house stands, measured from the world spawn. */
	private static int neighborhoodDx = 8;
	private static int neighborhoodDz = 8;

	private LandService() {
	}

	/** Moves the neighborhood. The automated tests use this to keep houses clear of the test area. */
	public static void setNeighborhoodOffset(int dx, int dz) {
		neighborhoodDx = dx;
		neighborhoodDz = dz;
	}

	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(LandService::tick);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> arrivals.clear());

		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!Activation.active() || !(player instanceof ServerPlayer serverPlayer) || mayBuild(serverPlayer, level, pos)) {
				return true;
			}

			refuse(serverPlayer, level, pos);
			return false;
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !Activation.active() || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}

			BlockPos clicked = hit.getBlockPos();
			InteractionResult shop = ShopService.onUseBlock(serverPlayer, level, hand, clicked);

			if (shop != null) {
				return shop;
			}

			InteractionResult bank = BankService.onUseBlock(serverPlayer, level, clicked);

			if (bank != null) {
				return bank;
			}

			com.goldbucks.economy.Town town = EconomyService.economy(level.getServer()).town();

			if (level.dimension() == Level.OVERWORLD && town.squareBuilt && clicked.equals(TownSquare.board(town)) && !serverPlayer.isSecondaryUseActive()) {
				BoardService.open(serverPlayer);
				return InteractionResult.SUCCESS;
			}

			if (level.dimension() == Level.OVERWORLD && EventService.onOpen(serverPlayer, clicked)) {
				return InteractionResult.PASS;
			}

			BlockPos placing = clicked.relative(hit.getDirection());
			boolean clickedOk = mayBuild(serverPlayer, level, clicked);
			boolean placingOk = mayBuild(serverPlayer, level, placing);

			if (clickedOk && placingOk) {
				return InteractionResult.PASS;
			}

			// Visitors may still open doors and press buttons, so shops can have customers.
			if (!clickedOk && isDoorLike(level.getBlockState(clicked)) && !serverPlayer.isSecondaryUseActive()) {
				return InteractionResult.PASS;
			}

			refuse(serverPlayer, level, clickedOk ? placing : clicked);
			return InteractionResult.FAIL;
		});

		Events.JOB_CHOSEN.add(player -> Text.tell(player, Component.literal("Stand where you want your business and click ")
				.append(Text.button("Claim this spot", "/plot claim"))
				.append(". Your plot will be " + Rules.plotSize() + " by " + Rules.plotSize() + " blocks around you.")));

		WorldRules.openCommand("plot");
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("plot")
						.executes(context -> {
							showLand(context.getSource().getPlayerOrException());
							return 1;
						})
						.then(Commands.literal("claim").executes(context ->
								claim(context.getSource().getPlayerOrException()).ok() ? 1 : 0))
						.then(Commands.literal("allow").then(Commands.argument("player", EntityArgument.player()).executes(context ->
								allow(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player"), true, false))))
						.then(Commands.literal("disallow").then(Commands.argument("player", EntityArgument.player()).executes(context ->
								allow(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player"), false, false))))));

		WorldRules.openCommand("bill");
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("home")
					.then(Commands.literal("allow").then(Commands.argument("player", EntityArgument.player()).executes(context ->
							allow(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player"), true, true))))
					.then(Commands.literal("disallow").then(Commands.argument("player", EntityArgument.player()).executes(context ->
							allow(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player"), false, true)))));

			dispatcher.register(Commands.literal("bill")
					.then(Commands.literal("pay").then(Commands.argument("bill", IntegerArgumentType.integer(1)).executes(context ->
							payBill(context.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(context, "bill")))))
					.then(Commands.argument("player", EntityArgument.player())
							.then(Commands.argument("amount", IntegerArgumentType.integer(1, 100000))
									.then(Commands.argument("what", StringArgumentType.greedyString()).executes(context ->
											sendBill(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player"),
													IntegerArgumentType.getInteger(context, "amount"), StringArgumentType.getString(context, "what")))))));
		});
	}

	private static int allow(ServerPlayer owner, ServerPlayer builder, boolean allow, boolean home) {
		MinecraftServer server = owner.level().getServer();
		Economy economy = EconomyService.economy(server);
		Result result = home ? economy.allowAtHome(owner.getUUID(), builder.getUUID(), allow)
				: economy.allowOnPlots(owner.getUUID(), builder.getUUID(), allow);

		if (!result.ok()) {
			Text.warn(owner, result == Result.NO_BUSINESS ? "You have no plot yet." : "You cannot do that for that player.");
			return 0;
		}

		EconomyService.changed(server);
		String where = home ? "your house" : "your plots";
		String name = builder.getGameProfile().name();
		Text.tell(owner, allow ? name + " can now build on " + where + "." : name + " can no longer build on " + where + ".");
		Text.tell(builder, owner.getGameProfile().name() + (allow ? " lets you build on their " : " no longer lets you build on their ")
				+ (home ? "house." : "plots."));
		return 1;
	}

	private static int sendBill(ServerPlayer from, ServerPlayer to, int amount, String what) {
		MinecraftServer server = from.level().getServer();
		Economy.Bill bill = EconomyService.economy(server).sendBill(from.getUUID(), to.getUUID(), amount, what);

		if (bill == null) {
			Text.warn(from, "That bill cannot be sent.");
			return 0;
		}

		Text.tell(from, "You sent " + to.getGameProfile().name() + " a bill for " + Text.money(amount) + ".");
		Text.tell(to, Component.literal(from.getGameProfile().name() + " sent you a bill for " + Text.money(amount) + " (" + what + "). ")
				.append(Text.button("Pay it", "/bill pay " + bill.id())));
		return 1;
	}

	private static int payBill(ServerPlayer payer, int id) {
		MinecraftServer server = payer.level().getServer();
		Economy economy = EconomyService.economy(server);
		Economy.Bill bill = economy.bill(id);
		Result result = economy.payBill(payer.getUUID(), id);

		if (!result.ok()) {
			Text.warn(payer, result == Result.NO_SUCH_ORDER ? "There is no such bill for you." : result.message());
			return 0;
		}

		EconomyService.changed(server);
		NameTags.refresh(server);
		Text.tell(payer, "You paid the bill: " + Text.money(bill.amount()) + ".");
		ServerPlayer builder = server.getPlayerList().getPlayer(bill.from());

		if (builder != null) {
			Notices.sale(builder, payer.getGameProfile().name() + " paid your bill: " + Text.money(bill.amount()) + ".", false);
		}

		return 1;
	}

	private static boolean isDoorLike(BlockState state) {
		return state.is(BlockTags.DOORS) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.TRAPDOORS)
				|| state.is(BlockTags.BUTTONS);
	}

	/** Who owns the land at this block, or null. Only the overworld has owned land. */
	public static UUID ownerAt(Level level, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD || level.getServer() == null) {
			return null;
		}

		return EconomyService.economy(level.getServer()).landOwnerAt(pos.getX(), pos.getZ());
	}

	public static boolean mayBuild(ServerPlayer player, Level level, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD || level.getServer() == null) {
			return true;
		}

		Economy economy = EconomyService.economy(level.getServer());

		if (TownSquare.contains(economy.town(), pos)) {
			return WorldRules.isHost(level.getServer(), player) || EventService.siteOpen(economy.town(), pos);
		}

		return economy.mayBuild(player.getUUID(), pos.getX(), pos.getZ());
	}

	private static void refuse(ServerPlayer player, Level level, BlockPos pos) {
		Economy economy = EconomyService.economy(level.getServer());

		if (TownSquare.contains(economy.town(), pos)) {
			player.sendOverlayMessage(Component.literal("Only the host can build on the town square."));
			return;
		}

		UUID owner = ownerAt(level, pos);
		Account account = owner == null ? null : economy.account(owner);
		player.sendOverlayMessage(Component.literal("This land belongs to " + (account == null ? "someone else" : account.name()) + "."));
	}

	/** A player placed a block: counts it for a Builder working on someone else's land, and for events. */
	public static void onPlaced(ServerPlayer player, BlockPos pos, BlockState state) {
		MinecraftServer server = player.level().getServer();

		if (player.level().dimension() != Level.OVERWORLD) {
			return;
		}

		Economy economy = EconomyService.economy(server);
		UUID owner = economy.landOwnerAt(pos.getX(), pos.getZ());

		if (owner != null && !owner.equals(player.getUUID()) && economy.isAllowedAt(player.getUUID(), pos.getX(), pos.getZ())
				&& economy.builderPlaced(player.getUUID())) {
			EconomyService.changed(server);
			Text.tell(player, "Builder task done! You will be paid at the end of the day.");
		}

		EventService.onPlaced(player, pos, state);
	}

	// ---- plots ----

	/** Claims a plot around where the player stands, for their first business that has none. */
	public static Result claim(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Economy economy = EconomyService.economy(server);

		if (player.level().dimension() != Level.OVERWORLD) {
			Text.warn(player, "Plots can only be claimed in the overworld.");
			return Result.OVERLAPS;
		}

		Business unplaced = null;

		for (Business business : economy.businessesOf(player.getUUID())) {
			if (business.plot() == null) {
				unplaced = business;
				break;
			}
		}

		if (unplaced == null) {
			Text.warn(player, economy.businessesOf(player.getUUID()).isEmpty()
					? "Pick a job first." : "Your business already has its plot.");
			return Result.ALREADY_CLAIMED;
		}

		BlockPos here = player.blockPosition();
		Result result = economy.claimPlot(player.getUUID(), unplaced.id(), here.getX(), here.getZ());

		if (result == Result.OVERLAPS) {
			UUID inTheWay = economy.landInTheWay(Plot.around(here.getX(), here.getZ()));
			Account neighbour = inTheWay == null ? null : economy.account(inTheWay);
			String whose = inTheWay == null ? "someone's" : inTheWay.equals(player.getUUID()) ? "your own" : neighbour.name() + "'s";
			Text.warn(player, "Too close to " + whose + " land. Your plot is " + Rules.plotSize() + " by " + Rules.plotSize() + " blocks around you, so walk at least "
					+ Rules.plotSize() + " blocks further away and try again.");
			return result;
		}

		if (!result.ok()) {
			Text.warn(player, result.message());
			return result;
		}

		EconomyService.changed(server);
		Plot plot = unplaced.plot();
		markCorners(player.level(), plot);
		Text.tell(player, "This is now your " + unplaced.job().title() + " plot: from " + plot.minX() + ", " + plot.minZ()
				+ " to " + (plot.minX() + plot.size() - 1) + ", " + (plot.minZ() + plot.size() - 1)
				+ ". The corners are marked with fence posts. Only you can build here.");
		ShopService.explain(player);
		return result;
	}

	public static void markCorners(ServerLevel level, Plot plot) {
		int last = plot.size() - 1;
		int[][] corners = {{0, 0}, {0, last}, {last, 0}, {last, last}};

		for (int[] corner : corners) {
			int x = plot.minX() + corner[0];
			int z = plot.minZ() + corner[1];
			BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);

			if (level.getBlockState(top).isAir()) {
				level.setBlockAndUpdate(top, Blocks.OAK_FENCE.defaultBlockState());
			}
		}
	}

	private static void showLand(ServerPlayer player) {
		Economy economy = EconomyService.economy(player.level().getServer());
		House house = economy.house(player.getUUID());

		if (house != null) {
			Text.tell(player, "Your house is at " + (house.lot().minX() + 3) + ", " + (house.lot().minZ() + 3) + ".");
		}

		for (Business business : economy.businessesOf(player.getUUID())) {
			if (business.plot() == null) {
				Text.tell(player, Component.literal("Your " + business.job().title() + " business has no plot yet. ")
						.append(Text.button("Claim this spot", "/plot claim")));
			} else {
				Text.tell(player, "Your " + business.job().title() + " plot is centred at "
						+ business.plot().centerX() + ", " + business.plot().centerZ() + ".");
			}
		}
	}

	// ---- houses ----

	/** Gives a first-time player their house in the neighborhood and takes them to its front door. */
	public static void onJoin(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Economy economy = EconomyService.economy(server);

		House existing = economy.house(player.getUUID());

		if (existing != null) {
			// Houses built by an older version of the mod get their name sign now.
			BlockPos existingDoor = HouseBuilder.findDoor(server.overworld(), existing.lot());

			if (existingDoor != null) {
				HouseBuilder.nameSign(server.overworld(), existingDoor, player.getGameProfile().name());
			}

			return;
		}

		ServerLevel overworld = server.overworld();
		BlockPos spawn = overworld.getRespawnData().pos();
		House house = economy.assignHouse(player.getUUID(), spawn.getX() + neighborhoodDx, spawn.getZ() + neighborhoodDz,
				lot -> dryLand(overworld, lot.minX() - 1, lot.minZ() - 1, lot.size() + 2, lot.size() + 4));
		int floorY = HouseBuilder.build(overworld, house.lot());
		EconomyService.changed(server);

		BlockPos door = HouseBuilder.doorPos(house.lot(), floorY);
		HouseBuilder.nameSign(overworld, door, player.getGameProfile().name());

		// Home is the player's spawn point from the start.
		player.setRespawnPosition(new ServerPlayer.RespawnConfig(
				net.minecraft.world.level.storage.LevelData.RespawnData.of(Level.OVERWORLD, door.south(2), 180f, 0f), true), false);
		GuideService.giveGuideBook(player);

		// The game finishes placing a joining player after this point, so take them home a moment later.
		arrivals.put(player.getUUID(), new Arrival(door, server.getTickCount() + 5));
	}

	/**
	 * Whether this area is dry land a house or the town square can stand on: no water or lava
	 * at the surface, and not too steep.
	 */
	public static boolean dryLand(ServerLevel level, int minX, int minZ, int sizeX, int sizeZ) {
		for (int x = minX; x < minX + sizeX; x += 16) {
			for (int z = minZ; z < minZ + sizeZ; z += 16) {
				level.getChunk(x >> 4, z >> 4);
			}
		}

		level.getChunk((minX + sizeX - 1) >> 4, (minZ + sizeZ - 1) >> 4);
		level.getChunk(minX >> 4, (minZ + sizeZ - 1) >> 4);
		level.getChunk((minX + sizeX - 1) >> 4, minZ >> 4);
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;

		for (int x = minX; x < minX + sizeX; x++) {
			for (int z = minZ; z < minZ + sizeZ; z++) {
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
				BlockState ground = level.getBlockState(new BlockPos(x, top, z));

				if (!ground.getFluidState().isEmpty()) {
					return false;
				}

				lowest = Math.min(lowest, top);
				highest = Math.max(highest, top);
			}
		}

		return highest - lowest <= 6;
	}

	/** Takes a player to their front door. Returns false if they have no house or its door is gone. */
	public static boolean goHome(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		House house = EconomyService.economy(server).house(player.getUUID());
		BlockPos door = house == null ? null : HouseBuilder.findDoor(server.overworld(), house.lot());

		if (door == null) {
			return false;
		}

		player.teleportTo(server.overworld(), door.getX() + 0.5, door.getY(), door.getZ() + 4.5, java.util.Set.of(), 180f, 0f, false);
		return true;
	}

	private record Arrival(BlockPos door, int atTick) {
	}

	private static final java.util.Map<UUID, Arrival> arrivals = new java.util.HashMap<>();

	private static void tick(MinecraftServer server) {
		if (!Activation.active()) {
			return;
		}

		if (arrivals.isEmpty()) {
			return;
		}

		arrivals.entrySet().removeIf(entry -> {
			Arrival arrival = entry.getValue();

			if (server.getTickCount() < arrival.atTick()) {
				return false;
			}

			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

			if (player != null) {
				BlockPos door = arrival.door();
				player.teleportTo(server.overworld(), door.getX() + 0.5, door.getY(), door.getZ() + 4.5, java.util.Set.of(), 180f, 0f, false);
				Text.tell(player, "Welcome to the neighborhood! This house is yours.");
			}

			return true;
		});
	}
}
