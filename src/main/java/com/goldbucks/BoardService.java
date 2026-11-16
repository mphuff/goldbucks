package com.goldbucks;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.goldbucks.economy.Economy;
import com.goldbucks.economy.Result;
import com.goldbucks.economy.Settings;
import com.goldbucks.economy.Town;

/** The Town Board: the day's orders, shown at the lectern on the town square or with /board. */
public final class BoardService {
	private BoardService() {
	}

	public static void register() {
		WorldRules.openCommand("board");
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("board")
				.executes(context -> {
					open(context.getSource().getPlayerOrException());
					return 1;
				})
				.then(Commands.literal("deliver").then(Commands.argument("order", IntegerArgumentType.integer(1))
						.executes(context -> deliver(context.getSource().getPlayerOrException(),
								IntegerArgumentType.getInteger(context, "order")).ok() ? 1 : 0)))));
	}

	static Item itemOf(String id) {
		Identifier parsed = id == null ? null : Identifier.tryParse(id);
		return parsed == null ? null : BuiltInRegistries.ITEM.getValue(parsed);
	}

	/** How many of an item the player is carrying. */
	static int carried(ServerPlayer player, Item item) {
		int total = 0;

		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);

			// Shop-bought items cannot be passed on to the town, so buying and reselling does not pay.
			if (stack.is(item) && ShopService.expiryDay(stack) < 0) {
				total += stack.getCount();
			}
		}

		return total;
	}

	/** Where to find things the town often asks for, shown under an order on the board. */
	public static String hint(String itemId) {
		return switch (itemId == null ? "" : itemId) {
			case "minecraft:wheat" -> "Grow it from wheat seeds. Break grass to find seeds.";
			case "minecraft:carrot", "minecraft:potato" -> "Plant one on farmland near water; each grows into more.";
			case "minecraft:bread" -> "Craft 3 wheat into bread.";
			case "minecraft:baked_potato" -> "Cook potatoes in a furnace.";
			case "minecraft:egg" -> "Chickens lay eggs. Breed them with seeds.";
			case "minecraft:white_wool" -> "Shear sheep (craft shears from 2 iron).";
			case "minecraft:sugar_cane" -> "Grows beside water on sand or grass.";
			case "minecraft:apple" -> "Break oak leaves; sometimes an apple drops.";
			case "minecraft:pumpkin" -> "Found in grassy areas, or grow it from pumpkin seeds.";
			case "minecraft:book" -> "Craft paper (from sugar cane) and leather.";
			case "minecraft:paper" -> "Craft 3 sugar cane into paper.";
			case "minecraft:gold_ingot", "minecraft:raw_gold" -> "Mine gold ore deep underground and smelt it.";
			default -> null;
		};
	}

	public static void open(ServerPlayer player) {
		Economy economy = EconomyService.economy(player.level().getServer());
		Town town = economy.town();
		int left = Settings.current().ordersPerPlayerPerDay - economy.account(player.getUUID()).ordersToday();
		Menus.Menu menu = new Menus.Menu("Town Board").subtitle("Day " + economy.day() + ". You can fill " + Math.max(0, left)
				+ " more order" + (left == 1 ? "" : "s") + " today.");

		if (town.orders().isEmpty()) {
			menu.text(town.event() == com.goldbucks.economy.TownEvent.HOLIDAY ? "It is a holiday! No orders today." : "No orders yet. New ones come every morning.");
		}

		for (Town.Order order : town.orders()) {
			String tag = order.job == null ? "" : "[" + order.job.title() + "] ";
			int pay = Town.payFor(order, town.event());
			String text = tag + order.describe() + " - " + Text.money(pay);

			if (!order.open()) {
				String who = economy.account(order.filledBy) == null ? "someone" : economy.account(order.filledBy).name();
				menu.text(text + " (done by " + who + ")");
			} else if (order.passenger) {
				menu.text(text + " (waiting at the Taxi Stand)");
			} else {
				Item item = itemOf(order.item);
				int have = item == null ? 0 : carried(player, item);
				boolean can = have >= order.count && economy.canFill(player.getUUID(), order.id).ok();
				String hint = hint(order.item);
				menu.button(text, "You have " + have + " of " + order.count + "." + (hint == null ? "" : "\n" + hint), "Deliver",
						"board deliver " + order.id, can);
			}
		}

		Menus.open(player, menu);
	}

	/** Takes the order's items from the player and pays them. */
	public static Result deliver(ServerPlayer player, int orderId) {
		MinecraftServer server = player.level().getServer();
		Economy economy = EconomyService.economy(server);
		Town.Order order = economy.town().order(orderId);
		Result check = economy.canFill(player.getUUID(), orderId);

		if (!check.ok()) {
			Text.warn(player, check.message());
			return check;
		}

		if (order.passenger) {
			Text.warn(player, "Passengers wait at the Taxi Stand. Pick them up in a boat.");
			return Result.NO_SUCH_ORDER;
		}

		Item item = itemOf(order.item);

		if (item == null || carried(player, item) < order.count) {
			Text.warn(player, "You need " + order.describe() + " (that you gathered or made yourself).");
			return Result.BAD_AMOUNT;
		}

		int toTake = order.count;

		for (int slot = 0; slot < player.getInventory().getContainerSize() && toTake > 0; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);

			if (stack.is(item) && ShopService.expiryDay(stack) < 0) {
				int take = Math.min(toTake, stack.getCount());
				stack.shrink(take);
				toTake -= take;
			}
		}

		Economy.Filled filled = economy.fill(player.getUUID(), orderId);
		EconomyService.changed(server);
		Text.tell(player, "Order delivered! You earned " + Text.money(filled.pay()) + ".");

		if (filled.taskDone()) {
			Text.tell(player, order.job.title() + " task done! You will be paid at the end of the day.");
		}

		if (economy.town().event() != null) {
			economy.joinEvent(player.getUUID());
		}

		Fx.sparkle(player.level(), player.blockPosition());
		Achievements.check(player);
		open(player);
		return Result.OK;
	}
}
