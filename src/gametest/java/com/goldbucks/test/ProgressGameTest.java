package com.goldbucks.test;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.goldbucks.Achievements;
import com.goldbucks.EconomyService;
import com.goldbucks.EventService;
import com.goldbucks.GoldbucksMod;
import com.goldbucks.JobsService;
import com.goldbucks.LandService;
import com.goldbucks.NameTags;
import com.goldbucks.TownSquare;
import com.goldbucks.economy.Economy;
import com.goldbucks.economy.Job;
import com.goldbucks.economy.Level;
import com.goldbucks.economy.Settings;
import com.goldbucks.economy.Town;
import com.goldbucks.economy.TownEvent;

/** Getting better at a job, achievements, the Mayor, and the events in the world. */
public class ProgressGameTest {
	private static Economy economy(GameTestHelper helper) {
		return EconomyService.economy(helper.getLevel().getServer());
	}

	@GameTest
	public void levelsShowOnTheNameTagAndCooksGetPerks(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Economy economy = economy(helper);
		ServerPlayer cook = helper.makeMockServerPlayerInLevel();
		JobsService.choose(cook, Job.COOK);
		NameTags.refresh(server);
		helper.assertValueEqual(NameTags.titleOf(cook), "Cook", "a new Cook's title");

		for (int day = 0; day < Settings.current().skilledDays; day++) {
			economy.completeTask(cook.getUUID(), Job.COOK);
			JobsService.endDayNow(server);
		}

		helper.assertValueEqual(economy.account(cook.getUUID()).level(Job.COOK), Level.SKILLED, "five days worked makes a Skilled Cook");
		NameTags.refresh(server);
		helper.assertValueEqual(NameTags.titleOf(cook), "Skilled Cook", "the level shows in front of the name");

		ItemStack bread = new ItemStack(Items.BREAD);
		int before = bread.get(DataComponents.FOOD).nutrition();
		JobsService.cookPerks(cook, bread);
		helper.assertValueEqual(bread.get(DataComponents.FOOD).nutrition(), before + 1, "a Skilled Cook's food fills one more point");
		JobsService.cookPerks(cook, bread);
		helper.assertValueEqual(bread.get(DataComponents.FOOD).nutrition(), before + 1, "the perk is given once per item");
		helper.succeed();
	}

	@GameTest
	public void achievementsAreAdvancementsWithRewards(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Economy economy = economy(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Settings rewarding = Settings.current().copy();
		rewarding.achievementReward = 5;
		Settings original = Settings.current();
		Settings.activate(rewarding);

		try {
			JobsService.choose(player, Job.STORE_OWNER);
			Achievements.check(player);
			helper.assertTrue(economy.account(player.getUUID()).achievements().contains("first_job"), "picking a job is an achievement");
			helper.assertValueEqual(economy.account(player.getUUID()).balance(), 25, "and the town paid $5");
			AdvancementHolder advancement = server.getAdvancements().get(GoldbucksMod.id("first_job"));
			helper.assertTrue(advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone(),
					"it shows as a vanilla advancement");
			Achievements.check(player);
			helper.assertValueEqual(economy.account(player.getUUID()).balance(), 25, "each achievement pays once");
		} finally {
			Settings.activate(original);
		}

		for (Achievements.Achievement achievement : Achievements.ALL) {
			helper.assertTrue(server.getAdvancements().get(GoldbucksMod.id(achievement.id())) != null,
					"every achievement has its advancement: " + achievement.id());
		}

		helper.assertTrue(server.getAdvancements().get(GoldbucksMod.id("welcome")) != null, "the Goldbucks tab's root");
		helper.succeed();
	}

	@GameTest
	public void electionsMakeAMayor(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Economy economy = economy(helper);
		ServerPlayer alex = helper.makeMockServerPlayerInLevel();
		ServerPlayer kai = helper.makeMockServerPlayerInLevel();
		EventService.hostStart(server, TownEvent.ELECTION_DAY);

		helper.assertTrue(economy.runForMayor(alex.getUUID(), "Free cake").ok(), "Alex runs");
		helper.assertFalse(economy.vote(alex.getUUID(), alex.getUUID()).ok(), "no voting for yourself");
		helper.assertTrue(economy.vote(kai.getUUID(), alex.getUUID()).ok(), "Kai votes for Alex");
		JobsService.endDayNow(server);

		helper.assertValueEqual(economy.town().mayor(), alex.getUUID(), "Alex is Mayor");
		NameTags.refresh(server);
		helper.assertTrue(NameTags.titleOf(alex).startsWith("Mayor"), "the Mayor's name tag");

		CommandSourceStack asAlex = alex.createCommandSourceStack();
		server.getCommands().performPrefixedCommand(asAlex, "mayor tax 6");
		helper.assertValueEqual(economy.taxRate(), 6, "the Mayor set the tax rate");
		server.getCommands().performPrefixedCommand(asAlex, "mayor tax 50");
		helper.assertValueEqual(economy.taxRate(), 6, "but not outside the allowed range");

		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "host impeach");
		helper.assertTrue(economy.town().mayor() == null, "the host can remove a Mayor");
		helper.assertValueEqual(economy.taxRate(), Settings.current().taxPercent, "the tax rate goes back to the setting");
		EventService.hostStop(server);
		helper.succeed();
	}

