package com.goldbucks;

import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.TeamColor;

import com.goldbucks.economy.Account;
import com.goldbucks.economy.Business;
import com.goldbucks.economy.Economy;
import com.goldbucks.economy.House;
import com.goldbucks.economy.Result;
import com.goldbucks.economy.StoreKind;
import com.goldbucks.economy.TownData;

/** The Town Store, run by the Town Clerk: house upgrades, land, pets, style and business extras. */
public final class StoreService {
	/** Colours a player can pick for their name. */
	public static final List<TeamColor> COLOURS = List.of(TeamColor.GOLD, TeamColor.AQUA, TeamColor.GREEN,
			TeamColor.LIGHT_PURPLE, TeamColor.YELLOW, TeamColor.BLUE, TeamColor.RED, TeamColor.DARK_GREEN,
			TeamColor.DARK_AQUA, TeamColor.DARK_PURPLE);

	private StoreService() {
	}

	public static void register() {
		for (String name : new String[] {"store", "pet", "herald"}) {
			WorldRules.openCommand(name);
		}

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("store")
					.executes(context -> {
						openStore(context.getSource().getPlayerOrException());
						return 1;
					})
					.then(Commands.literal("buy").then(Commands.argument("item", StringArgumentType.word())
							.suggests((context, builder) -> {
								TownData.current().store.forEach(item -> builder.suggest(item.id));
								return builder.buildFuture();
							})
							.executes(context -> buy(context.getSource().getPlayerOrException(),
									StringArgumentType.getString(context, "item")).ok() ? 1 : 0)))
					.then(Commands.literal("colour").then(Commands.argument("colour", StringArgumentType.word())
							.suggests((context, builder) -> {
								COLOURS.forEach(colour -> builder.suggest(colour.getSerializedName()));
								return builder.buildFuture();
							})
							.executes(context -> setColour(context.getSource().getPlayerOrException(),
									StringArgumentType.getString(context, "colour")) ? 1 : 0))));

			dispatcher.register(Commands.literal("pet").then(Commands.literal("call").executes(context -> {
				callPets(context.getSource().getPlayerOrException());
				return 1;
			})));

