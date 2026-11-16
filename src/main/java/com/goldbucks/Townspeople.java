package com.goldbucks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import com.goldbucks.economy.Account;
import com.goldbucks.economy.Business;
import com.goldbucks.economy.Economy;
import com.goldbucks.economy.ItemKind;
import com.goldbucks.economy.Job;
import com.goldbucks.economy.Level;
import com.goldbucks.economy.Rules;
import com.goldbucks.economy.Settings;
import com.goldbucks.economy.Town;
import com.goldbucks.economy.TownData;
import com.goldbucks.economy.TownEvent;
import com.goldbucks.economy.Townsfolk;

/**
 * The town's villagers: townsfolk customers who come to player shops, and passengers who wait at the
 * Taxi Stand. Customers walk up to a shop when someone is near enough to see them; otherwise the
 * sale simply happens and the owner hears about it.
 */
public final class Townspeople {
	public static final String CUSTOMER_TAG = "goldbucks_customer";
	public static final String PASSENGER_TAG = "goldbucks_passenger";
	private static final String ORDER_TAG_PREFIX = "goldbucks_order_";
	private static final String[] NAMES = {"Ada", "Bram", "Cleo", "Dev", "Ella", "Finn", "Gus", "Hana", "Ivy", "Jude", "Kit",
			"Lena", "Milo", "Nell", "Otto", "Pia", "Quinn", "Rosa", "Sam", "Tess", "Uma", "Vic", "Wren", "Zed"};
	/** How far a visible customer walks, and how long they may take. */
	private static final int WALK_TICKS = 400;
	private static final Random RANDOM = new Random();

	/** Visits planned for today: the owner, and the clock tick when the customer comes. */
	private record Visit(UUID owner, long atTick) {
	}

	/** A customer walking to a shop. */
	private static final class Walker {
		UUID entity;
		UUID owner;
		BlockPos sign;
		long deadline;
		long leaveAt = -1;
	}

	private static final List<Visit> planned = new ArrayList<>();
	private static final List<Walker> walkers = new ArrayList<>();
	private static int plannedForDay = -1;
	/** Passenger orders -> how far the passenger has travelled since getting in. */
	private static final Map<Integer, Double> rideDistance = new HashMap<>();
	private static final Map<Integer, Vec3> lastRidePos = new HashMap<>();
	/** Shops whose owner was already told today that they are too pricey or empty, so they are not nagged. */
	private static final Set<String> toldToday = new HashSet<>();