	@GameTest
	public void treasureHuntBuriesAChestThatPaysOnce(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Economy economy = economy(helper);
		ServerPlayer finder = helper.makeMockServerPlayerInLevel();
		EventService.hostStart(server, TownEvent.TREASURE_HUNT);
		String packed = economy.town().eventData().get("treasure");
		helper.assertTrue(packed != null, "the treasure is buried");
		BlockPos chest = BlockPos.of(Long.parseLong(packed));
		ServerLevel overworld = server.overworld();
		helper.assertTrue(overworld.getBlockEntity(chest) instanceof Container box && box.getItem(13).is(Items.DIAMOND), "with diamonds");
		helper.assertTrue(EventService.clue(server).contains("blocks"), "the Herald has a clue");
		helper.assertTrue(EventService.barTitle().startsWith("Treasure Hunt"), "the boss bar shows the event");

		int before = economy.account(finder.getUUID()).balance();
		helper.assertTrue(EventService.onOpen(finder, chest), "opening the chest finds the treasure");
		helper.assertValueEqual(economy.account(finder.getUUID()).balance(), before + 30, "+$30");
		helper.assertFalse(EventService.onOpen(finder, chest), "only once");
		EventService.hostStop(server);
		helper.assertValueEqual(EventService.barTitle(), "", "the boss bar goes when the event stops");
		helper.succeed();
	}

	@GameTest
	public void stormRepairsPayForEachPieceFixed(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Economy economy = economy(helper);
		ServerLevel overworld = server.overworld();
		ServerPlayer fixer = helper.makeMockServerPlayerInLevel();
		Town town = economy.town();
		EventService.hostStart(server, TownEvent.STORM_REPAIRS);
		BlockPos lantern = TownSquare.lanternPosts(town).get(4);
		helper.assertTrue(overworld.getBlockState(lantern).isAir(), "the storm knocked a lantern down");

		BlockPos post = TownSquare.lanternPosts(town).get(0);
		int before = economy.account(fixer.getUUID()).balance();
		overworld.setBlockAndUpdate(post, Blocks.OAK_FENCE.defaultBlockState());
		EventService.onPlaced(fixer, post, Blocks.OAK_FENCE.defaultBlockState());
		overworld.setBlockAndUpdate(lantern, Blocks.LANTERN.defaultBlockState());
		EventService.onPlaced(fixer, lantern, Blocks.LANTERN.defaultBlockState());
		helper.assertValueEqual(economy.account(fixer.getUUID()).balance(), before + 6, "$3 for each piece put back");
		EventService.onPlaced(fixer, lantern, Blocks.LANTERN.defaultBlockState());
		helper.assertValueEqual(economy.account(fixer.getUUID()).balance(), before + 6, "each piece pays once");

		EventService.hostStop(server);
		helper.assertTrue(overworld.getBlockState(TownSquare.lanternPosts(town).get(5)).is(Blocks.LANTERN), "stopping the event repairs the rest");
		helper.succeed();
	}