			dispatcher.register(Commands.literal("herald").then(Commands.literal("ad")
					.then(Commands.argument("message", StringArgumentType.greedyString()).executes(context ->
							heraldAd(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "message")) ? 1 : 0))));
		});
	}

	/** Opens the store screen. */
	public static void openStore(ServerPlayer player) {
		Economy economy = EconomyService.economy(player.level().getServer());
		Account account = economy.account(player.getUUID());
		Menus.Menu menu = new Menus.Menu("Town Store").subtitle("Cash to spend: " + Text.money(account.balance()) + "   Net worth: "
				+ Text.money(economy.netWorth(player.getUUID())));
		StoreKind.Category category = null;

		for (TownData.StoreItem item : TownData.current().store) {
			if (!item.enabled || item.kind() == null) {
				continue;
			}

			if (item.kind().category() != category) {
				category = item.kind().category();
				menu.text("-- " + category.title() + " --");
			}

			Result check = economy.canBuy(player.getUUID(), item.id);
			String state = check == Result.ALREADY_OWNED ? "  (yours)" : "";
			String tooltip = item.description + (check.ok() || check == Result.ALREADY_OWNED ? "" : "\n" + reason(check, item));
			menu.button(item.name + "  " + Text.money(item.price) + state, tooltip, check == Result.ALREADY_OWNED ? "Owned" : "Buy",
					"store buy " + item.id, check.ok());
		}

		if (account.owns(StoreKind.NAME_COLOUR.id())) {
			menu.text("-- Your name colour --");

			for (TeamColor colour : COLOURS) {
				menu.button(colour.getSerializedName().replace('_', ' '), account.nameColour() == null ? "Free the first time" : "$10 to change",
						"Pick", "store colour " + colour.getSerializedName(), true);
			}
		}

		menu.text("-- Bank --");
		menu.button("Savings: " + Text.money(account.savings()), "Money in the bank is safe from tax.", "Bank", "bank", true);

		Menus.open(player, menu);
	}

	static String reason(Result result, TownData.StoreItem item) {
		return switch (result) {
			case NEEDS_OTHER_ITEM -> "You need the " + TownData.current().storeItem(item.requires).name + " first.";
			case OVERLAPS -> "Someone's land is in the way.";
			case NO_BUSINESS -> "You need a business" + (item.kind() == StoreKind.BIGGER_PLOT || item.kind() == StoreKind.BIGGEST_PLOT
					? " with a plot." : ".");
			case OVER_CREDIT_LIMIT -> "You cannot afford it yet.";
			default -> result.message();
		};
	}

	/** Buys a store item and makes it happen in the world. */
	public static Result buy(ServerPlayer player, String id) {
		MinecraftServer server = player.level().getServer();
		Economy economy = EconomyService.economy(server);
		TownData.StoreItem item = TownData.current().storeItem(id);
		Result check = economy.canBuy(player.getUUID(), id);

		if (!check.ok()) {
			Text.warn(player, item == null ? Result.NO_SUCH_ITEM.message() : reason(check, item));
			return check;
		}

		StoreKind kind = item.kind();
		ServerLevel level = server.overworld();
		House house = economy.house(player.getUUID());

		// Check the space before taking the money, so a refused build costs nothing.
		if (kind.category() == StoreKind.Category.HOME) {
			String blocked = UpgradeBuilder.blocked(level, house, kind);

			if (blocked != null) {
				Text.warn(player, blocked);
				return Result.OVERLAPS;
			}
		}

		Result result = economy.buy(player.getUUID(), id);

		if (!result.ok()) {
			Text.warn(player, reason(result, item));
			return result;
		}

		EconomyService.changed(server);
		NameTags.refresh(server);
		Text.tell(player, "You bought the " + item.name + " for " + Text.money(item.price) + ". Balance: "
				+ Text.money(economy.account(player.getUUID()).balance()) + ".");

		switch (kind) {
			case PORCH, GARDEN, BASEMENT, SECOND_FLOOR, STABLE, POOL, TOWER -> {
				UpgradeBuilder.build(level, house, kind);
				Text.tell(player, "The builders are at your house now. Go and watch!");
			}
			case BIGGER_PLOT, BIGGEST_PLOT -> {
				Business business = economy.plottedBusiness(player.getUUID());
				LandService.markCorners(level, business.plot());
				Text.tell(player, "Your plot is now " + business.plot().size() + " by " + business.plot().size() + ". New corner posts mark it.");
			}
			case CAT, WOLF, PARROT -> spawnPet(player, kind);
			case AXOLOTL -> give(player, new ItemStack(Items.AXOLOTL_BUCKET));
			case NAME_COLOUR -> {
				Text.tell(player, "Pick your colour:");
				openStore(player);
			}
			case SHOP_BANNER -> {
				give(player, new ItemStack(Items.BANNER.white()));
				ItemStack open = new ItemStack(Items.OAK_SIGN);
				open.set(DataComponents.CUSTOM_NAME, Component.literal("OPEN sign"));
				give(player, open);
				give(player, new ItemStack(Items.GLOW_INK_SAC));
				Text.tell(player, "Design the banner on a loom, hang it over your shop, and write OPEN on the sign. "
						+ "Rub the glow ink sac on the sign to make it glow.");
			}
			case FIREWORKS -> {
				BlockPos above = house == null || UpgradeBuilder.floorY(level, house) == Integer.MIN_VALUE ? player.blockPosition().above(3)
						: new BlockPos(house.lot().minX() + 3, UpgradeBuilder.floorY(level, house) + 6, house.lot().minZ() + 3);
				Fx.fireworksShow(level, above);
			}
			case ADVERT -> Text.tell(player, "Your advert is up! Two extra townsfolk will visit your shops today.");
			case HERALD_AD -> {
				economy.setHeraldAd(player.getUUID(), HeraldService.UNWRITTEN_AD);
				Text.tell(player, "Now type /herald ad <your message> to write your line in tomorrow's news.");
			}
		}

		if (kind == StoreKind.ADVERT) {
			Townspeople.visit(server, player.getUUID(), false);
			Townspeople.visit(server, player.getUUID(), false);
		}

		Achievements.check(player);
		return Result.OK;
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack));
		}
	}

	static void spawnPet(ServerPlayer player, StoreKind kind) {
		ServerLevel level = player.level();
		EntityType<? extends TamableAnimal> type = switch (kind) {
			case CAT -> EntityTypes.CAT;
			case WOLF -> EntityTypes.WOLF;
			default -> EntityTypes.PARROT;
		};
		TamableAnimal pet = type.create(level, EntitySpawnReason.MOB_SUMMONED);

		if (pet == null) {
			return;
		}

		pet.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0f);
		pet.tame(player);
		pet.setCustomName(Component.literal(player.getGameProfile().name() + "'s " + kind.id()));
		Townspeople.makeHarmless(pet);
		level.addFreshEntity(pet);
		EconomyService.economy(level.getServer()).setPet(player.getUUID(), kind.id(), pet.getUUID());
		EconomyService.changed(level.getServer());
		Fx.sparkle(level, pet.blockPosition());
		Text.tell(player, "Meet your new " + kind.id() + "! If it wanders off, type /pet call.");
	}

	/** Brings the player's pets to them. */
	static void callPets(ServerPlayer player) {
		Economy economy = EconomyService.economy(player.level().getServer());
		Account account = economy.account(player.getUUID());
		int found = 0;

		for (var entry : account.pets().entrySet()) {
			Entity pet = player.level().getServer().overworld().getEntity(entry.getValue());

			if (pet != null && pet.isAlive()) {
				pet.teleportTo(player.level(), player.getX(), player.getY(), player.getZ(), java.util.Set.of(), pet.getYRot(), pet.getXRot(), false);
				found++;
			}
		}

		if (found == 0) {
			Text.tell(player, account.pets().isEmpty() ? "You have no pets. The Town Store sells them."
					: "Your pets are too far away to hear you. Go back to where you left them, then call again.");
		} else {
			Text.tell(player, "Here they come!");
		}
	}

	static boolean setColour(ServerPlayer player, String name) {
		TeamColor colour = TeamColor.byName(name.toLowerCase(Locale.ROOT));

		if (colour == null || !COLOURS.contains(colour)) {
			Text.warn(player, "Pick one of the colours in the store.");
			return false;
		}

		MinecraftServer server = player.level().getServer();
		Result result = EconomyService.economy(server).setNameColour(player.getUUID(), colour.getSerializedName());

		if (!result.ok()) {
			Text.warn(player, result == Result.NEEDS_OTHER_ITEM ? "Buy the Name colour in the Town Store first." : result.message());
			return false;
		}

		EconomyService.changed(server);
		NameTags.refresh(server);
		EconomyService.resendSidebar();
		player.sendSystemMessage(Component.literal("Your name is now this colour!").withStyle(style -> style.withColor(colour.textColor())));
		return true;
	}

	static boolean heraldAd(ServerPlayer player, String message) {
		MinecraftServer server = player.level().getServer();
		Economy economy = EconomyService.economy(server);
		String text = message.length() > 60 ? message.substring(0, 60) : message;

		// Writing an ad without having bought one buys it now.
		if (economy.account(player.getUUID()).heraldAd() == null) {
			Result bought = economy.buy(player.getUUID(), StoreKind.HERALD_AD.id());

			if (!bought.ok()) {
				Text.warn(player, bought.message());
				return false;
			}
		}

		economy.setHeraldAd(player.getUUID(), player.getGameProfile().name() + ": " + text);
		EconomyService.changed(server);
		Text.tell(player, "Your ad will be in tomorrow's Goldbucks Herald.");
		return true;
	}
}