	private Townspeople() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Townspeople::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			planned.clear();
			walkers.clear();
			plannedForDay = -1;
			rideDistance.clear();
			lastRidePos.clear();
			toldToday.clear();
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || !Activation.active() || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}

			if (entity.entityTags().contains(TownSquare.CLERK_TAG)) {
				StoreService.openStore(serverPlayer);
				return InteractionResult.SUCCESS;
			}

			if (entity.entityTags().contains(PASSENGER_TAG)) {
				board(serverPlayer, entity);
				return InteractionResult.SUCCESS;
			}

			if (entity.entityTags().contains(CUSTOMER_TAG)) {
				return InteractionResult.SUCCESS;
			}

			return InteractionResult.PASS;
		});
	}

	/** Whether this customer is on their way to a shop right now. */
	static boolean isWalking(UUID entity) {
		return walkers.stream().anyMatch(walker -> walker.entity.equals(entity));
	}

	/** Townspeople cannot be hurt, pushed off, led away, or despawn. */
	public static void makeHarmless(Mob mob) {
		mob.setPersistenceRequired();
		mob.setPermanentlyInvulnerable(true);
	}

	static String randomName() {
		return NAMES[RANDOM.nextInt(NAMES.length)];
	}

	// ---- planning the day's customers ----

	/** Plans today's townsfolk visits across the rest of the day. */
	public static void planDay(MinecraftServer server) {
		Economy economy = EconomyService.economy(server);
		planned.clear();
		toldToday.clear();
		plannedForDay = economy.day();
		long now = economy.clockTicks();
		long dayLength = Rules.ticksPerDay();
		long left = dayLength - now % dayLength;
		Set<UUID> owners = new HashSet<>();

		for (Business business : economy.businesses()) {
			if (!business.shopSigns().isEmpty()) {
				owners.add(business.owner());
			}
		}

		for (UUID owner : owners) {
			int visits = economy.townsfolkVisits(owner, anyFairShop(server, owner));

			if (economy.town().event() == TownEvent.NIGHT_MARKET && anyLitShop(server, owner)) {
				visits *= 2;
			}

			// A day already partly gone gets its share of visits.
			int count = (int) Math.round(visits * (double) left / dayLength);

			for (int i = 0; i < count; i++) {
				planned.add(new Visit(owner, now + 20 + (long) (RANDOM.nextDouble() * Math.max(1, left - 40))));
			}
		}
	}

	private static boolean anyFairShop(MinecraftServer server, UUID owner) {
		for (ShopService.Shop shop : shopsOf(server, owner)) {
			Townsfolk.Offer offer = offer(server, shop, 0);

			if (offer.inStock() && offer.bargain()) {
				return true;
			}
		}

		return false;
	}

	private static boolean anyLitShop(MinecraftServer server, UUID owner) {
		ServerLevel level = server.overworld();

		for (ShopService.Shop shop : shopsOf(server, owner)) {
			for (BlockPos near : BlockPos.betweenClosed(shop.sign().offset(-2, -1, -2), shop.sign().offset(2, 2, 2))) {
				var state = level.getBlockState(near);

				if (state.getLightEmission() >= 10) {
					return true;
				}
			}
		}

		return false;
	}

	/** Every working shop sign the owner has, reading each from the world. Forgets signs that are gone. */
	static List<ShopService.Shop> shopsOf(MinecraftServer server, UUID owner) {
		Economy economy = EconomyService.economy(server);
		ServerLevel level = server.overworld();
		List<ShopService.Shop> shops = new ArrayList<>();

		for (Business business : economy.businessesOf(owner)) {
			for (Long packed : List.copyOf(business.shopSigns())) {
				BlockPos pos = BlockPos.of(packed);
				level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
				ShopService.Shop shop = ShopService.readShop(level, pos);

				if (shop == null || shop.business().id() != business.id()) {
					economy.forgetShop(business.id(), packed);
					EconomyService.changed(server);
				} else {
					shops.add(shop);
				}
			}
		}

		return shops;
	}

	/** How a customer sees a shop: its item's value, and any event or perk that makes them pay more. */
	static Townsfolk.Offer offer(MinecraftServer server, ShopService.Shop shop, int index) {
		Economy economy = EconomyService.economy(server);
		double value = shop.sample().isEmpty() ? 1 : TownData.current().valueOf(shop.itemId()) * shop.bundle();
		double multiplier = 1;
		TownEvent event = economy.town().event();
		ItemStack sample = shop.sample();
		Account owner = economy.account(shop.business().owner());
		Job job = shop.business().job();

		if (event == TownEvent.FISHING_SEASON && (sample.is(net.minecraft.tags.ItemTags.BOATS) || sample.is(Items.FISHING_ROD))) {
			multiplier = 2;
		}

		if (event == TownEvent.BOOK_FAIR && sample.is(Items.WRITTEN_BOOK)) {
			multiplier = 2;
		}

		if (owner != null && owner.level(job).atLeast(Level.EXPERT)) {
			if (job == Job.VEHICLE_DEALER && ItemKinds.of(sample) == ItemKind.VEHICLE) {
				multiplier = Math.max(multiplier, 2);
			}

			if (job == Job.LIBRARIAN && sample.is(Items.WRITTEN_BOOK)) {
				multiplier = Math.max(multiplier, 2);
			}
		}

		return new Townsfolk.Offer(index, shop.itemId(), shop.price(), shop.inStock(), value, multiplier);
	}

	// ---- the clock ----

	private static void tick(MinecraftServer server) {
		if (!Activation.active()) {
			return;
		}

		if (server.getTickCount() % 10 != 0) {
			return;
		}

		Economy economy = EconomyService.economy(server);

		if (plannedForDay != economy.day()) {
			planDay(server);
		}

		long now = economy.clockTicks();

		for (Iterator<Visit> it = planned.iterator(); it.hasNext();) {
			Visit visit = it.next();

			if (visit.atTick() <= now) {
				it.remove();
				visit(server, visit.owner(), false);
			}
		}

		tickWalkers(server, now);

		if (server.getTickCount() % 20 == 0) {
			tickPassengers(server);
		}
	}

	/** A customer visits one owner's shops. Shown as a walking villager when the owner (or anyone) is close by. */
	public static void visit(MinecraftServer server, UUID owner, boolean forceInstant) {
		List<ShopService.Shop> shops = shopsOf(server, owner);

		if (shops.isEmpty()) {
			return;
		}

		ShopService.Shop target = shops.get(RANDOM.nextInt(shops.size()));
		ServerLevel level = server.overworld();
		boolean someoneNear = level.getNearestPlayer(target.sign().getX(), target.sign().getY(), target.sign().getZ(), 48, false) != null;

		if (!forceInstant && someoneNear && walkers.size() < Settings.current().maxVisibleTownsfolk) {
			spawnWalker(server, owner, target);
		} else {
			buyFrom(server, owner, null, randomName());
		}
	}

	private static void spawnWalker(MinecraftServer server, UUID owner, ShopService.Shop target) {
		ServerLevel level = server.overworld();
		double angle = RANDOM.nextDouble() * Math.PI * 2;
		int x = target.sign().getX() + (int) Math.round(Math.cos(angle) * 7);
		int z = target.sign().getZ() + (int) Math.round(Math.sin(angle) * 7);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.MOB_SUMMONED);

		if (villager == null) {
			buyFrom(server, owner, null, randomName());
			return;
		}

		String name = randomName();
		villager.snapTo(x + 0.5, y, z + 0.5, 0f, 0f);
		villager.setCustomName(Component.literal("Townsfolk " + name));
		villager.setCustomNameVisible(true);
		makeHarmless(villager);
		villager.addTag(CUSTOMER_TAG);
		level.addFreshEntity(villager);
		villager.getNavigation().moveTo(target.sign().getX() + 0.5, target.sign().getY(), target.sign().getZ() + 0.5, 0.6);
		level.sendParticles(ParticleTypes.POOF, x + 0.5, y + 1, z + 0.5, 8, 0.3, 0.5, 0.3, 0.02);

		Walker walker = new Walker();
		walker.entity = villager.getUUID();
		walker.owner = owner;
		walker.sign = target.sign();
		walker.deadline = EconomyService.economy(server).clockTicks() + WALK_TICKS;
		walkers.add(walker);
	}

	private static void tickWalkers(MinecraftServer server, long now) {
		ServerLevel level = server.overworld();

		for (Iterator<Walker> it = walkers.iterator(); it.hasNext();) {
			Walker walker = it.next();
			Entity entity = level.getEntity(walker.entity);

			if (!(entity instanceof Villager villager)) {
				if (walker.leaveAt < 0) {
					buyFrom(server, walker.owner, null, randomName());
				}

				it.remove();
				continue;
			}

			if (walker.leaveAt >= 0) {
				if (now >= walker.leaveAt) {
					level.sendParticles(ParticleTypes.POOF, villager.getX(), villager.getY() + 1, villager.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
					villager.discard();
					it.remove();
				}

				continue;
			}

			boolean arrived = villager.distanceToSqr(Vec3.atCenterOf(walker.sign)) < 3.5 * 3.5;

			if (!arrived && now < walker.deadline) {
				if (villager.getNavigation().isDone()) {
					villager.getNavigation().moveTo(walker.sign.getX() + 0.5, walker.sign.getY(), walker.sign.getZ() + 0.5, 0.6);
				}

				continue;
			}

			villager.getNavigation().stop();
			String name = villager.getCustomName() == null ? randomName() : villager.getCustomName().getString().replace("Townsfolk ", "");
			buyFrom(server, walker.owner, villager, name);
			walker.leaveAt = now + 60;
		}
	}

	/**
	 * The customer looks over the owner's shops and buys from the best one, if any is in stock and fairly priced.
	 * Returns true if they bought something.
	 */
	public static boolean buyFrom(MinecraftServer server, UUID owner, Villager villager, String name) {
		Economy economy = EconomyService.economy(server);
		List<ShopService.Shop> shops = shopsOf(server, owner);
		List<Townsfolk.Offer> offers = new ArrayList<>();

		for (int i = 0; i < shops.size(); i++) {
			offers.add(offer(server, shops.get(i), i));
		}

		Townsfolk.Choice choice = Townsfolk.choose(offers, Settings.current(), economy.town().event() == TownEvent.BIG_SALE_DAY);
		ServerLevel level = server.overworld();

		if (!choice.sale()) {
			ShopService.Shop problem = choice.tooPricey() != null ? shops.get(choice.tooPricey().index())
					: choice.empty() != null ? shops.get(choice.empty().index()) : null;

			if (villager != null) {
				level.sendParticles(ParticleTypes.ANGRY_VILLAGER, villager.getX(), villager.getY() + 2, villager.getZ(), 3, 0.3, 0.2, 0.3, 0);
				level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1f, 1f);
			}

			if (problem != null) {
				tellOwnerOnce(server, owner, problem, choice.tooPricey() != null
						? "Townsfolk " + name + " thought your " + problem.itemName() + " was too pricey."
						: "Townsfolk " + name + " wanted to buy from you, but your shop is empty.");
			}

			return false;
		}

		ShopService.Shop shop = shops.get(choice.bought().index());
		sell(server, shop, name, villager);
		Account account = economy.account(owner);

		// A Skilled Salesperson's customers buy a second item, if there is one.
		if (shop.business().job() == Job.SALESPERSON && account.level(Job.SALESPERSON).atLeast(Level.SKILLED)) {
			ShopService.Shop again = ShopService.readShop(level, shop.sign());

			if (again != null && offer(server, again, 0).fair(Settings.current()) && again.inStock()) {
				sell(server, again, name, villager);
			}
		}

		return true;
	}

	private static void sell(MinecraftServer server, ShopService.Shop shop, String name, Villager villager) {
		Economy economy = EconomyService.economy(server);
		ServerLevel level = server.overworld();
		Set<Integer> tasksBefore = JobsService.pendingTasks(economy, shop.business().owner());
		ItemStack bought = shop.stock().removeItem(shop.slot(), shop.bundle());
		shop.stock().setChanged();
		economy.townsfolkPurchase(shop.business().id(), shop.price(), ItemKinds.of(bought));
		EconomyService.changed(server);

		BlockPos where = villager != null ? villager.blockPosition() : shop.sign();
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, where.getX() + 0.5, where.getY() + 1.5, where.getZ() + 0.5, 8, 0.4, 0.4, 0.4, 0);
		level.playSound(null, where, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		ShopService.saleNotice(server, shop.business(), "Townsfolk " + name, bought.getHoverName().getString(), shop.price(), true, tasksBefore);
	}

	private static void tellOwnerOnce(MinecraftServer server, UUID owner, ShopService.Shop shop, String message) {
		if (!toldToday.add(shop.sign().asLong() + ":" + message.contains("empty"))) {
			return;
		}

		ServerPlayer player = server.getPlayerList().getPlayer(owner);

		if (player != null) {
			Text.tell(player, message);
		} else if (message.contains("empty")) {
			EconomyService.economy(server).recordAwayEmptyShop(owner);
		}
	}

	// ---- passengers ----

	/** Picks where a passenger wants to go: a shop or a house at least a ride away from the Taxi Stand. */
	public static void chooseDestinations(MinecraftServer server) {
		Economy economy = EconomyService.economy(server);
		Town town = economy.town();
		BlockPos stand = TownSquare.taxiStand(town);
		List<String> places = new ArrayList<>();
		List<String> near = new ArrayList<>();

		for (Business business : economy.businesses()) {
			for (Long packed : business.shopSigns()) {
				BlockPos pos = BlockPos.of(packed);
				String place = pos.getX() + " " + pos.getY() + " " + pos.getZ() + "|" + economy.account(business.owner()).name() + "'s shop";
				(far(stand, pos) ? places : near).add(place);
			}
		}

		for (var house : economy.houses()) {
			BlockPos door = new BlockPos(house.lot().minX() + 3, town.squareY, house.lot().minZ() + 8);
			String place = door.getX() + " " + door.getY() + " " + door.getZ() + "|" + economy.account(house.owner()).name() + "'s house";
			(far(stand, door) ? places : near).add(place);
		}

		if (places.isEmpty()) {
			places = near;
		}

		for (Town.Order order : town.orders()) {
			if (order.passenger && order.destination == null && !places.isEmpty()) {
				List<String> choices = places;

				// Tourists want shops.
				if (order.tourist) {
					List<String> shops = places.stream().filter(p -> p.endsWith("shop")).toList();
					choices = shops.isEmpty() ? places : shops;
				}

				order.destination = choices.get(RANDOM.nextInt(choices.size()));
			}
		}

		EconomyService.changed(server);
	}

	private static boolean far(BlockPos a, BlockPos b) {
		return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ()) >= Rules.TAXI_RIDE_BLOCKS;
	}

	public static BlockPos destinationPos(Town.Order order) {
		if (order.destination == null) {
			return null;
		}

		String[] parts = order.destination.substring(0, order.destination.indexOf('|')).split(" ");
		return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
	}

	private static int orderIdOf(Entity entity) {
		for (String tag : entity.entityTags()) {
			if (tag.startsWith(ORDER_TAG_PREFIX)) {
				return Integer.parseInt(tag.substring(ORDER_TAG_PREFIX.length()));
			}
		}

		return -1;
	}

	/** Keeps one waiting passenger at the Taxi Stand for each open passenger order, and takes them where they are going. */
	private static void tickPassengers(MinecraftServer server) {
		Economy economy = EconomyService.economy(server);
		Town town = economy.town();

		if (!town.squareBuilt) {
			return;
		}

		ServerLevel level = server.overworld();
		BlockPos stand = TownSquare.taxiStand(town);
		List<? extends Villager> existing = level.getEntities(EntityTypes.VILLAGER, v -> v.entityTags().contains(PASSENGER_TAG));
		Set<Integer> present = new HashSet<>();

		for (Villager passenger : existing) {
			int id = orderIdOf(passenger);
			Town.Order order = town.order(id);

			if (order == null || !order.open()) {
				passenger.stopRiding();
				passenger.discard();
				continue;
			}

			present.add(id);

			if (passenger.isPassenger()) {
				ride(server, passenger, order);
			}
		}

		// Passengers only appear while someone is near enough to see the stand.
		if (level.getNearestPlayer(stand.getX(), stand.getY(), stand.getZ(), 64, false) == null) {
			return;
		}

		int slot = 0;

		for (Town.Order order : town.orders()) {
			if (!order.passenger || !order.open() || present.contains(order.id)) {
				continue;
			}

			if (order.destination == null) {
				chooseDestinations(server);
			}

			if (order.destination == null) {
				continue;
			}

			Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.MOB_SUMMONED);

			if (villager == null) {
				continue;
			}

			villager.snapTo(stand.getX() + 0.5 + (slot % 3) - 1, stand.getY(), stand.getZ() - 1.5 - slot / 3, 180f, 0f);
			villager.setCustomName(Component.literal((order.tourist ? "Tourist" : "Passenger") + " to " + order.destinationLabel()));
			villager.setCustomNameVisible(true);
			makeHarmless(villager);
			villager.setNoAi(true);
			villager.addTag(PASSENGER_TAG);
			villager.addTag(ORDER_TAG_PREFIX + order.id);
			level.addFreshEntity(villager);
			slot++;
		}
	}

	/** A player right-clicked a waiting passenger. */
	static void board(ServerPlayer player, Entity passenger) {
		Economy economy = EconomyService.economy(player.level().getServer());
		Town.Order order = economy.town().order(orderIdOf(passenger));

		if (order == null || !order.open()) {
			return;
		}

		Entity vehicle = player.getVehicle();

		if (vehicle == null) {
			Text.tell(player, "\"I need a ride to " + order.destinationLabel() + ". Come back in a boat or on a camel and click me.\"");
			return;
		}

		if (passenger.isPassenger()) {
			return;
		}

		if (!passenger.startRiding(vehicle, true, true)) {
			Text.warn(player, "There is no room for a passenger. Use a boat or a camel.");
			return;
		}

		rideDistance.put(order.id, 0.0);
		lastRidePos.put(order.id, passenger.position());
		Text.tell(player, "Take your passenger to " + order.destinationLabel() + ". Pay: " + Text.money(order.pay) + ".");
	}

	private static void ride(MinecraftServer server, Villager passenger, Town.Order order) {
		Vec3 now = passenger.position();
		Vec3 before = lastRidePos.put(order.id, now);

		if (before != null) {
			rideDistance.merge(order.id, before.distanceTo(now), Double::sum);
		}

		BlockPos target = destinationPos(order);
		double toGo = Math.hypot(now.x - target.getX() - 0.5, now.z - target.getZ() - 0.5);

		if (toGo > 8 || rideDistance.getOrDefault(order.id, 0.0) < Rules.TAXI_RIDE_BLOCKS) {
			return;
		}

		ServerPlayer driver = null;

		for (Entity rider : passenger.getRootVehicle().getPassengers()) {
			if (rider instanceof ServerPlayer player) {
				driver = player;
			}
		}

		if (driver == null) {
			return;
		}

		deliver(server, driver, passenger, order);
	}

	/** The passenger arrives: they get out, the driver is paid, and a tourist goes shopping. */
	public static void deliver(MinecraftServer server, ServerPlayer driver, Villager passenger, Town.Order order) {
		Economy economy = EconomyService.economy(server);
		Economy.Filled filled = economy.fill(driver.getUUID(), order.id);

		if (!filled.result().ok()) {
			Text.warn(driver, filled.result().message());
			return;
		}

		passenger.stopRiding();
		ServerLevel level = server.overworld();
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, passenger.getX(), passenger.getY() + 1.5, passenger.getZ(), 10, 0.4, 0.4, 0.4, 0);
		level.playSound(null, passenger.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		Text.tell(driver, "Your passenger is there! You earned " + Text.money(filled.pay()) + ".");

		if (filled.taskDone()) {
			Text.tell(driver, "Taxi Driver task done! You will be paid at the end of the day.");
		}

		economy.joinEvent(driver.getUUID());

		if (order.tourist) {
			Business shop = economy.businessAt(destinationPos(order).getX(), destinationPos(order).getZ());

			if (shop != null) {
				String name = randomName();
				buyFrom(server, shop.owner(), passenger, name);
				buyFrom(server, shop.owner(), passenger, name);
			}
		}

		passenger.discard();
		rideDistance.remove(order.id);
		lastRidePos.remove(order.id);
		EconomyService.changed(server);
		Achievements.check(driver);
	}

	/** Whether a door, gate or similar is at this spot, so a passenger can be dropped near it. */
	static boolean isDoorLike(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).is(BlockTags.DOORS);
	}
}
