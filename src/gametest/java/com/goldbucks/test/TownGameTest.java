package com.goldbucks.test;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.goldbucks.BankService;
import com.goldbucks.BoardService;
import com.goldbucks.EconomyService;
import com.goldbucks.HeraldService;
import com.goldbucks.JobsService;
import com.goldbucks.LandService;
import com.goldbucks.Menus;
import com.goldbucks.ShopService;
import com.goldbucks.StoreService;
import com.goldbucks.TownSquare;
import com.goldbucks.Townspeople;
import com.goldbucks.UpgradeBuilder;
import com.goldbucks.economy.Business;
import com.goldbucks.economy.Economy;
import com.goldbucks.economy.House;
import com.goldbucks.economy.Job;
import com.goldbucks.economy.Result;
import com.goldbucks.economy.Town;
import com.goldbucks.economy.TownEvent;

/** The town square, the board, the store and its upgrades, townsfolk, passengers, the bank and the Herald. */
public class TownGameTest {
	private static final int PLOT_PADDING = 12;

	private static Economy economy(GameTestHelper helper) {
		return EconomyService.economy(helper.getLevel().getServer());
	}

	private static SignText text(String... lines) {
		Component[] parts = new Component[4];

		for (int i = 0; i < 4; i++) {
			parts[i] = Component.literal(i < lines.length ? lines[i] : "");
		}

		List<Component> list = List.of(parts);
		return new SignText(list, list, DyeColor.BLACK, false);
	}