	@GameTest
	public void constructionBoomOpensASiteOnTheSquare(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Economy economy = economy(helper);
		ServerLevel overworld = server.overworld();
		ServerPlayer builder = helper.makeMockServerPlayerInLevel();
		Town town = economy.town();
		BlockPos corner = TownSquare.constructionCorner(town);
		helper.assertFalse(LandService.mayBuild(builder, overworld, corner), "the square is closed to building");

		EventService.hostStart(server, TownEvent.CONSTRUCTION_BOOM);
		helper.assertTrue(LandService.mayBuild(builder, overworld, corner), "the site is open during the event");
		helper.assertFalse(LandService.mayBuild(builder, overworld, corner.offset(-3, 0, -3)), "but only the site");
		int before = economy.account(builder.getUUID()).balance();

		for (int dx = 0; dx < 5; dx++) {
			for (int dz = 0; dz < 5; dz++) {
				BlockPos pos = corner.offset(dx, 0, dz);
				overworld.setBlockAndUpdate(pos, Blocks.OAK_PLANKS.defaultBlockState());
				EventService.onPlaced(builder, pos, Blocks.OAK_PLANKS.defaultBlockState());
			}
		}

		helper.assertValueEqual(economy.account(builder.getUUID()).balance(), before + 30, "the whole $30 for building it alone");

		for (int dx = 0; dx < 5; dx++) {
			for (int dz = 0; dz < 5; dz++) {
				overworld.setBlockAndUpdate(corner.offset(dx, 0, dz), Blocks.AIR.defaultBlockState());
			}
		}

		EventService.hostStop(server);
		helper.succeed();
	}

	@GameTest
	public void lostPetAndHolidayAndHostEventCommands(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Economy economy = economy(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		CommandSourceStack host = server.createCommandSourceStack();

		server.getCommands().performPrefixedCommand(host, "host event start lost_pet");
		helper.assertValueEqual(economy.town().event(), TownEvent.LOST_PET, "the host started Lost Pet");
		helper.assertTrue(economy.town().eventData().containsKey("cat") && economy.town().eventData().containsKey("cat_x"),
				"Whiskers is out there somewhere");
		helper.assertTrue(EventService.clue(server).startsWith("Whiskers was last seen"), "the Herald says where");

		int before = economy.account(player.getUUID()).balance();
		server.getCommands().performPrefixedCommand(host, "host event start holiday");
		helper.assertFalse(economy.town().eventData().containsKey("cat"), "starting another event forgets the cat");
		helper.assertValueEqual(economy.account(player.getUUID()).balance(), before + 5, "a holiday gives everyone $5");

		server.getCommands().performPrefixedCommand(host, "host event start no_such_event");
		helper.assertValueEqual(economy.town().event(), TownEvent.HOLIDAY, "an unknown event changes nothing");

		player.addTag("bonus_target");
		server.getCommands().performPrefixedCommand(host, "host bonus @a[tag=bonus_target,limit=1] 12 the best storefront");
		helper.assertValueEqual(economy.account(player.getUUID()).balance(), before + 5 + 12, "the host's bonus");

		server.getCommands().performPrefixedCommand(host, "host contest best house");
		helper.assertValueEqual(economy.town().event(), TownEvent.CONTEST, "a contest");
		server.getCommands().performPrefixedCommand(host, "host contest winner @a[tag=bonus_target,limit=1] 20");
		helper.assertValueEqual(economy.account(player.getUUID()).balance(), before + 5 + 12 + 20, "the contest prize");
		helper.assertTrue(economy.town().event() == null, "the contest ends when the winner is named");

		// Other players cannot start events.
		server.getCommands().performPrefixedCommand(player.createCommandSourceStack()
				.withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER), "host event start market_day");
		helper.assertTrue(economy.town().event() == null, "only the host starts events");
		helper.succeed();
	}

	@GameTest
	public void everyEventCanStartAndStopCleanly(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Economy economy = economy(helper);

		for (TownEvent event : TownEvent.values()) {
			if (event == TownEvent.CONTEST) {
				continue;
			}

			EventService.hostStart(server, event);
			helper.assertValueEqual(economy.town().event(), event, "started " + event.id());
			helper.assertFalse(EventService.barTitle().isEmpty(), "boss bar for " + event.id());
			EventService.hostStop(server);
			helper.assertTrue(economy.town().event() == null, "stopped " + event.id());
		}

		helper.succeed();
	}
}
