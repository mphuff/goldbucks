package com.goldbucks.test;

import java.util.List;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.goldbucks.EconomyService;
import com.goldbucks.JobsService;
import com.goldbucks.economy.Economy;
import com.goldbucks.economy.Job;

/**
 * Each test uses different exclusive jobs because all tests share one server and one world.
 * The world is wiped before every run (see build.gradle).
 */
public class JobsGameTest {
	private static Economy economy(GameTestHelper helper) {
		return EconomyService.economy(helper.getLevel().getServer());
	}

	private static void leave(GameTestHelper helper, ServerPlayer... players) {
		for (ServerPlayer player : players) {
			economy(helper).fire(player.getUUID());
			player.connection.disconnect(Component.literal("test over"));
		}
	}

	private static boolean taskDone(GameTestHelper helper, ServerPlayer player) {
		List<com.goldbucks.economy.Business> owned = economy(helper).businessesOf(player.getUUID());
		return !owned.isEmpty() && owned.get(0).taskDoneToday();
	}

	@GameTest
	public void choosingAJob(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer first = helper.makeMockServerPlayerInLevel();
		ServerPlayer second = helper.makeMockServerPlayerInLevel();
		Economy economy = economy(helper);
		helper.assertTrue(economy.needsJob(first.getUUID()), "a new player has no job");

		// The job command must work for ordinary players, who cannot use cheat commands.
		CommandSourceStack firstSource = first.createCommandSourceStack();
		server.getCommands().performPrefixedCommand(firstSource, "job choose vehicle_dealer");
		helper.assertFalse(economy.needsJob(first.getUUID()), "the player now has a job");
		helper.assertTrue(economy.businessesOf(first.getUUID()).get(0).job() == Job.VEHICLE_DEALER, "and it is the one chosen");

		server.getCommands().performPrefixedCommand(second.createCommandSourceStack(), "job choose vehicle_dealer");
		helper.assertTrue(economy.needsJob(second.getUUID()), "the top job can only be held by one player");

		server.getCommands().performPrefixedCommand(second.createCommandSourceStack(), "job choose store_owner");
		helper.assertTrue(economy.businessesOf(second.getUUID()).get(0).job() == Job.STORE_OWNER, "a shared job is still free");

		leave(helper, first, second);
		helper.succeed();
	}

	@GameTest
	public void tasksCookTakesFoodFromFurnace(GameTestHelper helper) {
		ServerPlayer cook = helper.makeMockServerPlayerInLevel();
		JobsService.choose(cook, Job.COOK);
		helper.assertFalse(taskDone(helper, cook), "task starts undone");

		SimpleContainer furnace = new SimpleContainer(3);
		FurnaceResultSlot slot = new FurnaceResultSlot(cook, furnace, 2, 0, 0);
		slot.onTake(cook, new ItemStack(Items.IRON_INGOT));
		helper.assertFalse(taskDone(helper, cook), "smelting iron is not cooking");

		slot.onTake(cook, new ItemStack(Items.COOKED_BEEF));
		helper.assertTrue(taskDone(helper, cook), "taking cooked food from a furnace does the cook's task");

		leave(helper, cook);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void tasksLibrarianSignsABook(GameTestHelper helper) {
		ServerPlayer librarian = helper.makeMockServerPlayerInLevel();
		JobsService.choose(librarian, Job.LIBRARIAN);

		// The guide book every player is given must not count as the librarian's own writing.
		helper.assertFalse(taskDone(helper, librarian), "the guide book is not the librarian's work");

		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
				Filterable.passThrough("My Book"), librarian.getGameProfile().name(), 0, List.of(), true));
		librarian.getInventory().add(book);

		helper.succeedWhen(() -> {
			helper.assertTrue(taskDone(helper, librarian), "signing a book does the librarian's task");
		});
	}

	@GameTest(maxTicks = 160, skyAccess = true)
	public void tasksTaxiDriverGivesARide(GameTestHelper helper) {
		ServerPlayer driver = helper.makeMockServerPlayerInLevel();
		ServerPlayer passenger = helper.makeMockServerPlayerInLevel();
		JobsService.choose(driver, Job.TAXI_DRIVER);

		// New players are taken to their house a few ticks after joining, so start the ride after that.
		Entity[] boat = new Entity[1];
		helper.runAfterDelay(10, () -> {
			boat[0] = helper.spawn(EntityTypes.OAK_BOAT, new BlockPos(1, 2, 1));
			driver.startRiding(boat[0], true, false);
			passenger.startRiding(boat[0], true, false);
			helper.assertTrue(driver.getVehicle() == boat[0] && passenger.getVehicle() == boat[0], "both players are in the boat");
		});

		// Move the boat a little every tick, as a real ride would.
		for (int tick = 11; tick <= 130; tick++) {
			helper.runAfterDelay(tick, () -> boat[0].setPos(boat[0].getX() + 0.5, boat[0].getY(), boat[0].getZ()));
		}

		// Other tests may end the game day while this ride is going on, which clears the day's tasks,
		// so this checks the driver's count of rides, which a new day does not reset.
		int ridesBefore = economy(helper).account(driver.getUUID()).stat("passengers");
		helper.succeedWhen(() -> {
			helper.assertTrue(taskDone(helper, driver) || economy(helper).account(driver.getUUID()).stat("passengers") > ridesBefore,
					"carrying a passenger does the taxi driver's task");
		});
	}

	@GameTest
	public void paydayPaysOnlyThoseWhoDidTheirTask(GameTestHelper helper) {
		ServerPlayer worker = helper.makeMockServerPlayerInLevel();
		ServerPlayer slacker = helper.makeMockServerPlayerInLevel();
		JobsService.choose(worker, Job.STORE_OWNER);
		JobsService.choose(slacker, Job.STORE_OWNER);
		JobsService.completeTask(worker, Job.STORE_OWNER);
		Economy economy = economy(helper);

		JobsService.endDayNow(helper.getLevel().getServer());

		helper.assertValueEqual(economy.account(worker.getUUID()).balance(), 28, "worker's balance after payday");
		helper.assertValueEqual(economy.account(slacker.getUUID()).balance(), 20, "slacker's balance after payday");
		helper.assertFalse(taskDone(helper, worker), "the task must be done again tomorrow");

		leave(helper, worker, slacker);
		helper.succeed();
	}
}