	/** A player with a job, a plot here, a chest and a sign written as given. Returns the sign's position. */
	private static BlockPos shopFor(GameTestHelper helper, ServerPlayer owner, Job job, String... sign) {
		ServerLevel level = helper.getLevel();
		JobsService.choose(owner, job);
		BlockPos chest = helper.absolutePos(new BlockPos(1, 2, 1));
		owner.snapTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5);
		helper.assertTrue(LandService.claim(owner).ok(), "the owner claims a plot");
		level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
		level.setBlockAndUpdate(chest.above(), Blocks.OAK_SIGN.defaultBlockState());
		((SignBlockEntity) level.getBlockEntity(chest.above())).setText(text(sign), SignTextSlot.FRONT);
		return chest.above();
	}

	@GameTest(maxTicks = 200)
	public void townSquareIsBuiltAndOnlyTheHostCanChangeIt(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Town town = economy(helper).town();
		ServerLevel overworld = server.overworld();
		ServerPlayer visitor = helper.makeMockServerPlayerInLevel();

		helper.assertTrue(town.squareBuilt, "the square is built on first start");
		helper.assertTrue(overworld.getBlockState(TownSquare.board(town)).is(Blocks.LECTERN), "the Town Board lectern");
		helper.assertTrue(overworld.getBlockState(TownSquare.center(town)).is(Blocks.WATER), "the fountain");
		helper.assertFalse(LandService.mayBuild(visitor, overworld, TownSquare.center(town).above()), "players cannot build on the square");
		helper.assertTrue(town.clerkId != null, "the square has a Town Clerk");

		// The clerk can only be seen once the square's area is loaded, as it is when a player is nearby.
		BlockPos spot = TownSquare.clerkSpot(town);
		overworld.setChunkForced(spot.getX() >> 4, spot.getZ() >> 4, true);
		helper.succeedWhen(() -> {
			Entity clerk = overworld.getEntity(java.util.UUID.fromString(town.clerkId));
			helper.assertTrue(clerk instanceof Villager && clerk.isAlive(), "the Town Clerk stands at the stall");
			helper.assertTrue(((Villager) clerk).isPermanentlyInvulnerable(), "the clerk cannot be hurt");
			long clerks = overworld.getEntities(EntityTypes.VILLAGER, v -> v.entityTags().contains(TownSquare.CLERK_TAG)).size();
			helper.assertValueEqual(clerks, 1L, "exactly one clerk");
			overworld.setChunkForced(spot.getX() >> 4, spot.getZ() >> 4, false);
		});
	}

	@GameTest
	public void boardOrdersTakeItemsAndPay(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Economy economy = economy(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		economy.repostOrders(new java.util.Random(11));
		Town.Order order = economy.town().orders().stream().filter(o -> o.open() && !o.passenger).findFirst().orElse(null);
		helper.assertTrue(order != null, "the board has item orders");

		helper.assertValueEqual(BoardService.deliver(player, order.id), Result.BAD_AMOUNT, "no items, no delivery");
		player.getInventory().add(new ItemStack(BoardServiceAccess.item(order.item), order.count));
		int before = economy.account(player.getUUID()).balance();

		helper.assertValueEqual(BoardService.deliver(player, order.id), Result.OK, "delivered");
		helper.assertValueEqual(economy.account(player.getUUID()).balance(), before + Town.payFor(order, economy.town().event()), "paid");
		helper.assertFalse(order.open(), "the order is done");
		helper.assertValueEqual(BoardService.deliver(player, order.id), Result.ORDER_TAKEN, "an order is filled once");
		helper.assertTrue(Menus.lastOpened(player) != null && Menus.lastOpened(player).title().equals("Town Board"), "the board menu opened");
		helper.succeed();
	}

	@GameTest
	public void storeBuildsEveryHouseUpgrade(GameTestHelper helper) {
		ServerLevel overworld = helper.getLevel().getServer().overworld();
		Economy economy = economy(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		House house = economy.house(player.getUUID());
		int floor = UpgradeBuilder.floorY(overworld, house);
		economy.setBalance(player.getUUID(), 2000);

		for (String id : new String[] {"porch", "garden", "pool", "stable", "basement", "second_floor", "tower"}) {
			com.goldbucks.economy.StoreKind kind = com.goldbucks.economy.StoreKind.parse(id);
			String blocked = UpgradeBuilder.blocked(overworld, house, kind);
			helper.assertTrue(blocked == null, "nothing in the way of " + id + ": " + blocked);
			helper.assertTrue(economy.upgradeBlockedBy(player.getUUID(), kind) == null, "no land in the way of " + id);
			helper.assertValueEqual(StoreService.buy(player, id), Result.OK, "bought " + id);
		}

		int x = house.lot().minX();
		int z = house.lot().minZ();
		helper.assertTrue(overworld.getBlockState(new BlockPos(x, floor, z + 8)).is(Blocks.OAK_PLANKS), "porch floor");
		helper.assertTrue(overworld.getBlockState(new BlockPos(x + 3, floor, z - 6)).is(Blocks.WATER), "pool in the garden");
		helper.assertTrue(overworld.getBlockState(new BlockPos(x - 2, floor + 1, z + 1)).is(Blocks.HAY_BLOCK), "hay in the stable");
		helper.assertTrue(overworld.getBlockState(new BlockPos(x + 3, floor - 4, z + 3)).is(Blocks.STONE_BRICKS), "basement floor");
		helper.assertTrue(overworld.getBlockState(new BlockPos(x + 3, floor + 8, z + 3)).is(Blocks.OAK_PLANKS), "second floor roof");
		helper.assertTrue(overworld.getBlockState(new BlockPos(x + 5, floor + 10, z + 1)).is(Blocks.LADDER), "ladder up the tower");
		helper.assertTrue(HouseBuilderAccess.doorStillThere(overworld, house), "the front door is untouched");
		helper.assertValueEqual(economy.netWorth(player.getUUID()), 2000, "everything bought still counts toward net worth");
		helper.assertValueEqual(economy.landOwnerAt(x - 3, z + 2), player.getUUID(), "the stable's land is protected");
		helper.succeed();
	}

	@GameTest
	public void upgradesNeverBuildOverWhatAPlayerMade(GameTestHelper helper) {
		ServerLevel overworld = helper.getLevel().getServer().overworld();
		Economy economy = economy(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		House house = economy.house(player.getUUID());
		int floor = UpgradeBuilder.floorY(overworld, house);
		economy.setBalance(player.getUUID(), 500);
		BlockPos inTheWay = new BlockPos(house.lot().minX() + 2, floor + 1, house.lot().minZ() - 3);
		overworld.setBlockAndUpdate(inTheWay, Blocks.CHEST.defaultBlockState());

		helper.assertValueEqual(StoreService.buy(player, "garden"), Result.OVERLAPS, "the chest is in the way");
		helper.assertTrue(overworld.getBlockState(inTheWay).is(Blocks.CHEST), "the chest is still there");
		helper.assertValueEqual(economy.account(player.getUUID()).balance(), 500, "a refused upgrade costs nothing");
		helper.succeed();
	}

	@GameTest
	public void petsAreTameAndCannotBeHurt(GameTestHelper helper) {
		Economy economy = economy(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		economy.setBalance(player.getUUID(), 100);

		helper.assertValueEqual(StoreService.buy(player, "wolf"), Result.OK, "bought a wolf");
		Entity pet = helper.getLevel().getServer().overworld().getEntity(economy.account(player.getUUID()).pets().get("wolf"));
		helper.assertTrue(pet instanceof TamableAnimal tame && tame.isTame() && tame.isOwnedBy(player), "the wolf is tame and the player's");
		helper.assertTrue(((TamableAnimal) pet).isPermanentlyInvulnerable(), "the pet cannot be hurt");
		pet.discard();
		helper.succeed();
	}

	@GameTest
	public void nameColourShowsInTheTeamColour(GameTestHelper helper) {
		Economy economy = economy(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		economy.setBalance(player.getUUID(), 100);
		StoreService.buy(player, "name_colour");
		player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "store colour aqua");
		helper.assertValueEqual(economy.account(player.getUUID()).nameColour(), "aqua", "colour picked");
		com.goldbucks.NameTags.refresh(player.level().getServer());
		var team = player.level().getServer().getScoreboard().getPlayersTeam(player.getScoreboardName());
		helper.assertTrue(team != null && team.getColor().isPresent(), "the name is coloured");
		helper.succeed();
	}

	@GameTest(padding = PLOT_PADDING)
	public void townsfolkBuyOnlyAtFairPrices(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Economy economy = economy(helper);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		BlockPos sign = shopFor(helper, owner, Job.COOK, "[Shop]", "$3");
		Container stock = (Container) helper.getLevel().getBlockEntity(sign.below());
		stock.setItem(0, new ItemStack(Items.BREAD, 5));
		ShopService.onSignEdited(owner, helper.getLevel(), sign);
		Business business = economy.businessesOf(owner.getUUID()).get(0);
		helper.assertTrue(business.shopSigns().contains(sign.asLong()), "the shop is remembered for townsfolk");

		helper.assertTrue(Townspeople.buyFrom(server, owner.getUUID(), null, "Ada"), "bread at $3 (worth $2) sells");
		helper.assertValueEqual(stock.getItem(0).getCount(), 4, "one bread gone");
		helper.assertValueEqual(economy.account(owner.getUUID()).balance(), 23, "the town paid the owner");
		helper.assertTrue(business.taskDoneToday(), "a townsfolk sale is the Cook's task");

		((SignBlockEntity) helper.getLevel().getBlockEntity(sign)).setText(text("[Shop]", "$4"), SignTextSlot.FRONT);
		helper.assertFalse(Townspeople.buyFrom(server, owner.getUUID(), null, "Ada"), "bread at $4 is too pricey");
		helper.assertValueEqual(stock.getItem(0).getCount(), 4, "nothing sold");
		helper.succeed();
	}

	@GameTest(padding = PLOT_PADDING, maxTicks = 600)
	public void walkingCustomersArriveAndBuy(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Economy economy = economy(helper);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		BlockPos sign = shopFor(helper, owner, Job.STORE_OWNER, "[Shop]", "$2");
		Container stock = (Container) helper.getLevel().getBlockEntity(sign.below());
		stock.setItem(0, new ItemStack(Items.BREAD, 5));
		ShopService.onSignEdited(owner, helper.getLevel(), sign);

		Townspeople.visit(server, owner.getUUID(), false);
		helper.succeedWhen(() -> helper.assertValueEqual(stock.getItem(0).getCount(), 4, "a townsfolk customer bought bread"));
	}

	@GameTest
	public void passengersPayTheDriver(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Economy economy = economy(helper);
		ServerPlayer driver = helper.makeMockServerPlayerInLevel();
		economy.startEvent(TownEvent.TOURIST_DAY);
		economy.repostOrders(new java.util.Random(3));
		Townspeople.chooseDestinations(server);
		Town.Order ride = economy.town().orders().stream().filter(o -> o.passenger && o.open()).findFirst().orElse(null);
		helper.assertTrue(ride != null && ride.destination != null, "a passenger is waiting with somewhere to go");
		// Tourists also go shopping at other tests' shops, so this one is an ordinary passenger.
		ride.tourist = false;

		Villager passenger = EntityTypes.VILLAGER.create(helper.getLevel(), EntitySpawnReason.MOB_SUMMONED);
		passenger.snapTo(driver.position());
		helper.getLevel().addFreshEntity(passenger);
		int before = economy.account(driver.getUUID()).balance();
		Townspeople.deliver(server, driver, passenger, ride);

		helper.assertFalse(ride.open(), "the ride is done");
		helper.assertTrue(economy.account(driver.getUUID()).balance() > before, "the driver was paid");
		helper.assertFalse(passenger.isAlive(), "the passenger went on their way");
		economy.stopEvent();
		helper.succeed();
	}

	@GameTest(padding = PLOT_PADDING)
	public void theBankerRunsTheBank(GameTestHelper helper) {
		Economy economy = economy(helper);
		ServerPlayer banker = helper.makeMockServerPlayerInLevel();
		ServerPlayer saver = helper.makeMockServerPlayerInLevel();
		BlockPos sign = shopFor(helper, banker, Job.BANKER, "[Bank]");
		saver.snapTo(sign.getX() + 1.5, sign.getY(), sign.getZ() + 0.5);

		helper.assertValueEqual(BankService.bankHere(saver), economy.businessesOf(banker.getUUID()).get(0).id(), "the saver is at the bank");
		saver.level().getServer().getCommands().performPrefixedCommand(saver.createCommandSourceStack(), "bank deposit 15");
		helper.assertValueEqual(economy.account(saver.getUUID()).savings(), 15, "saved");
		helper.assertValueEqual(economy.account(saver.getUUID()).balance(), 5, "cash left");
		helper.assertValueEqual(economy.account(banker.getUUID()).balance(), 21, "the banker earned the fee");
		helper.assertTrue(economy.businessesOf(banker.getUUID()).get(0).taskDoneToday(), "a customer is the Banker's task");
		economy.fire(banker.getUUID());
		helper.succeed();
	}

	@GameTest
	public void heraldIsComposedAndKeptInTheLectern(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Economy economy = economy(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		economy.setBalance(player.getUUID(), 50);
		player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "herald ad Fresh bread at my shop!");
		helper.assertValueEqual(economy.account(player.getUUID()).balance(), 42, "the ad cost $8");

		List<String> herald = HeraldService.compose(server, new HeraldService.TopSeller("Alex", 7));
		helper.assertTrue(herald.stream().anyMatch(line -> line.contains("Alex sold 7")), "yesterday's top seller");
		helper.assertTrue(herald.stream().anyMatch(line -> line.contains("Fresh bread")), "the ad is printed");
		helper.assertTrue(herald.stream().anyMatch(line -> line.contains("Town Board")), "the orders line");
		helper.assertTrue(economy.account(player.getUUID()).heraldAd() == null, "an ad is printed once");
		LecternBlockEntity lectern = (LecternBlockEntity) server.overworld().getBlockEntity(TownSquare.board(economy.town()));
		helper.assertTrue(lectern.hasBook(), "the Herald is in the lectern");
		helper.succeed();
	}

	@GameTest
	public void loginSummaryShowsSalesWhileAway(GameTestHelper helper) {
		Economy economy = economy(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		economy.recordAwaySale(player.getUUID(), 4);
		economy.recordAwaySale(player.getUUID(), 3);
		helper.assertValueEqual(economy.account(player.getUUID()).awaySales(), 2, "two sales while away");
		HeraldService.loginSummary(player);
		helper.assertValueEqual(economy.account(player.getUUID()).awaySales(), 0, "the summary is shown once");
		helper.succeed();
	}

	@GameTest
	public void storeMenuListsItemsWithReasons(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		StoreService.openStore(player);
		Menus.Menu menu = Menus.lastOpened(player);
		helper.assertTrue(menu != null && menu.title().equals("Town Store"), "the store opened");
		helper.assertTrue(menu.rows().stream().anyMatch(row -> row.text().startsWith("Pool") && !row.enabled()
				&& row.tooltip().contains("Garden first")), "the pool says the garden comes first");
		helper.assertTrue(menu.rows().stream().anyMatch(row -> row.text().startsWith("Porch") && row.enabled()), "the porch can be bought");
		helper.succeed();
	}

	/** Small helpers kept out of the main code. */
	static final class BoardServiceAccess {
		static net.minecraft.world.item.Item item(String id) {
			return net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id));
		}
	}

	static final class HouseBuilderAccess {
		static boolean doorStillThere(ServerLevel level, House house) {
			return com.goldbucks.HouseBuilder.findDoor(level, house.lot()) != null;
		}
	}
}
