package com.goldbucks;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import com.goldbucks.economy.Account;
import com.goldbucks.economy.Economy;
import com.goldbucks.economy.Rules;

/** Help for players: the guide book and the look-up commands everyone may use. */
public final class GuideService {
	public static final String GUIDE_TITLE = "Goldbucks Guide";
	/** Not a player name, so the book never counts as a librarian's own writing. */
	public static final String GUIDE_AUTHOR = "The Bank";

	private static String[] pages() {
		return new String[] {
				"GOLDBUCKS\n\nYou have a house, a job and $" + Rules.startingMoney() + ".\n\nDo your job's task each day to get paid.\n\nType /goldbucks to see every command.",
				"YOUR JOB\n\n/job shows your task, your level and whether today's task is done.\n\nPayday comes once every Minecraft day. No task, no pay.",
				"LEVELS\n\nWork your job on more days to go from Apprentice to Skilled, Expert and Master.\n\nEach level pays more and unlocks a perk.",
				"YOUR PLOT\n\nStand where you want your business and type\n/plot claim\n\nOnly you can build there. /plot allow <name> lets a friend help.",
				"HOW TO SELL\n\n1. On your plot, place a chest and fill it.\n2. Put a sign on the chest.\n3. Line 1: [Shop]\n   Line 2: $5\n\nMost you can charge: $" + Rules.priceCap() + ".",
				"FARMING\n\nWheat grows from seeds. Break grass to find seeds.\n\nHoe dirt or grass within 4 blocks of water, then plant seeds, carrots or potatoes. Bone meal makes them grow fast.",
				"TOWNSFOLK\n\nVillagers from town visit your shops and buy things if the price is fair.\n\nToo pricey or empty, and they walk away!",
				"THE TOWN SQUARE\n\nNear spawn. The Town Board lectern has orders that pay. The Town Clerk runs the store. Passengers wait at the Taxi Stand.",
				"THE STORE\n\nSave up for house upgrades, a bigger plot, pets and more: /store\n\nThey count toward your net worth.",
				"HOW TO BUY\n\nRight-click a shop sign to buy.\n\nYou can go $" + Rules.creditLimit() + " into debt. Debt shows on your name and comes out of your pay.",
				"MONEY\n\n/balance shows your money. /bank to save: savings are safe from tax.\n\nEvery " + Rules.taxPeriodDays() + " days: tax on cash over $" + Rules.taxFreeAmount() + ".",
				"THE TOWN\n\nEach morning brings the Herald (/news) and sometimes an event!\n\nElect a Mayor on Election Day with /vote.",
		};
	}


	private GuideService() {
	}

	public static ItemStack guideBook() {
		List<Filterable<Component>> pages = new ArrayList<>();

		for (String page : pages()) {
			pages.add(Filterable.passThrough(Component.literal(page)));
		}

		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT,
				new WrittenBookContent(Filterable.passThrough(GUIDE_TITLE), GUIDE_AUTHOR, 0, pages, true));
		return book;
	}

	/** A small farming kit, so wheat, carrots and potatoes can be grown from the first day. */
	public static List<ItemStack> farmKit() {
		return List.of(new ItemStack(Items.WOODEN_HOE), new ItemStack(Items.WHEAT_SEEDS, 16), new ItemStack(Items.CARROT, 4),
				new ItemStack(Items.POTATO, 4), new ItemStack(Items.BONE_MEAL, 8));
	}

	/** Gives each player the farming kit once (including players who joined before it existed). */
	public static void giveFarmKitOnce(ServerPlayer player) {
		com.goldbucks.economy.Economy economy = EconomyService.economy(player.level().getServer());
		Account account = economy.account(player.getUUID());

		if (account == null || account.stat("farm_kit") > 0) {
			return;
		}

		economy.count(player.getUUID(), "farm_kit", 1);
		EconomyService.changed(player.level().getServer());

		for (ItemStack stack : farmKit()) {
			if (!player.getInventory().add(stack)) {
				player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(player.level(),
						player.getX(), player.getY(), player.getZ(), stack));
			}
		}

		Text.tell(player, "Here is a farming kit: a hoe, seeds, carrots, potatoes and bone meal. "
				+ "Hoe the grass next to water and plant them. More wheat seeds drop when you break grass.");
	}

	public static void giveGuideBook(ServerPlayer player) {
		player.getInventory().add(guideBook());
	}

	private static void listCommands(ServerPlayer player) {
		Text.tell(player, "Commands everyone can use:");
		Text.tell(player, "/job - your job and today's task.  /job choose <job> - pick a job.");
		Text.tell(player, "/plot - where your land is.  /plot claim - claim a plot where you stand.");
		Text.tell(player, "/shop - how to sell things.");
		Text.tell(player, "/balance - your money.  /balance <player> - someone else's.");
		Text.tell(player, "/home - go to your house.");
		Text.tell(player, "/business - every business and its price.  /business sell <player> - sell yours.");
		Text.tell(player, "/store - the Town Store.  /board - today's town orders.  /bank - your savings.");
		Text.tell(player, "/news - the morning Herald.  /mayor - the Mayor.  /vote - vote on Election Day.");
		Text.tell(player, "/plot allow <player>, /home allow <player> - let someone build.  /bill <player> <amount> <what>.");
		Text.tell(player, "/pet call - call your pets.  /herald ad <message> - an ad in the news.");
		Text.tell(player, "/guide - get another guide book.");
	}

	private static void showBalance(ServerPlayer asker, ServerPlayer whose) {
		Economy economy = EconomyService.economy(asker.level().getServer());
		Account account = economy.account(whose.getUUID());

		if (account == null) {
			Text.warn(asker, "That player has no account.");
			return;
		}

		String who = asker == whose ? "You have " : account.name() + " has ";
		Text.tell(asker, who + Text.money(account.balance()) + (account.inDebt() ? " (in debt)." : "."));

		if (asker == whose) {
			Text.tell(asker, "Tax day is in " + economy.daysUntilTax() + " day(s): " + economy.taxRate() + "% of cash over $" + Rules.taxFreeAmount() + ".");
		}
	}

	public static void register() {
		for (String name : new String[] {"goldbucks", "shop", "balance", "home", "guide"}) {
			WorldRules.openCommand(name);
		}

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("goldbucks").executes(context -> {
				listCommands(context.getSource().getPlayerOrException());
				return 1;
			}));

			dispatcher.register(Commands.literal("shop").executes(context -> {
				ShopService.explain(context.getSource().getPlayerOrException());
				return 1;
			}));

			dispatcher.register(Commands.literal("balance")
					.executes(context -> {
						ServerPlayer player = context.getSource().getPlayerOrException();
						showBalance(player, player);
						return 1;
					})
					.then(Commands.argument("player", EntityArgument.player()).executes(context -> {
						showBalance(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player"));
						return 1;
					})));

			dispatcher.register(Commands.literal("home").executes(context -> {
				ServerPlayer player = context.getSource().getPlayerOrException();

				if (!LandService.goHome(player)) {
					Text.warn(player, "Your house could not be found.");
					return 0;
				}

				return 1;
			}));

			dispatcher.register(Commands.literal("guide").executes(context -> {
				ServerPlayer player = context.getSource().getPlayerOrException();
				giveGuideBook(player);
				Text.tell(player, "Here is a new guide book.");
				return 1;
			}));
		});
	}
}
