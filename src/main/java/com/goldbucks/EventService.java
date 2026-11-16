package com.goldbucks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import com.goldbucks.economy.Business;
import com.goldbucks.economy.Economy;
import com.goldbucks.economy.House;
import com.goldbucks.economy.Job;
import com.goldbucks.economy.Rules;
import com.goldbucks.economy.Settings;
import com.goldbucks.economy.Town;
import com.goldbucks.economy.TownData;
import com.goldbucks.economy.TownEvent;
import com.goldbucks.economy.Townsfolk;

/**
 * The events in the running game: the boss bar, and everything that happens in the world for each event
 * (buried treasure, a lost cat, storm damage at the square, a rail line, a construction site, the inspector).
 */
public final class EventService {
	private static final Random RANDOM = new Random();
	private static final String LOST_CAT_TAG = "goldbucks_lost_cat";
	private static ServerBossEvent bar;
	private static TownEvent barFor;

	private EventService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(EventService::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (bar != null) {
				bar.removeAllPlayers();
			}

			bar = null;
			barFor = null;
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (bar != null) {
				bar.addPlayer(handler.getPlayer());
			}
		});

		// A lost cat or a customer left over from another day goes away when its area loads.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (level.getServer() == null || !Activation.active()) {
				return;
			}

			Town town = economy(level.getServer()).town();
			boolean strayCat = entity.entityTags().contains(LOST_CAT_TAG)
					&& (town.event() != TownEvent.LOST_PET || !entity.getUUID().toString().equals(town.eventData().get("cat"))
							|| "true".equals(town.eventData().get("cat_home")));

			boolean extraClerk = entity.entityTags().contains(TownSquare.CLERK_TAG) && town.clerkId != null
					&& !entity.getUUID().toString().equals(town.clerkId);

			if (strayCat || extraClerk || (entity.entityTags().contains(Townspeople.CUSTOMER_TAG) && !Townspeople.isWalking(entity.getUUID()))) {
				entity.discard();
			}
		});
	}

	private static Economy economy(MinecraftServer server) {
		return EconomyService.economy(server);
	}

	private static Map<String, String> data(MinecraftServer server) {
		return economy(server).town().eventData();
	}

	private static String name(TownEvent event) {
		return TownData.current().eventName(event);
	}

	private static void tellAll(MinecraftServer server, String message) {
		server.getPlayerList().getPlayers().forEach(player -> Text.tell(player, message));
	}

	// ---- starting ----

	/** Called when an event starts, by the day clock or the host: sets it up in the world. */
	public static void started(MinecraftServer server, TownEvent event) {
		Economy economy = economy(server);
		Town town = economy.town();
		ServerLevel level = server.overworld();
		updateBar(server);

		if (event == null) {
			return;
		}

		switch (event) {
			case TREASURE_HUNT -> buryTreasure(server);
			case LOST_PET -> loseCat(server);
			case STORM_REPAIRS -> stormDamage(server);
			case CONSTRUCTION_BOOM -> {
				data(server).put("site_done", "false");
				tellAll(server, "Construction Boom! Fill the 5 by 5 space next to the fountain at the town square with blocks. Anyone may build there today.");
			}
			case RAILWAY_WEEK -> tellAll(server, "Railway Week! Lay rails from the south edge of the town square to the neighborhood. $40 when they connect.");
			case HOLIDAY -> {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					economy.reward(player.getUUID(), 5);
					economy.joinEvent(player.getUUID());
					Text.tell(player, "Happy Holiday! The town gave you $5.");
				}

				if (town.squareBuilt) {
					Fx.fireworksShow(level, TownSquare.center(town).above(3));
				}
			}
			case SHOP_INSPECTOR -> data(server).put("inspect_at", String.valueOf(economy.clockTicks() + 2400));
			case GRAND_OPENING -> {
				UUID owner = town.grandOpeningOwner();

				if (owner != null && economy.account(owner) != null) {
					tellAll(server, "Grand Opening! " + economy.account(owner).name() + "'s new shop is open. Townsfolk are on their way!");
				}
			}
			case ELECTION_DAY -> {
				tellAll(server, "It is Election Day! Run for Mayor with /mayor run <your promise>. Vote with /vote.");
				TownSquare.updatePodium(server);
			}
			default -> {
			}
		}

		EconomyService.changed(server);
	}

	/** The host started an event by hand: it replaces any other and gets fresh orders. */
	public static void hostStart(MinecraftServer server, TownEvent event) {
		Economy economy = economy(server);
		cleanUp(server);
		economy.startEvent(event);
		economy.repostOrders(RANDOM);
		Townspeople.chooseDestinations(server);
		Townspeople.planDay(server);
		started(server, event);
		tellAll(server, "The host started " + name(event) + "! " + TownData.current().eventDescription(event));
		EconomyService.changed(server);
	}

	public static void hostStop(MinecraftServer server) {
		cleanUp(server);
		economy(server).stopEvent();
		updateBar(server);
		EconomyService.changed(server);
	}

	/** Removes anything an event left lying around: the lost cat, an unfound treasure chest. */
	static void cleanUp(MinecraftServer server) {
		Map<String, String> data = data(server);
		ServerLevel level = server.overworld();

		if (data.containsKey("cat")) {
			Entity cat = level.getEntity(UUID.fromString(data.get("cat")));

			if (cat != null) {
				cat.discard();
			}
		}

		if (data.containsKey("treasure") && !"true".equals(data.get("treasure_found"))) {
			BlockPos chest = BlockPos.of(Long.parseLong(data.get("treasure")));

			if (level.getBlockEntity(chest) instanceof Container container) {
				container.clearContent();
			}

			level.setBlock(chest, Blocks.DIRT.defaultBlockState(), 3);
		}

		if (economy(server).town().event() == TownEvent.STORM_REPAIRS && economy(server).town().squareBuilt) {
			TownSquare.buildLanterns(level, economy(server).town());
		}
	}

	// ---- the boss bar ----

	/** Takes the event bar off everyone's screen (when Goldbucks is switched off). */
	static void hideBar() {
		if (bar != null) {
			bar.removeAllPlayers();
		}

		bar = null;
		barFor = null;
	}

	static void updateBar(MinecraftServer server) {
		TownEvent event = economy(server).town().event();

		if (event == null) {
			if (bar != null) {
				bar.removeAllPlayers();
				bar = null;
			}

			barFor = null;
			return;
		}

		if (bar == null || barFor != event) {
			if (bar != null) {
				bar.removeAllPlayers();
			}

			bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(name(event)), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
			barFor = event;
			server.getPlayerList().getPlayers().forEach(bar::addPlayer);
		}

		Economy economy = economy(server);
		long dayLength = Rules.ticksPerDay();
		long ticksLeft = (economy.town().eventEndsDay() - economy.day()) * dayLength + (dayLength - economy.clockTicks() % dayLength);
		long total = (long) event.days() * dayLength;
		// Only send changes, so players are not sent the same bar every second.
		float progress = Math.round(Math.max(0f, Math.min(1f, (float) ticksLeft / total)) * 100) / 100f;

		if (bar.getProgress() != progress) {
			bar.setProgress(progress);
		}

		long minutes = Math.max(1, ticksLeft / 1200);
		String title = name(event) + " - " + minutes + " min left";

		if (!bar.getName().getString().equals(title) && !(event == TownEvent.CONTEST && bar.getName().getString().startsWith("Contest:"))) {
			bar.setName(Component.literal(title));
		}
	}

	/** The event's boss bar title, or "" when none is showing. For tests. */
	public static String barTitle() {
		return bar == null ? "" : bar.getName().getString();
	}

	// ---- every tick ----

	private static void tick(MinecraftServer server) {
		if (!Activation.active()) {
			return;
		}

		if (server.getTickCount() % 20 != 0) {
			return;
		}

		Economy economy = economy(server);
		TownEvent event = economy.town().event();
		updateBar(server);

		if (server.getTickCount() % 200 == 0 && economy.town().squareBuilt) {
			TownSquare.ensureClerk(server);
		}

		if (event == null) {
			return;
		}

		switch (event) {
			case LOST_PET -> tickCat(server);
			case RAILWAY_WEEK -> {
				if (server.getTickCount() % 200 == 0) {
					checkRailway(server);
				}
			}
			case SHOP_INSPECTOR -> {
				String at = data(server).get("inspect_at");

				if (at != null && economy.clockTicks() >= Long.parseLong(at)) {
					data(server).remove("inspect_at");
					inspect(server);
				}
			}
			default -> {
			}
		}
	}

	// ---- placing blocks ----

	/** Called when a player places a block anywhere. Counts it for events that care. */
	public static void onPlaced(ServerPlayer player, BlockPos pos, BlockState state) {
		MinecraftServer server = player.level().getServer();
		Economy economy = economy(server);
		Town town = economy.town();
		TownEvent event = town.event();

		if (event == null || player.level() != server.overworld()) {
			return;
		}

		Map<String, String> data = town.eventData();
		String who = player.getUUID().toString();

		switch (event) {
			case RAILWAY_WEEK -> {
				if (state.is(BlockTags.RAILS)) {
					data.merge("rails:" + who, "1", (a, b) -> String.valueOf(Integer.parseInt(a) + 1));
					economy.joinEvent(player.getUUID());
				}
			}
			case CONSTRUCTION_BOOM -> {
				if (inSite(town, pos) && !"true".equals(data.get("site_done"))) {
					data.merge("built:" + who, "1", (a, b) -> String.valueOf(Integer.parseInt(a) + 1));
					economy.joinEvent(player.getUUID());
					checkSite(server);
				}
			}
			case STORM_REPAIRS -> {
				String missing = data.getOrDefault("missing", "");
				String key = String.valueOf(pos.asLong());
				List<String> list = new ArrayList<>(List.of(missing.isEmpty() ? new String[0] : missing.split(",")));
				boolean right = state.is(Blocks.OAK_FENCE) || state.is(Blocks.LANTERN);

				if (right && list.remove(key)) {
					data.put("missing", String.join(",", list));
					economy.reward(player.getUUID(), 3);
					economy.joinEvent(player.getUUID());
					Text.tell(player, "Thanks for fixing the square! +$3." + (list.isEmpty() ? " Everything is repaired!" : ""));
				}
			}
			default -> {
			}
		}

		EconomyService.changed(server);
	}

	// ---- Construction Boom ----

	public static boolean inSite(Town town, BlockPos pos) {
		BlockPos corner = TownSquare.constructionCorner(town);
		return pos.getY() == corner.getY() && pos.getX() >= corner.getX() && pos.getX() < corner.getX() + 5
				&& pos.getZ() >= corner.getZ() && pos.getZ() < corner.getZ() + 5;
	}

	/** Whether the construction site is open for building right now. */
	public static boolean siteOpen(Town town, BlockPos pos) {
		return town.event() == TownEvent.CONSTRUCTION_BOOM && inSite(town, pos) && !"true".equals(town.eventData().get("site_done"));
	}

	static void checkSite(MinecraftServer server) {
		Town town = economy(server).town();
		BlockPos corner = TownSquare.constructionCorner(town);
		ServerLevel level = server.overworld();

		for (int dx = 0; dx < 5; dx++) {
			for (int dz = 0; dz < 5; dz++) {
				if (level.getBlockState(corner.offset(dx, 0, dz)).isAir()) {
					return;
				}
			}
		}

		town.eventData().put("site_done", "true");
		payShares(server, "built:", 30, "The construction site is finished!");
	}

	/** Splits a prize between players by how much each did. Everyone who helped gets at least $1. */
	static void payShares(MinecraftServer server, String prefix, int prize, String headline) {
		Economy economy = economy(server);
		Map<UUID, Integer> shares = new LinkedHashMap<>();
		int total = 0;

		for (Map.Entry<String, String> entry : economy.town().eventData().entrySet()) {
			if (entry.getKey().startsWith(prefix)) {
				int count = Integer.parseInt(entry.getValue());
				shares.put(UUID.fromString(entry.getKey().substring(prefix.length())), count);
				total += count;
			}
		}

		tellAll(server, headline);

		for (Map.Entry<UUID, Integer> share : shares.entrySet()) {
			int pay = Math.max(1, prize * share.getValue() / Math.max(1, total));
			com.goldbucks.economy.Account account = economy.account(share.getKey());

			// Master Builders' shares of town builds are doubled.
			if (account != null && economy.holdsJob(share.getKey(), Job.BUILDER)
					&& account.level(Job.BUILDER) == com.goldbucks.economy.Level.MASTER) {
				pay *= 2;
			}

			economy.reward(share.getKey(), pay);
			ServerPlayer player = server.getPlayerList().getPlayer(share.getKey());

			if (player != null) {
				Text.tell(player, "Your share: " + Text.money(pay) + ".");
			}
		}

		EconomyService.changed(server);
	}

	// ---- Railway Week ----

	public static BlockPos railStart(Town town) {
		return TownSquare.center(town).offset(0, 1, TownSquare.HALF + 1);
	}

	public static BlockPos railEnd(MinecraftServer server) {
		Economy economy = economy(server);

		for (House house : economy.houses()) {
			return new BlockPos(house.lot().minX() + 3, economy.town().squareY + 1, house.lot().minZ() + 8);
		}

		return null;
	}

	/** Looks for a connected line of rails from the square to the neighborhood. */
	public static boolean railsConnect(MinecraftServer server) {
		Town town = economy(server).town();
		BlockPos start = railStart(town);
		BlockPos end = railEnd(server);

		if (end == null) {
			return false;
		}

		ServerLevel level = server.overworld();
		Deque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> seen = new HashSet<>();

		for (BlockPos near : BlockPos.betweenClosed(start.offset(-3, -3, -3), start.offset(3, 3, 3))) {
			if (level.getBlockState(near).is(BlockTags.RAILS)) {
				queue.add(near.immutable());
				seen.add(near.immutable());
			}
		}

		while (!queue.isEmpty() && seen.size() < 6000) {
			BlockPos pos = queue.poll();

			if (Math.abs(pos.getX() - end.getX()) <= 4 && Math.abs(pos.getZ() - end.getZ()) <= 4) {
				return true;
			}

			for (BlockPos next : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
				if (!seen.contains(next) && level.isLoaded(next) && level.getBlockState(next).is(BlockTags.RAILS)) {
					BlockPos copy = next.immutable();
					seen.add(copy);
					queue.add(copy);
				}
			}
		}

		return false;
	}

	static void checkRailway(MinecraftServer server) {
		Map<String, String> data = data(server);

		if ("true".equals(data.get("rail_done")) || !railsConnect(server)) {
			return;
		}

		data.put("rail_done", "true");
		payShares(server, "rails:", 40, "The railway is finished! The square and the neighborhood are connected.");
	}

	// ---- Treasure Hunt ----

	static BlockPos randomSpotNear(ServerLevel level, BlockPos center, int min, int max) {
		double angle = RANDOM.nextDouble() * Math.PI * 2;
		int distance = min + RANDOM.nextInt(max - min + 1);
		int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
		int z = center.getZ() + (int) Math.round(Math.sin(angle) * distance);
		level.getChunk(x >> 4, z >> 4);
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	static void buryTreasure(MinecraftServer server) {
		Economy economy = economy(server);
		Town town = economy.town();

		if (!town.squareBuilt) {
			return;
		}

		ServerLevel level = server.overworld();
		BlockPos ground = randomSpotNear(level, TownSquare.center(town), 60, 160);

		// Stay off anyone's land.
		for (int tries = 0; tries < 10 && economy.landOwnerAt(ground.getX(), ground.getZ()) != null; tries++) {
			ground = randomSpotNear(level, TownSquare.center(town), 60, 160);
		}

		BlockPos chest = ground.below(2);
		level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);

		if (level.getBlockEntity(chest) instanceof Container container) {
			container.setItem(13, new ItemStack(Items.DIAMOND, 3));
			ItemStack note = new ItemStack(Items.PAPER);
			note.set(DataComponents.CUSTOM_NAME, Component.literal("Treasure! Worth $30"));
			container.setItem(12, note);
		}

		town.eventData().put("treasure", String.valueOf(chest.asLong()));
		String clue = clue(server);

		for (Business business : economy.businesses()) {
			ServerPlayer librarian = business.job() == Job.LIBRARIAN ? server.getPlayerList().getPlayer(business.owner()) : null;

			if (librarian != null) {
				ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
				book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Treasure Clue"), "The Town", 0,
						List.of(Filterable.passThrough(Component.literal("TREASURE CLUE\n\n" + clue))), true));
				librarian.getInventory().add(book);
				Text.tell(librarian, "You have a Treasure Clue book. Copy it on a crafting table with book and quills, and sell the copies!");
			}
		}
	}

	/** A clue for the Herald about where the treasure or the lost cat is, or null. */
	public static String clue(MinecraftServer server) {
		Economy economy = economy(server);
		Town town = economy.town();
		Map<String, String> data = town.eventData();

		if (!town.squareBuilt) {
			return null;
		}

		BlockPos target = null;
		String what = null;

		if (town.event() == TownEvent.TREASURE_HUNT && data.containsKey("treasure") && !"true".equals(data.get("treasure_found"))) {
			target = BlockPos.of(Long.parseLong(data.get("treasure")));
			what = "Clue: the treasure is buried 2 blocks down, about ";
		} else if (town.event() == TownEvent.LOST_PET && data.containsKey("cat_x") && !"true".equals(data.get("cat_home"))) {
			target = new BlockPos(Integer.parseInt(data.get("cat_x")), 0, Integer.parseInt(data.get("cat_z")));
			what = "Whiskers was last seen about ";
		}

		if (target == null) {
			return null;
		}

		BlockPos square = TownSquare.center(town);
		int dx = target.getX() - square.getX();
		int dz = target.getZ() - square.getZ();
		int distance = (int) Math.round(Math.hypot(dx, dz) / 10.0) * 10;
		return what + distance + " blocks " + direction(dx, dz) + " of the town square.";
	}

	/** "north", "south-east" and so on. North is negative z in Minecraft. */
	public static String direction(int dx, int dz) {
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
		return names[(int) Math.round(((angle + 360) % 360) / 45.0) % 8];
	}

	/** Called when a player opens a container. Returns true if it was the treasure. */
	public static boolean onOpen(ServerPlayer player, BlockPos pos) {
		MinecraftServer server = player.level().getServer();
		Economy economy = economy(server);
		Map<String, String> data = economy.town().eventData();

		if (economy.town().event() != TownEvent.TREASURE_HUNT || !String.valueOf(pos.asLong()).equals(data.get("treasure"))
				|| "true".equals(data.get("treasure_found"))) {
			return false;
		}

		data.put("treasure_found", "true");
		economy.reward(player.getUUID(), 30);
		economy.joinEvent(player.getUUID());
		EconomyService.changed(server);
		Fx.celebrate(player);
		tellAll(server, player.getGameProfile().name() + " found the buried treasure! +$30 and some diamonds.");
		return true;
	}

	// ---- Lost Pet ----

	static void loseCat(MinecraftServer server) {
		Town town = economy(server).town();

		if (!town.squareBuilt) {
			return;
		}

		ServerLevel level = server.overworld();
		BlockPos spot = randomSpotNear(level, TownSquare.center(town), 50, 120);
		Cat cat = EntityTypes.CAT.create(level, EntitySpawnReason.MOB_SUMMONED);

		if (cat == null) {
			return;
		}

		cat.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0f, 0f);
		cat.setCustomName(Component.literal("Whiskers"));
		cat.setCustomNameVisible(true);
		Townspeople.makeHarmless(cat);
		cat.addTag(LOST_CAT_TAG);
		level.addFreshEntity(cat);
		Map<String, String> data = town.eventData();
		data.put("cat", cat.getUUID().toString());
		data.put("cat_x", String.valueOf(spot.getX()));
		data.put("cat_z", String.valueOf(spot.getZ()));
	}

	static void tickCat(MinecraftServer server) {
		Economy economy = economy(server);
		Town town = economy.town();
		Map<String, String> data = town.eventData();

		if (!data.containsKey("cat") || "true".equals(data.get("cat_home"))) {
			return;
		}

		ServerLevel level = server.overworld();

		if (!(level.getEntity(UUID.fromString(data.get("cat"))) instanceof Cat cat)) {
			return;
		}

		data.put("cat_x", String.valueOf(cat.getBlockX()));
		data.put("cat_z", String.valueOf(cat.getBlockZ()));
		ServerPlayer follower = data.containsKey("cat_follows") ? server.getPlayerList().getPlayer(UUID.fromString(data.get("cat_follows"))) : null;

		if (follower == null) {
			if (level.getNearestPlayer(cat, 4) instanceof ServerPlayer found) {
				data.put("cat_follows", found.getUUID().toString());
				Text.tell(found, "You found Whiskers! She will follow you. Take her back to the town square.");
			}

			return;
		}

		if (cat.distanceTo(follower) > 3 && !cat.isPassenger()) {
			if (cat.distanceTo(follower) > 24) {
				cat.teleportTo(level, follower.getX(), follower.getY(), follower.getZ(), Set.of(), cat.getYRot(), 0f, false);
			} else {
				cat.getNavigation().moveTo(follower, 1.2);
			}
		}

		if (cat.blockPosition().distSqr(TownSquare.center(town)) < 9 * 9) {
			boolean byRide = cat.isPassenger() || follower.isPassenger();
			int reward = byRide ? 20 : 15;
			data.put("cat_home", "true");
			economy.reward(follower.getUUID(), reward);
			economy.joinEvent(follower.getUUID());
			EconomyService.changed(server);
			Fx.celebrate(follower);
			tellAll(server, follower.getGameProfile().name() + " brought Whiskers home! +" + Text.money(reward) + (byRide ? " (with a ride bonus)" : "") + ".");
			cat.stopRiding();
			cat.discard();
		}
	}

	// ---- Storm Repairs ----

	static void stormDamage(MinecraftServer server) {
		Town town = economy(server).town();

		if (!town.squareBuilt) {
			return;
		}

		ServerLevel level = server.overworld();
		List<BlockPos> posts = TownSquare.lanternPosts(town);
		List<String> missing = new ArrayList<>();

		// Two posts fall over, lantern and all, and the other two lanterns blow down.
		for (int i = 0; i < 4; i++) {
			BlockPos lantern = posts.get(i + 4);
			level.setBlock(lantern, Blocks.AIR.defaultBlockState(), 3);
			missing.add(String.valueOf(lantern.asLong()));

			if (i < 2) {
				level.setBlock(posts.get(i), Blocks.AIR.defaultBlockState(), 3);
				missing.add(String.valueOf(posts.get(i).asLong()));
			}
		}

		town.eventData().put("missing", String.join(",", missing));
		tellAll(server, "The storm knocked down lanterns at the town square! Put back fences and lanterns for $3 each.");
	}

	// ---- Shop Inspector ----

	static void inspect(MinecraftServer server) {
		Economy economy = economy(server);
		Set<UUID> owners = new HashSet<>();

		for (Business business : economy.businesses()) {
			if (!business.shopSigns().isEmpty()) {
				owners.add(business.owner());
			}
		}

		for (UUID owner : owners) {
			List<ShopService.Shop> shops = Townspeople.shopsOf(server, owner);
			boolean pass = !shops.isEmpty();

			for (ShopService.Shop shop : shops) {
				int stock = 0;

				for (int slot = 0; slot < shop.stock().getContainerSize(); slot++) {
					stock += shop.stock().getItem(slot).getCount();
				}

				Townsfolk.Offer offer = Townspeople.offer(server, shop, 0);
				pass &= stock >= 5 && offer.fair(Settings.current());
			}

			String name = economy.account(owner).name();

			if (pass) {
				economy.reward(owner, 10);
				economy.joinEvent(owner);
				tellAll(server, "The inspector gave " + name + "'s shops a gold star! +$10.");
			} else {
				ServerPlayer player = server.getPlayerList().getPlayer(owner);

				if (player != null) {
					Text.tell(player, "The inspector says: keep at least 5 of each thing in stock, at fair prices, to earn a gold star.");
				}
			}
		}

		EconomyService.changed(server);
	}

	// ---- contests ----

	public static void startContest(MinecraftServer server, String theme) {
		Economy economy = economy(server);
		cleanUp(server);
		economy.startEvent(TownEvent.CONTEST);
		economy.town().eventData().put("theme", theme);
		updateBar(server);
		bar.setName(Component.literal("Contest: " + theme));
		tellAll(server, "Contest! The host is looking for: " + theme + ". The winner gets a prize.");
		EconomyService.changed(server);
	}
}
